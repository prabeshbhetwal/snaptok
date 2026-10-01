import XCTest
@testable import SnapTokCore

final class LinkTests: XCTestCase {
    func testSharedCaptionFindsAndNormalisesLink() throws {
        let link = try VideoLink.parse("Watch this https://www.instagram.com/reel/ABC_123/?igsh=tracking")
        XCTAssertEqual(link.platform, .instagram)
        XCTAssertEqual(link.url.absoluteString, "https://www.instagram.com/p/ABC_123/")
    }
    func testYouTubeVariantsHaveSameIdentity() throws {
        let links = try ["https://youtu.be/aqz-KE-bpKQ?t=4", "https://www.youtube.com/shorts/aqz-KE-bpKQ", "https://www.youtube.com/watch?v=aqz-KE-bpKQ&list=anything"].map(VideoLink.parse)
        XCTAssertEqual(links[0], links[1]); XCTAssertEqual(links[1], links[2])
    }
    func testXKeepsSelectedVideo() throws {
        let link = try VideoLink.parse("https://twitter.com/person/status/2001950365332455490/video/2?s=20")
        XCTAssertEqual(link.url.absoluteString, "https://x.com/i/status/2001950365332455490/video/2")
    }
    func testSignedDirectLinkPreservesQuery() throws {
        let link = try VideoLink.parse("https://cdn.example.com/video.mp4?token=a%2Fb&expires=123#fragment")
        XCTAssertEqual(link.url.absoluteString, "https://cdn.example.com/video.mp4?token=a%2Fb&expires=123")
        XCTAssertEqual(link.platform, .direct)
    }
    func testRejectsMisleadingHostsAndUnsupportedRoutes() {
        for text in ["https://youtube.com.evil.test/watch?v=aqz-KE-bpKQ", "https://x.com@evil.test/a.mp4", "https://youtube.com/@creator", "https://instagram.com/person/", "https://www.tiktok.com/@person/live", "http://cdn.example.com/video.mp4", "https://cdn.example.com:8443/video.mp4", "javascript:alert(1)"] {
            XCTAssertThrowsError(try VideoLink.parse(text), text)
        }
    }
    func testBatchIsNotSilentlyTruncated() {
        XCTAssertThrowsError(try VideoLink.parse("https://cdn.example.com/1.mp4 https://cdn.example.com/2.mp4"))
    }
    func testRepeatedSameLinkIsAccepted() throws {
        XCTAssertEqual(try VideoLink.parse("https://cdn.example.com/1.mp4 https://cdn.example.com/1.mp4").platform, .direct)
    }
    func testAllSupportedPlatforms() throws {
        for (text, expected) in [("https://vm.tiktok.com/ZM12345/", SourcePlatform.tiktok), ("https://www.facebook.com/reel/1195289147628387", .facebook), ("https://www.reddit.com/r/test/comments/90bu6w/a_title/", .reddit)] {
            XCTAssertEqual(try VideoLink.parse(text).platform, expected)
        }
    }
    func testBridgeTransportBoundary() throws {
        for address in ["https://bridge.example.com", "http://my-mac.local:8765", "http://127.0.0.1:8765"] { XCTAssertNoThrow(try BridgeAddress.validate(address)) }
        for address in ["http://public.example.com", "https://user:password@bridge.example.com", "https://bridge.example.com?token=secret", "file:///tmp/a"] { XCTAssertThrowsError(try BridgeAddress.validate(address)) }
    }
}
