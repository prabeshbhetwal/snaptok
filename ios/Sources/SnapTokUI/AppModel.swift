import SwiftUI
import SnapTokCore
import UserNotifications
import AVKit
#if os(iOS)
import UIKit
import Photos
#else
import AppKit
#endif

public enum AppTab: String, CaseIterable, Identifiable {
    case save = "Save", library = "Library", settings = "Settings"
    public var id: String { rawValue }
    var symbol: String {
        switch self { case .save: "arrow.down.circle"; case .library: "square.stack"; case .settings: "slider.horizontal.3" }
    }
}

@MainActor
public final class AppModel: ObservableObject {
    public let store: JobStore
    public let transfers: TransferService
    @Published public var preferences: Preferences
    @Published public var jobs: [DownloadJob] = []
    @Published public var tab: AppTab = .save
    @Published var input = ""
    @Published var submitting = false
    @Published var message: String?
    @Published var problem: String?
    @Published var selectedJob: DownloadJob?
    @Published var bridgeKey: String
    @Published var checkingBridge = false
    @Published var bridgeStatus: String?
    private var messageTask: Task<Void, Never>?
    private var knownStates: [UUID: DownloadState] = [:]
    private var didLoad = false
    private var refreshing = false

    public init(store: JobStore, transfers: TransferService) throws {
        self.store = store; self.transfers = transfers
        preferences = try store.preferences(); bridgeKey = BridgeCredential.read()
        refresh()
    }
    public static func live() throws -> AppModel {
        let store = JobStore(paths: try AppPaths.live())
        let identifier = (Bundle.main.bundleIdentifier ?? "app.snaptok.preview") + ".downloads.app"
        return try AppModel(store: store, transfers: TransferService(store: store, identifier: identifier))
    }
    public func refresh() {
        guard !refreshing else { return }
        refreshing = true
        Task {
        defer { refreshing = false }
        do {
            let store = self.store
            let fresh = try await Task.detached(priority: .utility) { try store.all() }.value
            if didLoad {
                for job in fresh where knownStates[job.id] != nil && knownStates[job.id] != job.state {
                    if job.state == .saved { showMessage("Video saved. Ready in your library.") }
                    if job.state == .failed { problem = job.error ?? "The download could not finish." }
                }
            }
            knownStates = Dictionary(uniqueKeysWithValues: fresh.map { ($0.id, $0.state) })
            if jobs != fresh { jobs = fresh }
            didLoad = true
        } catch { problem = "Could not read your library. Your files have not been changed." }
        }
    }
    public func savePreferences() {
        do { try store.savePreferences(preferences) } catch { problem = "Could not save your settings. Please try again." }
    }
    func preference<Value>(_ keyPath: WritableKeyPath<Preferences, Value>) -> Binding<Value> {
        Binding(get: { self.preferences[keyPath: keyPath] }, set: { self.preferences[keyPath: keyPath] = $0; self.savePreferences() })
    }
    func submit() async {
        guard !submitting else { return }; submitting = true; defer { submitting = false }
        let original = input
        do {
            let job = try await transfers.submit(text: original, preferences: preferences, token: bridgeKey)
            if input == original { input = "" }
            refresh(); showMessage(job.state == .saved ? "Already saved in your library." : "Download started. You can carry on.")
        } catch { problem = TransferService.friendly(error); refresh() }
    }
    func retry(_ job: DownloadJob) async {
        do { try await transfers.retry(job.id, preferences: preferences, token: bridgeKey); refresh() }
        catch { problem = TransferService.friendly(error); refresh() }
    }
    func cancel(_ job: DownloadJob) {
        do { try transfers.cancel(job.id); refresh() } catch { problem = error.localizedDescription }
    }
    func favourite(_ job: DownloadJob) {
        do { try store.update(job.id) { $0.isFavourite.toggle() }; refresh() } catch { problem = error.localizedDescription }
    }
    func setCollection(_ job: DownloadJob, name: String) {
        do { try store.update(job.id) { $0.collection = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(60)) }; refresh() }
        catch { problem = error.localizedDescription }
    }
    func delete(_ job: DownloadJob) {
        do { try store.delete(job.id); selectedJob = nil; refresh(); showMessage("Video removed from SnapTok.") }
        catch { problem = error.localizedDescription }
    }
    func paste() {
        #if os(iOS)
        if let text = UIPasteboard.general.string { input = text } else { showMessage("Copy a video link first.") }
        #else
        if let text = NSPasteboard.general.string(forType: .string) { input = text } else { showMessage("Copy a video link first.") }
        #endif
    }
    func saveConnection() {
        do {
            bridgeKey = bridgeKey.trimmingCharacters(in: .whitespacesAndNewlines)
            if !preferences.bridgeURL.isEmpty { preferences.bridgeURL = try BridgeAddress.validate(preferences.bridgeURL).absoluteString }
            try BridgeCredential.save(bridgeKey); savePreferences(); showMessage("Connection saved.")
        } catch { problem = error.localizedDescription }
    }
    func checkBridge() async {
        checkingBridge = true; bridgeStatus = nil; defer { checkingBridge = false }
        do {
            try await BridgeClient().check(address: preferences.bridgeURL, token: bridgeKey)
            bridgeStatus = "Connected. Ready for social video links."; saveConnection()
        } catch { bridgeStatus = TransferService.friendly(error) }
    }
    func setNotifications(_ enabled: Bool) async {
        if !enabled { preferences.notifications = false; savePreferences(); return }
        do {
            let granted = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
            preferences.notifications = granted; savePreferences()
            if !granted { showMessage("Allow SnapTok notifications in system Settings to receive alerts.") }
        } catch { problem = "Notification permission could not be requested." }
    }
    func showMessage(_ text: String) {
        messageTask?.cancel(); message = text
        messageTask = Task { try? await Task.sleep(for: .seconds(4)); if !Task.isCancelled { message = nil } }
    }
    var saved: [DownloadJob] { jobs.filter { $0.state == .saved } }
    var active: [DownloadJob] { jobs.filter { $0.state.isActive } }
    var libraryBytes: Int64 { saved.reduce(0) { $0 + $1.receivedBytes } }
}
