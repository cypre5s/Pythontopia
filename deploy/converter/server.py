"""Private single-job presentation converter; no arbitrary paths, URLs or commands."""

import hmac
import os
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

TOKEN = os.environ.get("REBORN_CONVERTER_TOKEN", "")
if len(TOKEN) < 24:
    raise RuntimeError("Set a private REBORN_CONVERTER_TOKEN of at least 24 characters")
MAX_BYTES = 128 * 1024 * 1024
SLOT = threading.BoundedSemaphore(1)


class Handler(BaseHTTPRequestHandler):
    server_version = "RebornConverter/1"

    def log_message(self, *_):
        pass  # No tokens, filenames, document contents or request headers in logs.

    def reply(self, code, body, mime="text/plain; charset=utf-8"):
        self.send_response(code)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path != "/health":
            return self.reply(404, b"not found")
        self.reply(200, b"ok")

    def do_POST(self):
        if self.path != "/convert":
            return self.reply(404, b"not found")
        if not hmac.compare_digest(self.headers.get("X-Convert-Token", ""), TOKEN):
            return self.reply(401, b"unauthorized")
        suffix = self.headers.get("X-Source-Type", "")
        try:
            size = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self.reply(400, b"invalid length")
        if suffix not in {"ppt", "pptx"} or not 0 < size <= MAX_BYTES:
            return self.reply(400, b"invalid type or length")
        if not SLOT.acquire(blocking=False):
            return self.reply(429, b"converter busy")
        try:
            self.connection.settimeout(15)
            with tempfile.TemporaryDirectory(prefix="presentation-", dir="/work") as directory:
                job = Path(directory)
                source = job / ("source." + suffix)
                remaining = size
                with source.open("wb") as output:
                    while remaining:
                        chunk = self.rfile.read(min(65536, remaining))
                        if not chunk:
                            return self.reply(400, b"incomplete file")
                        output.write(chunk)
                        remaining -= len(chunk)
                profile = job / "profile/user"
                profile.mkdir(parents=True)
                (profile / "registrymodifications.xcu").write_text(
                    '<?xml version="1.0" encoding="UTF-8"?><oor:items xmlns:oor="http://openoffice.org/2001/registry"><item oor:path="/org.openoffice.Office.Common/Security/Scripting"><prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop></item></oor:items>'
                )
                command = [
                    "libreoffice",
                    "-env:UserInstallation=" + (job / "profile").as_uri(),
                    "--headless",
                    "--invisible",
                    "--nodefault",
                    "--norestore",
                    "--nolockcheck",
                    "--nofirststartwizard",
                    "--convert-to",
                    "pdf",
                    "--outdir",
                    str(job),
                    str(source),
                ]
                try:
                    subprocess.run(
                        command,
                        check=True,
                        timeout=60,
                        capture_output=True,
                        env={
                            "HOME": directory,
                            "TMPDIR": directory,
                            "PATH": "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                            "LANG": "C.UTF-8",
                        },
                    )
                except (OSError, subprocess.SubprocessError):
                    return self.reply(422, b"conversion failed or timed out")
                pdf = job / "source.pdf"
                if not pdf.is_file() or not 0 < pdf.stat().st_size <= MAX_BYTES:
                    return self.reply(422, b"no valid PDF produced")
                self.reply(200, pdf.read_bytes(), "application/pdf")
        except (OSError, TimeoutError):
            self.reply(408, b"upload timed out")
        finally:
            SLOT.release()


ThreadingHTTPServer(("0.0.0.0", 8090), Handler).serve_forever()
