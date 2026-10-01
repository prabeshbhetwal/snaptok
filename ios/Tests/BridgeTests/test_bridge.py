import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

module_path = Path(__file__).resolve().parents[2] / "Bridge" / "server.py"
spec = importlib.util.spec_from_file_location("snaptok_bridge", module_path)
bridge_module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = bridge_module
spec.loader.exec_module(bridge_module)


class SourceTests(unittest.TestCase):
    def test_known_routes(self):
        for url in ["https://www.youtube.com/watch?v=aqz-KE-bpKQ", "https://youtu.be/aqz-KE-bpKQ",
                    "https://www.tiktok.com/@user/video/123456789", "https://vm.tiktok.com/ZM123/",
                    "https://www.instagram.com/p/Chunk8-jurw/", "https://x.com/i/status/12345678/video/2",
                    "https://www.facebook.com/reel/1195289147628387", "https://www.reddit.com/comments/90bu6w/"]:
            self.assertEqual(bridge_module.validate_source(url), url)

    def test_rejects_unsupported_destinations(self):
        for url in ["http://youtube.com/watch?v=aqz-KE-bpKQ", "https://youtube.com.evil.test/watch?v=aqz-KE-bpKQ",
                    "https://user:pass@youtube.com/watch?v=aqz-KE-bpKQ", "https://127.0.0.1/private.mp4",
                    "file:///etc/passwd", "https://youtube.com/channel/aqz-KE-bpKQ", "https://instagram.com/profile/",
                    "https://www.tiktok.com/@user/live", "https://x.com:8443/i/status/1234567"]:
            with self.subTest(url=url), self.assertRaises(ValueError): bridge_module.validate_source(url)

    def test_option_limits(self):
        for change in [{"quality": 9000}, {"quality": True}, {"max_bytes": 100}, {"max_bytes": 3 * 1024**3}]:
            with self.assertRaises(ValueError): bridge_module.validate_options({"url": "https://youtu.be/aqz-KE-bpKQ", **change})


class HTTPTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.token = "test-connection-key-for-local-tests-only"
        self.bridge = bridge_module.Bridge(Path(self.temp.name), self.token)
        self.server = bridge_module.Server(("127.0.0.1", 0), self.bridge)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join()
        self.bridge.executor.shutdown(wait=True, cancel_futures=True); self.temp.cleanup()

    def request(self, path, payload=None, token=None):
        headers = {"Authorization": "Bearer " + (token if token is not None else self.token)}
        data = json.dumps(payload).encode() if payload is not None else None
        return urlopen(Request(self.url + path, data=data, headers=headers), timeout=5)

    def test_auth_required_even_for_health(self):
        with self.assertRaises(HTTPError) as result: self.request("/v1/health", token="wrong")
        self.assertEqual(result.exception.code, 401)
        with self.request("/v1/health") as response: self.assertEqual(response.status, 200)

    def test_bad_source_cannot_start_extractor(self):
        with patch.object(self.bridge, "_download") as worker:
            with self.assertRaises(HTTPError) as result: self.request("/v1/jobs", {"url": "https://127.0.0.1/a.mp4"})
            self.assertEqual(result.exception.code, 400); worker.assert_not_called()

    def test_create_prepare_transfer_contract(self):
        def prepare(job, url, quality, maximum):
            (job.directory / "video.mp4").write_bytes(b"test-media")
            job.size = 10; job.ready.set()
        with patch.object(self.bridge, "_download", side_effect=prepare):
            with self.request("/v1/jobs", {"url": "https://youtu.be/aqz-KE-bpKQ"}) as response:
                self.assertEqual(response.status, 201); identity = json.load(response)["id"]
            with self.request("/v1/media/" + identity) as response:
                self.assertEqual(response.headers["Content-Type"], "video/mp4")
                self.assertEqual(response.read(), b"test-media")
            with self.assertRaises(HTTPError) as result: self.request("/v1/media/" + identity, token="wrong")
            self.assertEqual(result.exception.code, 401)

    def test_failed_preparation_returns_error_not_media(self):
        def prepare(job, *args): job.error = "Source unavailable"; job.ready.set()
        with patch.object(self.bridge, "_download", side_effect=prepare):
            with self.request("/v1/jobs", {"url": "https://youtu.be/aqz-KE-bpKQ"}) as response: identity = json.load(response)["id"]
            with self.assertRaises(HTTPError) as result: self.request("/v1/media/" + identity)
            self.assertEqual(result.exception.code, 422)

    def test_path_traversal_is_not_a_media_route(self):
        with self.assertRaises(HTTPError) as result: self.request("/v1/media/../../etc/passwd")
        self.assertEqual(result.exception.code, 404)


if __name__ == "__main__": unittest.main()
