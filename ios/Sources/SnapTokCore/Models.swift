import Foundation

public enum SourcePlatform: String, Codable, CaseIterable, Sendable {
    case tiktok = "TikTok", instagram = "Instagram", youtube = "YouTube"
    case x = "X", facebook = "Facebook", reddit = "Reddit", direct = "Video link"
}

public enum VideoQuality: Int, Codable, CaseIterable, Identifiable, Sendable {
    case best = 0, fullHD = 1080, hd = 720, compact = 480
    public var id: Int { rawValue }
    public var label: String { self == .best ? "Best available" : "\(rawValue)p" }
}

public enum DownloadState: String, Codable, Sendable {
    case preparing, queued, downloading, verifying, saved, failed, cancelled
    public var isActive: Bool { [.preparing, .queued, .downloading, .verifying].contains(self) }
    public var label: String {
        switch self {
        case .preparing: "Preparing"
        case .queued: "Waiting to download"
        case .downloading: "Downloading"
        case .verifying: "Checking video"
        case .saved: "Saved"
        case .failed: "Needs attention"
        case .cancelled: "Cancelled"
        }
    }
}

public struct DownloadOptions: Codable, Equatable, Sendable {
    public var quality: VideoQuality = .best
    public var allowsCellular = true
    public var maxBytes: Int64 = 1_024 * 1_024 * 1_024
    public init() {}
}

public struct DownloadJob: Codable, Identifiable, Equatable, Sendable {
    public var id = UUID()
    public var attemptID = UUID()
    public var source: URL
    public var platform: SourcePlatform
    public var title: String
    public var createdAt = Date()
    public var updatedAt = Date()
    public var options: DownloadOptions
    public var state: DownloadState = .preparing
    public var receivedBytes: Int64 = 0
    public var expectedBytes: Int64 = 0
    public var error: String?
    public var fileName: String?
    public var duration: Double?
    public var isFavourite = false
    public var collection = ""
    public var sessionIdentifier: String?
    public var taskIdentifier: Int?
    public var taskKey: String { id.uuidString + ":" + attemptID.uuidString }
    public var progress: Double? {
        guard expectedBytes > 0 else { return nil }
        return min(1, max(0, Double(receivedBytes) / Double(expectedBytes)))
    }
    public init(link: VideoLink, options: DownloadOptions) {
        source = link.url; platform = link.platform; self.options = options
        title = link.platform == .direct ? link.url.deletingPathExtension().lastPathComponent : "\(link.platform.rawValue) video"
    }
}

public enum Appearance: String, Codable, CaseIterable, Sendable { case system, light, dark }

public struct Preferences: Codable, Equatable, Sendable {
    public var appearance: Appearance = .system
    /// Higher values reveal more of the scenery beneath custom controls. Text stays opaque.
    public var transparency = 0.55
    public var reduceMotion = false
    public var notifications = false
    public var options = DownloadOptions()
    public var bridgeURL = ""
    public var gridLibrary = true
    public init() {}
    public var safeTransparency: Double { transparency.isFinite ? min(1, max(0, transparency)) : 0.55 }
}

public enum SnapError: LocalizedError, Equatable {
    case invalidLink, multipleLinks, missingBridge, invalidBridge, missingFile, storage(String), transfer(String)
    public var errorDescription: String? {
        switch self {
        case .invalidLink: "Use a video-post link or a direct HTTPS MP4, MOV or M4V link."
        case .multipleLinks: "Add one video link at a time."
        case .missingBridge: "Connect your private download bridge in Settings to save social videos. Direct video links work without it."
        case .invalidBridge: "Use an HTTPS bridge address, or an HTTP .local address on your own network."
        case .missingFile: "This video is no longer in SnapTok. Download it again."
        case .storage(let message), .transfer(let message): message
        }
    }
}
