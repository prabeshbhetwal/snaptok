package app.snaptok.core
import org.junit.Assert.*
import org.junit.Test
class LibrarySearchTest {
    @Test fun matchesAcrossTitlePlatformAndUrlWithoutCaseSensitivity() {
        val url = "https://www.instagram.com/p/abc/"
        assertTrue(matchesLibrarySearch("Morning walk", url, " INSTAGRAM  Walk "))
        assertTrue(matchesLibrarySearch("Morning walk", url, "abc"))
        assertTrue(matchesLibrarySearch("Morning walk", url, " "))
        assertFalse(matchesLibrarySearch("Morning walk", url, "TikTok"))
    }
}
