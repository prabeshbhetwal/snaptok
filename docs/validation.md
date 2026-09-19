# SnapTok 0.1.0 validation

Validated on 19 September 2026. Source project: `/Users/prabeshbhetwal/Desktop/Files/Development/Project/SnapTok`.

## Results

| Check | Result | Coverage |
|---|---|---|
| Debug APK build | Passed | Android 10+ app, SDK/target 35, Kotlin/Compose |
| Unit tests | 22 passed | URL validation, finite retries, Room durability, duplicate suppression, scheduling failure recovery, worker success/failure/cancellation |
| Android lint | No errors; dependency-update warnings remain | Pinned compatible dependencies; no production release approval implied |
| ARM64 Android 15 emulator | 3 instrumented tests passed | Native Python/yt-dlp execution; pinned payload hash/version; valid gallery publication, pending-row recovery and duplicate receipt; invalid-file rejection |
| Simulated Android share | Passed | ACTION_SEND text/plain persisted a request and the share activity dismissed to the preceding activity |
| Live TikTok transfer | Passed for one public sample | Upstream yt-dlp fixture `https://www.tiktok.com/@patroxofficial/video/6742501081818877190` |
| Clean install | Passed | Download succeeded with the pinned bundled engine, without using Update engine |
| Saved media | Passed | 2,748,647 bytes; 27.466667 seconds; 540×960 H.264 video plus AAC audio; full FFmpeg decode returned 0 |
| Duplicate live share | Passed | Sharing the same canonical post with a tracking query produced no additional saved file |
| APK signature | Verified during packaging | Debug key, personal preview only |

The same sample was downloaded once after an engine update and once after clearing test app data and installing the pinned payload. These are two runs of one video, not a broad compatibility survey. The files matched byte-for-byte. Videos and user accounts are not bundled in the APK; the instrumentation APK contains only a generated one-second test clip.

## Important implementation finding

Live extraction failed with the Android wrapper's original bundled extractor. Updating to yt-dlp 2026.08.19 restored extraction for the sample. The final app overrides that older payload with the official, SHA-256-verified 2026.08.19 executable. A native test verifies both the bundled resource hash and executed version on a clean install. The Update engine control remains available for future source changes.

## Not yet verified

- The actual TikTok app share-menu placement on the Galaxy S24 Ultra.
- Samsung/One UI background restrictions, screen-lock behaviour, reboot recovery and physical-device storage pressure.
- Different regions, many videos, private posts, slideshows or live streams. The last three are outside this version's scope.
- Native 16 KB page-size compatibility, current public-store requirements, release signing and complete redistribution compliance.

Room and WorkManager tests support recovery logic; they do not prove every manufacturer lifecycle behaviour. Force-stop prevents work until the app is reopened. Distinct short-link aliases may still save the same underlying video twice. See `docs/device-test-plan.md` for the physical-device checklist.

## Artefact

- File: `SnapTok-0.1.0-debug.apk`
- Size: 81,633,388 bytes
- SHA-256: `7ecccca2e6a6a0c959c37a3d1fea39f67829b5a70d43689bc3fb58ba99f59a1d`
- App ID: `app.snaptok`
- Version: `0.1.0` (1)
- Bundled extractor: yt-dlp `2026.08.19`

Build/test logs remain under ignored `work/`; Gradle test and lint reports are under `app/build/reports/`. Machine-local Java, Gradle, SDK and emulator files are under ignored `work/toolchain/`.
