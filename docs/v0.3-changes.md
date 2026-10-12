# SnapTok 0.3.0

## Changes

- Compact home screen with an expandable sharing guide.
- Search download titles, source URLs and platform names. All search words must match; search ignores case.
- Failed filter alongside All, Active and Saved.
- Platform labels and thumbnails generated from local saved videos.
- Actual saved dimensions, duration and file size, read from content URIs in the background. Existing records gain these details without a database migration. Metadata is best-effort when a file or folder is unavailable.
- Share a saved MP4 through Android's share sheet with temporary read permission.
- Remove completed, failed or cancelled history entries after confirmation. Saved files remain untouched; active jobs cannot be removed.
- Failed jobs can open settings or retry using the current save location while preserving the requested quality.
- Public Instagram Reel/post links accepted as a beta. Equivalent Reel/post links deduplicate and tracking parameters are removed. Profiles, Stories, private/login-required content and arbitrary URLs are excluded. Multi-video posts save the first video.
- Silent saved files display No audio track. If a source reports an audio format but the selected file contains no audio, saving stops with an explicit unsupported-merging message.
- Rate limits and access blocks stop automatic retries and explain the source restriction instead of recommending an engine update.

## Boundaries

No YouTube, Facebook or Reddit support is enabled in this release. No accounts, cookies, remote thumbnail requests, new permissions or new dependencies were added. File deletion, creator metadata, pause/resume, theme selection, Wi-Fi-only mode and bulk downloads remain future work. The APK retains the existing local test certificate and disables debugging in the preview variant; this does not resolve Google's Play Protect assessment or make it a public-store release.
