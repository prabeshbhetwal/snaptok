// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SnapTok",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [
        .library(name: "SnapTokCore", targets: ["SnapTokCore"]),
        .library(name: "SnapTokUI", targets: ["SnapTokUI"]),
        .executable(name: "SnapTokPreview", targets: ["SnapTokPreview"]),
        .executable(name: "SnapTokCheck", targets: ["SnapTokCheck"])
    ],
    targets: [
        .target(name: "SnapTokCore"),
        .target(name: "SnapTokUI", dependencies: ["SnapTokCore"]),
        .executableTarget(name: "SnapTokPreview", dependencies: ["SnapTokUI", "SnapTokCore"]),
        .executableTarget(name: "SnapTokCheck", dependencies: ["SnapTokCore"]),
        .testTarget(name: "SnapTokCoreTests", dependencies: ["SnapTokCore"])
    ],
    swiftLanguageModes: [.v5]
)
