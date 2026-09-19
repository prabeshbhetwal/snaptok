# SnapTok

Personal Android app for saving TikTok and public X/Twitter videos from the Android share sheet. Built for Android 10+; the first target phone is a Samsung Galaxy S24 Ultra.

## Everyday use

1. Install the v0.2.0 APK over v0.1.0; do not uninstall. Your history is migrated and preserved.
2. Open Settings to choose a default quality, destination folder and notification preferences. Android notification permission is also required for system alerts.
3. In TikTok or X, choose **Share**, then **SnapTok** (possibly under **More**). The request is queued using your saved preferences; no second Download tap is required.
4. For manual downloads, use the paste icon, optionally check **Quality** for available resolutions, choose a folder, then press **Download video**. Accepted input clears automatically.
5. When saving finishes, the enabled completion notification can open the video. Failures appear in history and enabled alerts. Tap a source URL in history to open the original post.

The default destination is **Movies/SnapTok** in the gallery. Custom folders use Android's picker; some providers appear only in Files. New preferences apply to new jobs, while queued jobs and retries retain their options.

The APK bundles verified yt-dlp 2026.08.19. If source changes break extraction, use **Settings > Update engine** and keep the app open while it updates. Public video posts only: no account/login support, private posts, slideshows or live streams. X multi-video posts select the explicit `/video/2` suffix when supplied, otherwise the first video. Generic `t.co` links are not accepted.

Quality preferences never upscale. The source's best available supported MP4 at or below the preferred resolution is selected, falling back to the smallest available if necessary. Audio is retained when available in the selected file; separate video/audio streams are not merged. A watermarked copy is accepted when that is what the source provides.

See [v0.2 changes](docs/v0.2-changes.md) for the folder and notification behaviour, and the versioned validation report for test coverage.

## Build

Open this directory in Android Studio, use JDK 17, and install SDK 35 / Build Tools 35.0.0. The checked-in Gradle wrapper pins Gradle 8.11.1 and its distribution SHA-256.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

On the original development Mac, `scripts/build-local.sh` uses the local toolchain stored under ignored `work/toolchain/`. `local.properties` points at that SDK and is machine-specific. No global Java/Gradle installation is required. Do not run two builds concurrently against the same checkout.

APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed personal preview, not a production release. Keep the same signing key to install future updates without losing app data. Never commit signing keys.

## Design

- Kotlin + Jetpack Compose: home, manual input, status cards and settings.
- Room: source URL, status, retries and published media URI.
- WorkManager: durable unique requests, connectivity constraint, expedited start when quota allows and foreground download notification.
- youtubedl-android 0.18.1: bundled on-device Python/yt-dlp. No paid API or backend.
- MediaStore: default gallery publication uses `IS_PENDING`. Custom folders use a persisted SAF grant, verified temporary file and final rename; neither needs broad storage access.

Both entry points use the same queue. Tracking parameters are removed from ordinary post URLs. Identical URL/quality/destination requests are deduplicated; choosing another quality or folder permits another copy. Different short-link aliases may still produce duplicate saves because canonical video identity is not known at enqueue time. Retry is manual for failed/cancelled jobs and automatic up to three attempts for temporary errors. Force-stop and manufacturer power restrictions can delay work; reopen SnapTok to reconcile incomplete jobs.

The share target acknowledges **queued**, not **saved**. Only a validated, published gallery file receives Saved status. Progress is quiet. Completion/failure alerts and brief messages are configurable and require notification permission. Android’s required foreground-service indicator remains even when optional alerts are disabled; history remains available.

## Privacy

No analytics, account, adverts, clipboard monitoring or backend. Links, staging files and job history stay on-device. Extraction contacts TikTok/X and their media hosts. If you choose a cloud folder provider, that provider receives the saved file. Explicit engine updates contact GitHub. Android backup is disabled. Uninstalling removes private app data; published gallery videos remain.

## Development and release notes

Read `docs/design.md`, `docs/implementation-plan.md`, `docs/device-test-plan.md` and `THIRD_PARTY_NOTICES.md`. The extractor library is GPL-3.0; distribution needs licence and corresponding-source compliance. No release signing, store upload, production policy approval or universal extraction guarantee is included. Target SDK and native-library 16 KB compatibility need review before public release.

Incomplete transfers use app-private cache so retries can reuse partial data. Android may reclaim cache; a retry then downloads the file again. Clearing Android app cache removes these temporary files without removing gallery videos.
