#!/usr/bin/env python3
"""讯飞多模态凭据 / 服务开通 端到端验证脚本（独立可跑，不依赖后端栈）。

复刻后端 Java 的 HMAC-SHA256 鉴权（签名串 ``host date request-line``，与
XfyunTtsService / XfyunImageService 字节级一致），用 ``deploy/.env`` 里的
PYT_SPARK_APP_ID / PYT_SPARK_API_KEY / PYT_SPARK_API_SECRET 真连讯飞：

  - TTS（在线语音合成）: wss://tts-api.xfyun.cn/v2/tts → 合成一小段中文，报告音频字节数。
  - TTI（星火文生图）  : https://spark-api.cn-huabei-1.xf-yun.com/v2.1/tti → 报告图片字节数。

用法：
    python3 scripts/verify_xfyun.py

退出码：TTS+TTI 均成功=0；任一失败=1。失败时打印讯飞返回的 code/message，便于判断是
"凭据无效 / 服务未开通 / 欠费 / 限流 / 网络不可达"。绝不伪造结果。
"""

from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import json
import sys
import urllib.parse
import urllib.request
from email.utils import formatdate
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
ENV_PATH = REPO_ROOT / "deploy" / ".env"

TTS_HOST = "tts-api.xfyun.cn"
TTS_PATH = "/v2/tts"
TTI_URL = "https://spark-api.cn-huabei-1.xf-yun.com/v2.1/tti"


def load_env(path: Path) -> dict[str, str]:
    env: dict[str, str] = {}
    if not path.exists():
        return env
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        env[key.strip()] = value.strip()
    return env


def _gmt_date() -> str:
    # RFC1123，GMT；与 Java DateTimeFormatter "EEE, dd MMM yyyy HH:mm:ss z"(GMT) 等价。
    return formatdate(timeval=None, localtime=False, usegmt=True)


def build_signed_url(host: str, path: str, method: str, api_key: str, api_secret: str, *, scheme: str) -> str:
    date = _gmt_date()
    signature_origin = f"host: {host}\ndate: {date}\n{method} {path} HTTP/1.1"
    signature = base64.b64encode(
        hmac.new(api_secret.encode("utf-8"), signature_origin.encode("utf-8"), hashlib.sha256).digest()
    ).decode("utf-8")
    authorization_origin = (
        f'api_key="{api_key}", algorithm="hmac-sha256", '
        f'headers="host date request-line", signature="{signature}"'
    )
    authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode("utf-8")
    query = urllib.parse.urlencode({"authorization": authorization, "date": date, "host": host})
    return f"{scheme}://{host}{path}?{query}"


async def verify_tts(app_id: str, api_key: str, api_secret: str, *, voice: str = "x4_yezi") -> tuple[bool, str]:
    try:
        import websockets  # noqa: PLC0415
    except ImportError:
        return False, "缺少 websockets 库：pip install websockets"

    url = build_signed_url(TTS_HOST, TTS_PATH, "GET", api_key, api_secret, scheme="wss")
    text = "你好，欢迎来到 Python 小镇。"
    payload = {
        "common": {"app_id": app_id},
        "business": {
            "aue": "lame",
            "sfl": 1,
            "auf": "audio/L16;rate=16000",
            "vcn": voice,
            "tte": "UTF8",
        },
        "data": {
            "status": 2,
            "text": base64.b64encode(text.encode("utf-8")).decode("utf-8"),
        },
    }
    try:
        async with websockets.connect(url, max_size=None, open_timeout=15) as ws:
            await ws.send(json.dumps(payload))
            audio = bytearray()
            async for message in ws:
                resp = json.loads(message)
                code = resp.get("code", -1)
                if code != 0:
                    return False, f"讯飞 TTS code={code} message={resp.get('message')}"
                data = resp.get("data") or {}
                chunk = data.get("audio")
                if chunk:
                    audio.extend(base64.b64decode(chunk))
                if data.get("status") == 2:
                    break
            if audio:
                return True, f"TTS 成功，发音人 {voice}，音频 {len(audio)} 字节(mp3)"
            return False, "TTS 连接成功但未返回音频（疑似服务未开通/参数问题）"
    except Exception as exc:  # noqa: BLE001
        return False, f"TTS 连接/合成异常：{type(exc).__name__}: {exc}"


def verify_tti(app_id: str, api_key: str, api_secret: str) -> tuple[bool, str]:
    base = urllib.parse.urlparse(TTI_URL)
    url = build_signed_url(base.netloc, base.path, "POST", api_key, api_secret, scheme="https")
    body = {
        "header": {"app_id": app_id, "uid": "verify"},
        "parameter": {"chat": {"domain": "general", "width": 512, "height": 512}},
        "payload": {"message": {"text": [{"role": "user", "content": "一只像素风格的小猫，暖色调，无文字"}]}},
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=40) as resp:  # noqa: S310 - 固定讯飞域名
            data = json.loads(resp.read().decode("utf-8"))
    except Exception as exc:  # noqa: BLE001
        return False, f"TTI 请求异常：{type(exc).__name__}: {exc}"
    code = data.get("header", {}).get("code", -1)
    if code != 0:
        return False, f"讯飞 TTI code={code} message={data.get('header', {}).get('message')}"
    text_node = data.get("payload", {}).get("choices", {}).get("text", [])
    if text_node and text_node[0].get("content"):
        img = base64.b64decode(text_node[0]["content"])
        return True, f"TTI 成功，图片 {len(img)} 字节(jpg)"
    return False, "TTI code=0 但无图片内容"


def main() -> int:
    env = load_env(ENV_PATH)
    app_id = env.get("PYT_SPARK_APP_ID", "")
    api_key = env.get("PYT_SPARK_API_KEY", "")
    api_secret = env.get("PYT_SPARK_API_SECRET", "")
    print(f"== 讯飞验证（{ENV_PATH}）==")
    print(f"APP_ID={app_id or '(空)'}  API_KEY={'已配置' if api_key else '(空)'}  API_SECRET={'已配置' if api_secret else '(空)'}")
    if not (app_id and api_key and api_secret):
        print("[FAIL] 凭据缺失，无法验证")
        return 1

    tts_ok, tts_msg = asyncio.run(verify_tts(app_id, api_key, api_secret))
    print(f"[TTS]  {'OK ' if tts_ok else 'FAIL'} {tts_msg}")

    tti_ok, tti_msg = verify_tti(app_id, api_key, api_secret)
    print(f"[TTI]  {'OK ' if tti_ok else 'FAIL'} {tti_msg}")

    return 0 if (tts_ok and tti_ok) else 1


if __name__ == "__main__":
    sys.exit(main())
