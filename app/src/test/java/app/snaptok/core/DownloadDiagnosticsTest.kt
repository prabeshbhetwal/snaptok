package app.snaptok.core

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class DownloadDiagnosticsTest {
    @Test fun skippedPostsGetSpecificReasons() {
        assertEquals("Video exceeds the selected file size limit", DownloadDiagnostics.skipReason("[download] File is larger than max-filesize (2000 bytes > 1000 bytes). Aborting."))
        assertEquals("Live streams are not supported", DownloadDiagnostics.skipReason("[download] abc: Live now does not pass filter (!is_live), skipping .."))
        assertNull(DownloadDiagnostics.skipReason("[download] Destination: video.mp4"))
    }
    @Test fun skipReasonsMapToPermanentMessages() {
        val size = FailurePolicy.classify(IllegalStateException(DownloadDiagnostics.skipReason("larger than max-filesize")))
        assertTrue(size.message.contains("file size limit")); assertFalse(size.retryable)
        val live = FailurePolicy.classify(IllegalStateException(DownloadDiagnostics.skipReason("does not pass filter (!is_live)")))
        assertTrue(live.message.contains("Live")); assertFalse(live.retryable)
    }
    @Test fun engineAgeIsReadFromTheVersionDate() {
        val today = LocalDate.of(2026, 11, 18)
        assertFalse(DownloadDiagnostics.isStale("2026.10.01", today))
        assertTrue(DownloadDiagnostics.isStale("2026.08.19", today))
        assertTrue(DownloadDiagnostics.isStale("2026.08.19.232802", today))
        assertFalse(DownloadDiagnostics.isStale("nightly", today))
        assertFalse(DownloadDiagnostics.isStale(null, today))
    }
    @Test fun staleEngineAddsUpdateAdviceToExtractorFailures() {
        val error = IOException("Unable to extract webpage")
        assertFalse(FailurePolicy.classify(error).message.contains("days old"))
        assertTrue(FailurePolicy.classify(error, engineStale = true).message.contains("more than 60 days old"))
        assertFalse(FailurePolicy.classify(IOException("Connection reset"), engineStale = true).message.contains("days old"))
    }
}
