package app.snaptok.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DownloadDiagnostics {
    const val STALE_DAYS = 60L
    private val versionDate = Regex("^(\\d{4})\\.(\\d{2})\\.(\\d{2})")

    /** yt-dlp exits 0 when it skips a post, so its skip notice is the only clue to why no file exists. */
    fun skipReason(output: String): String? = when {
        "larger than max-filesize" in output -> "Video exceeds the selected file size limit"
        "does not pass filter" in output && "is_live" in output -> "Live streams are not supported"
        else -> null
    }

    /** yt-dlp versions are dates (2026.08.19); an old engine breaks when sites change. */
    fun isStale(version: String?, today: LocalDate): Boolean {
        val (y, m, d) = versionDate.find(version.orEmpty())?.destructured ?: return false
        val date = runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull() ?: return false
        return ChronoUnit.DAYS.between(date, today) > STALE_DAYS
    }
}
