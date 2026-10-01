import SwiftUI
import SnapTokCore

struct SaveView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dynamicTypeSize) private var typeSize
    @FocusState private var inputFocused: Bool
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                VStack(alignment: .leading, spacing: 12) {
                    Text("YOURS TO KEEP").font(.caption.weight(.bold)).tracking(2).foregroundStyle(Palette.accent)
                    Text("Keep the\nmoment.")
                        .font(.system(size: typeSize.isAccessibilitySize ? 38 : 48, weight: .bold, design: .rounded))
                        .tracking(-1.8).fixedSize(horizontal: false, vertical: true)
                    Text("A little less searching.\nA little more saving.")
                        .font(.title3).foregroundStyle(.secondary).lineSpacing(3)
                }.padding(.top, 22)

                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Label("Save a video", systemImage: "link").font(.headline)
                        Spacer()
                        if let source = try? VideoLink.parse(model.input) {
                            Text(source.platform.rawValue).font(.caption.weight(.medium)).foregroundStyle(Palette.accent)
                        }
                    }
                    HStack(alignment: .center, spacing: 4) {
                        TextField("Paste a video link", text: $model.input, axis: .vertical)
                            .lineLimit(1...4).textFieldStyle(.plain).focused($inputFocused)
                            .autocorrectionDisabled()
                            .submitLabel(.go)
                            .onSubmit { inputFocused = false; Task { await model.submit() } }
                            #if os(iOS)
                            .textInputAutocapitalization(.never).keyboardType(.URL)
                            #endif
                            .accessibilityIdentifier("save.link")
                        Button { model.paste() } label: {
                            Image(systemName: "document.on.clipboard").font(.title3)
                                .frame(width: 48, height: 48)
                        }.buttonStyle(.plain).foregroundStyle(Palette.accent).accessibilityLabel("Paste video link")
                    }.padding(.leading, 16).padding(.trailing, 4)
                        .background(.background.opacity(0.65), in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(.primary.opacity(0.09)))
                    HStack {
                        Menu {
                            ForEach(VideoQuality.allCases) { quality in
                                Button { model.preferences.options.quality = quality; model.savePreferences() } label: {
                                    if model.preferences.options.quality == quality { Label(quality.label, systemImage: "checkmark") }
                                    else { Text(quality.label) }
                                }
                            }
                        } label: {
                            Label(model.preferences.options.quality.label, systemImage: "slider.horizontal.3").font(.subheadline)
                        }.frame(minHeight: 44)
                        Spacer(minLength: 12)
                        Label("Library", systemImage: "folder").font(.subheadline).foregroundStyle(.secondary)
                    }
                    PrimaryButton(title: "Save video", busy: model.submitting) {
                        inputFocused = false; Task { await model.submit() }
                    }.accessibilityIdentifier("save.submit")
                    Text("We read your clipboard only when you tap Paste.")
                        .font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity)
                }.padding(22).snapGlass(model.preferences.safeTransparency, radius: 28)

                if !model.active.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("ON ITS WAY").font(.caption.weight(.bold)).tracking(1.5).foregroundStyle(.secondary)
                        ForEach(model.active.prefix(3)) { job in DownloadRow(job: job) }
                    }
                } else {
                    HStack(alignment: .top, spacing: 14) {
                        Image(systemName: "square.and.arrow.up").font(.title2).foregroundStyle(Palette.accent).padding(.top, 2)
                        VStack(alignment: .leading, spacing: 6) {
                            Text("See it. Share it. Save it.").font(.headline)
                            Text("Choose SnapTok from a video’s Share menu to send it straight to your library.")
                                .font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                        }
                    }.padding(.horizontal, 4)
                }
                HStack(spacing: 0) {
                    stat("\(model.saved.count)", "Saved")
                    Divider().frame(height: 28)
                    stat(formattedBytes(model.libraryBytes), "In your library")
                }.padding(.vertical, 18).contentPanel()
                if model.preferences.bridgeURL.isEmpty {
                    Button { model.tab = .settings } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "personalhotspot")
                            Text("Connect social video downloads").font(.subheadline.weight(.medium))
                            Spacer(); Image(systemName: "chevron.right").font(.caption)
                        }.padding(.vertical, 8)
                    }.buttonStyle(.plain).foregroundStyle(Palette.accent)
                    Text("Direct video links are ready. Social post links use your private download bridge.")
                        .font(.caption).foregroundStyle(.secondary).padding(.top, -18)
                }
            }.padding(.horizontal, 24).padding(.bottom, 28)
        }.scrollDismissesKeyboard(.interactively)
    }
    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 4) { Text(value).font(.system(.title3, design: .rounded, weight: .bold)); Text(label).font(.caption).foregroundStyle(.secondary) }
            .frame(maxWidth: .infinity)
    }
}

struct DownloadRow: View {
    @EnvironmentObject private var model: AppModel
    let job: DownloadJob
    var body: some View {
        HStack(spacing: 12) {
            PlatformMark(platform: job.platform)
            VStack(alignment: .leading, spacing: 7) {
                Text(job.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                HStack {
                    Text(job.state.label)
                    Spacer()
                    if let progress = job.progress, job.state == .downloading { Text(progress, format: .percent.precision(.fractionLength(0))).monospacedDigit() }
                }.font(.caption).foregroundStyle(.secondary)
                if job.state.isActive {
                    ProgressView(value: job.progress).tint(Palette.accent)
                        .accessibilityLabel("Download progress")
                }
                if let error = job.error { Text(error).font(.caption).foregroundStyle(.red).fixedSize(horizontal: false, vertical: true) }
            }
            if job.state.isActive {
                Button { model.cancel(job) } label: { Image(systemName: "xmark").frame(width: 44, height: 44) }
                    .buttonStyle(.plain).accessibilityLabel("Cancel \(job.title)")
            } else if job.state != .saved {
                Button { Task { await model.retry(job) } } label: { Image(systemName: "arrow.clockwise").frame(width: 44, height: 44) }
                    .buttonStyle(.plain).accessibilityLabel("Retry \(job.title)")
            }
        }.padding(16).background(.background.opacity(0.72), in: RoundedRectangle(cornerRadius: 20))
    }
}
