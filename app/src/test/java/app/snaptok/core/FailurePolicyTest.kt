package app.snaptok.core

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class FailurePolicyTest {
    @Test fun rateLimitsAndAccessBlocksDoNotRetryOrSuggestUpdating() {
        listOf("HTTP Error 429", "anonymous rate-limit reached", "HTTP 403 Forbidden", "captcha required").forEach {
            val failure = FailurePolicy.classify(IOException(it))
            assertFalse(failure.retryable)
            assertFalse(failure.message.contains("updating"))
        }
    }
    @Test fun unsupportedAudioMergeHasClearPermanentFailure() {
        val failure = FailurePolicy.classify(IllegalStateException("Separate audio track requires merging"))
        assertFalse(failure.retryable)
        assertTrue(failure.message.contains("sound"))
    }
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
    @Test fun protectedOrVideoLessPostsFailWithoutPointlessRetries() {
        listOf("This tweet is protected", "Sign in to view this post", "No video could be found in this tweet").forEach {
            assertFalse(FailurePolicy.classify(IOException(it)).retryable)
        }
    }
    @Test fun extractorErrorsGiveActionableAdviceWithoutLeakingRawLogs() {
        val failure = FailurePolicy.classify(IOException("Unable to extract webpage https://vm.tiktok.com/private-token"))
        assertTrue(failure.retryable)
        assertTrue(failure.message.contains("updating"))
        assertFalse(failure.message.contains("private-token"))
    }
}
