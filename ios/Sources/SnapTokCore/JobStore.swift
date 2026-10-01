import Foundation
import Darwin

public struct AppPaths: Sendable {
    public let root: URL
    public let appGroup: String?
    public var media: URL { root.appendingPathComponent("Media", isDirectory: true) }
    public var records: URL { root.appendingPathComponent("Jobs", isDirectory: true) }
    public init(root: URL, appGroup: String? = nil) throws {
        self.root = root; self.appGroup = appGroup
        for directory in [root, media, records] {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        }
        var privateRoot = root
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try? privateRoot.setResourceValues(values)
    }
    public static func live() throws -> AppPaths {
        #if os(iOS)
        guard let group = Bundle.main.object(forInfoDictionaryKey: "SnapTokAppGroup") as? String,
              let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)
        else { throw SnapError.storage("SnapTok’s shared storage is unavailable. Check the App Group signing configuration.") }
        return try AppPaths(root: url, appGroup: group)
        #else
        let base = ProcessInfo.processInfo.environment["SNAPTOK_PREVIEW_DATA"].map { URL(fileURLWithPath: $0) }
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("SnapTokPreview")
        return try AppPaths(root: base)
        #endif
    }
}

/// Atomic records plus a process-shared lock protect app/extension read-modify-write operations.
public final class JobStore: @unchecked Sendable {
    public let paths: AppPaths
    public init(paths: AppPaths) { self.paths = paths }

    private func locked<T>(_ operation: () throws -> T) throws -> T {
        let descriptor = open(paths.root.appendingPathComponent("store.lock").path, O_CREAT | O_RDWR, S_IRUSR | S_IWUSR)
        guard descriptor >= 0 else { throw SnapError.storage("Could not open download storage.") }
        defer { close(descriptor) }
        guard flock(descriptor, LOCK_EX) == 0 else { throw SnapError.storage("Download storage is busy. Try again.") }
        defer { flock(descriptor, LOCK_UN) }
        return try operation()
    }
    private func recordURL(_ id: UUID) -> URL { paths.records.appendingPathComponent(id.uuidString + ".json") }
    private func read(_ id: UUID) throws -> DownloadJob { try JSONDecoder().decode(DownloadJob.self, from: Data(contentsOf: recordURL(id))) }
    private func write(_ job: DownloadJob) throws {
        try JSONEncoder().encode(job).write(to: recordURL(job.id), options: .atomic)
    }
    private func readAll() throws -> [DownloadJob] {
        try FileManager.default.contentsOfDirectory(at: paths.records, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .map { try JSONDecoder().decode(DownloadJob.self, from: Data(contentsOf: $0)) }
            .sorted { $0.createdAt > $1.createdAt }
    }
    public func all() throws -> [DownloadJob] { try locked { try readAll() } }
    public func job(_ id: UUID) throws -> DownloadJob { try locked { try read(id) } }
    public func update(_ id: UUID, _ edit: (inout DownloadJob) -> Void) throws {
        try locked { var job = try read(id); edit(&job); job.updatedAt = Date(); try write(job) }
    }
    public func enqueue(link: VideoLink, options: DownloadOptions) throws -> (job: DownloadJob, inserted: Bool) {
        try locked {
            if let duplicate = try readAll().first(where: {
                $0.source == link.url && $0.options == options &&
                ($0.state.isActive || ($0.state == .saved && fileURL(for: $0) != nil))
            }) { return (duplicate, false) }
            let job = DownloadJob(link: link, options: options); try write(job); return (job, true)
        }
    }
    public func fileURL(for job: DownloadJob) -> URL? {
        guard let name = job.fileName, name == URL(fileURLWithPath: name).lastPathComponent,
              name.hasPrefix(job.id.uuidString) else { return nil }
        let url = paths.media.appendingPathComponent(name)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }
    public func delete(_ id: UUID) throws {
        try locked {
            let job = try read(id)
            guard !job.state.isActive else { throw SnapError.storage("Cancel the download before deleting it.") }
            if let file = fileURL(for: job) { try FileManager.default.removeItem(at: file) }
            try FileManager.default.removeItem(at: recordURL(id))
        }
    }
    public func preferences() throws -> Preferences {
        try locked {
            let url = paths.root.appendingPathComponent("preferences.json")
            guard FileManager.default.fileExists(atPath: url.path) else { return Preferences() }
            return try JSONDecoder().decode(Preferences.self, from: Data(contentsOf: url))
        }
    }
    public func savePreferences(_ preferences: Preferences) throws {
        try locked { try JSONEncoder().encode(preferences).write(to: paths.root.appendingPathComponent("preferences.json"), options: .atomic) }
    }
}
