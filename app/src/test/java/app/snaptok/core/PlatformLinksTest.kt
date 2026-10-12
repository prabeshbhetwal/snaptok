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
        assertEquals(1,parseBatch("https://youtu.be/BaW_jenozKc\nhttps://youtube.com/shorts/BaW_jenozKc").size)
        assertThrows(IllegalArgumentException::class.java) { parseBatch("https://example.com/video.mp4") }
        assertThrows(IllegalArgumentException::class.java) { parseBatch("") }
    }
}
