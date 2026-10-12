package app.snaptok.overlay

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/** Fixed user-triggered Share -> Copy link sequence, restricted to TikTok's English controls. */
open class CurrentPostAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var attempt: Attempt? = null
    private var ambiguousControls = false
    private data class Attempt(val source: String, val deadline: Long, var openedShare: Boolean = false)
    private data class Control(val node: AccessibilityNodeInfo, val actionId: Int) {
        fun click() = node.performAction(actionId)
    }
    private val tick = Runnable { inspectSafely() }
    override fun onServiceConnected() { instance = this; connected.value = true }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No tree access, event content retention or monitoring outside a requested capture.
        if (attempt != null) schedule()
    }
    override fun onInterrupt() { cancel("Capture interrupted. Tap again or use Share → SnapTok.") }
    override fun onUnbind(intent: Intent?): Boolean { disconnect(); return super.onUnbind(intent) }
    override fun onDestroy() { disconnect(); super.onDestroy() }
    private fun disconnect() {
        if (instance === this) { instance = null; connected.value = false }
        handler.removeCallbacksAndMessages(null); attempt = null
        CurrentPostCapture.tickets.clear(); CurrentPostCapture.busy.value = false
    }

    private fun allowed(): Boolean {
        val prefs = getSharedPreferences(FloatingSaveService.PREFS, MODE_PRIVATE)
        return prefs.getBoolean(CurrentPostRules.MODE, false) && prefs.getBoolean(CurrentPostRules.CONSENT, false) &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    }

    private fun begin() {
        if (CurrentPostCapture.busy.value) { feedback("Capturing this post… Please wait."); return }
        handler.removeCallbacksAndMessages(null)
        if (!allowed()) { feedback("Enable Save current post in SnapTok’s Floating Save settings."); return }
        val root = rootInActiveWindow
        val source = root?.packageName?.toString()
        @Suppress("DEPRECATION")
        root?.recycle()
        if (source !in CurrentPostRules.packages) { feedback("Open a TikTok post first, or use Share → SnapTok."); return }
        CurrentPostCapture.tickets.clear()
        attempt = Attempt(requireNotNull(source), SystemClock.elapsedRealtime() + CurrentPostRules.TIMEOUT_MS)
        CurrentPostCapture.busy.value = true
        feedback("Capturing current post…")
        inspectSafely()
    }

    private fun inspectSafely() {
        runCatching { inspect() }.onFailure { cancel("TikTok’s controls are unavailable. Use Share → SnapTok.") }
    }

    private fun schedule() { if (!handler.hasCallbacks(tick)) handler.postDelayed(tick, 250) }
    private fun inspect() {
        val active = attempt ?: return
        if (!allowed()) { cancel("Capture stopped. No download was started."); return }
        if (SystemClock.elapsedRealtime() >= active.deadline) {
            cancel(if (active.openedShare) "Could not find TikTok’s Copy link control. Use Share → SnapTok."
                else "TikTok’s Share control is not exposed in a recognised form. Open Share, then tap the floating button again."); return
        }
        val root = rootInActiveWindow
        if (root == null) { schedule(); return }
        try {
            if (root.packageName?.toString() != active.source) {
                cancel("You changed apps. Tap again on the TikTok post."); return
            }
            val copy = uniqueControl(root, CurrentPostRules::isCopy)
            if (ambiguousControls) { cancel("TikTok’s controls are ambiguous. Use Share → SnapTok."); return }
            if (copy != null) {
                val clickedAt = System.currentTimeMillis()
                val accepted = try { copy.click() } finally { recycle(copy.node) }
                if (!accepted) { cancel("TikTok did not accept Copy link. Use Share → SnapTok."); return }
                handler.removeCallbacks(tick)
                attempt = null
                // Allow TikTok to produce the link before taking focus for Android's clipboard API.
                handler.postDelayed({
                    runCatching { handOff(active, clickedAt) }.onFailure { cancel("Capture could not finish. Use Share → SnapTok.") }
                }, 650)
                return
            }
            if (!active.openedShare) {
                val share = uniqueControl(root, CurrentPostRules::isShare)
                if (ambiguousControls) { cancel("TikTok’s Share controls are ambiguous. Use Share → SnapTok."); return }
                if (share == null) { schedule(); return }
                active.openedShare = true
                val accepted = try { share.click() } finally { recycle(share.node) }
                if (!accepted) { cancel("TikTok did not open Share. Use Share → SnapTok."); return }
            }
        } catch (_: Exception) {
            cancel("TikTok’s controls changed. Use Share → SnapTok."); return
        } finally { recycle(root) }
        schedule()
    }

    private fun handOff(active: Attempt, clickedAt: Long) {
        if (!allowed() || SystemClock.elapsedRealtime() >= active.deadline) { cancel("Capture expired. Tap again."); return }
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != active.source) {
            root?.let(::recycle); cancel("Capture stopped because TikTok is no longer active."); return
        }
        // Close only a panel we opened and can still recognise. Never blindly navigate Back.
        val copy = uniqueControl(root, CurrentPostRules::isCopy)
        recycle(root)
        if (copy != null) { recycle(copy.node); if (active.openedShare) performGlobalAction(GLOBAL_ACTION_BACK) }
        val ticket = CurrentPostCapture.tickets.issue(UUID.randomUUID().toString(), clickedAt, SystemClock.elapsedRealtime())
        try {
            startActivity(Intent(this, SaveClipboardActivity::class.java)
                .putExtra(CurrentPostCapture.EXTRA_TOKEN, ticket.token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            handler.postDelayed({
                if (CurrentPostCapture.busy.value) cancel("Capture timed out. Use Share → SnapTok.")
            }, CurrentPostRules.TIMEOUT_MS)
        } catch (_: Exception) { cancel("Open SnapTok or use Share → SnapTok to save this post.") }
    }

    /** One visible, enabled action only. No guessed coordinates, broad text matching or scrolling. */
    private fun uniqueControl(root: AccessibilityNodeInfo, matches: (CharSequence?) -> Boolean): Control? {
        ambiguousControls = false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val found = mutableListOf<Control>()
        @Suppress("DEPRECATION")
        queue.add(AccessibilityNodeInfo.obtain(root))
        var visited = 0
        try {
            while (queue.isNotEmpty() && visited++ < 400) {
                val node = queue.removeFirst()
                try {
                    val labelledActions = node.actionList.filter { matches(it.label) }
                    if (node.isVisibleToUser && node.isEnabled && node.packageName?.toString() == root.packageName?.toString() && labelledActions.isNotEmpty()) {
                        labelledActions.forEach { action ->
                            if (found.none { it.node == node && it.actionId == action.id }) {
                                @Suppress("DEPRECATION")
                                found += Control(AccessibilityNodeInfo.obtain(node), action.id)
                            }
                        }
                    } else if (node.isVisibleToUser && node.isEnabled && (matches(node.text) || matches(node.contentDescription))) {
                        @Suppress("DEPRECATION")
                        var target: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
                        var depth = 0
                        while (target != null && !canClick(target) && depth++ < 3) {
                            val parent = target.parent; recycle(target); target = parent
                        }
                        if (target != null) {
                            if (canClick(target) && target.isEnabled && target.isVisibleToUser && target.packageName?.toString() == root.packageName?.toString() && found.none { it.node == target && it.actionId == AccessibilityNodeInfo.ACTION_CLICK }) found += Control(target, AccessibilityNodeInfo.ACTION_CLICK)
                            else recycle(target)
                        }
                    }
                    for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                } finally { recycle(node) }
            }
            if (queue.isNotEmpty() || found.size != 1) {
                ambiguousControls = queue.isNotEmpty() || found.size > 1
                found.forEach { recycle(it.node) }; return null
            }
            return found.single()
        } finally { queue.forEach(::recycle) }
    }
    private fun canClick(node: AccessibilityNodeInfo) = node.isClickable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
    @Suppress("DEPRECATION") private fun recycle(node: AccessibilityNodeInfo) = node.recycle()
    private fun cancel(message: String) {
        handler.removeCallbacksAndMessages(null); attempt = null
        CurrentPostCapture.tickets.clear(); CurrentPostCapture.busy.value = false
        feedback(message)
    }
    private fun feedback(message: String) {
        FloatingFeedback.latest.value = SaveFeedback(message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    companion object {
        private var instance: CurrentPostAccessibilityService? = null
        internal val connected = MutableStateFlow(false)
        internal fun startCapture(): Boolean {
            val service = instance ?: return false
            runCatching { service.begin() }.onFailure { service.cancel("Capture is unavailable. Use Share → SnapTok.") }
            return true
        }
        internal fun stopCapture() { instance?.cancel("Capture stopped. No download was started.") }
        internal fun finishCapture() {
            instance?.handler?.removeCallbacksAndMessages(null)
            CurrentPostCapture.busy.value = false
            CurrentPostCapture.tickets.clear()
        }
    }
}
