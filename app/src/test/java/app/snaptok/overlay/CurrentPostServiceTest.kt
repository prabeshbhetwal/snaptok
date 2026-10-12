package app.snaptok.overlay

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Synthetic UI trees verify routing/safety; these are not evidence of TikTok compatibility. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CurrentPostServiceTest {
    class FixtureService : CurrentPostAccessibilityService() {
        var tree: AccessibilityNodeInfo? = null
        var reads = 0
        fun connect() = onServiceConnected()
        @Suppress("DEPRECATION") override fun getRootInActiveWindow(): AccessibilityNodeInfo? {
            reads++
            return tree?.let(AccessibilityNodeInfo::obtain)
        }
    }
    private lateinit var service: FixtureService
    private val source = "com.zhiliaoapp.musically"
    private var clicks = mutableListOf<String>()
    @Before fun setup() {
        service = Robolectric.buildService(FixtureService::class.java).create().get()
        service.getSharedPreferences(FloatingSaveService.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(CurrentPostRules.MODE, true).putBoolean(CurrentPostRules.CONSENT, true).commit()
        service.connect()
    }
    @After fun cleanup() { service.onDestroy(); CurrentPostCapture.tickets.clear() }
    @Suppress("DEPRECATION") private fun node(label: String = "", pkg: String = source, visible: Boolean = true, action: (() -> Boolean)? = null): AccessibilityNodeInfo =
        AccessibilityNodeInfo.obtain().apply {
            packageName = pkg; text = label; isVisibleToUser = visible; isEnabled = true; isClickable = action != null
            if (action != null) shadowOf(this).setOnPerformActionListener { _, _ -> clicks += label; action() }
        }
    private fun tree(vararg children: AccessibilityNodeInfo, pkg: String = source): AccessibilityNodeInfo = node(pkg = pkg).apply {
        children.forEach { shadowOf(this).addChild(it) }
    }
    private fun advance(millis: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)) }
    @Test fun idleEventsDoNotReadOtherApps() {
        service.tree = tree(node("Share", action = { true }))
        repeat(5) { service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) }
        advance(1000)
        assertEquals(0, service.reads); assertTrue(clicks.isEmpty())
    }
    @Test fun explicitConsentIsRequiredBeforeInspection() {
        service.getSharedPreferences(FloatingSaveService.PREFS, Context.MODE_PRIVATE).edit().putBoolean(CurrentPostRules.CONSENT, false).commit()
        CurrentPostAccessibilityService.startCapture()
        assertEquals(0, service.reads); assertFalse(CurrentPostCapture.busy.value)
    }
    @Test fun unsupportedAppCannotTriggerActionsOrClipboardHandoff() {
        service.tree = tree(node("Share", pkg = "other.app", action = { true }), pkg = "other.app")
        CurrentPostAccessibilityService.startCapture(); advance(1000)
        assertTrue(clicks.isEmpty()); assertNull(shadowOf(service).nextStartedActivity)
        assertFalse(CurrentPostCapture.busy.value)
    }
    @Test fun oneTapRunsShareThenCopyAndCreatesSingleUseHandoff() {
        val copied = tree()
        val sheet = tree(node("Copy link", action = { service.tree = copied; true }))
        service.tree = tree(node("Share video", action = { service.tree = sheet; true }))
        CurrentPostAccessibilityService.startCapture(); advance(1200)
        assertEquals(listOf("Share video", "Copy link"), clicks)
        val intent = shadowOf(service).nextStartedActivity
        assertEquals(SaveClipboardActivity::class.java.name, intent?.component?.className)
        val token = intent?.getStringExtra(CurrentPostCapture.EXTRA_TOKEN)
        assertNotNull(CurrentPostCapture.tickets.take(token, android.os.SystemClock.elapsedRealtime()))
        assertNull(CurrentPostCapture.tickets.take(token, android.os.SystemClock.elapsedRealtime()))
    }
    @Test fun observedTikTokCountedButtonOpensShareAndCopies() {
        val sheet = tree(node("Copy link", action = { service.tree = tree(); true }))
        service.tree = tree(node("", action = { service.tree = sheet; true }).apply { contentDescription = "Share video 25K shares" })
        CurrentPostAccessibilityService.startCapture(); advance(1200)
        assertEquals(2, clicks.size)
        assertNotNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun ambiguousCopyControlsNeverClickAnything() {
        service.tree = tree(node("Copy link", action = { true }), node("Copy link", action = { true }), node("Share", action = { true }))
        CurrentPostAccessibilityService.startCapture(); advance(1000)
        assertTrue(clicks.isEmpty()); assertFalse(CurrentPostCapture.busy.value)
    }
    @Test fun invisibleAndUnrelatedControlsAreIgnored() {
        service.tree = tree(node("Share", visible = false, action = { true }), node("Share profile", action = { true }), node("Send", action = { true }))
        CurrentPostAccessibilityService.startCapture(); advance(1000)
        assertTrue(clicks.isEmpty()); assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun switchingAppsStopsBeforeCopy() {
        service.tree = tree(node("Share", action = { service.tree = tree(pkg = "other.app"); true }))
        CurrentPostAccessibilityService.startCapture(); advance(1000)
        assertEquals(listOf("Share"), clicks); assertFalse(CurrentPostCapture.busy.value)
        assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun repeatedEventsCannotPreventTimeout() {
        service.tree = tree(node("Share", action = { service.tree = tree(); true }))
        CurrentPostAccessibilityService.startCapture()
        repeat(90) { service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)); advance(100) }
        assertEquals(listOf("Share"), clicks); assertFalse(CurrentPostCapture.busy.value)
        assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun rejectedCopyDoesNotLaunchReader() {
        service.tree = tree(node("Copy link", action = { false }))
        CurrentPostAccessibilityService.startCapture(); advance(1200)
        assertFalse(CurrentPostCapture.busy.value); assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun labelledShareActionOnPostContainerUsesItsDeclaredAction() {
        val shareAction = 0x01020001
        val sheet = tree(node("Copy link", action = { service.tree = tree(); true }))
        val post = node("", action = { true }).apply {
            isClickable = false
            addAction(AccessibilityNodeInfo.AccessibilityAction(shareAction, "Share video"))
            shadowOf(this).setOnPerformActionListener { id, _ ->
                assertEquals(shareAction, id); clicks += "Share action"; service.tree = sheet; true
            }
        }
        service.tree = tree(post)
        CurrentPostAccessibilityService.startCapture(); advance(1200)
        assertEquals(listOf("Share action", "Copy link"), clicks)
        assertNotNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun advertisedClickWorksWithoutClickableFlag() {
        val control = node("Copy link", action = { service.tree = tree(); true }).apply {
            isClickable = false; addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        }
        service.tree = tree(control)
        CurrentPostAccessibilityService.startCapture(); advance(1000)
        assertEquals(listOf("Copy link"), clicks)
        assertNotNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun delayedShareControlCanAppearBeforeDeadline() {
        service.tree = tree()
        CurrentPostAccessibilityService.startCapture(); advance(500)
        assertTrue(CurrentPostCapture.busy.value)
        val sheet = tree(node("Copy link", action = { service.tree = tree(); true }))
        service.tree = tree(node("Share", action = { service.tree = sheet; true }))
        advance(1400)
        assertEquals(listOf("Share", "Copy link"), clicks)
        assertNotNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun missingShareStillExpiresWithoutClipboardHandoff() {
        service.tree = tree()
        CurrentPostAccessibilityService.startCapture(); advance(8500)
        assertFalse(CurrentPostCapture.busy.value)
        assertNull(shadowOf(service).nextStartedActivity)
    }
    @Test fun secondTapDoesNotRepeatActionsAndDisconnectClearsCapture() {
        service.tree = tree(node("Share", action = { service.tree = tree(); true }))
        CurrentPostAccessibilityService.startCapture(); CurrentPostAccessibilityService.startCapture()
        assertEquals(listOf("Share"), clicks)
        service.onUnbind(null); advance(9000)
        assertFalse(CurrentPostAccessibilityService.connected.value)
        assertFalse(CurrentPostCapture.busy.value); assertNull(shadowOf(service).nextStartedActivity)
    }
}
