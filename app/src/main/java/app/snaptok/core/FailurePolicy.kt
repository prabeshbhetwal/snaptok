package app.snaptok.core

data class DownloadFailure(val message: String, val retryable: Boolean)

object FailurePolicy {
    const val MAX_ATTEMPTS = 3
    fun classify(error: Throwable): DownloadFailure {
        val text = error.message.orEmpty().lowercase()
        return when {
            error is app.snaptok.download.DestinationUnavailableException -> DownloadFailure(error.message ?: "Choose a writable destination folder.", false)
            error is java.io.FileNotFoundException || "no space left" in text || "enospc" in text ->
                DownloadFailure("Could not save the video. Check free storage, then retry.", false)
            error is SecurityException -> DownloadFailure("Storage access was denied. Open SnapTok and retry.", false)
            "requested format" in text || "unsupported url" in text || error is IllegalArgumentException ->
                DownloadFailure("This post has no supported MP4 video. Photos and live streams are not supported yet.", false)
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
