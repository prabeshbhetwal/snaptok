package app.snaptok.core

import java.net.URI

data class VideoLink(val url: String) {
    companion object {
        private val hosts = setOf("tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com")
        private val instagramHosts = setOf("instagram.com", "www.instagram.com", "m.instagram.com")
        private val instagramPost = Regex("/(?:reel|reels|p)/([A-Za-z0-9_-]+)/?")
        private val extraHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch", "reddit.com", "www.reddit.com", "old.reddit.com", "v.redd.it")
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
                if ((host !in hosts && host !in xHosts && host !in instagramHosts && host !in extraHosts) || uri.userInfo != null || uri.port != -1) return@mapNotNull null
                val path = uri.rawPath.orEmpty()
                if (host in extraHosts) {
                    val query = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }.filter { it.size == 2 }.associate { it[0] to it[1] }
                    when {
                        host.contains("youtube.com") || host == "youtu.be" -> {
                            val id = if (host == "youtu.be") path.trim('/') else if (path == "/watch") query["v"].orEmpty() else Regex("/(?:shorts|embed)/([A-Za-z0-9_-]{11})/?").matchEntire(path)?.groupValues?.get(1).orEmpty()
                            if (Regex("[A-Za-z0-9_-]{11}").matches(id)) return@mapNotNull VideoLink("https://www.youtube.com/watch?v=$id")
                        }
                        host.contains("facebook.com") -> {
                            if (path.trimEnd('/') in listOf("/watch", "/video.php") && query["v"]?.matches(Regex("[0-9]+")) == true) return@mapNotNull VideoLink("https://www.facebook.com/watch/?v=${query["v"]}")
                            if (Regex("/(?:reel/[0-9]+|[^/]+/videos/[0-9]+|share/[vr]/[A-Za-z0-9]+)/?").matches(path)) return@mapNotNull VideoLink("https://www.facebook.com${path.trimEnd('/')}/")
                        }
                        host == "fb.watch" && Regex("/[A-Za-z0-9_-]+/?").matches(path) -> return@mapNotNull VideoLink("https://fb.watch${path.trimEnd('/')}/")
                        host == "v.redd.it" && Regex("/[a-z0-9]+/?").matches(path) -> return@mapNotNull VideoLink("https://v.redd.it/${path.trim('/')}")
                        host.endsWith("reddit.com") -> {
                            val id = Regex("/(?:(?:r|user)/[^/]+/)?comments/([a-z0-9]+)(?:/[^/]*)?/?").matchEntire(path)?.groupValues?.get(1)
                            if (id != null) return@mapNotNull VideoLink("https://www.reddit.com/comments/$id/")
                        }
                    }
                    return@mapNotNull null
                }
                if (host in instagramHosts) {
                    val code = instagramPost.matchEntire(path)?.groupValues?.get(1) ?: return@mapNotNull null
                    return@mapNotNull VideoLink("https://www.instagram.com/p/$code/")
                }
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
            require(links.size == 1) { if (links.size > 1) "Share one video at a time." else "Use a supported video-post link: TikTok, X, Instagram, YouTube, Facebook or Reddit. Photos, profiles and live streams are not supported." }
            return links.single()
        }
    }
}

fun videoPlatform(url: String): String = when (runCatching { URI(url).host }.getOrNull()) {
    "www.instagram.com" -> "Instagram"
    "x.com" -> "X"
    "www.youtube.com" -> "YouTube"
    "www.facebook.com", "fb.watch" -> "Facebook"
    "www.reddit.com", "v.redd.it" -> "Reddit"
    else -> "TikTok"
}

fun parseBatch(text: String): List<VideoLink> {
    require(text.length <= 100_000) { "Batch is too large. Add up to 50 links." }
    val links = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE).findAll(text).map { VideoLink.parse(it.value) }.distinct().toList()
    require(links.isNotEmpty() && links.size <= 50) { "Add between 1 and 50 supported video links." }
    return links
}
