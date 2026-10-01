#!/usr/bin/env python3
"""Package an already-built native Mac design preview. Does not simulate iOS behaviour."""
from pathlib import Path
import plistlib
import shutil
import sys

binary = Path(sys.argv[1]).resolve()
destination = Path(sys.argv[2]).resolve()
contents = destination / "Contents"
(contents / "MacOS").mkdir(parents=True, exist_ok=True)
shutil.copy2(binary, contents / "MacOS" / "SnapTokPreview")
with (contents / "Info.plist").open("wb") as file:
    plistlib.dump({"CFBundleIdentifier": "app.snaptok.designpreview", "CFBundleName": "SnapTok Preview",
                  "CFBundleExecutable": "SnapTokPreview", "CFBundlePackageType": "APPL",
                  "CFBundleShortVersionString": "0.1.0", "CFBundleVersion": "1",
                  "NSHighResolutionCapable": True, "LSMinimumSystemVersion": "15.0",
                  "NSAppTransportSecurity": {"NSAllowsLocalNetworking": True}}, file)
print(destination)
