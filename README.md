# SnapTok

Personal Android app for saving TikTok videos from the Android share sheet. Built for Android 10+; the first target phone is a Samsung Galaxy S24 Ultra.

## Everyday use

1. Install the debug APK and open SnapTok once. Allow notifications if you want progress and failure alerts.
2. The APK bundles yt-dlp 2026.08.19, verified during development. If TikTok changes break extraction later, use **Settings > Update engine**. This fetches the latest stable yt-dlp release from GitHub; keep SnapTok open while it updates.
3. In TikTok, choose **Share**, then **SnapTok** (possibly under **More**). The share screen closes after the request is persisted.
4. Continue using TikTok. Finished MP4s appear in **Movies/SnapTok** in the gallery.
5. Open SnapTok for manual link entry, history, cancellation, retry or engine updates.

The app saves the best available single MP4 with sound, including a watermarked copy when that is what the source supplies. It does not promise watermark removal. Ordinary public video posts are supported; photo slideshows, live streams, private posts and account login are outside this version.

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
- MediaStore: validated videos are published with `IS_PENDING`, without broad storage permission.

Both entry points use the same queue. Tracking parameters are removed from ordinary post URLs. Repeated canonical links are suppressed. Different short-link aliases may still produce duplicate saves because canonical video identity is not known at enqueue time. Retry is manual for failed/cancelled jobs and automatic up to three attempts for temporary errors. Force-stop and manufacturer power restrictions can delay work; reopen SnapTok to reconcile incomplete jobs.

The share target acknowledges **queued**, not **saved**. Only a validated, published gallery file receives Saved status. Visible progress is intentionally quiet. Failure alerts require notification permission; otherwise consult history.

## Privacy

No analytics, account, adverts, clipboard monitoring or backend. Links, staging files and job history stay on-device. Extraction contacts TikTok and its media hosts. Explicit engine updates contact GitHub. Android backup is disabled. Uninstalling removes private app data; published gallery videos remain.

## Development and release notes

Read `docs/design.md`, `docs/implementation-plan.md`, `docs/device-test-plan.md` and `THIRD_PARTY_NOTICES.md`. The extractor library is GPL-3.0; distribution needs licence and corresponding-source compliance. No release signing, store upload, production policy approval or universal extraction guarantee is included. Target SDK and native-library 16 KB compatibility need review before public release.

Incomplete transfers use app-private cache so retries can reuse partial data. Android may reclaim cache; a retry then downloads the file again. Clearing Android app cache removes these temporary files without removing gallery videos.
