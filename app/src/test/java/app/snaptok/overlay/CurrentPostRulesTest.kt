package app.snaptok.overlay

import org.junit.Assert.*
import org.junit.Test

class CurrentPostRulesTest {
    private val url = "https://vt.tiktok.com/ZSbQFueH8/"
    @Test fun acceptsOnlyFreshTikTokPosts() {
        assertEquals(url, CurrentPostRules.freshLink(url, 1001, 1000, 1200))
        assertEquals("https://www.tiktok.com/@a/photo/123456789", CurrentPostRules.freshLink("Caption https://www.tiktok.com/@a/photo/123456789", 1001, 1000, 1200))
        assertNull(CurrentPostRules.freshLink("https://x.com/a/status/123456789", 1001, 1000, 1200))
        assertNull(CurrentPostRules.freshLink("https://www.tiktok.com/@a", 1001, 1000, 1200))
    }
    @Test fun staleFutureMissingAndExpiredClipboardAreRejected() {
        assertNull(CurrentPostRules.freshLink(url, 999, 1000, 1200))
        assertNull(CurrentPostRules.freshLink(url, 0, 1000, 1200))
        assertNull(CurrentPostRules.freshLink(url, 1400, 1000, 1200))
        assertNull(CurrentPostRules.freshLink(url, 1001, 1000, 9001))
        assertNull(CurrentPostRules.freshLink(null, 1001, 1000, 1200))
    }
    @Test fun unrelatedLabelsCannotTriggerSocialActions() {
        assertTrue(CurrentPostRules.isShare(" Share video "))
        assertTrue(CurrentPostRules.isCopy("COPY LINK"))
        assertTrue(CurrentPostRules.isCopy(" Copy\nlink "))
        assertTrue(CurrentPostRules.isShare("Share\u00a0video"))
        listOf("Share profile", "Share live", "Repost", "Send", "Share to friends", "Unshare").forEach { assertFalse(CurrentPostRules.isShare(it)) }
        listOf("Copy", "Copy message", "Copy link to profile", "Copied link", "Copy Link and send").forEach { assertFalse(CurrentPostRules.isCopy(it)) }
    }
    @Test fun liveTikTokShareCountLabelIsAcceptedWithoutMatchingOtherActions() {
        listOf("Share video 25K shares", "Share video 1 share", "Share video 1,234 shares", "Share video 2.5M shares", "Share photo 12 shares")
            .forEach { assertTrue(it, CurrentPostRules.isShare(it)) }
        listOf("Share profile 25K shares", "Share video with friends", "Do not share video 25K shares", "Share video 25K shares Send", "25K", "Share live 25K shares")
            .forEach { assertFalse(it, CurrentPostRules.isShare(it)) }
    }
    @Test fun ticketCannotBeReplayedOrUsedByAnotherRequest() {
        val store = CaptureTicketStore()
        store.issue("one", 1000, 50)
        assertNull(store.take("wrong", 51))
        assertEquals("one", store.take("one", 52)?.token)
        assertNull(store.take("one", 53))
    }
    @Test fun expiredCancelledAndSupersededTicketsCannotBeUsed() {
        val store = CaptureTicketStore()
        store.issue("old", 1000, 50)
        assertNull(store.take("old", 8051))
        store.issue("first", 1000, 100)
        store.issue("second", 1000, 101)
        assertNull(store.take("first", 102))
        store.clear()
        assertNull(store.take("second", 103))
    }
    @Test fun ambiguousOrOversizedClipboardIsRejected() {
        assertNull(CurrentPostRules.freshLink("$url https://vt.tiktok.com/Different/", 1001, 1000, 1200))
        assertNull(CurrentPostRules.freshLink("x".repeat(17000) + url, 1001, 1000, 1200))
    }
}
