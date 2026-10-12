# SnapTok 0.3.0 validation

Date: 22 September 2026. Android 15 ARM64 emulator; no physical Samsung-device test in this run.

- Final build: unit tests, preview lint, preview APK, debug APK and instrumentation APK succeeded.
- 41 JVM tests passed, including Instagram canonicalisation and hostile URL rejection, library search, bounded retries, audio-merge failure classification and protection against removing active jobs.
- Eight device tests passed, including existing migration, native engine, publication, custom folder, paste/input behaviour, plus saved metadata/thumbnail extraction and history removal preserving file bytes.
- Lint: no errors; 19 warnings (dependency update suggestions, KTX suggestions and dynamically loaded extractor resource).
- Preview installed over the existing app successfully; previous library entries and persisted custom folder remained available.
- Visually checked compact home and library. Search for Instagram filtered the library. Share opened Android's file share sheet. Remove displayed a confirmation and removed only the history entry; the file was still present. Re-sharing the same post created a new successful download.
- Live Instagram Reel sample: https://www.instagram.com/reel/Chunk8-jurw/ saved 720 × 1280 H.264, approximately 4.97 seconds, 1,949,801 bytes. Full FFmpeg video decode passed.
- Live Instagram multi-video sample: https://www.instagram.com/p/BQ0eAlwhDrw/ saved its first video, 640 × 640, 39,753 bytes. Repeated successfully on the final non-debuggable preview after history removal.
- These samples exposed video-only formats to the extractor. Saved cards explicitly show No audio track. No successful audible Instagram download is claimed. Sources requiring separate audio/video merging fail clearly; merging remains future work.
- Existing TikTok/X tests passed; live TikTok/X extraction was not repeated in this run.
- APK: version 0.3.0, versionCode 4, package app.snaptok. Signature verified; existing test certificate preserved. Packaged manifest contains neither debuggable nor testOnly.
- APK SHA-256: c1193011317094206e982ed833194ad067c430063d7ccf5c1c2fff75cb02e9cd.

No public-store upload, signing-identity migration or Google Play Protect assessment was performed. Instagram remains beta; sample success does not establish universal post, account, country or network compatibility.
