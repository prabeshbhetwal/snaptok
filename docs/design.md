# SnapTok Android v0.1

An Android 10+ personal app receiving TikTok links from the system share sheet, persisting a request, then returning to TikTok without a second Download tap. Manual entry uses the same queue. The initial design and architecture were discussed in the task; the user instructed implementation on 19 September 2026.

## Scope and defaults

Native Kotlin and Compose; Room stores durable jobs; WorkManager owns background execution and retries. An on-device yt-dlp adapter downloads a single combined MP4 to app-private staging. MediaStore publishes verified video files into Movies/SnapTok. No backend, TikTok credentials, clipboard monitoring, analytics, adverts or broad storage permission. Android 10 is the minimum; ordinary public video posts only. Slideshows, live streams and private posts are excluded. Best available combined MP4 is selected without a watermark-free guarantee.

The share receiver acknowledges only after database and WorkManager persistence complete. Failure to enqueue leaves a recoverable database job. Startup reconciles incomplete jobs with unique WorkManager work. Canonical post URLs strip tracking queries; short URLs are resolved by the extractor. Duplicate canonical URLs are suppressed; aliases that cannot yet be resolved may duplicate.

## Reliability

Temporary failures retry up to three WorkManager attempts with exponential backoff. Native extraction is serialised and interruptible. Jobs survive ordinary process death and reboot through WorkManager; force-stop and vendor power restrictions remain platform limits. The foreground notification is quiet but visible. Terminal failures appear in history and a notification if permitted. Successful downloads are published atomically using MediaStore IS_PENDING. Publication uses a stable job filename to recover from interruption without creating duplicate files. A saved record means validated and published, not merely queued.

## Validation

JVM tests cover untrusted share text, host spoofing, canonicalisation, retry classification, database durability/duplicates and worker success/retry/failure paths using a fake downloader and publisher. Build a debug APK and run Android lint. Real-device acceptance must exercise actual TikTok sharing and extraction, screen lock, network loss, reboot, low storage and force-stop recovery. Host tests cannot prove those behaviours.

## Distribution

The Android yt-dlp wrapper is GPL-3.0. Do not publish binaries without satisfying applicable source/licence obligations. Public store policy, dependency audit, signing, API-target requirements and native 16 KB page compatibility require a release review. No release signing keys or publishing are part of this implementation.

## Verified extractor payload

The application bundles the official yt-dlp 2026.08.19 payload, verified against the release SHA-256, overriding the wrapper’s older bundled executable. Fresh-install execution and a live sample transfer passed; see `validation.md` for the evidence and remaining limits.
