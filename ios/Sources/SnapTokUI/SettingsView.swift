import SwiftUI
import SnapTokCore
#if os(iOS)
import UIKit
#endif

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.accessibilityReduceTransparency) private var systemReducedTransparency
    @State private var showBridge = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Make it yours.").font(.system(.largeTitle, design: .rounded, weight: .bold))
                    Text("Small details. Just the way you like them.").font(.subheadline).foregroundStyle(.secondary)
                }.padding(.top, 18)
                VStack(alignment: .leading, spacing: 18) {
                    Label("Appearance", systemImage: "circle.lefthalf.filled").font(.headline)
                    Picker("Colour scheme", selection: model.preference(\.appearance)) {
                        ForEach(Appearance.allCases, id: \.self) { Text($0.rawValue.capitalized).tag($0) }
                    }.pickerStyle(.segmented)
                    glassPreview
                    HStack {
                        Text("Glass transparency").font(.subheadline.weight(.semibold))
                        Spacer()
                        Text(model.preferences.safeTransparency, format: .percent.precision(.fractionLength(0)))
                            .font(.subheadline.monospacedDigit()).foregroundStyle(Palette.accent)
                    }
                    Slider(value: $model.preferences.transparency, in: 0...1) { Text("Glass transparency") }
                        onEditingChanged: { editing in if !editing { model.savePreferences() } }
                        .labelsHidden()
                        .accessibilityValue("\(Int(model.preferences.safeTransparency * 100)) per cent")
                        .accessibilityIdentifier("settings.transparency")
                        .disabled(systemReducedTransparency)
                    HStack { Text("Frosted"); Spacer(); Text("Clear") }.font(.caption).foregroundStyle(.secondary)
                    Text(systemReducedTransparency ? "Reduce Transparency is enabled in your device settings. Surfaces stay opaque for readability." : "Adjusts SnapTok’s glass panels and controls. Text always stays clear; system bars follow your device settings.")
                        .font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                    Divider()
                    Toggle("Reduce motion", isOn: model.preference(\.reduceMotion))
                    Text("Uses quiet transitions. Your device’s Reduce Motion setting is always respected.")
                        .font(.caption).foregroundStyle(.secondary)
                }.contentPanel()

                VStack(alignment: .leading, spacing: 18) {
                    Label("Downloads", systemImage: "arrow.down.circle").font(.headline)
                    Picker("Preferred quality", selection: Binding(get: { model.preferences.options.quality }, set: { model.preferences.options.quality = $0; model.savePreferences() })) {
                        ForEach(VideoQuality.allCases) { Text($0.label).tag($0) }
                    }
                    Text("For social videos, choose the best available quality at or below your preference. Direct links keep their original quality.")
                        .font(.caption).foregroundStyle(.secondary)
                    Toggle("Use mobile data", isOn: Binding(get: { model.preferences.options.allowsCellular }, set: { model.preferences.options.allowsCellular = $0; model.savePreferences() }))
                    Picker("Maximum video size", selection: Binding(get: { model.preferences.options.maxBytes }, set: { model.preferences.options.maxBytes = $0; model.savePreferences() })) {
                        ForEach([100, 500, 1024, 2048], id: \.self) { amount in Text(amount >= 1024 ? "\(amount / 1024) GB" : "\(amount) MB").tag(Int64(amount) * 1024 * 1024) }
                    }
                    Divider()
                    LabeledContent("Save to", value: "SnapTok Library")
                    Text("Your saved videos stay in the app. Open a video to export it to Photos or choose a destination in Files.")
                        .font(.caption).foregroundStyle(.secondary)
                    Toggle("Completion and failure alerts", isOn: Binding(get: { model.preferences.notifications }, set: { enabled in Task { await model.setNotifications(enabled) } }))
                    #if os(iOS)
                    Link("System notification settings", destination: URL(string: UIApplication.openSettingsURLString)!).font(.subheadline)
                    #endif
                }.contentPanel()

                VStack(alignment: .leading, spacing: 16) {
                    Label("Social video connection", systemImage: "personalhotspot").font(.headline)
                    Text("Direct video links work on this device. To save social posts, connect a private download bridge that you control.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    DisclosureGroup("Connection details", isExpanded: $showBridge) {
                        VStack(alignment: .leading, spacing: 14) {
                            TextField("https://your-bridge.example", text: $model.preferences.bridgeURL)
                                .textFieldStyle(.roundedBorder).autocorrectionDisabled()
                                #if os(iOS)
                                .textInputAutocapitalization(.never).keyboardType(.URL)
                                #endif
                                .accessibilityLabel("Private bridge address")
                            SecureField("Connection key", text: $model.bridgeKey).textFieldStyle(.roundedBorder)
                            Text("Shared links and video files pass through your bridge. The connection key is kept in Keychain. Nothing is sent until you save a social video or test the connection.")
                                .font(.caption).foregroundStyle(.secondary)
                            HStack {
                                Button("Save connection") { model.saveConnection() }.buttonStyle(.bordered)
                                Button { Task { await model.checkBridge() } } label: {
                                    if model.checkingBridge { ProgressView().controlSize(.small) } else { Text("Test connection") }
                                }.buttonStyle(.borderedProminent).disabled(model.checkingBridge)
                            }
                            if let status = model.bridgeStatus { Text(status).font(.caption).foregroundStyle(.secondary) }
                        }.padding(.top, 14)
                    }
                }.contentPanel()
                VStack(alignment: .leading, spacing: 10) {
                    Label("Your space", systemImage: "externaldrive").font(.headline)
                    LabeledContent("Saved videos", value: "\(model.saved.count)")
                    LabeledContent("Library size", value: formattedBytes(model.libraryBytes))
                    Text("No account, advertising or analytics. Clipboard access happens only when you tap Paste. Deleting the app removes its private library, so export videos you want to keep.")
                        .font(.caption).foregroundStyle(.secondary)
                }.contentPanel()
                Text("SnapTok for iOS · 0.1.0").font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity)
            }.padding(.horizontal, 24).padding(.bottom, 28)
        }
    }
    private var glassPreview: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.29, green: 0.62, blue: 0.87), Palette.mint, Color(red: 0.80, green: 0.91, blue: 0.87)], startPoint: .topLeading, endPoint: .bottomTrailing)
            Circle().fill(.white.opacity(0.35)).frame(width: 100).offset(x: 105, y: -45)
            HStack(spacing: 12) {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(Palette.accent).font(.title2)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Moment saved").font(.subheadline.weight(.semibold))
                    Text("Ready whenever you are.").font(.caption).foregroundStyle(.secondary)
                }
            }.padding(18).snapGlass(model.preferences.safeTransparency, radius: 22)
        }.frame(height: 152).clipShape(RoundedRectangle(cornerRadius: 22))
            .accessibilityElement(children: .ignore).accessibilityLabel("Live glass transparency preview")
    }
}
