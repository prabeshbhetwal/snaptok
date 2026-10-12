#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
: "${ANDROID_SERIAL:?Select a disposable emulator explicitly; instrumentation replaces the installed app.}"
snaptok_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$PWD/work/toolchain/android-sdk}}"
snaptok_adb="$snaptok_sdk/platform-tools/adb"
test "$("$snaptok_adb" -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" = 1 || {
    echo 'Offline integration tests require a disposable emulator.' >&2
    exit 1
}
snaptok_classes='app.snaptok.data.MigrationTest,app.snaptok.data.UpgradeTest,app.snaptok.download.GalleryPublisherTest,app.snaptok.download.NativeEngineTest,app.snaptok.download.MediaPipelineTest,app.snaptok.ui.ManualInputTest,app.snaptok.ui.SavedMediaTest,app.snaptok.ui.LibraryFeatureTest,app.snaptok.download.PhotoPipelineTest#slideshowPreservesImagesAndLoopsSoundtrack,app.snaptok.download.CustomFolderTest,app.snaptok.ui.ShareActivityTest,app.snaptok.download.ConstraintTest,app.snaptok.overlay.FloatingSaveTest#invalidClipboardReturnsToPreviousScreenWithoutAJob,app.snaptok.overlay.FloatingSaveTest#sizeDragAndLongPressHideWork'
sh scripts/build-local.sh :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$snaptok_classes" \
    --no-daemon --no-parallel --max-workers=1 \
    -Dorg.gradle.jvmargs='-Xmx1g -XX:+UseSerialGC' \
    -Pkotlin.compiler.execution.strategy=in-process
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

build = Path('work/build-directory.txt').read_text().strip()
cases = [case for path in Path(build, 'outputs/androidTest-results/connected').rglob('*.xml')
         for case in ET.parse(path).getroot().iter('testcase')]
assert len(cases) == 21, f'Expected 21 native tests, found {len(cases)}'
assert all(not any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped'))
           for case in cases), 'Native tests failed, errored or skipped'
print('Offline Android integration: 21 passed; 0 failures/errors/skips')
PY
