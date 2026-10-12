# Photo-post downloads

1. Detect TikTok photo posts from public page metadata; preserve ordered original image URLs and optional music. Validate source identity, URLs, count and byte limits.
2. Add saved output preference: images / slideshow / both; default images. Shared links use snapshot without another prompt. Slideshow: 2–10 seconds per image, fit without cropping, optional original soundtrack, explicit missing-audio error.
3. Publish ordered image outputs plus optional MP4. Completion only after every requested output saved. Retry reuses stable per-output names; history lists all outputs and distinguishes images from videos.
4. Android: native extraction/download, Media3 slideshow composition, Room migration and MediaStore/SAF support. iOS: bridge prepares a manifest/assets bundle with FFmpeg rendering; the native background downloader verifies and publishes every bundled file and previews images.
5. Verify fixture cases, database/backward compatibility, actual provided TikTok post and both builds. Preserve prior Android/iOS behaviour.

Source sample: https://vt.tiktok.com/ZSbQFueH8/ → TikTok photo post 7676012424734051606.

Timing: photo posts have user-controlled swiping, so slideshow timing is chosen by the user. Generated MP4 is labelled a slideshow. Music is never silently omitted when requested. No interpolation/upscaling claim; no profile, live-stream, private-account or login bypass support.
