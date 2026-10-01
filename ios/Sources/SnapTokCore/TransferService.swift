import Foundation
import AVFoundation
import UserNotifications

public final class TransferService: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    public let store: JobStore
    public let identifier: String
    private var session: URLSession!
    private let publication = DispatchGroup()
    public var backgroundEventsFinished: (() -> Void)?

    public init(store: JobStore, identifier: String, background: Bool = true) {
        self.store = store; self.identifier = identifier
        super.init()
        let configuration: URLSessionConfiguration
        #if os(iOS)
        configuration = background ? .background(withIdentifier: identifier) : .default
        if background {
            configuration.sharedContainerIdentifier = store.paths.appGroup
            configuration.sessionSendsLaunchEvents = true
            configuration.isDiscretionary = false
        }
        #else
        configuration = .default
        #endif
        configuration.timeoutIntervalForRequest = 180
        configuration.timeoutIntervalForResource = 6 * 60 * 60
        configuration.waitsForConnectivity = true
        configuration.httpMaximumConnectionsPerHost = 2
        let queue = OperationQueue(); queue.maxConcurrentOperationCount = 1
        session = URLSession(configuration: configuration, delegate: self, delegateQueue: queue)
    }

    @discardableResult
    public func submit(text: String, preferences: Preferences, token: String) async throws -> DownloadJob {
        let link = try VideoLink.parse(text)
        if link.platform != .direct {
            guard !preferences.bridgeURL.isEmpty, !token.isEmpty else { throw SnapError.missingBridge }
            _ = try BridgeAddress.validate(preferences.bridgeURL)
        }
        let result = try store.enqueue(link: link, options: preferences.options)
        guard result.inserted else { return result.job }
        try await start(result.job, preferences: preferences, token: token)
        return try store.job(result.job.id)
    }

    public func retry(_ id: UUID, preferences: Preferences, token: String) async throws {
        let current = try store.job(id)
        guard [.failed, .cancelled].contains(current.state) || (current.state == .saved && store.fileURL(for: current) == nil) else { return }
        try store.update(id) { job in
            job.state = .preparing; job.attemptID = UUID(); job.error = nil; job.receivedBytes = 0; job.expectedBytes = 0
            job.sessionIdentifier = nil; job.taskIdentifier = nil
        }
        try await start(store.job(id), preferences: preferences, token: token)
    }

    private func start(_ job: DownloadJob, preferences: Preferences, token: String) async throws {
        do {
            var prepared = try await BridgeClient().prepare(job, preferences: preferences, token: token)
            try Task.checkCancellation()
            prepared.request.allowsCellularAccess = job.options.allowsCellular
            let task = session.downloadTask(with: prepared.request)
            task.taskDescription = job.taskKey
            var accepted = false
            do {
                try store.update(job.id) { current in
                    guard current.state == .preparing, current.attemptID == job.attemptID else { return }
                    current.state = .queued; current.sessionIdentifier = identifier
                    current.taskIdentifier = task.taskIdentifier; accepted = true
                }
            } catch { task.cancel(); throw error }
            if accepted { task.resume() } else { task.cancel() }
        } catch {
            fail(job.id, attempt: job.attemptID, message: Self.friendly(error))
            throw error
        }
    }

    public func cancel(_ id: UUID) throws {
        try store.update(id) { job in
            if job.state.isActive { job.state = .cancelled; job.error = nil }
        }
        session.getAllTasks { tasks in tasks.filter { $0.taskDescription?.hasPrefix(id.uuidString + ":") == true }.forEach { $0.cancel() } }
    }

    /// A force quit or interrupted preparation must not leave an eternal Queued card.
    public func reconcile() {
        session.getAllTasks { [self] tasks in
            let live = Set(tasks.compactMap(\.taskDescription))
            guard let jobs = try? store.all() else { return }
            for job in jobs where job.state.isActive && !live.contains(job.taskKey) {
                if job.sessionIdentifier == identifier && job.state != .verifying && Date().timeIntervalSince(job.updatedAt) > 5 {
                    fail(job.id, attempt: job.attemptID, message: "The transfer was interrupted. Tap Retry to start it again.")
                } else if job.sessionIdentifier == nil && Date().timeIntervalSince(job.updatedAt) > 60 {
                    fail(job.id, attempt: job.attemptID, message: "Preparation was interrupted. Tap Retry to start it again.")
                } else if job.state == .verifying && Date().timeIntervalSince(job.updatedAt) > 300 {
                    fail(job.id, attempt: job.attemptID, message: "Saving was interrupted. Tap Retry to save the video again.")
                }
            }
        }
    }

    public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                           didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        guard let job = currentJob(for: downloadTask), job.state.isActive else { downloadTask.cancel(); return }
        let id = job.id
        if totalBytesWritten > job.options.maxBytes || totalBytesExpectedToWrite > job.options.maxBytes {
            fail(id, attempt: job.attemptID, message: "This video exceeds your download size limit."); downloadTask.cancel(); return
        }
        // Avoid rewriting the shared record for every network packet.
        guard Date().timeIntervalSince(job.updatedAt) > 0.3 || job.state == .queued else { return }
        try? store.update(id) { current in
            guard current.state.isActive, current.attemptID == job.attemptID else { return }
            current.state = .downloading; current.receivedBytes = totalBytesWritten
            current.expectedBytes = totalBytesExpectedToWrite
        }
    }

    public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard let job = currentJob(for: downloadTask), job.state.isActive else { return }
        let id = job.id
        guard let response = downloadTask.response as? HTTPURLResponse, response.statusCode == 200 else {
            var message = "The source could not deliver the video (\((downloadTask.response as? HTTPURLResponse)?.statusCode ?? 0)). Try again or open the original link."
            if job.platform != .direct,
               let handle = try? FileHandle(forReadingFrom: location) {
                defer { try? handle.close() }
                if let data = try? handle.read(upToCount: 8192),
                   let body = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let detail = body["error"] as? String { message = String(detail.prefix(300)) }
            }
            fail(id, attempt: job.attemptID, message: message)
            return
        }
        let size = ((try? FileManager.default.attributesOfItem(atPath: location.path)[.size]) as? NSNumber)?.int64Value ?? 0
        guard size > 0, size <= job.options.maxBytes else { fail(id, attempt: job.attemptID, message: "The downloaded file is empty or exceeds your size limit."); return }
        let ext = response.mimeType == "video/quicktime" ? "mov" : "mp4"
        let staged = store.paths.media.appendingPathComponent(id.uuidString + "." + job.attemptID.uuidString + ".pending." + ext)
        do {
            if FileManager.default.fileExists(atPath: staged.path) { try FileManager.default.removeItem(at: staged) }
            try FileManager.default.moveItem(at: location, to: staged)
            try store.update(id) { current in
                if current.state.isActive && current.attemptID == job.attemptID {
                    current.state = .verifying; current.receivedBytes = size; current.expectedBytes = size
                    if let title = response.value(forHTTPHeaderField: "X-SnapTok-Title")?.removingPercentEncoding, !title.isEmpty {
                        current.title = String(title.prefix(160))
                    }
                }
            }
        } catch { try? FileManager.default.removeItem(at: staged); fail(id, attempt: job.attemptID, message: "There is not enough available storage to save this video."); return }
        publication.enter()
        Task {
            defer { publication.leave() }
            do {
                let asset = AVURLAsset(url: staged)
                let tracks = try await asset.loadTracks(withMediaType: .video)
                guard !tracks.isEmpty, try await asset.load(.isPlayable) else { throw SnapError.transfer("The source returned a file that is not a playable video.") }
                let duration = try await asset.load(.duration).seconds
                let final = store.paths.media.appendingPathComponent(id.uuidString + "." + job.attemptID.uuidString + "." + ext)
                var published = false
                // Rename and state transition are synchronised with Cancel under the same store lock.
                var fileError: Error?
                try store.update(id) { current in
                    guard current.state == .verifying, current.attemptID == job.attemptID else { return }
                    do {
                        try FileManager.default.moveItem(at: staged, to: final)
                        current.fileName = final.lastPathComponent; current.state = .saved
                        current.duration = duration.isFinite ? duration : nil; current.error = nil
                        published = true
                    } catch { fileError = error }
                }
                if let fileError { throw fileError }
                if published { notify(id: id, success: true) }
                else { try? FileManager.default.removeItem(at: staged) }
            } catch {
                try? FileManager.default.removeItem(at: staged)
                fail(id, attempt: job.attemptID, message: Self.friendly(error))
            }
        }
    }

    public func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let error, let job = currentJob(for: task) else { return }
        fail(job.id, attempt: job.attemptID, message: Self.friendly(error))
    }
    public func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        publication.notify(queue: .main) { [weak self] in
            self?.backgroundEventsFinished?(); self?.backgroundEventsFinished = nil
        }
    }
    public func finishWhenIdle() { session.finishTasksAndInvalidate() }
    private func currentJob(for task: URLSessionTask) -> DownloadJob? {
        guard let key = task.taskDescription, let id = key.split(separator: ":").first.flatMap({ UUID(uuidString: String($0)) }),
              let job = try? store.job(id), key == job.taskKey else { return nil }
        return job
    }
    private func fail(_ id: UUID, attempt: UUID, message: String) {
        var changed = false
        try? store.update(id) { job in
            guard job.state.isActive, job.attemptID == attempt else { return }
            job.state = .failed; job.error = message; changed = true
        }
        if changed { notify(id: id, success: false) }
    }
    private func notify(id: UUID, success: Bool) {
        guard (try? store.preferences().notifications) == true else { return }
        let content = UNMutableNotificationContent()
        content.title = success ? "Video saved" : "Download needs attention"
        content.body = success ? "Your video is ready in SnapTok." : "Open SnapTok to see what happened and retry."
        content.userInfo = ["jobID": id.uuidString]; content.sound = .default
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id.uuidString, content: content, trigger: nil))
    }
    public static func friendly(_ error: Error) -> String {
        if error is CancellationError { return "Preparation was cancelled. Tap Retry when you are ready." }
        if let error = error as? URLError {
            switch error.code {
            case .notConnectedToInternet, .networkConnectionLost: return "The connection was interrupted. Check your internet connection and retry."
            case .timedOut: return "The source took too long to respond. Try again shortly."
            case .cannotConnectToHost, .cannotFindHost: return "Could not reach the download source. If you use a private bridge, check that it is running."
            default: break
            }
        }
        return error.localizedDescription
    }
}
