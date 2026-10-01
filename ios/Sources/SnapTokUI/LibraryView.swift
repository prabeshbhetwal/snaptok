import SwiftUI
import AVFoundation
import SnapTokCore

struct LibraryView: View {
    @EnvironmentObject private var model: AppModel
    @State private var search = ""
    @State private var favourites = false
    @State private var platform: SourcePlatform?
    @State private var newestFirst = true
    @State private var deleting: DownloadJob?
    private var filtered: [DownloadJob] {
        model.jobs.filter { job in
            (!favourites || job.isFavourite) && (platform == nil || job.platform == platform) &&
            (search.isEmpty || [job.title, job.source.absoluteString, job.platform.rawValue, job.collection]
                .contains { $0.localizedCaseInsensitiveContains(search) })
        }.sorted { newestFirst ? $0.createdAt > $1.createdAt : $0.createdAt < $1.createdAt }
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("Your collection.").font(.system(.largeTitle, design: .rounded, weight: .bold))
                        Text("Good moments, all in one place.").foregroundStyle(.secondary).font(.subheadline)
                    }
                    Spacer()
                    Button { model.preferences.gridLibrary.toggle(); model.savePreferences() } label: {
                        Image(systemName: model.preferences.gridLibrary ? "list.bullet" : "square.grid.2x2").frame(width: 44, height: 44)
                    }.buttonStyle(.plain).snapGlass(model.preferences.safeTransparency, radius: 16)
                        .accessibilityLabel(model.preferences.gridLibrary ? "Switch to list" : "Switch to grid")
                }.padding(.top, 18)
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("Search videos or collections", text: $search).textFieldStyle(.plain)
                    if !search.isEmpty { Button { search = "" } label: { Image(systemName: "xmark.circle.fill") }.buttonStyle(.plain).accessibilityLabel("Clear search") }
                }.padding(15).snapGlass(model.preferences.safeTransparency, radius: 18)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        Button { favourites.toggle() } label: { Label("Favourites", systemImage: favourites ? "heart.fill" : "heart") }
                            .foregroundStyle(favourites ? Palette.accent : .secondary)
                        Menu {
                            Button("All sources") { platform = nil }
                            ForEach(SourcePlatform.allCases, id: \.self) { p in Button(p.rawValue) { platform = p } }
                        } label: { Label(platform?.rawValue ?? "All sources", systemImage: "line.3.horizontal.decrease") }
                        Button { newestFirst.toggle() } label: { Label(newestFirst ? "Newest" : "Oldest", systemImage: "arrow.up.arrow.down") }
                    }.font(.subheadline).buttonStyle(.bordered).buttonBorderShape(.capsule)
                }
                if filtered.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: search.isEmpty ? "rectangle.stack.badge.plus" : "magnifyingglass")
                            .font(.system(size: 44, weight: .light)).foregroundStyle(Palette.accent)
                            .frame(width: 100, height: 100).snapGlass(model.preferences.safeTransparency, radius: 32)
                        Text(model.jobs.isEmpty ? "Make room for your favourites." : "No matching videos")
                            .font(.title3.weight(.semibold)).multilineTextAlignment(.center)
                        Text(model.jobs.isEmpty ? "Save your first video. We’ll keep it here, ready whenever you are." : "Try another search or change your filters.")
                            .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        if model.jobs.isEmpty { Button("Save a video") { model.tab = .save }.buttonStyle(.borderedProminent).buttonBorderShape(.capsule) }
                    }.frame(maxWidth: .infinity).padding(.vertical, 60)
                } else {
                    let unfinished = filtered.filter { $0.state != .saved }
                    ForEach(unfinished) { job in DownloadRow(job: job).contextMenu { Button("Delete", role: .destructive) { deleting = job }.disabled(job.state.isActive) } }
                    LazyVGrid(columns: model.preferences.gridLibrary ? [GridItem(.adaptive(minimum: 155), spacing: 14)] : [GridItem(.flexible())], spacing: 18) {
                        ForEach(filtered.filter { $0.state == .saved }) { job in
                            VideoCard(job: job).contextMenu {
                                Button(job.isFavourite ? "Remove favourite" : "Add favourite") { model.favourite(job) }
                                if let file = model.store.fileURL(for: job) { ShareLink(item: file) }
                                Button("Delete video", role: .destructive) { deleting = job }
                            }
                        }
                    }
                }
            }.padding(.horizontal, 24).padding(.bottom, 24)
        }
        .confirmationDialog("Delete this video from SnapTok?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button("Delete video", role: .destructive) { if let job = deleting { model.delete(job) }; deleting = nil }
        } message: { Text("Copies already exported to Photos or Files are kept.") }
    }
}

struct VideoCard: View {
    @EnvironmentObject private var model: AppModel
    let job: DownloadJob
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button { model.selectedJob = job } label: {
                ZStack(alignment: .bottomTrailing) {
                    VideoThumbnail(url: model.store.fileURL(for: job)).frame(height: 176).clipped()
                    HStack {
                        Image(systemName: "play.fill").font(.caption)
                        Spacer()
                        if let duration = job.duration { Text(formattedDuration(duration)).font(.caption.monospacedDigit()) }
                    }.foregroundStyle(.white).padding(12)
                        .background(LinearGradient(colors: [.clear, .black.opacity(0.6)], startPoint: .top, endPoint: .bottom))
                }.clipShape(RoundedRectangle(cornerRadius: 20))
            }.buttonStyle(.plain).accessibilityLabel("Play \(job.title)")
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(job.title).font(.subheadline.weight(.semibold)).lineLimit(2)
                    Text("\(job.platform.rawValue) · \(formattedBytes(job.receivedBytes))").font(.caption).foregroundStyle(.secondary)
                    if !job.collection.isEmpty { Text(job.collection).font(.caption).foregroundStyle(Palette.accent).lineLimit(1) }
                }
                Spacer(minLength: 0)
                Button { model.favourite(job) } label: { Image(systemName: job.isFavourite ? "heart.fill" : "heart").frame(width: 36, height: 44) }
                    .buttonStyle(.plain).foregroundStyle(job.isFavourite ? .pink : .secondary)
                    .accessibilityLabel(job.isFavourite ? "Remove \(job.title) from favourites" : "Favourite \(job.title)")
            }
        }
    }
}

struct VideoThumbnail: View {
    let url: URL?
    @State private var image: CGImage?
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                LinearGradient(colors: [Palette.accent.opacity(0.22), Palette.mint.opacity(0.3)], startPoint: .topLeading, endPoint: .bottomTrailing)
                if let image { Image(decorative: image, scale: 1).resizable().scaledToFill().frame(width: geometry.size.width, height: geometry.size.height).clipped() }
                else { Image(systemName: "film").font(.largeTitle).foregroundStyle(Palette.accent.opacity(0.5)) }
            }
        }.task(id: url) {
            image = nil; guard let url else { return }
            let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
            generator.appliesPreferredTrackTransform = true; generator.maximumSize = CGSize(width: 600, height: 600)
            if let result = try? await generator.image(at: .zero), !Task.isCancelled { image = result.image }
        }.accessibilityHidden(true)
    }
}
