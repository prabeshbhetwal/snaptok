package app.snaptok.core

import java.net.URI

data class VideoLink(val url: String) {
    companion object {
        private val hosts = setOf("tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com")
        private val xHosts = setOf("x.com", "www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")
        private val xPost = Regex("/(?:[A-Za-z0-9_]+/status|i/status|i/web/status)/([0-9]{6,})(?:/video/([1-9][0-9]*))?/?")
        private val urlPattern = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)
        private val post = Regex("/@[^/]+/video/[0-9]{6,}/?")
        private val short = Regex("/[A-Za-z0-9]+/?")
        private val namedShort = Regex("/t/[A-Za-z0-9]+/?")

        fun parse(text: String): VideoLink {
            require(text.length <= 16_384) { "Shared text is too long." }
            val links = urlPattern.findAll(text).mapNotNull { match ->
                val raw = match.value.trimEnd('.', ',', ';', '!', '?', ')', ']', '}')
                val uri = runCatching { URI(raw) }.getOrNull() ?: return@mapNotNull null
                val host = uri.host?.lowercase() ?: return@mapNotNull null
                if ((host !in hosts && host !in xHosts) || uri.userInfo != null || uri.port != -1) return@mapNotNull null
                val path = uri.rawPath.orEmpty()
                if (host in xHosts) {
                    val matchPost = xPost.matchEntire(path) ?: return@mapNotNull null
                    val suffix = matchPost.groupValues[2].takeIf { it.isNotEmpty() }?.let { "/video/$it" }.orEmpty()
                    return@mapNotNull VideoLink("https://x.com/i/status/${matchPost.groupValues[1]}$suffix")
                }
                val isShortHost = host == "vm.tiktok.com" || host == "vt.tiktok.com"
                when {
                    isShortHost && short.matches(path) -> VideoLink("https://$host/${path.trim('/')}/")
                    !isShortHost && post.matches(path) -> VideoLink("https://www.tiktok.com${path.trimEnd('/')}")
                    !isShortHost && namedShort.matches(path) -> VideoLink("https://www.tiktok.com${path.trimEnd('/')}/")
                    else -> null
                }
            }.distinct().toList()
            require(links.size == 1) { if (links.size > 1) "Share one video at a time." else "Use a TikTok or X video-post link. Photos, profiles and live streams are not supported yet." }
            return links.single()
        }
    }
}
