package app.snaptok.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

const val DEFAULT_DESTINATION = "Gallery · Movies/SnapTok"

data class DownloadOptions(
    val qualityHeight: Int = 0,
    val destinationTreeUri: String? = null,
    val destinationLabel: String = DEFAULT_DESTINATION
)
data class DownloadSettings(
    val options: DownloadOptions = DownloadOptions(),
    val notificationsEnabled: Boolean = true,
    val showProgress: Boolean = true,
    val showCompletion: Boolean = true,
    val showErrors: Boolean = true
)

class DownloadPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("download-settings", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val mutable = MutableStateFlow(DownloadSettings(
        DownloadOptions(prefs.getInt("quality", 0), prefs.getString("tree", null), prefs.getString("folder", DEFAULT_DESTINATION) ?: DEFAULT_DESTINATION),
        prefs.getBoolean("notifications", true), prefs.getBoolean("progress", true),
        prefs.getBoolean("completion", true), prefs.getBoolean("errors", true)
    ))
    val state = mutable.asStateFlow()
    fun snapshot() = state.value

    suspend fun update(transform: (DownloadSettings) -> DownloadSettings) = lock.withLock {
        val value = transform(mutable.value)
        withContext(Dispatchers.IO) {
            check(prefs.edit().putInt("quality", value.options.qualityHeight)
                .putString("tree", value.options.destinationTreeUri).putString("folder", value.options.destinationLabel)
                .putBoolean("notifications", value.notificationsEnabled).putBoolean("progress", value.showProgress)
                .putBoolean("completion", value.showCompletion).putBoolean("errors", value.showErrors).commit())
        }
        mutable.value = value
    }
}

fun downloadRequestKey(url: String, options: DownloadOptions) = "$url|${options.qualityHeight}|${options.destinationTreeUri.orEmpty()}"
