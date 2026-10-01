#!/usr/bin/env python3
"""Single-user SnapTok bridge. Private by default; no hosted service or account required."""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
import hmac
import importlib.util
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import secrets
import signal
import shutil
import subprocess
import sys
import threading
import time
from urllib.parse import parse_qs, urlsplit, quote
import uuid


def validate_source(value: str) -> str:
    if not isinstance(value, str) or len(value) > 4096:
        raise ValueError("Invalid video link")
    parsed = urlsplit(value)
    if parsed.scheme != "https" or parsed.username or parsed.password or parsed.port:
        raise ValueError("Use a public HTTPS video-post link")
    host, path = parsed.hostname, parsed.path
    patterns = {
        "tiktok.com": r"/(?:@[^/]+/video/[0-9]{6,}|t/[A-Za-z0-9]+)/?",
        "vm.tiktok.com": r"/[A-Za-z0-9]+/?",
        "vt.tiktok.com": r"/[A-Za-z0-9]+/?",
        "instagram.com": r"/(?:p|reel|reels)/[A-Za-z0-9_-]+/?",
        "x.com": r"/(?:[A-Za-z0-9_]+/status|i/status|i/web/status)/[0-9]{6,}(?:/video/[1-9][0-9]*)?/?",
        "reddit.com": r"/(?:(?:r|user)/[^/]+/)?comments/[a-z0-9]+(?:/[^/]*)?/?",
        "v.redd.it": r"/[a-z0-9]+/?",
        "fb.watch": r"/[A-Za-z0-9_-]+/?",
        "facebook.com": r"/(?:reel/[0-9]+|[^/]+/videos/[0-9]+|share/[vr]/[A-Za-z0-9]+)/?",
    }
    aliases = {
        "www.tiktok.com": "tiktok.com", "m.tiktok.com": "tiktok.com",
        "www.instagram.com": "instagram.com", "m.instagram.com": "instagram.com",
        "twitter.com": "x.com", "www.twitter.com": "x.com", "www.x.com": "x.com",
        "mobile.x.com": "x.com", "mobile.twitter.com": "x.com",
        "www.reddit.com": "reddit.com", "old.reddit.com": "reddit.com",
        "www.facebook.com": "facebook.com", "m.facebook.com": "facebook.com",
    }
    normal = aliases.get(host, host)
    if host in ("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"):
        video_id = path.strip("/") if host == "youtu.be" else (
            parse_qs(parsed.query).get("v", [""])[0] if path == "/watch" else path.rstrip("/").split("/")[-1]
        )
        route_ok = host == "youtu.be" or path == "/watch" or re.fullmatch(r"/(?:shorts|embed)/[\w-]+/?", path)
        if route_ok and re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id):
            return value
    if normal == "facebook.com" and path in ("/watch", "/watch/", "/video.php"):
        if re.fullmatch(r"[0-9]+", parse_qs(parsed.query).get("v", [""])[0]):
            return value
    if normal in patterns and re.fullmatch(patterns[normal], path):
        return value
    raise ValueError("This is not a supported public video-post link")


def validate_options(payload: dict) -> tuple[str, int, int]:
    url = validate_source(payload.get("url", ""))
    quality, maximum = payload.get("quality", 0), payload.get("max_bytes", 1024**3)
    if type(quality) is not int or quality not in (0, 480, 720, 1080):
        raise ValueError("Invalid quality")
    if type(maximum) is not int or not 1024**2 <= maximum <= 2 * 1024**3:
        raise ValueError("Invalid size limit")
    return url, quality, maximum


@dataclass
class Job:
    id: str
    directory: Path
    created: float = field(default_factory=time.monotonic)
    ready: threading.Event = field(default_factory=threading.Event)
    error: str | None = None
    title: str = "Video"
    size: int = 0


class Bridge:
    def __init__(self, root: Path, token: str):
        if len(token) < 24:
            raise ValueError("Use a connection key of at least 24 characters")
        self.root, self.token = root, token
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.lock = threading.Lock()
        self.jobs: dict[str, Job] = {}
        self.executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="snaptok")
        # Only expire our UUID-named cache directories; never traverse arbitrary paths.
        for path in self.root.iterdir():
            if path.is_dir() and re.fullmatch(r"[a-f0-9-]{36}", path.name) and time.time() - path.stat().st_mtime > 3600:
                shutil.rmtree(path, ignore_errors=True)

    def create(self, payload: dict) -> Job:
        url, quality, maximum = validate_options(payload)
        with self.lock:
            self._expire()
            if len(self.jobs) >= 32 or sum(not job.ready.is_set() for job in self.jobs.values()) >= 8:
                raise OverflowError("The bridge is busy. Try again shortly.")
            identity = str(uuid.uuid4())
            job = Job(identity, self.root / identity)
            job.directory.mkdir(mode=0o700)
            self.jobs[identity] = job
        self.executor.submit(self._download, job, url, quality, maximum)
        return job

    def get(self, identity: str) -> Job | None:
        with self.lock:
            return self.jobs.get(identity)

    def _expire(self):
        for identity, job in list(self.jobs.items()):
            if job.ready.is_set() and time.monotonic() - job.created > 3600:
                shutil.rmtree(job.directory, ignore_errors=True)
                del self.jobs[identity]

    def _download(self, job: Job, url: str, quality: int, maximum: int):
        limit = f"[height<={quality}]" if quality else ""
        selector = f"bestvideo[ext=mp4]{limit}+bestaudio[ext=m4a]/best[ext=mp4]{limit}/worst[ext=mp4]"
        command = [sys.executable, "-m", "yt_dlp", "--ignore-config", "--no-playlist", "--playlist-items", "1",
                   "--match-filter", "!is_live", "--no-progress", "--no-warnings", "--socket-timeout", "20",
                   "--retries", "1", "--fragment-retries", "1", "--max-filesize", str(maximum),
                   "--format", selector, "--merge-output-format", "mp4", "--write-info-json",
                   "--no-write-playlist-metafiles", "--no-mtime", "--no-cache-dir",
                   "--output", str(job.directory / "video.%(ext)s"), "--", url]
        try:
            # Run without a shell. A private per-job log is never returned through the API.
            with (job.directory / "extract.log").open("wb") as log:
                process = subprocess.Popen(command, stdout=log, stderr=log, start_new_session=True)
                try:
                    returncode = process.wait(timeout=180)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                    raise
            video = job.directory / "video.mp4"
            if returncode or not video.is_file():
                raise ValueError("The source could not provide a public downloadable video.")
            job.size = video.stat().st_size
            if not 0 < job.size <= maximum:
                video.unlink(missing_ok=True)
                raise ValueError("The video exceeds the chosen size limit.")
            metadata_file = job.directory / "video.info.json"
            if metadata_file.is_file():
                metadata = json.loads(metadata_file.read_text())
                job.title = str(metadata.get("title") or "Video")[:160]
        except subprocess.TimeoutExpired:
            job.error = "The source took too long to prepare this video."
        except Exception as error:
            job.error = str(error) if isinstance(error, ValueError) else "The bridge could not prepare this video."
        finally:
            job.ready.set()


class Server(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, bridge: Bridge):
        self.bridge = bridge
        super().__init__(address, Handler)


class Handler(BaseHTTPRequestHandler):
    server: Server
    protocol_version = "HTTP/1.1"
    def setup(self):
        super().setup()
        self.connection.settimeout(240)
    def log_message(self, format, *args):
        pass  # Do not log URLs, credentials or viewing history.
    def authorised(self) -> bool:
        actual = self.headers.get("Authorization", "")
        if hmac.compare_digest(actual.encode(), ("Bearer " + self.server.bridge.token).encode()):
            return True
        self.reply(401, {"error": "Connection key required"})
        return False
    def reply(self, status: int, value: dict):
        data = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        self.wfile.write(data)
    def do_POST(self):
        if not self.authorised(): return
        if self.path != "/v1/jobs": return self.reply(404, {"error": "Not found"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 16_384: return self.reply(413, {"error": "Invalid request size"})
            payload = json.loads(self.rfile.read(length))
            if not isinstance(payload, dict): raise ValueError("Invalid request")
            job = self.server.bridge.create(payload)
            self.reply(201, {"id": job.id})
        except (ValueError, TypeError): self.reply(400, {"error": "Use a supported public video link and valid download settings"})
        except OverflowError: self.reply(429, {"error": "The bridge is busy. Try again shortly."})
    def do_GET(self):
        if not self.authorised(): return
        if self.path == "/v1/health": return self.reply(200, {"service": "SnapTok", "version": 1})
        match = re.fullmatch(r"/v1/(jobs|media)/([a-f0-9-]{36})", self.path)
        if not match: return self.reply(404, {"error": "Not found"})
        job = self.server.bridge.get(match[2])
        if job is None: return self.reply(404, {"error": "This download has expired. Start it again."})
        if match[1] == "jobs":
            return self.reply(200, {"state": "failed" if job.error else "ready" if job.ready.is_set() else "preparing",
                                    "title": job.title, "error": job.error, "size": job.size})
        if not job.ready.wait(210): return self.reply(504, {"error": "Preparation timed out"})
        if job.error: return self.reply(422, {"error": job.error})
        try:
            # Hold an open descriptor so expiry cannot interrupt an established transfer.
            with (job.directory / "video.mp4").open("rb") as file:
                self.send_response(200)
                self.send_header("Content-Type", "video/mp4")
                self.send_header("Content-Length", str(job.size))
                self.send_header("Content-Disposition", f'attachment; filename="{job.id}.mp4"')
                self.send_header("X-SnapTok-Title", quote(job.title, safe=""))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                shutil.copyfileobj(file, self.wfile, length=256 * 1024)
        except FileNotFoundError: self.reply(404, {"error": "The file expired. Start the download again."})
        except (BrokenPipeError, ConnectionResetError): pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1", help="Use 0.0.0.0 only when intentionally allowing devices on your private network")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--data", type=Path, default=Path(__file__).parent / ".bridge-data")
    args = parser.parse_args()
    if importlib.util.find_spec("yt_dlp") is None or shutil.which("ffmpeg") is None:
        parser.error("Install the pinned yt-dlp requirements and FFmpeg before starting the bridge.")
    args.data.mkdir(parents=True, exist_ok=True, mode=0o700)
    token_file = args.data / "connection.token"
    if not token_file.exists():
        with open(token_file, "x", opener=lambda path, flags: os.open(path, flags, 0o600)) as file:
            file.write(secrets.token_urlsafe(32))
    bridge = Bridge(args.data / "videos", token_file.read_text().strip())
    server = Server((args.host, args.port), bridge)
    print(f"SnapTok bridge listening on {args.host}:{args.port}", flush=True)
    print(f"Connection key is stored privately in {token_file}. Keep this file private.", flush=True)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally:
        server.server_close(); bridge.executor.shutdown(wait=True, cancel_futures=True)


if __name__ == "__main__": main()
