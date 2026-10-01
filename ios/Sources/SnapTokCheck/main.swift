import Foundation
import SnapTokCore
import Darwin

/// Explicit live integration check. Uses an isolated temporary library, deleted on exit.
@main
struct SnapTokCheck {
    static func main() async {
        guard CommandLine.arguments.count == 2 else {
            print("Usage: SnapTokCheck VIDEO_URL. Optional SNAPTOK_CHECK_BRIDGE and SNAPTOK_CHECK_TOKEN_FILE.")
            exit(2)
        }
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("snaptok-check-" + UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        do {
            let store = JobStore(paths: try AppPaths(root: directory))
            let service = TransferService(store: store, identifier: "app.snaptok.check", background: false)
            defer { service.finishWhenIdle() }
            var preferences = Preferences()
            preferences.options.maxBytes = 100 * 1024 * 1024
            preferences.options.quality = .hd
            preferences.bridgeURL = ProcessInfo.processInfo.environment["SNAPTOK_CHECK_BRIDGE"] ?? ""
            let tokenPath = ProcessInfo.processInfo.environment["SNAPTOK_CHECK_TOKEN_FILE"]
            let token = try tokenPath.map { try String(contentsOfFile: $0, encoding: .utf8).trimmingCharacters(in: .whitespacesAndNewlines) } ?? ""
            let job = try await service.submit(text: CommandLine.arguments[1], preferences: preferences, token: token)
            let deadline = Date().addingTimeInterval(240)
            while Date() < deadline {
                let result = try store.job(job.id)
                if !result.state.isActive {
                    guard result.state == .saved, result.receivedBytes > 0, result.duration != nil, store.fileURL(for: result) != nil else {
                        throw SnapError.transfer(result.error ?? "Transfer did not publish a playable video.")
                    }
                    let duplicate = try await service.submit(text: CommandLine.arguments[1], preferences: preferences, token: token)
                    guard duplicate.id == result.id else { throw SnapError.storage("Duplicate protection failed.") }
                    try store.update(result.id) { $0.isFavourite = true; $0.collection = "Smoke test" }
                    let reopened = JobStore(paths: try AppPaths(root: directory))
                    guard try reopened.job(result.id).isFavourite else { throw SnapError.storage("Persistence check failed.") }
                    print("PASS: transfer, playable media, publication, duplicate protection and library persistence; \(result.receivedBytes) bytes; \(result.duration ?? 0) seconds")
                    try store.delete(result.id)
                    guard try store.all().isEmpty, store.fileURL(for: result) == nil else { throw SnapError.storage("Deletion check failed.") }
                    print("PASS: confirmed library deletion")
                    return
                }
                try await Task.sleep(for: .milliseconds(250))
            }
            try service.cancel(job.id); throw SnapError.transfer("Integration check timed out.")
        } catch { fputs("FAIL: \(error.localizedDescription)\n", stderr); exit(1) }
    }
}
