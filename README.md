# SnapTok

An iOS implementation is now under [`ios/`](ios/README.md), with a native SwiftUI interface, Liquid Glass, adjustable transparency, a Share Extension and download foundations. Its [validation record](ios/docs/validation.md) distinguishes the verified Mac preview and live transfer checks from pending iPhone builds and testing. The Android project remains at the repository root.

Android media saver for TikTok and X, with Instagram, YouTube, Facebook and Reddit routes in beta from the Android share sheet. Built for Android 10+; the first target phone is a Samsung Galaxy S24 Ultra.

## Version 0.4

Save, Library and Settings have dedicated navigation, coordinated system/light/dark themes and reduced-motion support. The library adds favourites, collections, list/grid views, sorting, captions and an integrated player with speed/resume controls. Media tools export trimmed or audio-only copies without changing originals.

The downloader now bundles FFmpeg for separate video/audio merging. It supports audio-only output, multi-video selection, batches up to 50 links, per-platform quality/folder presets, unmetered/charging constraints and file-size limits. Metadata aliases reuse a saved file when possible; missing files can be downloaded again. New requests after failures can use updated constraints; ordinary Retry retains the job's original options.

Storage settings report accessible saved bytes, temporary files and free phone storage. Cleanup excludes active jobs. Confirmed file deletion is separate from removing history. The floating button adds progress/count/result feedback, optional fading/haptics, an action menu and a restore notification.

See [v0.4 changes](docs/v0.4-changes.md) and [validation](docs/v0.4-validation.md). New platform routes remain beta; accepting a URL is not proof every post can be extracted.

## Everyday use

1. Install the v0.4.0 ARM64 preview APK over the existing personal build; do not uninstall. Your history and settings are preserved.
2. Open Settings to choose a default quality, destination folder and notification preferences. Android notification permission is also required for system alerts.
3. In a supported app, choose **Share**, then **SnapTok** (possibly under **More**). The request is queued using your saved preferences; no second Download tap is required.
4. On Save, use the paste icon, choose quality or audio-only, optionally use **Check post** to select several videos, then press **Download video**. Batch mode accepts up to 50 links. Folder/network defaults live in Settings. Accepted input clears automatically.
5. Search your library by title, URL or platform. Saved cards show local thumbnails and actual file details. Share sends the saved MP4; Remove deletes only the history entry after confirmation.
6. When saving finishes, the enabled completion notification can open the video. Failures appear in history and enabled alerts. Tap a source URL in history to open the original post.

The default destination is **Movies/SnapTok** in the gallery. Custom folders use Android's picker; some providers appear only in Files. New preferences apply to new jobs, while queued jobs and retries retain their options.

The APK bundles verified yt-dlp 2026.08.19. If source changes break extraction, use **Settings > Update engine** and keep the app open while it updates. Public video posts only: no account/login support, private posts, slideshows or live streams. X multi-video posts select the explicit `/video/2` suffix when supplied, otherwise the first video. Generic `t.co` links are not accepted.

Quality preferences never upscale. The source's best available supported MP4 at or below the preferred resolution is selected, falling back to the smallest available if necessary. FFmpeg combines separate video/audio streams when available. If the source reports audio but the result has none, the job fails rather than silently dropping sound. Silent files are labelled No audio track. A watermarked copy is accepted when that is what the source provides.

See [v0.4 changes](docs/v0.4-changes.md) for the folder and notification behaviour, and the versioned validation report for test coverage.

## Floating Save

Enable **Settings → Floating Save button**, then allow **Appear on top** in Android and return to SnapTok. Copy a supported link in another app and tap the floating button. A brief focused screen reads the copied text once, queues it and closes. Downloads continue in the background using existing defaults.

Customise **size (48–80 dp)** and **visibility (25–100%)** in Settings. Drag to dock at either edge; hold to hide. The settings switch and ongoing notification also stop the overlay without cancelling downloads. Appearance and position persist, but the service does not automatically restart after a reboot or process termination. No clipboard monitoring or accessibility service is used. See [floating Save details](docs/v0.3.1-floating-save.md).

## Build

Open this directory in Android Studio, use JDK 17, and install SDK 35 / Build Tools 35.0.0. The checked-in Gradle wrapper pins Gradle 8.11.1 and its distribution SHA-256.

```sh
./gradlew :app:testDebugUnitTest :app:lintPreview :app:assemblePreview
```

On the original development Mac, `scripts/build-local.sh` uses the local toolchain stored under ignored `work/toolchain/`. `local.properties` points at that SDK and is machine-specific. No global Java/Gradle installation is required. The local script places generated app build outputs in a project-specific temporary directory to avoid duplicate generated files in the project folder; its path is recorded in `work/build-directory.txt`. Android Studio/direct Gradle builds still use the standard app build directory unless `SNAPTOK_BUILD_DIR` is set. Do not run two builds concurrently against the same checkout.

S24 Ultra APK: `app/build/outputs/apk/preview/app-arm64-v8a-preview.apk`. A universal APK and other configured ABI packages are also built. This is a non-debuggable, test-signed preview, not a production release. Keep the same signing key to install future updates without losing app data. Never commit signing keys.

## Design

- Kotlin + Jetpack Compose: home, manual input, status cards and settings.
- Room: source URL, status, retries and published media URI.
- WorkManager: durable unique requests, connectivity constraint, expedited start when quota allows and foreground download notification.
- youtubedl-android 0.18.1: bundled on-device Python/yt-dlp. No paid API or backend.
- MediaStore: default gallery publication uses `IS_PENDING`. Custom folders use a persisted SAF grant, verified temporary file and final rename; neither needs broad storage access.

Both entry points use the same queue. Tracking parameters are removed from ordinary post URLs. Identical URL/quality/destination requests are deduplicated; choosing another quality or folder permits another copy. Different short-link aliases may still produce duplicate saves because canonical video identity is not known at enqueue time. Retry is manual for failed/cancelled jobs and automatic up to three attempts for temporary errors. Force-stop and manufacturer power restrictions can delay work; reopen SnapTok to reconcile incomplete jobs.

The share target acknowledges **queued**, not **saved**. Only a validated, published gallery file receives Saved status. Progress is quiet. Completion/failure alerts and brief messages are configurable and require notification permission. Android’s required foreground-service indicator remains even when optional alerts are disabled; history remains available.

## Privacy

No analytics, account, adverts, clipboard monitoring or backend. Links, staging files and job history stay on-device. Extraction contacts the supported platforms and their media hosts. If you choose a cloud folder provider, that provider receives the saved file. Explicit engine updates contact GitHub. Android backup is disabled. Uninstalling removes private app data; published gallery videos remain.

## Development and release notes

Read `docs/design.md`, `docs/implementation-plan.md`, `docs/device-test-plan.md` and `THIRD_PARTY_NOTICES.md`. The extractor library is GPL-3.0; distribution needs licence and corresponding-source compliance. No release signing, store upload, production policy approval or universal extraction guarantee is included. Target SDK and native-library 16 KB compatibility need review before public release.

Incomplete transfers use app-private cache so retries can reuse partial data. Android may reclaim cache; a retry then downloads the file again. Clearing Android app cache removes these temporary files without removing gallery videos.
