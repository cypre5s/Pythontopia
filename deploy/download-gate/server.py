#!/usr/bin/env python3
"""Pythontopia 客户端下载验证码门控（ECS 主机侧，stdlib only）。

- GET  /captcha?file=<name>  → 算术验证码
- POST /verify               → 校验通过后返回一次性下载 token
- GET  /deliver?token=<tok>  → 校验 token 并直出安装包
"""
from __future__ import annotations

import hashlib
import hmac
import json
import mimetypes
import os
import random
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import parse_qs, urlparse

DOWNLOAD_DIR = os.environ.get("PYT_DOWNLOAD_DIR", "/opt/pythontopia/downloads")
ALLOWED_FILES: dict[str, dict[str, str]] = {
    "pythontopia.apk": {
        "label": "Android App（APK）",
        "stat_key": "android_apk",
    },
    "pythontopia-windows.exe": {
        "label": "Windows 安装版（EXE）",
        "stat_key": "windows_exe",
    },
    "pythontopia-windows.zip": {
        "label": "Windows 免装版（ZIP）",
        "stat_key": "windows_zip",
    },
}

CAPTCHA_TTL_SEC = 300
TOKEN_TTL_SEC = 120
CAPTCHA_STORE: dict[str, dict[str, Any]] = {}
TOKEN_STORE: dict[str, dict[str, Any]] = {}
STORE_LOCK = threading.Lock()


def load_secret() -> str:
    secret = os.environ.get("PYT_DOWNLOAD_SIGN_SECRET", "").strip()
    if not secret:
        path = os.environ.get(
            "PYT_DOWNLOAD_SIGN_SECRET_FILE",
            "/opt/pythontopia/shared/download-sign.secret",
        )
        if os.path.isfile(path):
            secret = open(path, encoding="utf-8").read().strip()
    if not secret:
        raise RuntimeError("missing PYT_DOWNLOAD_SIGN_SECRET or secret file")
    return secret


def prune_store(now: float) -> None:
    for store in (CAPTCHA_STORE, TOKEN_STORE):
        expired = [k for k, v in store.items() if v["expires_at"] <= now]
        for key in expired:
            store.pop(key, None)


def _make_choices(correct: int) -> list[str]:
    options: set[int] = {correct}
    while len(options) < 4:
        offset = random.randint(-2, 2)
        if offset == 0:
            offset = random.choice([-1, 1])
        candidate = correct + offset
        if candidate > 0:
            options.add(candidate)
    result = [str(v) for v in options]
    random.shuffle(result)
    return result


def new_captcha(file_name: str) -> dict[str, Any]:
    if file_name not in ALLOWED_FILES:
        raise ValueError("invalid file")
    a = random.randint(1, 5)
    b = random.randint(1, 5)
    correct = a + b
    captcha_id = uuid.uuid4().hex
    now = time.time()
    with STORE_LOCK:
        prune_store(now)
        CAPTCHA_STORE[captcha_id] = {
            "answer": str(correct),
            "file": file_name,
            "expires_at": now + CAPTCHA_TTL_SEC,
        }
    return {
        "id": captcha_id,
        "question": f"{a} + {b} = ?",
        "choices": _make_choices(correct),
        "file": file_name,
        "label": ALLOWED_FILES[file_name]["label"],
    }


def sign_token(token: str, file_name: str, expires_at: float) -> str:
    secret = load_secret().encode()
    payload = f"{token}:{file_name}:{int(expires_at)}".encode()
    return hmac.new(secret, payload, hashlib.sha256).hexdigest()


def verify_captcha(captcha_id: str, answer: str) -> dict[str, str]:
    now = time.time()
    with STORE_LOCK:
        prune_store(now)
        entry = CAPTCHA_STORE.pop(captcha_id, None)
    if entry is None:
        raise ValueError("验证码已过期，请刷新")
    if str(answer).strip() != entry["answer"]:
        raise ValueError("验证码错误")
    file_name = entry["file"]
    token = uuid.uuid4().hex
    expires_at = now + TOKEN_TTL_SEC
    signature = sign_token(token, file_name, expires_at)
    with STORE_LOCK:
        TOKEN_STORE[token] = {
            "file": file_name,
            "signature": signature,
            "expires_at": expires_at,
        }
    return {
        "url": f"/download-gate/deliver?token={token}&sig={signature}",
        "file": file_name,
        "stat_key": ALLOWED_FILES[file_name]["stat_key"],
    }


def consume_token(token: str, signature: str) -> str:
    now = time.time()
    with STORE_LOCK:
        prune_store(now)
        entry = TOKEN_STORE.pop(token, None)
    if entry is None:
        raise ValueError("下载链接已失效，请重新验证")
    if entry["expires_at"] <= now:
        raise ValueError("下载链接已过期，请重新验证")
    if not hmac.compare_digest(entry["signature"], signature):
        raise ValueError("下载链接无效")
    return entry["file"]


def read_json(handler: BaseHTTPRequestHandler) -> dict[str, Any]:
    length = int(handler.headers.get("Content-Length", "0") or "0")
    if length <= 0:
        return {}
    body = handler.rfile.read(length)
    if not body:
        return {}
    return json.loads(body.decode("utf-8"))


class GateHandler(BaseHTTPRequestHandler):
    server_version = "PythontopiaDownloadGate/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        return

    def _send_json(self, status: int, payload: dict[str, Any]) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _send_file(self, file_name: str) -> None:
        path = os.path.join(DOWNLOAD_DIR, file_name)
        if not os.path.isfile(path):
            self._send_json(404, {"error": "安装包不存在"})
            return
        mime, _ = mimetypes.guess_type(path)
        if mime is None:
            mime = "application/octet-stream"
        size = os.path.getsize(path)
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(size))
        self.send_header("Content-Disposition", f'attachment; filename="{file_name}"')
        self.end_headers()
        with open(path, "rb") as handle:
            while True:
                chunk = handle.read(1024 * 1024)
                if not chunk:
                    break
                self.wfile.write(chunk)

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/captcha":
            params = parse_qs(parsed.query)
            file_name = (params.get("file") or [""])[0]
            try:
                payload = new_captcha(file_name)
                self._send_json(200, payload)
            except ValueError as exc:
                self._send_json(400, {"error": str(exc)})
            return
        if parsed.path == "/deliver":
            params = parse_qs(parsed.query)
            token = (params.get("token") or [""])[0]
            signature = (params.get("sig") or [""])[0]
            try:
                file_name = consume_token(token, signature)
                self._send_file(file_name)
            except ValueError as exc:
                self._send_json(403, {"error": str(exc)})
            return
        self._send_json(404, {"error": "not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path != "/verify":
            self._send_json(404, {"error": "not found"})
            return
        try:
            body = read_json(self)
            payload = verify_captcha(str(body.get("id", "")), str(body.get("answer", "")))
            self._send_json(200, payload)
        except ValueError as exc:
            self._send_json(400, {"error": str(exc)})
        except Exception:
            self._send_json(500, {"error": "服务暂时不可用，请稍后重试"})


def main() -> None:
    host = os.environ.get("PYT_DOWNLOAD_GATE_HOST", "127.0.0.1")
    port = int(os.environ.get("PYT_DOWNLOAD_GATE_PORT", "9191"))
    load_secret()
    server = ThreadingHTTPServer((host, port), GateHandler)
    server.serve_forever()


if __name__ == "__main__":
    main()
