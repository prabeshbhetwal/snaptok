# SnapTok 0.2.0 validation

Validated on 19 September 2026. Personal Android preview; application ID `app.snaptok`, version code 2. Existing v0.1 history is upgraded in place.

| Check | Result |
|---|---|
| Clean build | Passed: debug APK and Android test APK |
| JVM tests | 35 passed; 0 failures |
| Android emulator tests | 7 passed on ARM64 Android 15, including real persisted folder access |
| Android lint | 0 errors; 17 non-blocking warnings |
| Upgrade migration | Schema 1 to 2 retained the title, saved URI and safe defaults; the emulator's original saved video remained in history |
| Paste / clear | Real clipboard button pasted the test link; successful acceptance cleared it; rejected input remained editable |
| Custom folder | Android folder picker granted Movies/SnapTok-Test-v02. Publication recovered a partial file, verified bytes and returned the same receipt on retry |
| Source link | Tapping the X source URL opened that exact URL in the emulator's WebView browser |
| Notification controls | Turning Notify me off persisted the setting and removed existing SnapTok alerts; restored to on afterwards |
| Completion / failure | Both were observed in the notification tray; successful notification included Play video |
| Progress | Unit tests verified percentage/max fields and a separate indeterminate saving state; a minimal foreground indicator remains when optional progress is off |
| Signing | Same certificate as the existing v0.1 APK; verified for in-place installation |

## Live media checks

- TikTok: `https://www.tiktok.com/@patroxofficial/video/6742501081818877190`. A source-reported 576p preference was selected, the field cleared on acceptance, and the result saved in the chosen custom folder. Actual file: H.264 540×960 plus AAC, 27.466667 seconds, 2,748,647 bytes. Full FFmpeg decode passed. The request is a preference, not an assertion that the final file has that exact resolution.
- X: `https://x.com/TopHeroes_/status/2001950365332455490`. A public post downloaded to the custom folder without account credentials. Actual best-available file: H.264 720×720 plus AAC, 30.325261 seconds, 3,393,585 bytes. Full decode passed.
- A second X run selected **540p** from the actual **720p / 540p / 320p** menu. The resulting file was confirmed as 540×540 H.264 with AAC and passed full decoding. This verifies the quality selection affects the downloaded media, not just its label.
- An older X test post referenced an unavailable legacy media host and correctly ended in a failure alert. This is not a claim that all public posts remain downloadable.

X exposed a real compatibility issue: its direct MP4 formats can report unknown codecs. Selection now uses yt-dlp's best-format semantics instead of rejecting missing codec metadata. A regression test covers discovery of those resolutions while excluding explicitly audio-only formats.

## Limits

Actual TikTok/X share-menu placement and Samsung battery/screen-lock behaviour for the new build still need confirmation on the S24 Ultra. Android may retain its required ongoing-service indicator even with optional notifications disabled. Protected/login-required posts, live streams, slideshows and generic t.co links are unsupported. Folder providers must support reading, writing and rename; only the local Android provider was exercised. Public-store policy, release signing and native 16 KB compatibility remain separate release work.

## Artefact

- APK: `SnapTok-0.2.0-debug.apk`
- Bytes: 77,980,670
- SHA-256: `96be868c44bdfd144078f283135f652e4c3de1af86ddd65a4b7d02b84fc1d632`
- Signing certificate SHA-256: `4f32a4b1c05b2bd550045ba523f514fa4495cefe4492bcf3a121a1105a65c829`
- Engine payload: verified yt-dlp 2026.08.19, with optional manual stable updates.

A packaging pass encountered duplicated generated ` 2.dex` files, with no matching duplicate source files. The old build directory was preserved under ignored work/ and a clean rebuild succeeded. Logs and media verification evidence remain under ignored work/. No existing v0.1 deliverables were overwritten.
