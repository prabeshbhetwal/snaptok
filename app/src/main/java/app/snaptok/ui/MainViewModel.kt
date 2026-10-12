package app.snaptok.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.snaptok.SnapTokApplication
import app.snaptok.core.AvailableQualities
import app.snaptok.core.VideoLink
import app.snaptok.data.*
import app.snaptok.download.FolderStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.UUID
import app.snaptok.download.*
import app.snaptok.core.parseBatch

sealed interface UiEvent {
    data class Message(val text: String, val openJobId: String? = null) : UiEvent
    data class Accepted(val input: String) : UiEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as SnapTokApplication).container
    val jobs = container.queue.jobs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val preferences = container.preferences.state
    private val eventChannel = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)
    val events = eventChannel.asSharedFlow()
    val input = MutableStateFlow("")
    private suspend fun accepted(text: String) {
        if (input.value == text) input.value = ""
        eventChannel.emit(UiEvent.Accepted(text))
    }
    val busy = MutableStateFlow(false)
    val updating = MutableStateFlow(false)
    val inspecting = MutableStateFlow(false)
    val qualities = MutableStateFlow<AvailableQualities?>(null)
    val engineVersion = MutableStateFlow(container.engine.version())
    private var inspection: Job? = null
    val page = MutableStateFlow(0)
    val player = MutableStateFlow<DownloadJob?>(null)
    val storage = MutableStateFlow<StorageSummary?>(null)
    val exporting = MutableStateFlow(false)
    fun openJob(id: String) = viewModelScope.launch {
        val job = container.dao.get(id)
        if (job?.status == JobStatus.SAVED && job.mediaUri != null) player.value = job
        else {
            player.value = null
            page.value = 1
            message("Saved file unavailable. Check this download in Library.")
        }
    }
    fun closePlayer() { player.value = null }
    fun favourite(job: DownloadJob) = viewModelScope.launch { container.dao.favourite(job.id, !job.favourite) }
    fun collection(job: DownloadJob, name: String) = viewModelScope.launch { container.dao.collection(job.id, name.trim().take(60)) }
    fun savePlatform(platform: String, enabled: Boolean) = viewModelScope.launch {
        try { withContext(Dispatchers.IO) { container.preferences.savePlatform(platform, enabled) }; message(if (enabled) "Current quality and folder saved for $platform" else "$platform uses global defaults") }
        catch (_: Exception) { message("Could not save platform defaults") }
    }
    fun defaultsFor(text: String): DownloadOptions = runCatching { container.preferences.optionsFor(VideoLink.parse(text).url) }.getOrDefault(preferences.value.options)
    fun platformOverride(platform: String) = container.preferences.platformOverride(platform)
    fun refreshStorage() = viewModelScope.launch { storage.value = StorageManager(getApplication(), container.dao).summary() }
    fun cleanCache() = viewModelScope.launch {
        val bytes = container.queue.cleanTemporaryFiles(File(getApplication<Application>().cacheDir, "staging"))
        message("Cleared ${android.text.format.Formatter.formatFileSize(getApplication(), bytes)} of inactive temporary files")
        refreshStorage()
    }
    fun deleteFile(job: DownloadJob) = viewModelScope.launch {
        try {
            container.publication.withLock { withContext(NonCancellable) { StorageManager(getApplication(), container.dao).delete(job) } }
            message("File deleted"); refreshStorage()
        }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message("Could not delete the file. Check folder access, or delete it in Files.") }
    }
    fun exportMedia(job: DownloadJob, startSeconds: Long, endSeconds: Long?, audio: Boolean) = viewModelScope.launch {
        if (exporting.value) return@launch
        exporting.value = true
        val id = UUID.randomUUID().toString()
        val file = File(getApplication<Application>().cacheDir, "export-$id.${if (audio) "m4a" else "mp4"}")
        try {
            require(job.mediaUri != null) { "Saved file unavailable" }
            MediaTools.export(getApplication(), Uri.parse(job.mediaUri), file, Math.multiplyExact(startSeconds, 1000L), endSeconds?.let { Math.multiplyExact(it, 1000L) }, audio)
            val newJob = job.exportCopy(id, audio)
            container.publication.withLock { withContext(NonCancellable) {
                container.publisher.publishExport(container.dao, newJob, DownloadedVideo(file, newJob.title, audioOnly = audio))
            } }
            message("Export saved · ${newJob.destinationLabel}")
        } catch (e: CancellationException) { throw e }
        catch (e: ExportCleanupException) { message(e.message!!) }
        catch (_: Exception) { message("Could not export this media. Check the time range and that an audio track exists.") }
        finally { withContext(NonCancellable + Dispatchers.IO) { file.delete() }; exporting.value = false }
    }
    fun enqueueSelection(text: String, quality: Int, indices: Set<Int>, audio: Boolean, batch: Boolean) = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        try {
            val links = if (batch) parseBatch(text) else listOf(VideoLink.parse(text))
            val chosen = if (batch) setOf(1) else indices.ifEmpty { setOf(1) }
            var count = 0; var last = ""
            for (link in links) for (index in chosen.sorted()) {
                val options = container.preferences.optionsFor(link.url).copy(qualityHeight = quality, itemIndex = index, audioOnly = audio)
                last = container.queue.enqueue(link.url, options); count++
                if (links.size == 1 && chosen.size == 1 && last == "Already saved") {
                    val existing = container.dao.findRequest(downloadRequestKey(link.url, options))
                    accepted(text)
                    eventChannel.emit(UiEvent.Message("Already saved", existing?.id))
                    return@launch
                }
            }
            accepted(text)
            message(if (count == 1) last else "$count links processed. Existing saves were kept.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message(e.message ?: "Could not queue these links") }
        finally { busy.value = false }
    }

    init {
        viewModelScope.launch {
            var previous: Map<String, String>? = null
            container.queue.jobs.collect { current ->
                val old = previous
                previous = current.associate { it.id to it.status }
                if (old != null) current.forEach { job ->
                    if (old[job.id] != job.status && job.status in listOf(JobStatus.SAVED, JobStatus.FAILED))
                        message(if (job.status == JobStatus.SAVED) "Download complete · ${job.destinationLabel}" else job.error ?: "Download failed")
                }
            }
        }
    }
    private suspend fun message(text: String) { eventChannel.emit(UiEvent.Message(text)) }
    fun notify(text: String) { viewModelScope.launch { message(text) } }

    fun enqueue(text: String, qualityHeight: Int) = enqueue(text, preferences.value.options.copy(qualityHeight = qualityHeight))
    private fun enqueue(text: String, options: DownloadOptions) = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        try {
            val result = container.queue.enqueue(text, options)
            accepted(text)
            message(result)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message(if (e is IllegalArgumentException) e.message.orEmpty() else "Could not schedule the download. Reopen SnapTok to recover the queue.") }
        finally { busy.value = false }
    }
    fun invalidateQualities() { inspection?.cancel(); qualities.value = null; inspecting.value = false }
    fun inspect(text: String) {
        val previous = inspection
        previous?.cancel()
        qualities.value = null
        inspection = viewModelScope.launch {
            previous?.join()
            inspecting.value = true
            try { qualities.value = container.engine.inspect(VideoLink.parse(text).url) }
            catch (_: TimeoutCancellationException) { message("Checking qualities timed out. You can still try Best available.") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(if (e is IllegalArgumentException) e.message.orEmpty() else "Could not check available qualities. You can still try Best available.") }
            finally { inspecting.value = false }
        }
    }
    fun setDefaultQuality(height: Int) = changeSettings { it.copy(options = it.options.copy(qualityHeight = height)) }
    fun changeSettings(transform: (DownloadSettings) -> DownloadSettings) = viewModelScope.launch {
        try { container.preferences.update(transform); container.notifications.refreshPreferences() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message("Could not save this preference. Please try again.") }
    }
    fun chooseFolder(uri: Uri) = viewModelScope.launch {
        try {
            val context = getApplication<Application>()
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val label = FolderStorage(context).labelAndValidate(uri)
            container.preferences.update { it.copy(options = it.options.copy(destinationTreeUri = uri.toString(), destinationLabel = label)) }
            message("New downloads will save to $label")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message("That folder is not writable. Choose a subfolder in Movies or Downloads.") }
    }
    fun resetFolder() = changeSettings { it.copy(options = it.options.copy(destinationTreeUri = null, destinationLabel = DEFAULT_DESTINATION)) }
    fun retry(id: String) = viewModelScope.launch {
        try { container.queue.retry(id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message("Could not schedule a retry. Reopen SnapTok.") }
    }
    fun cancel(id: String) = viewModelScope.launch {
        try { container.queue.cancel(id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message("Could not cancel. Check download history.") }
    }
    fun removeHistory(id: String) = viewModelScope.launch {
        try {
            if (container.dao.removeFinished(id) > 0) message("Removed from history. Saved file kept.")
            else message("Could not remove this entry. Delete its partial files first, or wait for the download to finish.")
        }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message("Could not remove this entry. Please try again.") }
    }
    fun retryInCurrentFolder(job: DownloadJob) = if (job.requestKey.startsWith("export:")) {
        viewModelScope.launch { message("Export again from the original file.") }
    } else enqueue(job.url, container.preferences.optionsFor(job.url).copy(itemIndex = job.itemIndex, audioOnly = job.audioOnly))
    fun updateEngine() = viewModelScope.launch {
        if (updating.value) return@launch
        updating.value = true
        try {
            engineVersion.value = container.engine.update()
            message("Download engine is up to date")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message("Engine update failed. Check your connection and try again.") }
        finally { updating.value = false }
    }
}
