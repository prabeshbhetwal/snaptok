package app.snaptok.core

import org.junit.Assert.*
import org.junit.Test

class VideoQualityTest {
    @Test fun portraitQualityUsesShortEdgeAndIgnoresAudioOnlyFormats() {
        val formats = listOf(
            VideoFormat("mp4", 1920, true, true, 1080),
            VideoFormat("mp4", 1080, true, true, 1920),
            VideoFormat("mp4", 1280, true, true, 720),
            VideoFormat("webm", 2160, true, true, 3840),
            VideoFormat("mp4", 0, false, true)
        )
        assertEquals(listOf(1080, 720), VideoQuality.heights(formats))
    }
    @Test fun doesNotOfferVideoOnlyResolutionWhenAudioFormatsExist() {
        assertEquals(listOf(720), VideoQuality.heights(listOf(VideoFormat("mp4", 1080, true, false), VideoFormat("mp4", 720, true, true))))
    }
    @Test fun supportsSourcesThatAreActuallySilent() {
        assertEquals(listOf(480), VideoQuality.heights(listOf(VideoFormat("mp4", 480, true, false))))
        assertTrue(VideoQuality.selector(480).endsWith("/bestvideo[ext=mp4]"))
    }
    @Test fun unknownCodecMetadataOnXDoesNotHideValidMp4Qualities() {
        val direct = VideoQuality.format("mp4", 720, 720, null, null)
        val separateVideo = VideoQuality.format("mp4", 1080, 1080, "avc1", "none")
        val audioOnly = VideoQuality.format("mp4", 0, 0, "none", "aac")
        assertEquals(listOf(720), VideoQuality.heights(listOf(direct, separateVideo, audioOnly)))
        assertFalse(VideoQuality.selector(720).contains("[vcodec!="))
    }
    @Test fun qualityPreferenceDoesNotUpscaleOrRequireAnUnavailableExactFormat() {
        assertEquals("res:720", VideoQuality.sort(720))
        assertNull(VideoQuality.sort(0))
        assertFalse(VideoQuality.selector(720).contains("[height=720]"))
    }
}
