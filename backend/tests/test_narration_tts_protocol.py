"""TTS 协议适配：DeepSeek 的 /v1/tools/tts 与 OpenAI 的 /v1/audio/speech 请求形状不同。"""
import io
import wave

import pytest

from app.narration import tts


def wav_bytes(seconds: float = 1.0, rate: int = 24000) -> bytes:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        handle.writeframes(b"\x00\x00" * int(rate * seconds))
    return buffer.getvalue()


@pytest.fixture
def captured(monkeypatch):
    calls: list[tuple[str, dict]] = []

    class FakeResponse:
        status_code = 200
        content = wav_bytes(1.0)

    class FakeClient:
        def __init__(self, **kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def post(self, url, headers=None, json=None):
            calls.append((url, json))
            return FakeResponse()

    monkeypatch.setattr(tts.httpx, "Client", FakeClient)
    return calls


def test_deepseek_protocol_sends_its_own_fields(monkeypatch, captured):
    monkeypatch.setenv("REBORN_TTS_BASE_URL", "https://api.deepseek.com")
    monkeypatch.setenv("REBORN_TTS_API_KEY", "k")
    monkeypatch.setenv("REBORN_TTS_PROTOCOL", "deepseek")

    audio = tts.synthesize("这一页讲变量。")

    url, body = captured[0]
    assert url.endswith("/v1/tools/tts")
    assert body == {"text": "这一页讲变量。", "voice_id": "stella", "format": "wav", "filter_markdown": False}
    assert audio[:4] == b"RIFF"


def test_deepseek_rejects_a_container_it_cannot_produce(monkeypatch, captured):
    monkeypatch.setenv("REBORN_TTS_BASE_URL", "https://api.deepseek.com")
    monkeypatch.setenv("REBORN_TTS_PROTOCOL", "deepseek")
    monkeypatch.setenv("REBORN_TTS_FORMAT", "mp3")
    with pytest.raises(tts.SpeechFailed, match="只支持 wav/pcm"):
        tts.synthesize("x")


def test_deepseek_pcm_is_wrapped_into_wav(monkeypatch):
    payload = b"\x01\x02" * 1200  # 1200 帧 @24kHz = 50ms

    class FakeResponse:
        status_code = 200
        content = payload

    class FakeClient:
        def __init__(self, **kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def post(self, url, headers=None, json=None):
            return FakeResponse()

    monkeypatch.setattr(tts.httpx, "Client", FakeClient)
    monkeypatch.setenv("REBORN_TTS_BASE_URL", "https://api.deepseek.com")
    monkeypatch.setenv("REBORN_TTS_PROTOCOL", "deepseek")
    monkeypatch.setenv("REBORN_TTS_FORMAT", "pcm")

    audio = tts.synthesize("一段话")
    assert audio[:4] == b"RIFF" and audio[8:12] == b"WAVE"
    with wave.open(io.BytesIO(audio)) as handle:
        assert handle.getframerate() == 24000
        assert handle.getnchannels() == 1
        assert handle.getnframes() == 1200


def test_truncated_wav_is_a_failure(monkeypatch):
    full = wav_bytes(1.0)

    class FakeResponse:
        status_code = 200
        content = full[: len(full) // 2]

    class FakeClient:
        def __init__(self, **kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def post(self, url, headers=None, json=None):
            return FakeResponse()

    monkeypatch.setattr(tts.httpx, "Client", FakeClient)
    monkeypatch.setenv("REBORN_TTS_BASE_URL", "https://api.deepseek.com")
    monkeypatch.setenv("REBORN_TTS_PROTOCOL", "deepseek")
    with pytest.raises(tts.SpeechFailed, match="中途断开"):
        tts.synthesize("这一页讲变量。")


def test_openai_protocol_unchanged(monkeypatch, captured):
    monkeypatch.setenv("REBORN_TTS_BASE_URL", "http://tts.local")
    monkeypatch.setenv("REBORN_TTS_PROTOCOL", "openai")
    monkeypatch.delenv("REBORN_TTS_FORMAT", raising=False)

    tts.synthesize("这一页讲变量。")

    url, body = captured[0]
    assert url.endswith("/v1/audio/speech")
    assert body["input"] == "这一页讲变量。"
    assert body["response_format"] == "mp3"
    assert body["voice"] == "Meilin"


def test_status_codes_map_to_readable_errors(monkeypatch):
    def respond(status: int):
        class FakeResponse:
            content = b"{}"
            status_code = status

        class FakeClient:
            def __init__(self, **kwargs):
                pass

            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def post(self, url, headers=None, json=None):
                return FakeResponse()

        return FakeClient

    monkeypatch.setenv("REBORN_TTS_BASE_URL", "https://api.deepseek.com")
    monkeypatch.setenv("REBORN_TTS_PROTOCOL", "deepseek")
    for status, fragment in ((400, "内容审核"), (429, "并发"), (503, "繁忙"), (504, "繁忙")):
        monkeypatch.setattr(tts.httpx, "Client", respond(status))
        with pytest.raises(tts.SpeechFailed, match=fragment):
            tts.synthesize("这一页讲变量。")
