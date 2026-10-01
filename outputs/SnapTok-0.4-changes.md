# SnapTok 0.4 — Library and media upgrade

## Downloading

- Native FFmpeg support combines separately supplied video and audio into MP4.
- Audio-only download mode produces M4A when a source exposes audio.
- Multi-video inspection offers individual item selection or Select all, capped at 50 items per post.
- Batch input accepts up to 50 supported links, validates the set before scheduling and deduplicates equivalent URLs.
- Network preferences support any connection or unmetered connections; charging-only and maximum output size are saved with each request.
- Platform-specific quality/folder presets are optional. Global options remain the fallback.
- Saved requests check file availability before declaring Already saved. Source metadata identities prevent a second published copy from known link aliases with matching quality/output type/destination. Alias resolution occurs after extraction, so a redundant transfer may still happen.
- YouTube, Facebook and Reddit post routes are accepted as beta. Authentication, private content, DRM and platform access restrictions are not bypassed.

## Library and playback

- Separate Save, Library and Settings views, with restrained transitions.
- Search titles, URLs, platform names, creators and saved captions.
- Favourites and named collections; list/grid layouts; newest/oldest/title sorting.
- Integrated playback with seeking, speed controls and saved resume position.
- Trim or export audio from a saved file into a new file; originals remain untouched. Keep SnapTok open during an export.
- Explicit, confirmed Delete file is separate from Remove history. Deleting a file also removes aliases pointing to the same URI.
- Storage view reports accessible saved media, temporary files and available phone storage. Cleanup excludes active jobs.

## Floating control

- Progress ring represents one measured active transfer; count badge represents all active requests.
- Brief result badge for queued/saved/attention states; no continuous clipboard monitoring.
- Optional fade while idle, tap haptics, hold-to-open action menu and restore notification.
- Existing size/opacity/position settings remain.

## Appearance and accessibility

- System, light and dark themes; reduced-motion preference.
- Named navigation, meaningful control labels and minimum touch sizes.
- Text and icons accompany colour-based status.

## Upgrade and boundaries

Schema migration 2→3 adds safe defaults without destructive recreation. Previous download history, source links and folder permissions are retained. Existing test signing identity remains; no store submission or Play Protect approval is implied. Native media components increase package size.

Source access remains dependent on each platform. New-platform acceptance and native component availability are separate from successful live extraction; see the validation report for actual results.

## Final interaction refinements

- Download and destination controls remain visible while Save options scroll.
- Entered links, library filters and scroll state survive switching tabs. Successful acceptance clears the entered link.
- Optional library filters open on demand. Thumbnails reserve space before file details arrive.
- Large text uses wrapping controls and a compact Save layout; long folder names use an ellipsis.
- Repeated completion messages replace the current brief message instead of building a long on-screen backlog.
- Progress/failure notifications open the library; completion opens the integrated player. Android notification settings remain reachable after permission denial.
- A new request after a failure can use changed network/size preferences. Explicit Retry retains the original job; Retry with current defaults is a separate action.
