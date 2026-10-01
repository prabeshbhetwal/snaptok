import Foundation

public struct VideoLink: Equatable, Sendable {
    public let url: URL
    public let platform: SourcePlatform

    public static func parse(_ text: String) throws -> VideoLink {
        guard text.utf8.count <= 16_384 else { throw SnapError.invalidLink }
        let regex = try NSRegularExpression(pattern: #"https?://[^\s<>\"]+"#, options: .caseInsensitive)
        let matches = regex.matches(in: text, range: NSRange(text.startIndex..., in: text))
        var candidates: [VideoLink] = []
        for match in matches {
            guard let range = Range(match.range, in: text) else { continue }
            let raw = String(text[range]).trimmingCharacters(in: CharacterSet(charactersIn: ".,;!)]}"))
            guard let link = try? canonicalise(raw) else { throw SnapError.invalidLink }
            if !candidates.contains(link) { candidates.append(link) }
        }
        guard candidates.count == 1 else { throw candidates.count > 1 ? SnapError.multipleLinks : SnapError.invalidLink }
        return candidates[0]
    }

    private static func canonicalise(_ text: String) throws -> VideoLink {
        guard var c = URLComponents(string: text), let host = c.host?.lowercased(),
              c.user == nil, c.password == nil, c.port == nil,
              ["https", "http"].contains(c.scheme?.lowercased() ?? "") else { throw SnapError.invalidLink }
        c.host = host
        let p = c.path
        func match(_ pattern: String) -> [String]? {
            guard let re = try? NSRegularExpression(pattern: "^(?:" + pattern + ")$"),
                  let m = re.firstMatch(in: p, range: NSRange(p.startIndex..., in: p)) else { return nil }
            return (0..<m.numberOfRanges).map { Range(m.range(at: $0), in: p).map { String(p[$0]) } ?? "" }
        }
        func make(_ string: String, _ platform: SourcePlatform) throws -> VideoLink {
            guard let url = URL(string: string) else { throw SnapError.invalidLink }
            return VideoLink(url: url, platform: platform)
        }
        let query = c.queryItems ?? []
        if ["youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"].contains(host) {
            let id = host == "youtu.be" ? p.trimmingCharacters(in: CharacterSet(charactersIn: "/")) :
                (p == "/watch" ? query.first(where: { $0.name == "v" })?.value : match(#"/(?:shorts|embed)/([\w-]{11})/?"#)?[1])
            guard let id, id.range(of: #"^[A-Za-z0-9_-]{11}$"#, options: .regularExpression) != nil else { throw SnapError.invalidLink }
            return try make("https://www.youtube.com/watch?v=\(id)", .youtube)
        }
        if ["x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com", "mobile.x.com"].contains(host) {
            guard let m = match(#"/(?:[A-Za-z0-9_]+/status|i/status|i/web/status)/([0-9]{6,})(?:/video/([1-9][0-9]*))?/?"#) else { throw SnapError.invalidLink }
            return try make("https://x.com/i/status/\(m[1])" + (m[2].isEmpty ? "" : "/video/\(m[2])"), .x)
        }
        if ["instagram.com", "www.instagram.com", "m.instagram.com"].contains(host) {
            guard let m = match(#"/(?:p|reel|reels)/([A-Za-z0-9_-]+)/?"#) else { throw SnapError.invalidLink }
            return try make("https://www.instagram.com/p/\(m[1])/", .instagram)
        }
        if ["tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com"].contains(host) {
            let short = ["vm.tiktok.com", "vt.tiktok.com"].contains(host)
            guard match(short ? #"/[A-Za-z0-9]+/?"# : #"/(?:@[^/]+/video/[0-9]{6,}|t/[A-Za-z0-9]+)/?"#) != nil else { throw SnapError.invalidLink }
            return try make("https://\(short ? host : "www.tiktok.com")\(p)", .tiktok)
        }
        if ["reddit.com", "www.reddit.com", "old.reddit.com"].contains(host) {
            guard let m = match(#"/(?:(?:r|user)/[^/]+/)?comments/([a-z0-9]+)(?:/[^/]*)?/?"#) else { throw SnapError.invalidLink }
            return try make("https://www.reddit.com/comments/\(m[1])/", .reddit)
        }
        if host == "v.redd.it", match(#"/[a-z0-9]+/?"#) != nil {
            return try make("https://\(host)\(p)", .reddit)
        }
        if ["facebook.com", "www.facebook.com", "m.facebook.com"].contains(host) {
            if ["/watch", "/watch/", "/video.php"].contains(p), let id = query.first(where: { $0.name == "v" })?.value,
               id.range(of: #"^[0-9]+$"#, options: .regularExpression) != nil {
                return try make("https://www.facebook.com/watch/?v=\(id)", .facebook)
            }
            guard match(#"/(?:reel/[0-9]+|[^/]+/videos/[0-9]+|share/[vr]/[A-Za-z0-9]+)/?"#) != nil else { throw SnapError.invalidLink }
            return try make("https://www.facebook.com\(p)", .facebook)
        }
        if host == "fb.watch", match(#"/[A-Za-z0-9_-]+/?"#) != nil { return try make("https://fb.watch\(p)", .facebook) }
        guard c.scheme?.lowercased() == "https", let url = c.url,
              ["mp4", "mov", "m4v"].contains(url.pathExtension.lowercased()) else { throw SnapError.invalidLink }
        c.fragment = nil // Preserve signed media query parameters.
        return VideoLink(url: c.url!, platform: .direct)
    }
}

public enum BridgeAddress {
    public static func validate(_ text: String) throws -> URL {
        guard var c = URLComponents(string: text.trimmingCharacters(in: .whitespacesAndNewlines)),
              let host = c.host?.lowercased(), !host.isEmpty,
              c.user == nil, c.password == nil, c.query == nil, c.fragment == nil else { throw SnapError.invalidBridge }
        let local = host == "localhost" || host == "127.0.0.1" || host == "::1" || host.hasSuffix(".local")
        guard c.scheme == "https" || (c.scheme == "http" && local) else { throw SnapError.invalidBridge }
        c.path = c.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if !c.path.isEmpty { c.path = "/" + c.path }
        guard let url = c.url else { throw SnapError.invalidBridge }; return url
    }
}
