package app.snaptok.download

import android.app.Application
import app.snaptok.core.VideoLink
import app.snaptok.data.DownloadOptions
import app.snaptok.data.downloadRequestKey
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PhotoMetadataTest {
    private fun html(images: JSONArray = JSONArray().put(image("first")).put(image("second"))): String {
        val item = JSONObject().put("id", "123456789").put("desc", "Photo post")
            .put("imagePost", JSONObject().put("images", images)).put("music", JSONObject().put("playUrl", "https://sf16.tiktokcdn.com/sound.mp3"))
        return "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">${JSONObject().put("__DEFAULT_SCOPE__", JSONObject().put("webapp.video-detail", JSONObject().put("itemInfo", JSONObject().put("itemStruct", item))))}</script>"
    }
    private fun image(name: String) = JSONObject().put("imageURL", JSONObject().put("urlList", JSONArray().put("https://p16.tiktokcdn.com/$name.jpg")))
    @Test fun preservesImageOrderAndMusic() {
        val post = PhotoMetadata.parse(html(), "123456789")
        assertEquals("https://p16.tiktokcdn.com/first.jpg", post.images[0][0])
        assertEquals("https://p16.tiktokcdn.com/second.jpg", post.images[1][0])
        assertNotNull(post.music)
    }
    @Test fun photoLinkIsAcceptedWithoutTracking() {
        assertEquals("https://www.tiktok.com/@creator/photo/123456789", VideoLink.parse("https://www.tiktok.com/@creator/photo/123456789?tracking=yes").url)
    }
    @Test fun slideshowOptionsHaveIndependentRequestIdentity() {
        val images = DownloadOptions()
        assertNotEquals(downloadRequestKey("link", images), downloadRequestKey("link", images.copy(photoMode = "slideshow")))
        assertNotEquals(downloadRequestKey("link", images.copy(photoMode = "both")), downloadRequestKey("link", images.copy(photoMode = "both", slideMusic = false)))
    }
    @Test fun rejectsWrongPostAndMissingImages() {
        assertThrows(PhotoPostException::class.java) { PhotoMetadata.parse(html(), "999999999") }
        assertThrows(PhotoPostException::class.java) { PhotoMetadata.parse(html(JSONArray()), "123456789") }
    }
    @Test fun rejectsUntrustedMediaHosts() {
        listOf("http://p16.tiktokcdn.com/a.jpg", "https://tiktokcdn.com.evil.test/a.jpg", "https://127.0.0.1/a.jpg", "https://user@p16.tiktokcdn.com/a.jpg").forEach { assertFalse(PhotoMetadata.mediaURL(it)) }
    }
    @Test fun embedFallbackPreservesIdentityAndImageOrder() {
        val video = JSONObject().put("itemInfos", JSONObject().put("id", "123456789").put("text", "Photo post"))
            .put("imagePostInfo", JSONObject().put("displayImages", JSONArray().put(JSONObject().put("urlList", JSONArray().put("https://p16.tiktokcdn.com/first.jpg")))) )
            .put("musicInfos", JSONObject().put("playUrl", JSONArray().put("https://sf16.tiktokcdn.com/sound.mp3")))
        val root = JSONObject().put("source", JSONObject().put("data", JSONObject().put("/embed/v2/123456789", JSONObject().put("videoData", video))))
        val result = PhotoMetadata.parseEmbed("<script id=\"__FRONTITY_CONNECT_STATE__\">$root</script>", "123456789")
        assertEquals("https://p16.tiktokcdn.com/first.jpg", result.images.single().single())
        assertEquals("https://sf16.tiktokcdn.com/sound.mp3", result.music)
    }
}
