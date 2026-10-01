import SwiftUI
import AVKit
import SnapTokCore
#if os(iOS)
import Photos
#endif

struct VideoDetailView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    let jobID: UUID
    @State private var player: AVPlayer?
    @State private var speed: Float = 1
    @State private var collection = ""
    @State private var exporting = false
    @State private var exportMessage: String?
    @State private var confirmDelete = false
    private var job: DownloadJob? { model.jobs.first { $0.id == jobID } }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let job {
                        if let player {
                            NativeVideoPlayer(player: player).frame(minHeight: 260, idealHeight: 340, maxHeight: 430)
                                .clipShape(RoundedRectangle(cornerRadius: 22))
                        } else {
                            ContentUnavailableView("Video unavailable", systemImage: "film", description: Text("The file may have been moved or deleted. You can download it again."))
                            Button("Download again") { Task { await model.retry(job); dismiss() } }
                        }
                        Text(job.title).font(.title2.bold())
                        HStack {
                            Label(job.platform.rawValue, systemImage: "link")
                            Spacer(); Text(formattedBytes(job.receivedBytes))
                        }.font(.subheadline).foregroundStyle(.secondary)
                        Picker("Playback speed", selection: $speed) {
                            ForEach([Float(0.5), 1, 1.5, 2], id: \.self) { Text(String(format: "%g×", $0)).tag($0) }
                        }.pickerStyle(.segmented).onChange(of: speed) { _, rate in player?.rate = rate }
                        HStack(spacing: 16) {
                            Button { model.favourite(job) } label: { Label(job.isFavourite ? "Favourited" : "Favourite", systemImage: job.isFavourite ? "heart.fill" : "heart") }
                            if let file = model.store.fileURL(for: job) { ShareLink(item: file) { Label("Export", systemImage: "square.and.arrow.up") } }
                        }.buttonStyle(.bordered).buttonBorderShape(.capsule)
                        #if os(iOS)
                        Button { Task { await saveToPhotos(job) } } label: {
                            if exporting { ProgressView("Saving to Photos…") } else { Label("Save to Photos", systemImage: "photo.on.rectangle") }
                        }.disabled(exporting || model.store.fileURL(for: job) == nil)
                        #endif
                        if let exportMessage { Text(exportMessage).font(.subheadline).foregroundStyle(.secondary) }
                        Divider()
                        Text("Collection").font(.headline)
                        HStack {
                            TextField("e.g. Travel, Ideas, Recipes", text: $collection).textFieldStyle(.roundedBorder)
                            Button("Save") { model.setCollection(job, name: collection); exportMessage = "Collection updated." }.buttonStyle(.bordered)
                        }
                        Link(destination: job.source) { Label("Open original video", systemImage: "arrow.up.right.square") }
                        Text(job.source.absoluteString).font(.caption).foregroundStyle(.secondary).textSelection(.enabled)
                        Text("Saved \(job.createdAt.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(.secondary)
                        Button("Delete from SnapTok", role: .destructive) { confirmDelete = true }.padding(.top, 12)
                    }
                }.padding(24)
            }.navigationTitle("Your video")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
                .confirmationDialog("Delete this video?", isPresented: $confirmDelete, titleVisibility: .visible) {
                    Button("Delete from SnapTok", role: .destructive) { if let job { model.delete(job) }; dismiss() }
                } message: { Text("Exported copies are kept. This removes the file from your SnapTok library.") }
        }.onAppear {
            if let job { collection = job.collection; if let url = model.store.fileURL(for: job) { player = AVPlayer(url: url); player?.play() } }
        }.onDisappear { player?.pause(); player = nil }
            .onChange(of: scenePhase) { _, phase in if phase != .active { player?.pause() } }
    }
    #if os(iOS)
    private func saveToPhotos(_ job: DownloadJob) async {
        guard let file = model.store.fileURL(for: job) else { exportMessage = SnapError.missingFile.localizedDescription; return }
        exporting = true; defer { exporting = false }
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else { exportMessage = "Allow Photos access in Settings to save a copy."; return }
        do {
            try await PHPhotoLibrary.shared().performChanges { PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: file) }
            exportMessage = "A copy was saved to Photos."
        } catch { exportMessage = "Could not save to Photos. Your original is still in SnapTok." }
    }
    #endif
}
