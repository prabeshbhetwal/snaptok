import XCTest
@testable import SnapTokCore

final class LiveTransferTests: XCTestCase {
    func testRealTransferAndMediaValidation() async throws {
        guard let link = ProcessInfo.processInfo.environment["SNAPTOK_LIVE_TEST_URL"] else {
            throw XCTSkip("Set SNAPTOK_LIVE_TEST_URL to explicitly run a public-media integration test.")
        }
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("snaptok-live-" + UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = JobStore(paths: try AppPaths(root: root))
        let service = TransferService(store: store, identifier: "test.live", background: false)
        var preferences = Preferences()
        preferences.bridgeURL = ProcessInfo.processInfo.environment["SNAPTOK_LIVE_BRIDGE_URL"] ?? ""
        let tokenPath = ProcessInfo.processInfo.environment["SNAPTOK_LIVE_TOKEN_FILE"]
        let token = try tokenPath.map { try String(contentsOfFile: $0, encoding: .utf8).trimmingCharacters(in: .whitespacesAndNewlines) } ?? ""
        let job = try await service.submit(text: link, preferences: preferences, token: token)
        let deadline = Date().addingTimeInterval(240)
        while Date() < deadline {
            let current = try store.job(job.id)
            if !current.state.isActive {
                XCTAssertEqual(current.state, .saved, current.error ?? "No error detail")
                XCTAssertNotNil(store.fileURL(for: current)); XCTAssertGreaterThan(current.receivedBytes, 0)
                XCTAssertNotNil(current.duration)
                return
            }
            try await Task.sleep(for: .milliseconds(250))
        }
        try service.cancel(job.id); XCTFail("Transfer did not complete before the integration-test deadline")
    }
}
