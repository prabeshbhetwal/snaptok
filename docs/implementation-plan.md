# SnapTok implementation plan

**Goal:** A buildable Android share-to-download prototype with durable history and explicit failure recovery.
**Architecture:** Share and manual entry use one queue. Room is the user-facing job record, WorkManager owns scheduling, and a narrow DownloadEngine isolates extraction from publication.
**Stack:** Kotlin, Compose, Room, WorkManager, youtubedl-android.
**Spec:** docs/design.md

## Global constraints

Android 10+; ordinary public TikTok video URLs; no login/server; gallery destination Movies/SnapTok; no clipboard monitoring; no success acknowledgement before persistence or publication.

## Review focus

Reject lookalike hosts and unsupported post types. Repeated shares must not start duplicate jobs. Cancellation must stop extraction. Process interruption during publication must not publish duplicate files. Transient failures must retry finitely and permanent errors must remain actionable.

## Steps

- [x] Scaffold pinned Gradle wrapper, app manifest, permissions and resources.
- [x] Implement and test TikTokLink.parse, FailurePolicy.classify and Room job queries.
- [x] Implement durable unique queue, native engine adapter, foreground worker and atomic MediaStore publisher.
- [x] Implement fast share activity and accessible Compose home/history/settings.
- [x] Test worker success, retry, permanent failure and cancelled job handling with injected dependencies.
- [x] Compile debug APK, run unit tests and lint; inspect results and fix failures.
- [x] Record exact validation coverage, known limitations and device acceptance steps.

Execute locally in this task; no delegated agents or public publishing.
