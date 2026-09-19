package app.snaptok.core

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class FailurePolicyTest {
    @Test fun networkRetriesAreBounded() {
        val failure = FailurePolicy.classify(IOException("Connection reset"))
        assertTrue(FailurePolicy.shouldRetry(failure, 0))
        assertTrue(FailurePolicy.shouldRetry(failure, 1))
        assertFalse(FailurePolicy.shouldRetry(failure, 2))
    }
    @Test fun permanentFailuresDoNotRetry() {
        listOf("Video is private", "Video not available", "Requested format is not available", "No space left on device").forEach {
            assertFalse(FailurePolicy.shouldRetry(FailurePolicy.classify(IOException(it)), 0))
        }
    }
    @Test fun extractorErrorsGiveActionableAdviceWithoutLeakingRawLogs() {
        val failure = FailurePolicy.classify(IOException("Unable to extract webpage https://vm.tiktok.com/private-token"))
        assertTrue(failure.retryable)
        assertTrue(failure.message.contains("updating"))
        assertFalse(failure.message.contains("private-token"))
    }
}
