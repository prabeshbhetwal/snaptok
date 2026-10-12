# SnapTok

Android project lives at the repository root. A separate local iOS implementation remains paused and is outside this Android source update. The [photo-post validation record](docs/v0.5-photo-posts.md) records historical Android and iOS checks; physical iPhone testing remains outstanding.

Android media saver for TikTok and X, with Instagram, YouTube, Facebook and Reddit routes in beta from the Android share sheet. Built for Android 10+; the first target phone is a Samsung Galaxy S24 Ultra.

Android **0.6.2 / build 13** protects new and legacy export receipts and failed-album cleanup, rolls back an export when its receipt cannot be saved, and serialises deletion with publication. Failed exports offer cleanup rather than retrying their original URL. [Build 12 evidence](docs/v0.6.2-build12-validation.md) preserves earlier packaging; final results are recorded in [PR #1](https://github.com/prabeshbhetwal/snaptok/pull/1). [Android validation](docs/android-ci.md) defines serial unit/lint/assembly and 21 isolated native checks. Physical-device checks remain outstanding; iOS remains paused with known recovery/export gaps. The [12 October readiness record](docs/readiness-2026-10-12.md) preserves the earlier audit and build blockage.

## Version 0.6 — optional current-post capture (Android)

Version **0.6.1** fixes recognition of TikTok's counted Share label (for example, **Share video 25K shares**). One-tap capture and completed downloading were verified on the S24 Ultra with TikTok 47.1.4. See [the on-device fix report](docs/v0.6.1-capture-fix.md).

Floating Save now offers **Save current post · TikTok**. After separate consent and Android Accessibility setup, a tap runs a fixed English Share → Copy link sequence, validates a fresh TikTok link and uses the existing download queue. The share panel may appear briefly. No guessed screen coordinates, continuous monitoring or stale-clipboard fallback. This experimental adapter needs validation against the user's installed TikTok version; synthetic tests cannot establish live compatibility. See [setup and verification](docs/v0.6-current-post.md). iOS work is paused.

## Version 0.5 — photo posts

Public TikTok photo posts can be saved as **Images**, **Slideshow** or **Both**. Settings remembers the default for Share and Floating Save; manual input can override it. Originals retain their source bytes and order. Slideshows fit each image without cropping, with 2–10 seconds per image and optional music repeated to match the duration. Missing requested music produces an actionable failure rather than a silently muted video.

Albums support browsing and sharing all images. Default destinations are Pictures/SnapTok for images and Movies/SnapTok for slideshows; custom folders support both. Saved status is committed only after every requested file is published. See [feature details and validation](docs/v0.5-photo-posts.md).

## Existing features

Save, Library and Settings have dedicated navigation, coordinated system/light/dark themes and reduced-motion support. The library adds favourites, collections, list/grid views, sorting, captions and an integrated player with speed/resume controls. Media tools export trimmed or audio-only copies without changing originals.

The downloader now bundles FFmpeg for separate video/audio merging. It supports audio-only output, multi-video selection, batches up to 50 links, per-platform quality/folder presets, unmetered/charging constraints and file-size limits. Metadata aliases reuse a saved file when possible; missing files can be downloaded again. New requests after failures can use updated constraints; ordinary Retry retains the job's original options.

Storage settings report accessible saved bytes, temporary files and free phone storage. Cleanup excludes active jobs. Confirmed file deletion is separate from removing history. The floating button adds progress/count/result feedback, optional fading/haptics, an action menu and a restore notification.

See [v0.4 changes](docs/v0.4-changes.md) and [validation](docs/v0.4-validation.md). New platform routes remain beta; accepting a URL is not proof every post can be extracted.

## Everyday use

1. Install the v0.6.2 ARM64 preview APK over the existing personal build; do not uninstall. The signer matches v0.6.1, retaining the existing app's data on upgrade.
2. Open Settings to choose a default quality, destination folder and notification preferences. Android notification permission is also required for system alerts.
3. In a supported app, choose **Share**, then **SnapTok** (possibly under **More**). The request is queued using your saved preferences; no second Download tap is required.
4. On Save, use the paste icon, choose quality and photo output, then press **Download post**. Audio-only and **Check post** apply to video posts. Batch mode accepts up to 50 links. Accepted input clears automatically.
5. Search your library by title, URL or platform. Saved cards show thumbnails and file details. Share sends the saved media; Remove deletes only the history entry after confirmation.
6. When saving finishes, the enabled completion notification opens the saved video or album. Failures appear in history and enabled alerts. Tap a source URL to open the original post.

The default destinations are **Movies/SnapTok** and **Pictures/SnapTok** in the gallery. Custom folders use Android's picker; some providers appear only in Files. New preferences apply to new jobs, while queued jobs and ordinary retries retain their options. **Retry with current defaults** applies changed preferences to a failed request.

The APK bundles verified yt-dlp 2026.08.19. If video extraction breaks, use **Settings > Update engine** and keep the app open while it updates. TikTok photo extraction is bundled app code and requires an app update if its source format changes. Public posts only: no account/login support, private posts or live streams. Photo support currently targets TikTok `/photo/` posts and their official short links. X multi-video posts select the explicit `/video/2` suffix when supplied, otherwise the first video. Generic `t.co` links are not accepted.

Quality preferences never upscale. The source's best available supported MP4 at or below the preferred resolution is selected, falling back to the smallest available if necessary. FFmpeg combines separate video/audio streams when available. If the source reports audio but the result has none, the job fails rather than silently dropping sound. Silent files are labelled No audio track. A watermarked copy is accepted when that is what the source provides.

See [v0.4 changes](docs/v0.4-changes.md) for the folder and notification behaviour, and the versioned validation report for test coverage.

## Floating Save

Enable **Settings → Floating Save button**, then allow **Appear on top** in Android and return to SnapTok. Copy a supported link in another app and tap the floating button. A brief focused screen reads the copied text once, queues it and closes. Downloads continue in the background using existing defaults.

Customise **size (48–80 dp)** and **visibility (25–100%)** in Settings. Drag to dock at either edge; hold to hide. The settings switch and ongoing notification also stop the overlay without cancelling downloads. Appearance and position persist, but the service does not automatically restart after a reboot or process termination. Copied-link mode does not require Accessibility. The optional current-post mode uses a separately enabled Accessibility Service only during an explicit capture. No continuous clipboard monitoring is used. See [current setup](docs/v0.6-current-post.md) and [original floating Save details](docs/v0.3.1-floating-save.md).

## Build

Open this directory in Android Studio, use JDK 17, and install SDK 35 / Build Tools 35.0.0. The checked-in Gradle wrapper pins Gradle 8.11.1 and its distribution SHA-256.

```sh
./gradlew :app:testDebugUnitTest :app:lintPreview :app:assemblePreview
```

On the development Mac, `scripts/build-local.sh` uses the Android SDK under ignored `work/toolchain/` and the local JDK when present, otherwise Homebrew OpenJDK 17. Explicit JAVA_HOME takes precedence. `local.properties` is machine-specific. Gradle uses the checked-in wrapper. Generated app outputs use a project-specific temporary directory recorded in `work/build-directory.txt`. Android Studio/direct Gradle builds use the standard app build directory unless `SNAPTOK_BUILD_DIR` is set. Do not run two builds concurrently against the same checkout; use `--no-parallel --max-workers=2` for bounded local checks.

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
