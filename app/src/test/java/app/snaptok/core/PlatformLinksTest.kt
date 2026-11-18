package app.snaptok.core
import org.junit.Assert.*
import org.junit.Test
class PlatformLinksTest {
    @Test fun normalisesYoutubeAndRejectsPlaylistsProfilesAndSpoofing() {
        val expected = "https://www.youtube.com/watch?v=BaW_jenozKc"
        assertEquals(expected,VideoLink.parse("https://youtu.be/BaW_jenozKc?si=tracking").url)
        assertEquals(expected,VideoLink.parse("https://www.youtube.com/shorts/BaW_jenozKc").url)
        listOf("https://youtube.com/playlist?list=anything","https://youtube.com/@user","https://youtube.com.evil.test/watch?v=BaW_jenozKc","https://evil@youtube.com/watch?v=BaW_jenozKc").forEach { value -> assertThrows(IllegalArgumentException::class.java) { VideoLink.parse(value) } }
    }
    @Test fun acceptsOnlyPostRoutesOnFacebookAndReddit() {
        assertEquals("https://www.facebook.com/watch/?v=12345",VideoLink.parse("https://m.facebook.com/watch/?v=12345&track=1").url)
        assertEquals("https://www.reddit.com/comments/abc123/",VideoLink.parse("https://www.reddit.com/r/videos/comments/abc123/a_video/?utm=tracking").url)
        listOf("https://facebook.com/user","https://reddit.com/r/videos/","https://reddit.com.evil.test/comments/abc123/").forEach { value -> assertThrows(IllegalArgumentException::class.java) { VideoLink.parse(value) } }
    }
    @Test fun batchValidatesAndDeduplicatesBeforeScheduling() {
        assertEquals(1,parseBatch("https://youtu.be/BaW_jenozKc\nhttps://youtube.com/shorts/BaW_jenozKc").links.size)
        assertThrows(IllegalArgumentException::class.java) { parseBatch("https://example.com/video.mp4") }
        assertThrows(IllegalArgumentException::class.java) { parseBatch("") }
    }
    @Test fun batchKeepsGoodLinksAndCountsSkippedOnes() {
        val result = parseBatch("https://youtu.be/BaW_jenozKc\nhttps://example.com/nope\nhttps://youtube.com/@user")
        assertEquals(1,result.links.size)
        assertEquals(2,result.skipped)
    }
    @Test fun acceptsCommonShareShapes() {
        assertEquals("https://www.instagram.com/p/AbC_123/",VideoLink.parse("https://www.instagram.com/someuser/reel/AbC_123/").url)
        assertEquals("https://www.instagram.com/p/AbC_123/",VideoLink.parse("https://www.instagram.com/tv/AbC_123/").url)
        assertEquals("https://www.reddit.com/comments/abc123/",VideoLink.parse("https://redd.it/abc123").url)
        assertEquals("https://www.reddit.com/r/videos/s/AbC123xyz/",VideoLink.parse("https://www.reddit.com/r/videos/s/AbC123xyz").url)
        assertEquals("https://www.facebook.com/page/videos/some-title/12345/",VideoLink.parse("https://www.facebook.com/page/videos/some-title/12345/").url)
        assertEquals("https://www.facebook.com/page/videos/12345/",VideoLink.parse("https://web.facebook.com/page/videos/12345").url)
        assertEquals("https://www.youtube.com/watch?v=BaW_jenozKc",VideoLink.parse("https://www.youtube.com/live/BaW_jenozKc?feature=share").url)
        assertEquals("https://www.youtube.com/watch?v=BaW_jenozKc",VideoLink.parse("https://music.youtube.com/watch?v=BaW_jenozKc").url)
        assertEquals("https://www.tiktok.com/@_/video/7312345678901234567",VideoLink.parse("https://m.tiktok.com/v/7312345678901234567.html").url)
    }
}
