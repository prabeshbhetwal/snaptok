import SwiftUI
import SnapTokCore

enum Palette {
    static let accent = Color.blue
    static let mint = Color(red: 0.19, green: 0.74, blue: 0.65)
    static let ink = Color(red: 0.08, green: 0.15, blue: 0.23)
}

struct AmbientBackground: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                (scheme == .dark ? Color(red: 0.035, green: 0.065, blue: 0.10) : Color(red: 0.95, green: 0.97, blue: 0.98))
                Ellipse().fill(Palette.mint.opacity(scheme == .dark ? 0.16 : 0.2))
                    .frame(width: geometry.size.width * 1.5, height: geometry.size.height * 0.65)
                    .blur(radius: 60).offset(x: 110, y: geometry.size.height * 0.33)
                Ellipse().fill(Palette.accent.opacity(scheme == .dark ? 0.14 : 0.12))
                    .frame(width: geometry.size.width, height: geometry.size.height * 0.5)
                    .blur(radius: 75).offset(x: -160, y: 50)
            }.ignoresSafeArea()
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

struct GlassSurface: ViewModifier {
    let transparency: Double
    var radius: CGFloat = 24
    var interactive = false
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.colorScheme) private var scheme
    @Environment(\.colorSchemeContrast) private var contrast
    private var base: Color { scheme == .dark ? Color(red: 0.11, green: 0.15, blue: 0.20) : .white }
    private var backingOpacity: Double {
        if contrast == .increased { return 0.96 }
        let value = min(1, max(0, transparency))
        // Clear glass needs a contrast floor, particularly for white text over bright scenery.
        return scheme == .dark ? 0.96 - value * 0.38 : 0.94 - value * 0.72
    }
    func body(content: Content) -> some View {
        content.background {
            let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
            if reduceTransparency {
                shape.fill(base)
            } else if #available(iOS 26.0, macOS 26.0, *) {
                shape.fill(.clear)
                    .glassEffect(.clear.interactive(interactive), in: shape)
                    .background(shape.fill(base.opacity(backingOpacity)))
            } else {
                shape.fill(.ultraThinMaterial)
                    .overlay(shape.fill(base.opacity((1 - min(1, max(0, transparency))) * 0.85)))
            }
        }
        .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(.white.opacity(scheme == .dark ? 0.10 : 0.65), lineWidth: 0.6))
    }
}

extension View {
    func snapGlass(_ transparency: Double, radius: CGFloat = 24, interactive: Bool = false) -> some View {
        modifier(GlassSurface(transparency: transparency, radius: radius, interactive: interactive))
    }
    func contentPanel() -> some View {
        padding(20).background(.background.opacity(0.72), in: RoundedRectangle(cornerRadius: 24))
    }
}

struct PrimaryButton: View {
    var title: String
    var symbol = "arrow.down"
    var busy = false
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if busy { ProgressView().tint(.white) } else { Image(systemName: symbol).fontWeight(.semibold) }
                Text(title).fontWeight(.semibold)
            }.frame(maxWidth: .infinity).frame(minHeight: 52)
        }
        .buttonStyle(.plain)
        .foregroundStyle(.white)
        .background(Palette.accent, in: RoundedRectangle(cornerRadius: 18))
        .disabled(busy)
        .accessibilityLabel(busy ? "Preparing download" : title)
    }
}

struct PlatformMark: View {
    let platform: SourcePlatform
    var body: some View {
        Text(platform == .direct ? "↗" : String(platform.rawValue.prefix(1)))
            .font(.system(.caption, design: .rounded, weight: .bold))
            .frame(width: 28, height: 28).foregroundStyle(Palette.accent)
            .background(Palette.accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 9))
            .accessibilityHidden(true)
    }
}

func formattedBytes(_ bytes: Int64) -> String { ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file) }
func formattedDuration(_ seconds: Double) -> String {
    let value = max(0, Int(seconds)); return String(format: "%d:%02d", value / 60, value % 60)
}
