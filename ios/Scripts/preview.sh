#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -d /Applications/Xcode.app/Contents/Developer ]; then
    export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
    swift build --product SnapTokPreview
    bin_dir=$(swift build --show-bin-path)
else
    # CLT's macOS 27 SDK omits the SwiftUI macro plug-in. The 26 SDK can build this preview.
    sdk_path=/Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk
    swift build --product SnapTokPreview --sdk "$sdk_path"
    bin_dir=$(swift build --show-bin-path --sdk "$sdk_path")
fi
exec "$bin_dir/SnapTokPreview"
