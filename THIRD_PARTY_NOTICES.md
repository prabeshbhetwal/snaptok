# Third-party notices

This project depends on the following projects. Their licences remain applicable to their respective code and assets.

| Dependency | Licence | Source |
|---|---|---|
| youtubedl-android 0.18.1 (library/common/ffmpeg) | GPL-3.0 | https://github.com/yausername/youtubedl-android |
| yt-dlp | Unlicense for the project; bundled components have separate terms | https://github.com/yt-dlp/yt-dlp |
| FFmpeg native package | Build-dependent LGPL/GPL and bundled-library terms; inspect the exact packaged build before public distribution | https://ffmpeg.org/legal.html |
| AndroidX Media3 1.6.1 | Apache-2.0 | https://github.com/androidx/media |
| AndroidX / Jetpack | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin / kotlinx.coroutines | Apache-2.0 | https://github.com/JetBrains/kotlin |
| Jackson | Apache-2.0 | https://github.com/FasterXML/jackson |
| Commons IO | Apache-2.0 | https://commons.apache.org/proper/commons-io/ |
| ZIPFoundation 0.9.20 (iOS photo packages) | MIT | https://github.com/weichsel/ZIPFoundation/tree/0.9.20 |

The native wrapper contains a Python runtime and dependencies with their own licences. A complete transitive licence inventory and corresponding-source package must be prepared before distributing publicly. This notice is not a substitute for those obligations. No upstream app UI or branding was copied. SnapTok is not affiliated with TikTok, SnapTik or ByteDance.

## Pinned extractor payload

`app/src/main/res/raw/ytdlp` overrides the wrapper's older bundled payload with the official yt-dlp 2026.08.19 Python zip executable. Source and licence: https://github.com/yt-dlp/yt-dlp/tree/2026.08.19 . Binary: https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp . Verified SHA-256 from GitHub release metadata: `1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6`.

Version 0.4 adds the wrapper's native FFmpeg package and AndroidX Media3 playback/transformation modules. The FFmpeg build configuration and its transitive libraries must be included in the public-distribution licence/source review. App interface icons are a small locally defined vector set; no general icon catalogue is packaged.
