# Android validation

The Android workflow checks the PR's exact head SHA on Ubuntu 24.04 with JDK 17, Android SDK 35 and one Gradle worker. Actions are pinned to commits; repository permissions are read-only. Its API 35/default/x86_64 emulator uses the Pixel 2 profile, two cores and 2 GB RAM. Only build/test reports are uploaded, retained for seven days. CI's temporary signer is not the locally installed Preview signer; no installable APK or signing material is uploaded.

The owner requested **local-only CI on 13 October 2026**. The GitHub job is explicitly disabled for every event, including ready PRs, `master` pushes and manual dispatch. A skipped job is **not a passing validation**; local script results are the validation gate. No remote runner, token scope or billing-setting change is authorised. Remote execution requires a new explicit instruction.

## Local checks

Run unit tests, lint and assembly serially:

```sh
sh scripts/build-local.sh :app:testDebugUnitTest :app:lintPreview :app:assemblePreview :app:assembleDebugAndroidTest \
  --no-daemon --no-parallel --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1g -XX:+UseSerialGC' \
  -Pkotlin.compiler.execution.strategy=in-process
```

Then run the same offline native selection as CI, **on a fresh disposable emulator only**:

```sh
ANDROID_SERIAL=emulator-5560 sh scripts/test-android-offline.sh
```

The script rejects a physical device and requires explicit selection. Gradle instrumentation installs/replaces and then uninstalls the target/test apps; never point it at an existing personal installation. The local validation emulator was created separately from existing AVDs and imported no personal app data.

The selection requires exactly 22 executed tests with zero failures, errors or skips: all supported Room upgrade edges; MediaStore valid/invalid publication and repeat receipt; real SAF picker grant, recovery and byte equality; bundled Python/yt-dlp execution; native FFmpeg merge; Media3 trim/audio and photo slideshow with music; manual paste/input; library metadata, favourites, collections, player and deletion; actual `ACTION_SEND` Saved/invalid/fresh queue handling; stale completion notification recovery after a missing save is requeued with charging blocked; charging constraint; invalid floating clipboard and overlay resize/drag/hide. Fixtures are synthetic and isolated. SAF obtains a real persistable grant through the default API 35 system picker and restores download preferences after publication. Instrumentation must complete successfully; a passing case in an interrupted runner is not acceptance.

LiveSource/PhotoPublication and supplied live-photo checks are opt-in and excluded. A default-returning or skipped live test is never counted as provider validation. Physical Samsung/TikTok accessibility semantics, live provider extraction and interrupted background/process lifecycle remain separate checks. iOS remains paused.

Emulator setup follows the upstream [Android Emulator Runner documentation](https://github.com/ReactiveCircus/android-emulator-runner/blob/main/README.md). Local fresh-AVD options follow [Android emulator command-line documentation](https://developer.android.com/studio/run/emulator-commandline).
