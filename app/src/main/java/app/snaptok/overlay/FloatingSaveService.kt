package app.snaptok.overlay

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.content.pm.ServiceInfo
import app.snaptok.R
import app.snaptok.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import app.snaptok.SnapTokApplication
import app.snaptok.data.JobStatus

/** Optional, user-started overlay; explicit taps delegate to clipboard or opted-in capture. */
class FloatingSaveService : Service() {
    private lateinit var manager: WindowManager
    private lateinit var prefs: android.content.SharedPreferences
    private var button: FloatingButton? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var feedbackClear: Job? = null
    private val fade = Runnable {
        if (prefs.getBoolean("fade", true)) button?.animate()?.alpha(opacity(prefs.getFloat("opacity", .45f)) * .6f)?.setDuration(if ((application as SnapTokApplication).container.preferences.snapshot().reduceMotion) 0 else 300)?.start()
    }
    private fun resetFade() { handler.removeCallbacks(fade); handler.postDelayed(fade, 4000) }
    private lateinit var position: WindowManager.LayoutParams
    private var registered = false
    private var lastTap = 0L
    private val changed = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        button?.let {
            it.alpha = opacity(prefs.getFloat("opacity", .45f))
            it.contentDescription = actionLabel()
            val size = (buttonSize(prefs.getInt("size", 56)) * resources.displayMetrics.density).toInt()
            position.width = size; position.height = size
            val padding = size / 4
            it.setPadding(padding, padding, padding, padding)
            restorePosition(); updatePosition()
        }
    }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { updateVisibility() }
    }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(WindowManager::class.java)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(changed)
        scope.launch {
            var previous: Map<String, String>? = null
            (application as SnapTokApplication).container.dao.observeAll().collect { jobs ->
                val activeJobs = jobs.filter { it.status in listOf(JobStatus.QUEUED, JobStatus.DOWNLOADING, JobStatus.RETRYING, JobStatus.SAVING) }
                val current = activeJobs.firstOrNull { it.status == JobStatus.DOWNLOADING && it.progress > 0 }
                button?.apply {
                    count = activeJobs.size; progress = current?.progress ?: -1
                    contentDescription = actionLabel() + if (count > 0) ", $count active" + (if (current != null) ", ${current.progress}% on current download" else "") else ""
                    invalidate()
                }
                jobs.firstOrNull { previous?.get(it.id) != null && previous?.get(it.id) != it.status && it.status in listOf(JobStatus.SAVED, JobStatus.FAILED) }?.let {
                    FloatingFeedback.latest.value = SaveFeedback(if (it.status == JobStatus.SAVED) "Saved" else "Needs attention")
                }
                previous = jobs.associate { it.id to it.status }
            }
        }
        scope.launch {
            FloatingFeedback.latest.collect { feedback ->
                if (System.currentTimeMillis() - feedback.time < 5000) {
                    button?.apply { message = if (feedback.text.contains("saved", true)) "✓" else if (feedback.text.contains("queue", true)) "+" else if (feedback.text.startsWith("Captur")) "…" else "!"; contentDescription = "${actionLabel()} · ${feedback.text}"; alpha = 1f; invalidate() }
                    feedbackClear?.cancel(); feedbackClear = scope.launch { delay(2500); button?.apply { message = ""; contentDescription = actionLabel(); alpha = opacity(prefs.getFloat("opacity", .45f)); invalidate() }; resetFade() }
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == HIDE || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.cancel(80102)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Floating Save button", NotificationManager.IMPORTANCE_LOW))
            val hide = PendingIntent.getService(this, 0, Intent(this, FloatingSaveService::class.java).setAction(HIDE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val open = PendingIntent.getActivity(this, 801, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_download)
                .setContentTitle("Floating Save is on").setContentText("Tap to save using your selected Floating Save mode.")
                .setContentIntent(open).addAction(0, "Hide button", hide).setOngoing(true).setOnlyAlertOnce(true).build()
            ServiceCompat.startForeground(this, 80101, notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            if (button == null) attach()
            mutableActive.value = true
        } catch (_: Exception) {
            Toast.makeText(this, "Could not show the floating button. Check Appear on top permission in Settings.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    @android.annotation.SuppressLint("RtlHardcoded") // Positions are physical screen coordinates for edge docking.
    private fun attach() {
        val size = (buttonSize(prefs.getInt("size", 56)) * resources.displayMetrics.density).toInt()
        position = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT }
        restorePosition()
        var longPressed = false
        val view = FloatingButton(this).apply {
            contentDescription = actionLabel()
            setImageResource(R.drawable.ic_download)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(185, 246, 202))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(33, 48, 43)); setStroke(2, Color.rgb(185, 246, 202)) }
            val inset = size / 4
            setPadding(inset, inset, inset, inset)
            alpha = opacity(prefs.getFloat("opacity", .45f))
            setOnClickListener { saveCopiedLink() }
            setOnLongClickListener {
                longPressed = true
                if (prefs.getBoolean("holdMenu", false)) runCatching { startActivity(Intent(this@FloatingSaveService, FloatingControlActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                else stopSelf()
                true
            }
            ViewCompatActions.addHideAction(this) { stopSelf() }
        }
        var startX = 0f; var startY = 0f; var windowX = 0; var windowY = 0; var dragged = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY; windowX = position.x; windowY = position.y; dragged = false; longPressed = false
                    handler.removeCallbacks(fade); view.animate().cancel(); view.alpha = 1f
                    false // Retain normal click, long-press and accessibility behaviour.
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - startX) > slop || abs(event.rawY - startY) > slop) {
                        dragged = true; view.cancelLongPress()
                    }
                    if (dragged) {
                        position.x = windowX + (event.rawX - startX).toInt(); position.y = windowY + (event.rawY - startY).toInt()
                        clampPosition(); updatePosition()
                    }
                    dragged
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.alpha = opacity(prefs.getFloat("opacity", .45f))
                    if (dragged) {
                        val bounds = available()
                        position.x = if (position.x < bounds.x / 2) 0 else bounds.x
                        updatePosition()
                        prefs.edit().putBoolean("right", position.x > 0).putFloat("y", position.y.toFloat() / bounds.y.coerceAtLeast(1)).apply()
                        view.isPressed = false
                    }
                    if (event.actionMasked == MotionEvent.ACTION_UP && !dragged && !longPressed) view.performClick()
                    view.isPressed = false
                    resetFade()
                    true
                }
                else -> false
            }
        }
        button = view
        manager.addView(view, position)
        ContextCompat.registerReceiver(this, screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        updateVisibility(); resetFade()
    }
    private fun saveCopiedLink() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTap < 800 || getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        lastTap = now
        if (prefs.getBoolean("haptic", false)) button?.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        if (prefs.getBoolean(CurrentPostRules.MODE, false)) {
            if (!CurrentPostAccessibilityService.startCapture()) {
                val message = "Enable SnapTok — Save current post in Android Accessibility settings, or select Save copied link."
                FloatingFeedback.latest.value = SaveFeedback(message)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            return
        }
        try {
            startActivity(Intent(this, SaveClipboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (_: Exception) { Toast.makeText(this, "Open SnapTok to save the copied link.", Toast.LENGTH_SHORT).show() }
    }
    private fun actionLabel() = if (prefs.getBoolean(CurrentPostRules.MODE, false)) "Save current TikTok post" else "Save copied video link"
    @Suppress("DEPRECATION")
    private fun available(): Point {
        val size = if (Build.VERSION.SDK_INT >= 30) manager.currentWindowMetrics.bounds.let { Point(it.width(), it.height()) }
            else Point().also { manager.defaultDisplay.getSize(it) }
        return Point((size.x - position.width).coerceAtLeast(0), (size.y - position.height - (48 * resources.displayMetrics.density).toInt()).coerceAtLeast(0))
    }
    private fun clampPosition() { val bounds = available(); position.x = position.x.coerceIn(0, bounds.x); position.y = position.y.coerceIn(0, bounds.y) }
    private fun restorePosition() {
        val bounds = available()
        position.x = if (prefs.getBoolean("right", true)) bounds.x else 0
        position.y = (prefs.getFloat("y", .5f).coerceIn(0f, 1f) * bounds.y).toInt()
    }
    private fun updatePosition() { button?.let { if (runCatching { manager.updateViewLayout(it, position) }.isFailure) stopSelf() } }
    private fun updateVisibility() {
        val visible = getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        button?.visibility = if (visible) View.VISIBLE else View.GONE
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); if (button != null) { restorePosition(); updatePosition() } }
    override fun onDestroy() {
        if (CurrentPostCapture.busy.value) CurrentPostAccessibilityService.stopCapture()
        button?.let { runCatching { manager.removeView(it) } }; button = null
        if (registered) unregisterReceiver(screen)
        prefs.unregisterOnSharedPreferenceChangeListener(changed)
        mutableActive.value = false
        scope.cancel(); handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (prefs.getBoolean("restoreNotice", true)) runCatching {
            val restore = PendingIntent.getActivity(this, 802, Intent(this, MainActivity::class.java).putExtra("restoreFloating", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            getSystemService(NotificationManager::class.java).notify(80102, NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_download)
                .setContentTitle("Floating Save is hidden").setContentText("Tap to restore the button.").setContentIntent(restore).setAutoCancel(true).addAction(0, "Restore", restore).build())
        }
        super.onDestroy()
    }
    companion object {
        const val PREFS = "floating-save"
        const val HIDE = "app.snaptok.HIDE_FLOATING_SAVE"
        private const val CHANNEL = "floating-save"
        private val mutableActive = MutableStateFlow(false)
        val active = mutableActive.asStateFlow()
        fun buttonSize(value: Int) = value.coerceIn(48, 80)
        fun opacity(value: Float) = if (value.isFinite()) value.coerceIn(.25f, 1f) else .45f
    }
}

private object ViewCompatActions {
    fun addHideAction(view: View, hide: () -> Unit) {
        androidx.core.view.ViewCompat.addAccessibilityAction(view, "Hide floating Save button") { _, _ -> hide(); true }
    }
}
