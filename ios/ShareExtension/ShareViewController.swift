import UIKit
import SwiftUI
import UniformTypeIdentifiers
import SnapTokCore

final class ShareViewController: UIViewController {
    private var work: Task<Void, Never>?
    private var transfers: TransferService?
    private let status = ShareStatus()
    override func viewDidLoad() {
        super.viewDidLoad()
        let controller = UIHostingController(rootView: ShareStatusView(status: status, close: { [weak self] in self?.close() }))
        addChild(controller); view.addSubview(controller.view)
        controller.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            controller.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            controller.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            controller.view.topAnchor.constraint(equalTo: view.topAnchor),
            controller.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        controller.didMove(toParent: self)
        work = Task { @MainActor [weak self] in await self?.save() }
    }
    private func save() async {
        do {
            let items = extensionContext?.inputItems as? [NSExtensionItem] ?? []
            let providers = items.flatMap { $0.attachments ?? [] }
            guard let provider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.url.identifier) })
                ?? providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) })
            else { throw SnapError.invalidLink }
            let type = provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) ? UTType.url.identifier : UTType.plainText.identifier
            let item = try await provider.loadItem(forTypeIdentifier: type, options: nil)
            let text: String
            if let url = item as? URL { text = url.absoluteString }
            else if let string = item as? String { text = string }
            else { throw SnapError.invalidLink }
            try Task.checkCancellation()
            let store = JobStore(paths: try AppPaths.live())
            let base = Bundle.main.object(forInfoDictionaryKey: "SnapTokContainingBundle") as? String ?? "app.snaptok.ios"
            let service = TransferService(store: store, identifier: base + ".downloads.share." + UUID().uuidString)
            transfers = service
            let job = try await service.submit(text: text, preferences: store.preferences(), token: BridgeCredential.read())
            status.message = job.state == .saved ? "Already in your library." : "Download started. Carry on."
            status.finished = true
            // Complete only after the job and system download task have been created.
            extensionContext?.completeRequest(returningItems: nil)
        } catch {
            guard !Task.isCancelled else { return }
            status.message = TransferService.friendly(error); status.failed = true
        }
    }
    private func close() { work?.cancel(); extensionContext?.completeRequest(returningItems: nil) }
}

@MainActor
private final class ShareStatus: ObservableObject {
    @Published var message = "Preparing your video…"
    @Published var failed = false
    @Published var finished = false
}
private struct ShareStatusView: View {
    @ObservedObject var status: ShareStatus
    var close: () -> Void
    var body: some View {
        VStack(spacing: 22) {
            Image(systemName: status.failed ? "exclamationmark.circle" : "arrow.down.circle")
                .font(.system(size: 48, weight: .light)).foregroundStyle(.blue)
            Text("SnapTok").font(.title2.bold())
            if !status.failed && !status.finished { ProgressView() }
            Text(status.message).multilineTextAlignment(.center).font(.body)
            Button(status.failed ? "Close" : "Cancel", action: close).buttonStyle(.bordered).buttonBorderShape(.capsule)
        }.padding(32).frame(maxWidth: .infinity, maxHeight: .infinity).background(.regularMaterial)
    }
}
