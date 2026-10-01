import SwiftUI
import Combine
import SnapTokCore

public struct RootView: View {
    @StateObject private var model: AppModel
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(\.scenePhase) private var scenePhase
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    public init(model: AppModel) { _model = StateObject(wrappedValue: model) }
    public var body: some View {
        ZStack {
            AmbientBackground()
            VStack(spacing: 0) {
                HStack(spacing: 9) {
                    Image(systemName: "arrow.down.to.line.compact").font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.white).frame(width: 34, height: 34)
                        .background(Palette.accent.gradient, in: RoundedRectangle(cornerRadius: 11))
                    Text("SnapTok").font(.system(.title3, design: .rounded, weight: .bold))
                    Spacer()
                    if !model.active.isEmpty {
                        Button { model.tab = .library } label: {
                            Label("\(model.active.count)", systemImage: "arrow.down.circle.fill")
                                .font(.subheadline.weight(.semibold)).padding(10)
                        }.buttonStyle(.plain).snapGlass(model.preferences.safeTransparency, radius: 18)
                            .accessibilityLabel("\(model.active.count) active downloads. Open library")
                    }
                }.padding(.horizontal, 24).padding(.top, 18).padding(.bottom, 10)
                Group {
                    switch model.tab {
                    case .save: SaveView()
                    case .library: LibraryView()
                    case .settings: SettingsView()
                    }
                }.frame(maxWidth: .infinity, maxHeight: .infinity).transition(.opacity)
            }.frame(maxWidth: 780)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 10) {
                if let message = model.message {
                    Text(message).font(.subheadline).padding(14).frame(maxWidth: 600)
                        .snapGlass(model.preferences.safeTransparency, radius: 18)
                        .accessibilityAddTraits(.updatesFrequently).transition(.opacity.combined(with: .move(edge: .bottom)))
                }
                navigation.padding(.horizontal, 32).padding(.bottom, 10)
            }.padding(.horizontal, 16)
        }
        .environmentObject(model)
        .tint(Palette.accent)
        .preferredColorScheme(model.preferences.appearance == .system ? nil : model.preferences.appearance == .dark ? .dark : .light)
        .animation(systemReduceMotion || model.preferences.reduceMotion ? nil : .easeInOut(duration: 0.22), value: model.tab)
        .animation(systemReduceMotion || model.preferences.reduceMotion ? nil : .easeInOut(duration: 0.2), value: model.message)
        .onReceive(timer) { _ in if scenePhase == .active { model.refresh() } }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.refresh(); model.transfers.reconcile() }
            else { model.savePreferences() }
        }
        .alert("Couldn’t complete that", isPresented: Binding(get: { model.problem != nil }, set: { if !$0 { model.problem = nil } })) {
            Button("OK") { model.problem = nil }
        } message: { Text(model.problem ?? "") }
        .sheet(item: $model.selectedJob) { job in VideoDetailView(jobID: job.id).environmentObject(model) }
    }
    private var navigation: some View {
        HStack(spacing: 4) {
            ForEach(AppTab.allCases) { tab in
                Button { model.tab = tab } label: {
                    VStack(spacing: 4) {
                        Image(systemName: tab.symbol).font(.system(size: 21, weight: model.tab == tab ? .semibold : .regular))
                        Text(tab.rawValue).font(.caption.weight(.medium))
                    }.foregroundStyle(model.tab == tab ? Palette.accent : .secondary)
                        .frame(maxWidth: .infinity).frame(minHeight: 54)
                        .background(model.tab == tab ? Palette.accent.opacity(0.10) : .clear, in: Capsule())
                }.buttonStyle(.plain).accessibilityAddTraits(model.tab == tab ? .isSelected : [])
                    .accessibilityIdentifier("tab.\(tab.id)")
            }
        }.padding(6).frame(maxWidth: 380).snapGlass(model.preferences.safeTransparency, radius: 36, interactive: true)
    }
}
