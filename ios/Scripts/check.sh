#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -d /Applications/Xcode.app/Contents/Developer ]; then
    export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
fi
swift test
python3 -m unittest discover -s Tests/BridgeTests -v
xcodebuild -project SnapTok.xcodeproj -scheme SnapTok -configuration Debug \
    -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath DerivedData CODE_SIGNING_ALLOWED=NO build
