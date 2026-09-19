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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Accepted(val input: String) : UiEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as SnapTokApplication).container
    val jobs = container.queue.jobs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val preferences = container.preferences.state
    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    val busy = MutableStateFlow(false)
    val updating = MutableStateFlow(false)
    val inspecting = MutableStateFlow(false)
    val qualities = MutableStateFlow<AvailableQualities?>(null)
    val engineVersion = MutableStateFlow(container.engine.version())
    private var inspection: Job? = null

    init {
        viewModelScope.launch {
            var previous: Map<String, String>? = null
            container.queue.jobs.collect { current ->
                val old = previous
                previous = current.associate { it.id to it.status }
                if (old != null) current.forEach { job ->
                    if (old[job.id] != job.status && job.status in listOf(JobStatus.SAVED, JobStatus.FAILED))
                        message(if (job.status == JobStatus.SAVED) "Video downloaded · ${job.destinationLabel}" else job.error ?: "Download failed")
                }
            }
        }
    }
    private suspend fun message(text: String) { eventChannel.send(UiEvent.Message(text)) }
    fun notify(text: String) { viewModelScope.launch { message(text) } }

    fun enqueue(text: String, qualityHeight: Int) = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        try {
            val result = container.queue.enqueue(text, preferences.value.options.copy(qualityHeight = qualityHeight))
            eventChannel.send(UiEvent.Accepted(text))
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
