# SnapTok 0.2.1

Settings copy now describes SnapTok without mentioning SnapTik. The footer displays the actual app version without the personal-preview label. Download maintenance and multi-video guidance use simpler wording. Privacy disclosures remain visible; third-party licensing remains documented in THIRD_PARTY_NOTICES.md.

## Installation build

The `preview` build uses release defaults, disables debugging and retains the existing local test certificate so it can update 0.2.0 without uninstalling. Build with:

```sh
scripts/build-local.sh :app:testDebugUnitTest :app:lintPreview :app:assemblePreview
```

This is still a personal testing package, not a production-signed release. Removing debugging is a packaging improvement, not a solution to Google's reputation checks. The `release` variant is deliberately not assigned the test signer.

## Play Protect warning

The supplied screenshot explicitly says Google has not seen an app from this developer before. It does not establish a specific malicious behaviour. The exact reason behind Google's classification cannot be determined from our source code or this screenshot alone.

Google controls this warning. Changing app copy, renaming the APK, or rebuilding cannot guarantee its removal. Do not disable Play Protect. If Google offers an app scan, use its normal scanning flow. If the app remains incorrectly blocked after review, use Google's Play Protect appeal process. No scan, appeal or Play Console submission has been performed for this build.

Before public distribution:

- Set up the developer's Play Console identity and dedicated upload key / Play App Signing. The existing debug certificate is unsuitable for publication.
- Plan migration from the personal test installation before changing signing identity; a different signer cannot simply replace the installed app.
- Review the downloadable extractor update mechanism, current Play requirements and third-party licensing before submitting to a testing track. Store acceptance is not established.
- Retain a stable production signing identity for subsequent releases and complete Google's review. Neither signing nor a testing track guarantees a particular Play Protect verdict.

Official references:

- [Google's Play Protect warning guidance and appeal route](https://developers.google.com/android/play-protect/warning-dev-guidance)
- [Android app signing and debug-certificate limitations](https://developer.android.com/studio/publish/app-signing)

## Validation

- 35 JVM tests passed; preview lint completed with zero errors and 18 warnings (dependency updates, KTX suggestions and the dynamically loaded extractor resource).
- Preview APK signature verified; certificate matches 0.2.0. Package version 0.2.1, code 3. Packaged manifest has no debuggable or testOnly flag; backup remains disabled.
- Installed over the previous emulator version successfully. Four saved entries and the selected Movies/SnapTok-Test-v02 folder remained visible.
- Opened and visually checked the revised settings screen, including the version footer.
- Play Protect was not tested: the stock emulator has no Google Play services. The screenshot's warning remains unresolved pending Google's assessment.
- APK SHA-256: `04d4fbb4599c6752fd90208f3f99490ac749e48a7987c167886ebd9347d7f328`.
