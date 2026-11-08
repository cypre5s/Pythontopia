"""Bounded current-content learning rules and server-clock video participation."""

from datetime import datetime, timezone
from math import isfinite


def _object(value, keys, label):
    if not isinstance(value, dict) or set(value) - set(keys):
        raise ValueError(f"{label}包含未知或无效字段")
    return value


def _integer(value, low=1, high=86400):
    return type(value) is int and low <= value <= high


def _range(value, start, end, label, low=1):
    if not _integer(value.get(start), low) or not _integer(value.get(end), low) or value[start] > value[end]:
        raise ValueError(f"{label}起止范围无效")


def video_duration(material):
    value = material.content.get("duration_seconds") if material else None
    return float(value) if type(value) in (int, float) and isfinite(value) and 0 < value <= 86400 else None


def validate_learning(content, kind, material=None, *, resolve=False):
    """Validate authoring first without a DB, then resolve actual material on import/use."""
    learning = content.get("learning", {})
    _object(learning, {"reading", "video", "highlights"}, "学习配置")
    reading, video = learning.get("reading"), learning.get("video")
    if "reading" in learning:
        _object(reading, {"scope", "start_page", "end_page"}, "阅读规则")
        if reading.get("scope") == "all":
            if set(reading) != {"scope"}:
                raise ValueError("整份阅读不能同时声明页范围")
        elif reading.get("scope") == "range":
            if set(reading) != {"scope", "start_page", "end_page"}:
                raise ValueError("范围阅读必须同时声明起止页")
            _range(reading, "start_page", "end_page", "阅读页")
        else:
            raise ValueError("阅读范围只支持all或range")
    if "video" in learning:
        _object(video, {"minimum_seconds"}, "视频规则")
        if set(video) != {"minimum_seconds"} or not _integer(video["minimum_seconds"]):
            raise ValueError("视频有效播放目标必须为1至86400秒整数")
    if reading is not None and video is not None:
        raise ValueError("单个活动不能同时配置阅读和视频完成规则")
    if (reading is not None or video is not None) and kind not in {"lesson", "reading"}:
        raise ValueError("只有阅读活动可声明材料完成规则")
    legacy = content.get("completion")
    if "completion" in content:
        _object(legacy, {"required_page_ids"}, "阅读完成规则")
        ids = legacy.get("required_page_ids")
        if not isinstance(ids, list) or not ids or len(ids) > 5000 or any(not _integer(key, high=2**53 - 1) for key in ids) or len(set(ids)) != len(ids):
            raise ValueError("阅读参与规则必须引用非重复真实页面ID")
        if reading is not None or video is not None:
            raise ValueError("新材料完成规则不能与required_page_ids同时配置")
    highlights = learning.get("highlights", {})
    _object(highlights, {"pages", "video"}, "重点标记")
    for key, start, end, low in (("pages", "start_page", "end_page", 1), ("video", "start_seconds", "end_seconds", 0)):
        rows = highlights.get(key, [])
        if not isinstance(rows, list) or len(rows) > 100:
            raise ValueError("每种重点标记最多100项")
        for row in rows:
            _object(row, {start, end, "label"}, "重点标记")
            if set(row) != {start, end, "label"} or not isinstance(row["label"], str) or not 1 <= len(row["label"].strip()) <= 200:
                raise ValueError("重点标记需要1至200字说明")
            _range(row, start, end, "重点标记", low)
            if key == "video" and row[start] == row[end]:
                raise ValueError("视频重点片段长度必须大于0")
    if not resolve:
        return learning
    if not material:
        if learning or legacy:
            raise ValueError("学习配置必须绑定真实资料")
        return learning
    pages = material.content.get("pages", [])
    page_numbers = {page["page_no"] for page in pages}
    if reading is not None or highlights.get("pages") or legacy:
        if material.kind not in {"text", "pdf", "presentation"} or not pages:
            raise ValueError("阅读规则或重点页必须绑定有页面的课件")
    if reading and reading["scope"] == "range" and not {reading["start_page"], reading["end_page"]}.issubset(page_numbers):
        raise ValueError("阅读范围引用了不存在的原始页码")
    if legacy and not set(legacy["required_page_ids"]).issubset({page["id"] for page in pages}):
        raise ValueError("阅读完成规则引用了不存在的资料页")
    for row in highlights.get("pages", []):
        if not {row["start_page"], row["end_page"]}.issubset(page_numbers):
            raise ValueError("重点标记引用了不存在的原始页码")
    if video is not None or highlights.get("video"):
        duration = video_duration(material)
        if material.kind != "video" or duration is None:
            raise ValueError("视频规则或片段必须绑定有可靠时长的视频资料")
        if any(row["end_seconds"] > duration for row in highlights.get("video", [])):
            raise ValueError("重点片段超出视频时长")
    return learning


def has_completion_rule(activity):
    learning = activity.content.get("learning", {})
    return bool(activity.content.get("completion") or learning.get("reading") or learning.get("video"))


def required_pages(activity, material):
    reading = activity.content.get("learning", {}).get("reading")
    if not reading:
        return list(activity.content.get("completion", {}).get("required_page_ids", []))
    pages = material.content.get("pages", []) if material else []
    return [page["id"] for page in pages if reading["scope"] == "all" or reading["start_page"] <= page["page_no"] <= reading["end_page"]]


def utcnow():
    return datetime.now(timezone.utc)


def video_watched(state, material_id):
    if state.get("material_id") == material_id:
        return state.get("watched_seconds", 0)
    return state.get("material_seconds", {}).get(str(material_id), 0)


def video_state_for_material(state, material_id):
    if state.get("material_id") == material_id:
        return state
    summary = dict(state.get("material_seconds", {}))
    if state.get("material_id") is not None:
        summary[str(state["material_id"])] = state.get("watched_seconds", 0)
    if str(material_id) not in summary and len(summary) >= 64:
        raise ValueError("活动累计关联的视频资料过多，请联系教师")
    return {"material_id": material_id, "watched_seconds": summary.get(str(material_id), 0), "material_seconds": summary}


def record_video(state, session_id, sequence, position, playing, duration, playback_rate=1):
    """Only sequential, recent, position-continuous heartbeats earn actual elapsed time."""
    if not 0 <= position <= duration:
        raise ValueError("播放位置超出视频时长")
    now = utcnow()
    previous_time = datetime.fromisoformat(state["received_at"]) if state.get("received_at") else None
    elapsed = (now - previous_time).total_seconds() if previous_time else None
    signature = [position, playing, playback_rate]
    same_session = state.get("session_id") == session_id
    if same_session:
        receipt = next((row for row in state.get("receipts", []) if row["sequence"] == sequence), None)
        if receipt:
            if receipt["signature"] != signature:
                raise ValueError("播放序号已用于另一条记录")
            return state, False
        if sequence != state.get("sequence", 0) + 1:
            raise ValueError("播放序号不连续，请恢复当前播放会话")
    else:
        if sequence != 1:
            raise ValueError("新播放会话必须从序号1开始")
        if state.get("playing") and elapsed is not None and 0 <= elapsed < 15:
            raise ValueError("另一个播放会话正在记录进度，请先暂停或稍后重试")
    watched = state.get("watched_seconds", 0)
    delta = position - state.get("position", position)
    previous_rate = state.get("rate", 1)
    if same_session and state.get("playing") and elapsed is not None and 0 < elapsed <= 20 and 0 < delta <= elapsed * previous_rate * 1.25 + .5:
        watched = min(86400, watched + min(delta / previous_rate, elapsed))
    receipts = state.get("receipts", []) if same_session else []
    return {
        "material_id": state.get("material_id"),
        "material_seconds": state.get("material_seconds", {}),
        "session_id": session_id, "sequence": sequence, "position": position,
        "playing": playing, "rate": playback_rate, "received_at": now.isoformat(), "watched_seconds": round(watched, 3),
        "receipts": [*receipts[-31:], {"sequence": sequence, "signature": signature}],
    }, True
