import SwiftUI
import UIKit
import UserNotifications
import SnapTokCore
import SnapTokUI

@main
struct SnapTokApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    var body: some Scene {
        WindowGroup {
            switch delegate.model {
            case .success(let model): RootView(model: model)
            case .failure(let error): ContentUnavailableView("Shared storage unavailable", systemImage: "externaldrive.badge.exclamationmark", description: Text(error.localizedDescription))
            }
        }
    }
}

@MainActor
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    static let sharedModel = Result { try AppModel.live() }
    var model: Result<AppModel, Error> { Self.sharedModel }
    private var extensionTransfers: [String: TransferService] = [:]
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        if case .success(let model) = model { model.transfers.reconcile() }
        return true
    }
    func application(_ application: UIApplication, handleEventsForBackgroundURLSession identifier: String,
                     completionHandler: @escaping () -> Void) {
        guard case .success(let model) = model else { completionHandler(); return }
        if identifier == model.transfers.identifier { model.transfers.backgroundEventsFinished = completionHandler; return }
        let prefix = (Bundle.main.bundleIdentifier ?? "app.snaptok.ios") + ".downloads.share."
        guard identifier.hasPrefix(prefix) else { completionHandler(); return }
        let service = extensionTransfers[identifier] ?? TransferService(store: model.store, identifier: identifier)
        extensionTransfers[identifier] = service
        service.backgroundEventsFinished = { [weak self] in
            completionHandler()
            service.finishWhenIdle()
            Task { @MainActor in self?.extensionTransfers.removeValue(forKey: identifier); model.refresh() }
        }
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        Task { @MainActor in
            if case .success(let model) = self.model { model.refresh(); model.tab = .library }
            completionHandler()
        }
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([]) // The foreground app already presents the result.
    }
}
