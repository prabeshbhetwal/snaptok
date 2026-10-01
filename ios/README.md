# SnapTok for iOS

Native SwiftUI app for iOS 18 and later. Liquid Glass appears on iOS 26+; earlier systems use a translucent material fallback. The iOS project lives alongside Android and does not change its build.

## Included

- Save, Library and Settings with light/dark/system appearance, a live 0–100% glass-transparency slider, reduced-motion support and opaque surfaces when the system requests Reduce Transparency. Text is not made transparent.
- Manual link entry, explicit paste, quality preference, cellular-data preference and size limit. Input clears only after an accepted request.
- Durable download records with progress, cancellation, retry, errors and optional completion/failure notifications. Files are validated with AVFoundation before Saved status. Retries have unique attempt identities to ignore stale callbacks.
- Search, platform/favourite filters, sorting, list/grid layout, local thumbnails, favourites, collections, source links and confirmed deletion.
- Native AVKit playback, speed controls, export through the system share sheet and explicit Save to Photos on iPhone.
- Share Extension, App Group storage, shared Keychain credentials and separate background URLSession identifiers. The extension prepares a system transfer before returning to the source app.
- Shortcuts action taking a video URL. A user-created shortcut can get the clipboard and call Save a video, then be assigned to an Action Button or Back Tap.

## Download support

Direct **HTTPS MP4, MOV and M4V** links download on the device without a server. Signed query parameters are retained. The downloaded file must contain playable video. A direct file keeps its original quality.

Social posts from TikTok, Instagram, YouTube, X, Facebook and Reddit use the optional [private bridge](Bridge/README.md). The first iOS implementation does **not** bundle Android's Python/FFmpeg executables or perform social extraction on the phone. Configure your own bridge address and connection key in Settings. No public/default provider is built in. The bridge receives the post URL and temporarily prepares the video; the app explains this before configuration. Photos, private/login-only posts, DRM and live streams are outside this version's scope. Source compatibility varies and must be tested per platform.

Videos are stored in SnapTok's private library. Export copies to Photos or Files to keep them independently of the app. Persistent custom destination folders, audio-only downloads, trimming, Live Activities, batches and a fully on-device social extractor are not implemented in this version. iOS does not provide our Android floating overlay.

## Open and build

1. Install full Xcode 26 or later (Xcode 27 is the current development target), accept its licence and install an iOS simulator runtime.
2. Open `SnapTok.xcodeproj`. The project is checked in; XcodeGen is needed only to regenerate it after changing `project.yml`.
3. For a physical device, copy `Configuration/Local.xcconfig.example` to `Configuration/Local.xcconfig`, set your development team and unique bundle/App Group identifiers, and enable the same App Group and Keychain group for both the app and Share Extension. The optional local file is ignored by Git.
4. Choose the SnapTok scheme and an iPhone simulator or your connected iPhone, then Run.

Unsigned simulator build and tests:

```sh
./Scripts/check.sh
xcodebuild -project SnapTok.xcodeproj -scheme SnapTok \
  -destination 'platform=iOS Simulator,name=YOUR_INSTALLED_IPHONE' test
```

Regenerate: `xcodegen generate`. Development commands run from this `ios` folder. No developer account, team, device or distribution permission has been silently configured. App Store submission is separate work.

## Mac interface preview

`./Scripts/preview.sh` builds the same screens in a native Mac window. This is a visual/development preview, not an iPhone simulator. Mac transfer checks use a foreground URLSession; they do not prove iOS background relaunch, Share Extension or Photos behaviour.

The command-line tools' macOS 27 SDK currently lacks the SwiftUI macro plug-in. The preview script can use the installed macOS 26.5 SDK when full Xcode is unavailable. Full Xcode is required for XCTest and iPhone builds.

## Validation tools

- `swift test`: link validation, storage isolation, concurrent duplicate requests, persistence, retry-attempt protection and size-limit failures. The live transfer test is skipped unless explicitly enabled.
- `python3 -m unittest discover -s Tests/BridgeTests -v`: bridge routes, authentication, limits and media/error delivery.
- `swift run SnapTokCheck HTTPS_VIDEO_URL`: isolated live transfer, playable-file validation, duplicate protection, persistence and deletion. For social links, set `SNAPTOK_CHECK_BRIDGE` and `SNAPTOK_CHECK_TOKEN_FILE` to the private test bridge. The tool never prints the connection key.
- [Validation record](docs/validation.md) distinguishes actual test results from pending iPhone verification.

## Architecture and privacy

`SnapTokCore` owns validation, atomic shared records, Keychain access, bridge preparation and transfers. `SnapTokUI` owns presentation and native playback/export wrappers. `App` handles iOS lifecycle, notifications and Shortcuts; `ShareExtension` is a separate entry point into the same queue. No downloaded executable code or third-party iOS SDK is included. The optional bridge's dependencies remain on the operator's computer.

The app has no analytics, advertising, accounts, automatic clipboard reading or bundled third-party server. Library data is excluded from device backups. Bridge credentials are shared through Keychain for this device; download files and records use the App Group. Public release will still need platform authorisation, final privacy declarations, release signing and App Review.
