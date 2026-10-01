# Private download bridge

Optional companion for the first iOS build. Direct HTTPS MP4/MOV/M4V links download on the phone without it. Social links are prepared here with yt-dlp/FFmpeg, then transferred as a single video by iOS's background downloader. This is not on-device social extraction, a hosted service, or App Store authorisation.

Requires Python 3.11+ and FFmpeg on PATH. Setup:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python server.py
```

The default address accepts connections only from this Mac. To deliberately allow your iPhone on the same private Wi-Fi network, run `server.py --host 0.0.0.0`, use `http://YOUR-MAC.local:8765` in SnapTok's connection settings, and copy the generated `.bridge-data/connection.token` into the Connection key field. Find the Mac's local hostname in System Settings > General > Sharing. Allow Local Network access when iOS asks. Do not use a bare LAN IP with HTTP; the app deliberately restricts cleartext connections to local hostnames.

For remote access, put the bridge behind a properly configured HTTPS reverse proxy. Do not expose this development HTTP server directly to the internet. A server is not required for the library, player, Photos/Files exports, or direct video links.

Every endpoint requires a bearer token. Sources are restricted to recognised public video-post routes. No login cookies or account scraping. Two extraction workers, eight unfinished requests, 32 retained jobs, three-minute extraction timeout and maximum 2 GB output. Temporary media and private extraction logs expire after one hour when another request is created; shutdown/restart cleanup is described in the release notes. Stop the process when not in use. The Mac must stay awake and reachable while it prepares and serves a video.

App requests: `POST /v1/jobs` with `url`, `quality` (0/480/720/1080), `max_bytes`. Response: opaque job UUID. `GET /v1/media/{id}` waits for preparation, then serves MP4. `GET /v1/jobs/{id}` gives status; `GET /v1/health` checks connectivity. No API sends viewing-history logs or tokens back to the caller.

Source changes can break extraction. Update the pinned yt-dlp version only after verification. FFmpeg and yt-dlp remain separate tools on your own machine; no Android binaries are embedded in the iOS application.
