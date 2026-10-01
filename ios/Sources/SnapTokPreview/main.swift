import SwiftUI
import SnapTokCore
import SnapTokUI

@main
struct SnapTokPreview: App {
    private let result: Result<AppModel, Error>
    init() { result = Result { try AppModel.live() } }
    var body: some Scene {
        WindowGroup("SnapTok · iOS design preview") {
            switch result {
            case .success(let model): RootView(model: model).frame(minWidth: 390, idealWidth: 430, minHeight: 740, idealHeight: 900)
            case .failure(let error): ContentUnavailableView("Storage unavailable", systemImage: "externaldrive.badge.exclamationmark", description: Text(error.localizedDescription))
            }
        }.defaultSize(width: 430, height: 900).windowResizability(.contentMinSize)
    }
}
