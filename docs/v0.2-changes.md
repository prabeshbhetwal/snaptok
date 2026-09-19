# SnapTok 0.2.0

## What changed

- Tap the underlined source URL in history to open the original post in its app or browser.
- Tap the paste icon at the right of the link field. The clipboard is read only on that explicit tap. The field clears after a valid request is durably accepted; invalid text stays available for correction.
- Tap Quality beside a manual link to check the actual downloadable MP4 resolutions. Portrait videos use the shorter dimension, so 1080×1920 is shown as 1080p. No upscaling is performed.
- Choose a default quality in Settings for one-tap shares. A requested cap prefers the best available resolution at or below it, falling back to the smallest available if no format fits. History labels this as the requested quality, not a claim about the final file's exact resolution.
- Choose a destination folder through Android's folder picker, from either the download form or Settings. The choice persists across restarts and applies to new requests. Existing queued jobs and retries keep their original quality and folder. Movies/SnapTok remains the default gallery destination.
- Progress notifications distinguish transfer percentage from final saving/verification. Successful publication produces a Video downloaded notification with a Play action. Failed jobs produce a failure notification. Brief outcome messages accompany enabled outcome alerts; the OS may rate-limit these messages.
- Settings controls optional notifications, percentage progress, completed-download alerts and failure alerts. Android's required foreground-service indicator remains. In-app history and status messages remain available with optional system alerts off.
- Public X/Twitter video-post links are accepted alongside TikTok. Login-required/private posts are unsupported. For multi-video X posts, the specific /video/2 suffix is retained; otherwise the first video is selected. Generic t.co short links are not accepted.

## How downloads work

The app runs yt-dlp on the phone. It obtains the source's media URLs, selects a supported MP4, downloads to private staging, validates the video, then publishes it. It does not use SnapTik's website, a paid downloader API or an app backend. Media with an audio stream is preferred; a genuinely silent source can still be downloaded. Separate video/audio streams are not merged in this build.

## Folder reliability

The default MediaStore destination uses pending entries. A chosen Storage Access Framework folder uses a hidden temporary file, SHA-256 read-back verification and rename to the final filename. Providers must support writing, reading and renaming. Revoked access or unsuitable providers produce an actionable error rather than silently falling back to a different folder. A cloud provider selected by the user may transfer the file to that provider; arbitrary folders are not guaranteed to appear in Gallery, but the app can open the saved file through its URI.

Folder grants are retained while existing jobs may reference them. Selecting another folder does not revoke access needed by older queued requests. Choosing a new quality or destination permits another copy of a previously saved source; identical URL/quality/destination requests are deduplicated.

## Upgrade

Install v0.2.0 over v0.1.0 using the same signing key. Room migrates history from schema 1 to schema 2, preserving saved media URIs. Existing rows keep Best available and Movies/SnapTok defaults. Do not uninstall or clear app data to upgrade.
