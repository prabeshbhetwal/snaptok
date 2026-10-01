# SnapTok 0.1.0 — install and try

Built for your Samsung Galaxy S24 Ultra. This is a debug-signed personal preview.

1. Transfer **SnapTok-0.1.0-debug.apk** to your phone and open it in My Files. If Android asks, allow installation from that file manager, then install SnapTok.
2. Open SnapTok once. Enable notifications for quiet progress and failure alerts.
3. In TikTok, tap **Share**, then **More** if needed, and select **SnapTok**. The request is queued without another Download tap.
4. Continue scrolling. Saved videos appear in **Gallery > Movies/SnapTok**. Open SnapTok for history, manual link entry, retry or cancellation.
5. If TikTok changes cause failures, open **Settings > Update engine**, keep the app open until it finishes, then retry the failed video.

The app saves the best available MP4 with sound, including watermarked copies. It does not promise watermark removal. Ordinary public video posts only in this version.

The build passed 22 unit tests and 3 Android emulator tests. A live TikTok sample downloaded and decoded successfully on a clean installation. Your phone's actual TikTok share menu, screen locking and Samsung battery management still need validation.

Future updates should be installed over this app using the same signing key. Uninstalling or clearing app data removes download history; gallery videos remain.
