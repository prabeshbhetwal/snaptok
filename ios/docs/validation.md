# iOS validation — 25 September 2026

## Verified

| Check | Result | Evidence |
| --- | --- | --- |
| Native Swift core and shared interface build | Passed using Swift 6.4 in Swift 5 language mode and the installed macOS 26.5 SDK | `work/ios-swift-build-26.log` |
| Direct HTTPS transfer through the Swift service | Passed: 1,128,375-byte, 5.011-second playable MP4 | `work/ios-direct-live.log` |
| Social bridge through the Swift client and transfer service | Passed: public Instagram sample, 918,120-byte, 4.967-second playable MP4 | `work/ios-social-live.log` |
| Duplicate protection, library persistence and deletion | Passed in both live integration checks using isolated temporary libraries | Same two live logs |
| Bridge automated suite | 8 tests passed: allowed routes, rejected destinations, limits, authentication, media contract, failure contract and path traversal | `work/ios-bridge-tests.log` |
| Native Mac UI walkthrough | Download input cleared after acceptance; failed source displayed HTTP 403; successful sample showed Video saved; library thumbnail, player, favourite and collection update verified | Interactive native-app inspection |
| Player recovery | SwiftUI VideoPlayer crashed in this Mac's `_AVKit_SwiftUI`; replaced with native AVKit wrappers and verified playback visually | Native player displaying the saved flower sample |
| Glass interface | Native glass surfaces rendered in light/dark appearance; accessible transparency adjustment and 100% setting updated live; setting survived relaunch. A contrast floor was added after inspecting clear glass over bright scenery | Interactive native-app inspection |
| Xcode project generation | Passed with XcodeGen 2.46.0 | Checked-in `.xcodeproj` and `project.yml` |
| iOS-only source syntax | Parsed with Swift frontend; not a substitute for type-checking against iOS SDK | App and Share Extension sources |
| Plists and entitlements | All five files passed `plutil -lint` | `Configuration/` |
| Repository whitespace check | Passed | `git diff --check` |

Live direct sample: `https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4`.
Live social sample: `https://www.instagram.com/p/Chunk8-jurw/`.
An earlier Google sample URL returned 403; this exercised the visible failure state. It is not counted as a successful source test.

## Blocked or not yet verified

- Xcode 27.0 (27A266a) is installed in `/Applications/Xcode.app`. Apple requires acceptance of the Xcode and Apple SDKs licence before first-run setup, SDK commands, simulator installation or iPhone builds can proceed. The licence screen is open; user confirmation was requested. No acceptance was performed by the agent.
- The XCTest suite is written but has not run. Initial command-line-tools testing failed because XCTest is not included in that toolchain. Do not report these tests as passed.
- No iOS simulator or physical-iPhone build has been completed. Native Mac preview success does not prove iOS compilation or background behaviour.
- Share Extension lifecycle, App Group/Keychain entitlements, background relaunch, force-quit recovery, Photos permission/export, notification delivery and Shortcuts require iPhone/simulator validation after setup.
- iOS 18 material fallback, iOS 26/27 Liquid Glass, large text and system accessibility settings still require device coverage. The transparency slider controls SnapTok's custom backing surfaces, not Apple's system-wide glass settings.
- The local test bridge was stopped after verification. Start it deliberately before using a configured bridge connection.
- No signed IPA, TestFlight upload, developer-team registration or App Store submission exists.
- Social extraction depends on the optional private bridge. Only the Instagram route was exercised end to end in this implementation; other accepted platforms are unverified on this bridge build.

The macOS command-line-tools build reported missing optional developer-framework search paths and `arclite` warnings. No source compile errors remain in that native preview build.

## Next verification sequence

1. Accept the Xcode licence with user authorisation, finish first-run components, install an iOS runtime.
2. Run `Scripts/check.sh`; resolve iOS compiler findings before claiming a working iPhone build.
3. Run the SnapTokTests scheme on an iPhone simulator. Add focused Share Extension/background tests as device behaviour is exercised.
4. Inspect Save/Library/Settings in light/dark appearance, transparency extremes, large text and Reduce Transparency/Motion.
5. Test the share workflow on a signed physical device, including lock screen, network interruption, cancellation, retries and notification permission denial.
