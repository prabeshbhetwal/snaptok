# SnapTok 0.4 validation

Validated on 22 September 2026 using an Android 15 ARM64 emulator. No physical Galaxy S24 Ultra test or public-store submission was performed.

## Automated checks

- 51 JVM tests passed: parsing and canonicalisation, batch validation, quality selection, durable queue behaviour, missing-file recovery, request-option snapshots, failure policy, cancellation, notifications and appearance preferences.
- All 17 device tests passed together in `work/v04/final-acceptance.log` (`OK (17 tests)`). Coverage includes migrations 1→2 and 2→3; safe gallery and custom-folder publication; native Python/extractor execution; native FFmpeg merging; audio export and trimming preserving original bytes; the charging constraint; clipboard/share controls; floating-button operation; favourites/collections/player/grid/deletion; and preserving entered text across tabs.
- Native merge fixture produced an MP4 with both video and audio tracks. The trim fixture was shortened to approximately one second. Audio export contained audio without a video track; source bytes were unchanged.
- Preview build and lint completed successfully. Lint reported zero errors and 37 warnings, primarily dependency-version suggestions, KTX suggestions, dynamic resources and storage/touch-listener advice.
- Manual checks covered final preview installation, light/dark rendering, grid/list display and the compact layout at 200% system text size. System text size was restored after the check.

## Live source checks

Each successful Android check downloaded a file through `YtDlpEngine` and inspected it with Android's media metadata reader. These are sample results, not guarantees for every post, region, account or network. Live tests use an explicit URL argument and are excluded from the normal offline suite.

| Platform | Sample | Android result |
|---|---|---|
| TikTok | `@patroxofficial/video/6742501081818877190` | Passed; video and audio tracks present |
| X | `TopHeroes_/status/2001950365332455490` | Passed; video and audio tracks present |
| Instagram | `p/Chunk8-jurw/` | Passed; source exposed video-only media |
| YouTube | `watch?v=aqz-KE-bpKQ` | Passed; video and audio tracks present |
| Facebook | `reel/1195289147628387` | Passed; video and audio tracks present |
| Reddit | `comments/90bu6w/` | Passed; video present; audio was not required for this sample |

Logs: `work/v04/live-{platform}.log`. The earlier Facebook sample `radiokicksfm/videos/3676516585958356/` failed desktop metadata extraction. Additional Reel and Watch examples resolved successfully, and the Reel passed the Android download check. The bundled extractor matched the latest official release reported during validation: 2026.08.19.

## Upgrade and package

- Version 0.4.0, versionCode 6, package `app.snaptok`.
- Room migration keeps existing URLs, titles, media URIs and quality/destination preferences; added fields have safe defaults. The emulator's six existing saved entries remained available.
- Non-debuggable ARM64 preview, suitable for the S24 Ultra architecture. Size: 84,710,143 bytes (approximately 85 MB).
- Signature verified; certificate SHA-256 remains `4f32a4b1c05b2bd550045ba523f514fa4495cefe4492bcf3a121a1105a65c829`.
- APK SHA-256: `17eab294ebab58a905268737e121f8827253fba9bdda3f9c9a59e8478e4d3694`.
- Local build outputs are isolated outside the project directory. The exact path is in `work/build-directory.txt`. This avoids the duplicate generated DEX files observed in the former app build directory.

## Boundaries

Instagram, YouTube, Facebook and Reddit remain beta. Private/login-required/DRM content is not supported. Live streams are filtered out. Missing source audio cannot be recreated. Multi-post selection is capped at 50 items; batch input is capped at 50 links. Post link aliases may still incur extraction or transfer before an existing saved source identity is recognised.

Network mode means **unmetered**, usually Wi-Fi, rather than every Wi-Fi network. Charging/network constraints are checked again before transfer, and interrupted work returns to Queued without reviving user-cancelled jobs. File limits are checked where source sizes are known and against the finished output; components and exports require temporary space.

Exports require SnapTok to remain open. Physical-device smoothness, manufacturer battery restrictions and long-running transfers still need handset testing. The APK retains the local test signer; Google Play Protect status and public-release approval are unchanged.
