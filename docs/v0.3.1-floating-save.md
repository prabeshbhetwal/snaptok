# SnapTok 0.3.1 — Floating Save

Copy a supported video link, tap the floating Save button, then continue using the previous app. A brief foreground screen reads the clipboard once and hands the link to the existing durable queue. This is deliberate user-triggered clipboard access, not background clipboard monitoring.

## Controls

Settings → Floating Save button:

- Off by default. Enable with Android's Appear on top permission.
- Size: 48–80 dp, default 56 dp. Minimum preserves an accessible touch target.
- Visibility: 25–100%, default 45%. Fully visible while dragging.
- Preview size and transparency before enabling. Preferences persist across restarts.
- Drag anywhere and release to dock at the nearest edge. Position persists.
- Hold to hide; or use the settings switch or Hide button notification action.
- Hidden when the screen is locked/off. No automatic restart after reboot or service termination; enable again in Settings.

The floating control has an accessibility label and a Hide action. No accessibility-service permission, screen-reading permission or account access is required. Android requires an ongoing foreground-service notification while the control is enabled, separately from optional download alerts.

Empty or unsupported clipboard contents show brief guidance without creating a job. Repeated links reuse the queue's existing duplicate handling. Downloads use the current quality, folder and notification preferences. Stopping the floating button does not cancel queued downloads.

## Packaging and release

Two optional-feature permissions are added: SYSTEM_ALERT_WINDOW and FOREGROUND_SERVICE_SPECIAL_USE. The service declares its user-visible overlay purpose. Public Play distribution requires review of this foreground-service declaration. This update remains test-signed, preserves the existing update identity and does not resolve Play Protect's assessment.

References: Android clipboard restrictions and foreground service requirements were checked against official Android documentation. No background clipboard bypass is used.

## Validation

- 42 JVM tests passed. Eight existing device regression tests passed; three new overlay device tests passed in the focused rerun.
- New device checks cover a cancelled job requeued from a copied link while the launcher is foreground, return to the launcher, invalid clipboard without a new job, size changes, dragging to the other edge and long-press hide.
- Test harness corrections: use a swipe rather than UiAutomator's long-press drag; check the resumed activity rather than the launcher accessibility tree; check the database and helper lifecycle rather than transient toast text.
- Manual preview checks: Android permission screen → grant → return → overlay starts; settings size slider changes to 80 dp; notification Hide button stops the overlay service. Final preview installed successfully with existing library and folder settings intact. Size persisted across replacement installs.
- Build and preview lint passed with zero errors and 23 warnings. Remaining warnings include dependency updates, KTX suggestions, the dynamically loaded engine resource and a touch-listener lint warning; the custom button explicitly forwards tap activation through performClick, and device click/drag tests passed.
- Final APK: package app.snaptok, version 0.3.1, versionCode 5; non-debuggable preview with the same test certificate as earlier versions.
- SHA-256: `2b872587ae0ee1e3556a5f40ad8e24761bc6fb60587787a81385993f4894ddfc`.
- Tested on the Android 15 ARM64 emulator. Physical S24 Ultra behaviour and Google Play Protect remain unverified. Live platform extraction was not repeated for this UI feature; the existing queue and downloader are reused.
