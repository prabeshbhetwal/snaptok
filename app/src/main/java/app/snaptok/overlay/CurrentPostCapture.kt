package app.snaptok.overlay

import app.snaptok.core.VideoLink
import java.net.URI
import java.util.Locale

/** Rules shared by the Android adapter and tests. No UI inspection or clipboard polling while idle. */
internal object CurrentPostRules {
    val packages = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    const val MODE = "currentPost"
    const val CONSENT = "currentPostConsentV1"
    const val TIMEOUT_MS = 8_000L
    fun label(value: CharSequence?) = value?.toString()?.replace('\u00a0', ' ')?.trim()?.replace(Regex("\\s+"), " ")?.lowercase(Locale.ROOT).orEmpty()
    // Verified on TikTok 47.1.4 / S24 Ultra: "Share video 25K shares".
    // Keep the entire shape anchored; never match profile shares, recipients or caption fragments.
    private val countedShare = Regex("share (?:video|post|photos?) [0-9]+(?:[.,][0-9]+)*[kmb]? shares?")
    fun isShare(value: CharSequence?): Boolean {
        val label = label(value)
        return label in setOf("share", "share video", "share post", "share photo", "share photos") || countedShare.matches(label)
    }
    fun isCopy(value: CharSequence?) = label(value) == "copy link"

    fun freshLink(text: String?, copiedAt: Long, clickedAt: Long, now: Long): String? {
        if (clickedAt <= 0 || copiedAt < clickedAt || copiedAt > now || now - clickedAt > TIMEOUT_MS) return null
        val link = text?.let { runCatching { VideoLink.parse(it) }.getOrNull() } ?: return null
        val host = URI(link.url).host
        return link.url.takeIf { host == "www.tiktok.com" || host == "vt.tiktok.com" || host == "vm.tiktok.com" }
    }
}

/** A single-use in-memory handoff. Restarting the process deliberately invalidates a capture. */
internal class CaptureTicketStore {
    data class Ticket(val token: String, val clickedAt: Long, val expiresAt: Long)
    private var ticket: Ticket? = null
    @Synchronized fun issue(token: String, clickedAt: Long, elapsedNow: Long): Ticket =
        Ticket(token, clickedAt, elapsedNow + CurrentPostRules.TIMEOUT_MS).also { ticket = it }
    @Synchronized fun take(token: String?, elapsedNow: Long): Ticket? {
        val current = ticket ?: return null
        if (elapsedNow > current.expiresAt) { ticket = null; return null }
        if (token != current.token) return null
        ticket = null
        return current
    }
    @Synchronized fun clear() { ticket = null }
}

internal object CurrentPostCapture {
    const val EXTRA_TOKEN = "currentPostCaptureToken"
    val tickets = CaptureTicketStore()
    val busy = kotlinx.coroutines.flow.MutableStateFlow(false)
}
