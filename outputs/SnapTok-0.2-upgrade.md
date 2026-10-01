# Upgrade to SnapTok 0.2.0

Install **SnapTok-0.2.0-debug.apk** over your current app. The signing certificate matches v0.1.0, and download history is migrated. **Do not uninstall or clear app data.**

- Tap any underlined source link in history to open the original post.
- Use the paste icon at the right of the link field. Accepted downloads clear the input; invalid links stay editable.
- Enter a link and tap **Quality** to check its available MP4 resolutions. In **Settings**, choose a default quality for one-tap shares.
- Tap **Save to** or **Settings > Choose folder** to select the destination. The choice persists. Existing queued jobs keep their earlier settings.
- Under **Settings > Download notifications**, choose progress, completion and failure alerts. Allow Android notification permission too. Android may still show its required ongoing-download indicator when optional alerts are off.
- TikTok and public X/Twitter video-post links are supported. No account credentials are used. Private/login-required posts, live streams and slideshows remain unsupported; use a full X post URL rather than a generic t.co link.

Downloads use yt-dlp directly on the phone. SnapTik's website is not involved. Source changes may still require **Settings > Update engine**.

Verified: 35 unit tests, 7 Android emulator tests, live TikTok and X downloads, custom-folder saving, and an actual 540p quality selection. New-build behaviour under Samsung battery management still needs your normal phone testing.
