package app.snaptok.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.snaptok.SnapTokApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as SnapTokApplication).container
    val jobs = container.queue.jobs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    val busy = MutableStateFlow(false)
    val updating = MutableStateFlow(false)
    val engineVersion = MutableStateFlow(container.engine.version())

    fun enqueue(text: String) = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        try { eventChannel.send(container.queue.enqueue(text)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { eventChannel.send(if (e is IllegalArgumentException) e.message.orEmpty() else "Could not schedule the download. Reopen SnapTok to recover the queue.") }
        finally { busy.value = false }
    }
    fun retry(id: String) = viewModelScope.launch {
        try { container.queue.retry(id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { eventChannel.send("Could not schedule a retry. Reopen SnapTok.") }
    }
    fun cancel(id: String) = viewModelScope.launch {
        try { container.queue.cancel(id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { eventChannel.send("Could not cancel. Check download history.") }
    }
    fun updateEngine() = viewModelScope.launch {
        if (updating.value) return@launch
        updating.value = true
        try {
            engineVersion.value = container.engine.update()
            eventChannel.send("Download engine is up to date")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { eventChannel.send("Engine update failed. Check your connection and try again.") }
        finally { updating.value = false }
    }
}
