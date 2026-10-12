package app.snaptok.data

import org.json.JSONArray
import org.json.JSONObject

data class MediaOutput(val uri: String, val mimeType: String, val label: String)

fun DownloadJob.exportCopy(id: String, audio: Boolean): DownloadJob = copy(
    id = id, createdAt = System.currentTimeMillis(),
    title = (if (audio) "Audio · " else "Clip · ") + title,
    destinationLabel = if (audio && destinationTreeUri == null) "Music · Music/SnapTok" else destinationLabel,
    audioOnly = audio, status = JobStatus.SAVED, mediaUri = null, outputsJson = "[]",
    sourceId = "", requestKey = "export:$id"
)

fun DownloadJob.outputs(): List<MediaOutput> {
    // Older exports inherited source receipts; an export owns only its primary file.
    val values = if (requestKey.startsWith("export:")) emptyList() else runCatching {
        val json = JSONArray(outputsJson)
        (0 until json.length()).map { index -> json.getJSONObject(index).let { MediaOutput(it.getString("uri"), it.getString("mimeType"), it.getString("label")) } }
    }.getOrDefault(emptyList())
    return values.ifEmpty { mediaUri?.let { listOf(MediaOutput(it, if (audioOnly) "audio/mp4" else "video/mp4", title)) } ?: emptyList() }
}
fun encodeOutputs(values: List<MediaOutput>): String = JSONArray().apply {
    values.forEach { put(JSONObject().put("uri", it.uri).put("mimeType", it.mimeType).put("label", it.label)) }
}.toString()
