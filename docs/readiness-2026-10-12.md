# Readiness record — 12 October 2026

**Update — 13 October:** [Android 0.6.2 build 11 validation](v0.6.2-build11-validation.md) establishes 86 passing tests, lint with 0 errors, successful Preview/Android-test-APK assembly and a verified versioned ARM64 APK. Build 11 also protects older export records from sharing/deleting their original files. The record below describes the historical 12 October blockage. Physical-device checks and paused iOS work remain outstanding.

**Not release-ready.** Source corrections are written; Android runtime/build verification remains blocked, and iOS is paused.

## Android source corrections

- Trim/audio export creates an independent receipt rather than retaining the original's `outputsJson`. Sharing or deleting the exported record resolves its new file. `FeatureOptionsTest.exportedMediaNeverRetainsOriginalSharingOrDeletionReceipts` covers clip and audio output.
- Album publication records each owned URI. A later publication failure rolls earlier outputs back; denied cleanup retains unresolved receipts with `Delete partial files` guidance. History removal cannot orphan failed/cancelled partial files. Manual cleanup records progress and protects Saved aliases.
- Five publication/cleanup regressions in `DownloadWorkerTest` cover rollback, denied cleanup, retry, Saved-alias protection and interrupted manual cleanup. New regressions have not completed execution.
- Explicit `JAVA_HOME` takes precedence in the local build helper. `outputs/` is ignored so versioned APKs/screenshots/archives are preserved locally without source staging.

Whitespace and shell syntax checks passed. A two-worker build exceeded 65 minutes without results. A one-worker, 1 GB heap/in-process Kotlin retry exceeded 25 minutes without test XML, lint output or APKs. Only this chat's verified Gradle processes were stopped. Host evidence: 16 GB physical RAM, 33.4 GB swap used and about 866 MiB swapped during a one-second sample. No test/lint/build success is claimed.

## Reproduce validation when host load permits

Run from repository root with JDK 17 and Android SDK 35 / Build Tools 35.0.0:

```sh
scripts/build-local.sh :app:testDebugUnitTest :app:lintPreview :app:assemblePreview :app:assembleDebugAndroidTest \
  --no-daemon --no-parallel --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1g -XX:+UseSerialGC' \
  -Pkotlin.compiler.execution.strategy=in-process
```

Inspect test XML, Preview lint and APK results under the app build directory recorded in `work/build-directory.txt`. Android test APK compilation does not execute device tests. No Android device/emulator was attached during this audit.

On isolated device fixtures, verify that exporting/sharing/deleting a trimmed or audio copy preserves the original; second-output publication failure rolls back earlier album outputs; revoked folder access retains cleanup receipts; restoring access allows partial cleanup without losing unrelated Saved files. Run existing migration/share/library/photo tests and background/process-interruption checks. Real current-post capture also requires an installed supported TikTok version and explicit consent. Preserve app/database state; do not uninstall or reset personal data.

Stop the overlay through Settings or its notification. Backgrounding the app allows queued WorkManager jobs to continue; force-stop is a separate recovery test. Stop a terminal build with Ctrl-C before starting another build in this checkout.

## Paused iOS findings

| Source | Gap |
|---|---|
| `ShareExtension/ShareViewController.swift`, `Sources/SnapTokCore/TransferService.swift`, `App/SnapTokApp.swift` | Ordinary launch reconciles the main background session but can leave interrupted unique share sessions active indefinitely. Existing Cancel/Retry is a workaround. |
| `Bridge/server.py` | Two workers admit eight unfinished jobs, while a 210-second media wait includes queue time and can expire before valid preparations finish. |
| `Sources/SnapTokUI/VideoDetailView.swift`, `PhotoAlbumView.swift` | Export-all silently omits missing photo assets; missing albums lack the recovery action available to missing videos. |

Paths above are relative to `ios/`. iOS source was left untouched. Resume scope before remediation, then run iOS checks and physical-iPhone Share Extension/background/Photos/Files/entitlement verification.

Existing 0.6.1 APKs and historical validation documents predate these corrections. No new installation, demo recording, signing configuration, production release or store submission was performed. Keep repository private until required checks, disclosure review and release dependencies are satisfied.
