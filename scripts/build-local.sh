#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -d "$PWD/work/toolchain/jdk-17.0.20.1+1/Contents/Home" ]; then
    export JAVA_HOME="$PWD/work/toolchain/jdk-17.0.20.1+1/Contents/Home"
fi
export GRADLE_USER_HOME="$PWD/work/gradle-home"
snaptok_build_tag=$(printf '%s' "$PWD" | cksum | cut -d ' ' -f 1)
export SNAPTOK_BUILD_DIR="${TMPDIR:-/tmp}/snaptok-build-$snaptok_build_tag"
mkdir -p "$PWD/work"
printf '%s\n' "$SNAPTOK_BUILD_DIR" > "$PWD/work/build-directory.txt"
if [ "$#" -eq 0 ]; then set -- :app:testDebugUnitTest :app:lintDebug :app:assembleDebug; fi
if [ -x "$PWD/work/toolchain/gradle-8.11.1/bin/gradle" ]; then
    exec "$PWD/work/toolchain/gradle-8.11.1/bin/gradle" "$@"
fi
exec ./gradlew "$@"
