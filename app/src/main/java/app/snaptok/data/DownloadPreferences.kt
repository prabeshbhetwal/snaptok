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
    val destinationLabel: String = DEFAULT_DESTINATION,
    val unmetered: Boolean = false,
    val charging: Boolean = false,
    val maxSizeMb: Int = 1024,
    val itemIndex: Int = 1,
    val audioOnly: Boolean = false
)
data class DownloadSettings(
    val options: DownloadOptions = DownloadOptions(),
    val notificationsEnabled: Boolean = true,
    val showProgress: Boolean = true,
    val showCompletion: Boolean = true,
    val showErrors: Boolean = true,
    val theme: String = "system",
    val grid: Boolean = false,
    val reduceMotion: Boolean = false
)

class DownloadPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("download-settings", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val mutable = MutableStateFlow(DownloadSettings(
        DownloadOptions(prefs.getInt("quality", 0), prefs.getString("tree", null), prefs.getString("folder", DEFAULT_DESTINATION) ?: DEFAULT_DESTINATION,
            prefs.getBoolean("unmetered", false), prefs.getBoolean("charging", false), prefs.getInt("maxSizeMb", 1024)),
        prefs.getBoolean("notifications", true), prefs.getBoolean("progress", true),
        prefs.getBoolean("completion", true), prefs.getBoolean("errors", true), prefs.getString("theme", "system") ?: "system", prefs.getBoolean("grid", false), prefs.getBoolean("reduceMotion", false)
    ))
    val state = mutable.asStateFlow()
    fun snapshot() = state.value
    fun optionsFor(url: String): DownloadOptions {
        val platform = app.snaptok.core.videoPlatform(url)
        val base = snapshot().options
        if (!prefs.getBoolean("override-$platform", false)) return base
        return base.copy(qualityHeight = prefs.getInt("quality-$platform", base.qualityHeight),
            destinationTreeUri = prefs.getString("tree-$platform", null),
            destinationLabel = prefs.getString("folder-$platform", DEFAULT_DESTINATION) ?: DEFAULT_DESTINATION)
    }
    fun platformOverride(platform: String) = prefs.getBoolean("override-$platform", false)
    fun savePlatform(platform: String, enabled: Boolean) {
        val value = snapshot().options
        check(prefs.edit().putBoolean("override-$platform", enabled).putInt("quality-$platform", value.qualityHeight)
            .putString("tree-$platform", value.destinationTreeUri).putString("folder-$platform", value.destinationLabel).commit())
    }

    suspend fun update(transform: (DownloadSettings) -> DownloadSettings) = lock.withLock {
        val value = transform(mutable.value)
        withContext(Dispatchers.IO) {
            check(prefs.edit().putInt("quality", value.options.qualityHeight)
                .putString("tree", value.options.destinationTreeUri).putString("folder", value.options.destinationLabel)
                .putBoolean("notifications", value.notificationsEnabled).putBoolean("progress", value.showProgress)
                .putBoolean("unmetered", value.options.unmetered).putBoolean("charging", value.options.charging).putInt("maxSizeMb", value.options.maxSizeMb)
                .putString("theme", value.theme).putBoolean("grid", value.grid).putBoolean("reduceMotion", value.reduceMotion)
                .putBoolean("completion", value.showCompletion).putBoolean("errors", value.showErrors).commit())
        }
        mutable.value = value
    }
}

fun downloadRequestKey(url: String, options: DownloadOptions) = "$url|${options.qualityHeight}|${options.destinationTreeUri.orEmpty()}" +
    (if (options.itemIndex != 1) "|item=${options.itemIndex}" else "") + (if (options.audioOnly) "|audio" else "")
