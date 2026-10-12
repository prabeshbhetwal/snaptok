# SnapTok development

- Android app lives at repository root: Kotlin/Compose, Room, WorkManager, bundled yt-dlp and FFmpeg. iOS lives in `ios/`: Swift packages, app, Share Extension and optional private Python bridge.
- Preserve existing uncommitted work. Use focused patches; never reset the checkout or replace whole files to implement a feature.
- Android source changes must preserve queued-job option snapshots, database migration paths, explicit clipboard access and publication-before-Saved ordering.
- Android uses MediaStore or a user-selected SAF folder. iOS uses an App Group library and explicit Photos/Files exports. Credentials belong in Keychain, never source or logs.
- No default third-party extraction service. Android extraction runs on device; iOS social extraction currently uses the explicitly configured private bridge.
- Use `graphify-out/GRAPH_REPORT.md` as an architecture map; verify inferred edges in source. Prefer available CodeGraph tools for symbols; fall back to bounded source reads when unavailable.
- Android checks: `scripts/build-local.sh :app:testDebugUnitTest :app:lintPreview :app:assemblePreview`. Requires JDK 17, Android SDK 35 and Gradle 8.11.1. Inspect current tool availability; ignored local toolchains may not exist.
- iOS checks: run `ios/Scripts/check.sh` with full Xcode and an installed iOS simulator runtime. `ios/project.yml` generates the checked-in Xcode project through XcodeGen.
- Distinguish source validation, Mac preview checks, simulator tests, physical-device tests and live-platform extraction. Never infer universal provider support from one sample.
- Keep build outputs, signing material, credentials, temporary extraction data and virtual environments out of Git. Create versioned deliverables rather than overwriting earlier releases.
- TikTok photos use ordered public-page/embed metadata with bounded HTTPS media downloads. Android renders slideshows with Media3; the iOS private bridge renders with FFmpeg and delivers a validated flat ZIP or MP4. Never silently discard requested music or mark a partial album Saved.
- Room schema exports share one directory: keep Debug/Preview/Release KSP exports ordered and avoid concurrent builds. Migration tests cover each supported upgrade edge.
- Swift builds/tests use external temporary scratch/DerivedData directories; Finder metadata on Desktop outputs can invalidate code signing. A simulator build is not evidence that its UI or background lifecycle ran.
- Current-post capture is Android-only, opt-in and tap-triggered. Restrict accessibility inspection/actions to supported TikTok packages and exact recognised controls; reject stale clipboard data and never fall back silently to a previous link. No coordinate guessing or continuous monitoring.
- Capture selectors must account for observed accessibility semantics, including labelled actions and dynamic share counts using anchored grammars. Verify new selector variants against a real device where possible; screenshot pixels alone do not establish an accessibility label. Retain synthetic regressions for observed variants.
- iOS work is paused at the user's request. For current Android work, avoid iOS builds/simulators and keep build concurrency/memory bounded.
- Run Gradle connected instrumentation only on a fresh disposable Android emulator: AGP replaces and uninstalls the target/test apps. `scripts/test-android-offline.sh` requires an explicit emulator serial and zero skipped tests; never target a personal installation.
