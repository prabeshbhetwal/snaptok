package app.snaptok.core

import org.junit.Assert.*
import org.junit.Test

class TikTokLinkTest {
    @Test fun extractsCanonicalLinkFromShareText() {
        assertEquals("https://www.tiktok.com/@author/video/1234567890", TikTokLink.parse("Watch this https://www.tiktok.com/@author/video/1234567890?is_from_webapp=1&sender_device=pc").url)
    }
    @Test fun acceptsShortLinksAndPunctuation() {
        assertEquals("https://vm.tiktok.com/ZXabc123/", TikTokLink.parse("Try (https://vm.tiktok.com/ZXabc123/).").url)
        assertEquals("https://www.tiktok.com/t/ZXabc123/", TikTokLink.parse("https://www.tiktok.com/t/ZXabc123/").url)
    }
    @Test fun canonicalisesMobileAndHttp() {
        assertEquals("https://www.tiktok.com/@author/video/1234567890", TikTokLink.parse("http://m.tiktok.com/@author/video/1234567890/").url)
    }
    @Test fun rejectsSpoofedHostsAndCredentials() {
        listOf("https://www.tiktok.com.evil.test/@a/video/123456789", "https://tiktok.com@evil.test/@a/video/123456789", "https://evil@www.tiktok.com/@a/video/123456789", "https://www.tiktok.com:8080/@a/video/123456789", "file:///sdcard/video.mp4").forEach(::reject)
    }
    @Test fun rejectsPhotosProfilesAndLive() {
        listOf("https://www.tiktok.com/@a/photo/123456789", "https://www.tiktok.com/@a", "https://www.tiktok.com/@a/live", "https://example.com/video.mp4", "").forEach(::reject)
    }
    @Test fun rejectsMultipleDifferentVideosButAcceptsRepeatedLink() {
        reject("https://vm.tiktok.com/one/ https://vm.tiktok.com/two/")
        assertEquals("https://vm.tiktok.com/one/", TikTokLink.parse("https://vm.tiktok.com/one/ https://vm.tiktok.com/one/").url)
    }
    @Test fun rejectsOverlongAndEncodedPaths() {
        reject("x".repeat(16385))
        reject("https://www.tiktok.com/@a/video/123456789%2f..")
    }
    private fun reject(value: String) {
        try { TikTokLink.parse(value); fail("Accepted unsupported URL: $value") } catch (_: IllegalArgumentException) { }
    }
}
