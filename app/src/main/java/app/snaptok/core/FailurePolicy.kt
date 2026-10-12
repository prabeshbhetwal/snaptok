package app.snaptok.core

data class DownloadFailure(val message: String, val retryable: Boolean)

object FailurePolicy {
    const val MAX_ATTEMPTS = 3
    fun classify(error: Throwable): DownloadFailure {
        val text = error.message.orEmpty().lowercase()
        return when {
            error is app.snaptok.download.PhotoPostException -> DownloadFailure(error.message ?: "This photo post could not be saved.", false)
            "no downloadable media returned" in text -> DownloadFailure("No supported media was returned. The post may be live, unavailable or above the file size limit.", false)
            "size limit" in text -> DownloadFailure("The media exceeds your file size limit. Increase the limit in Settings, then choose Retry with current defaults from the video menu.", false)
            "audio verification" in text -> DownloadFailure("The source audio could not be saved correctly. Try another format or update download support.", false)
            error is app.snaptok.download.DestinationUnavailableException -> DownloadFailure(error.message ?: "Choose a writable destination folder.", false)
            error is java.io.FileNotFoundException || "no space left" in text || "enospc" in text ->
                DownloadFailure("Could not save the video. Check free storage, then retry.", false)
            error is SecurityException -> DownloadFailure("Storage access was denied. Open SnapTok and retry.", false)
            "separate audio track" in text ->
                DownloadFailure("This video needs separate audio and video tracks to be combined. This version cannot save it with sound. Open the original post instead.", false)
            "429" in text || "rate-limit" in text || "rate limit" in text || "too many requests" in text ->
                DownloadFailure("The source is limiting requests. Wait a while before retrying, or open the original post.", false)
            "captcha" in text || "403" in text ->
                DownloadFailure("The source blocked this request. Open the original post to check access, then try again later.", false)
            "requested format" in text || "unsupported url" in text || error is IllegalArgumentException ->
                DownloadFailure("This post has no supported video or TikTok photo album. Live streams and inaccessible posts are not supported.", false)
            "protected" in text || "sign in" in text || "login" in text || "authentication" in text ->
                DownloadFailure("This post requires login or is not publicly accessible. Open the original post to check it.", false)
            "no video" in text -> DownloadFailure("This post does not contain a downloadable video.", false)
            ("video is private" in text || "private video" in text || "private account" in text) || "login required" in text || "log in" in text || "not available" in text || "unavailable" in text || "removed" in text ->
                DownloadFailure("This video is private, removed or unavailable. Open the original post to check availability.", false)
            "extract" in text || "webpage" in text || "403" in text || "429" in text || "captcha" in text ->
                DownloadFailure("The source could not provide the video. Try updating the download engine in SnapTok, then retry.", true)
            else -> DownloadFailure("The download was interrupted. Check your connection and retry.", true)
        }
    }
    fun shouldRetry(failure: DownloadFailure, previousAttempts: Int) =
        failure.retryable && previousAttempts < MAX_ATTEMPTS - 1
}
