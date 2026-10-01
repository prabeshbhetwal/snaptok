import XCTest
@testable import SnapTokCore

final class StoreTests: XCTestCase {
    private var root: URL!
    private var store: JobStore!
    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent("snaptok-tests-" + UUID().uuidString)
        store = JobStore(paths: try AppPaths(root: root))
    }
    override func tearDownWithError() throws { try FileManager.default.removeItem(at: root) }
    private func create(_ index: Int = 1) throws -> DownloadJob {
        try store.enqueue(link: VideoLink.parse("https://cdn.example.com/\(index).mp4"), options: DownloadOptions()).job
    }
    func testRecordSurvivesStoreReopening() throws {
        let job = try create(); try store.update(job.id) { $0.collection = "Ideas"; $0.isFavourite = true }
        let reopened = JobStore(paths: try AppPaths(root: root))
        XCTAssertEqual(try reopened.job(job.id).collection, "Ideas")
        XCTAssertTrue(try reopened.job(job.id).isFavourite)
    }
    func testConcurrentDuplicateRequestsCreateOneJob() throws {
        let link = try VideoLink.parse("https://cdn.example.com/1.mp4")
        DispatchQueue.concurrentPerform(iterations: 30) { _ in
            let secondProcessView = JobStore(paths: store.paths)
            _ = try? secondProcessView.enqueue(link: link, options: DownloadOptions())
        }
        XCTAssertEqual(try store.all().count, 1)
    }
    func testIndependentUpdatesDoNotLoseChanges() throws {
        let job = try create()
        DispatchQueue.concurrentPerform(iterations: 50) { _ in
            let peer = JobStore(paths: store.paths)
            try? peer.update(job.id) { $0.receivedBytes += 1 }
        }
        XCTAssertEqual(try store.job(job.id).receivedBytes, 50)
    }
    func testFailedRequestsCanBeQueuedAgain() throws {
        let first = try create(); try store.update(first.id) { $0.state = .failed }
        XCTAssertNotEqual(try create().id, first.id)
    }
    func testSavedFileMissingDoesNotBlockNewDownload() throws {
        let first = try create(); try store.update(first.id) { $0.state = .saved; $0.fileName = first.id.uuidString + ".mp4" }
        XCTAssertNotEqual(try create().id, first.id)
    }
    func testSavedFileIsDeduplicatedAndDeletionRemovesOnlyItsFile() throws {
        let first = try create(), fileName = firstFileName()
        let actualName = first.id.uuidString + ".mp4"
        try Data([1, 2, 3]).write(to: store.paths.media.appendingPathComponent(actualName))
        try Data([4]).write(to: store.paths.media.appendingPathComponent(fileName))
        try store.update(first.id) { $0.state = .saved; $0.fileName = actualName }
        XCTAssertEqual(try create().id, first.id)
        try store.delete(first.id)
        XCTAssertTrue(FileManager.default.fileExists(atPath: store.paths.media.appendingPathComponent(fileName).path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.paths.media.appendingPathComponent(actualName).path))
    }
    private func firstFileName() -> String { UUID().uuidString + ".mp4" }
    func testUnsafeFileNameCannotEscapeLibrary() throws {
        let first = try create(); try store.update(first.id) { $0.fileName = "../preferences.json" }
        XCTAssertNil(store.fileURL(for: try store.job(first.id)))
    }
    func testCannotDeleteActiveJob() throws { XCTAssertThrowsError(try store.delete(create().id)) }
    func testPreferencesRetainTransparencyAndDefaults() throws {
        var preferences = Preferences(); preferences.transparency = 0.82; preferences.options.quality = .hd
        try store.savePreferences(preferences)
        XCTAssertEqual(try store.preferences(), preferences)
    }
    func testCancellationPersistsAcrossReopening() async throws {
        let job = try create(); let service = TransferService(store: store, identifier: "test", background: false)
        try service.cancel(job.id)
        XCTAssertEqual(try JobStore(paths: AppPaths(root: root)).job(job.id).state, .cancelled)
        // A retry is explicit; cancelling alone never changes preferences or queues a replacement.
        XCTAssertEqual(try store.all().count, 1)
    }
    func testOldAttemptCannotFailNewRetry() throws {
        let job = try create()
        let service = TransferService(store: store, identifier: "test", background: false)
        let oldTask = URLSession.shared.downloadTask(with: job.source)
        oldTask.taskDescription = job.taskKey
        try store.update(job.id) { $0.attemptID = UUID(); $0.state = .queued }
        service.urlSession(URLSession.shared, task: oldTask, didCompleteWithError: URLError(.cancelled))
        XCTAssertEqual(try store.job(job.id).state, .queued)
        oldTask.cancel(); service.finishWhenIdle()
    }
    func testSizeFailureDoesNotBecomeCancelledOrSaved() throws {
        let job = try create()
        let service = TransferService(store: store, identifier: "test", background: false)
        let task = URLSession.shared.downloadTask(with: job.source); task.taskDescription = job.taskKey
        service.urlSession(URLSession.shared, downloadTask: task, didWriteData: 1, totalBytesWritten: 1, totalBytesExpectedToWrite: job.options.maxBytes + 1)
        service.urlSession(URLSession.shared, task: task, didCompleteWithError: URLError(.cancelled))
        XCTAssertEqual(try store.job(job.id).state, .failed)
        XCTAssertEqual(try store.job(job.id).error, "This video exceeds your download size limit.")
        service.finishWhenIdle()
    }
    func testSocialLinkWithoutBridgeDoesNotCreateStuckQueue() async throws {
        let service = TransferService(store: store, identifier: "test", background: false)
        do { _ = try await service.submit(text: "https://youtu.be/aqz-KE-bpKQ", preferences: Preferences(), token: ""); XCTFail("Expected connection error") }
        catch { XCTAssertEqual(error as? SnapError, .missingBridge) }
        XCTAssertTrue(try store.all().isEmpty)
    }
}
