import AppIntents
import Foundation
import SnapTokCore

struct SaveVideoIntent: AppIntent {
    static var title: LocalizedStringResource = "Save a video"
    static var description = IntentDescription("Save a video link to your SnapTok library using your download preferences.")
    static var openAppWhenRun = false
    @Parameter(title: "Video link") var url: URL

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let model = try AppDelegate.sharedModel.get()
        let job = try await model.transfers.submit(text: url.absoluteString, preferences: model.store.preferences(), token: BridgeCredential.read())
        if job.state == .saved { return .result(dialog: "This video is already saved in SnapTok.") }
        return .result(dialog: "Download started. You can carry on.")
    }
}

struct SnapTokShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: SaveVideoIntent(), phrases: ["Save a video with \(.applicationName)"],
                    shortTitle: "Save a video", systemImageName: "arrow.down.circle")
    }
}
