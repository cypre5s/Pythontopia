"""Portable page narration metadata, independent of server paths and user IDs."""

import hashlib
import json
from datetime import datetime
from types import SimpleNamespace

from app.course_packages.manifest import PackageError
from app.narration import repository
from app.narration.models import STATUSES

FIELDS = ("text", "bridge_in", "target_seconds", "status", "source", "model", "prompt_version",
          "image_used", "anchors", "edited_by_teacher")


def directory(material_id):
    # Stable IDs may contain filename delimiters; never interpolate them into paths.
    return "narrations/" + hashlib.sha256(material_id.encode()).hexdigest()


def fingerprint(material, page_no, assets):
    page = repository.page_map(SimpleNamespace(content=material["content"])).get(page_no)
    value = [page, *(assets[material[key]]["sha256"] if material[key] else None for key in ("asset_id", "preview_asset_id"))]
    return repository.text_hash(json.dumps(value, ensure_ascii=False, sort_keys=True))


def validate_narrations(bundle, root):
    materials = {row["id"]: row for row in bundle["data"]["material"]}
    assets = {row["id"]: row for row in bundle["assets"]}
    used, seen = set(), set()
    for row in bundle.get("narrations", []):
        fields = {*FIELDS, "material_id", "page_no", "page_hash", "reviewed_at", "audio"}
        if not isinstance(row, dict) or set(row) != fields:
            raise PackageError("讲稿字段缺失或包含未知字段")
        material = materials.get(row["material_id"])
        if material is None or material["kind"] not in {"pdf", "presentation"}:
            raise PackageError("讲稿必须引用当前PDF或演示文稿")
        page_no = row["page_no"]
        if type(page_no) is not int or page_no < 1 or (row["material_id"], page_no) in seen:
            raise PackageError("讲稿页码无效或重复")
        seen.add((row["material_id"], page_no))
        for field, limit in (("text", 4000), ("bridge_in", 4000), ("model", 80), ("prompt_version", 24)):
            if not isinstance(row[field], str) or len(row[field]) > limit:
                raise PackageError("讲稿文本字段无效或超限")
        if row["status"] not in STATUSES or row["source"] not in {"teacher", "ai_generated"}:
            raise PackageError("讲稿状态或来源无效")
        if row["status"] in {"accepted", "draft", "rejected"} and not row["text"].strip():
            raise PackageError("讲稿正文不能为空")
        if type(row["target_seconds"]) is not int or not 30 <= row["target_seconds"] <= 300:
            raise PackageError("讲稿目标时长无效")
        if any(type(row[field]) is not bool for field in ("image_used", "edited_by_teacher")):
            raise PackageError("讲稿标记无效")
        if not isinstance(row["anchors"], list) or len(row["anchors"]) > 6 or any(not isinstance(a, str) or len(a) > 200 for a in row["anchors"]):
            raise PackageError("讲稿原文锚点无效")
        if row["reviewed_at"] is not None:
            if not isinstance(row["reviewed_at"], str):
                raise PackageError("讲稿审校时间无效")
            stamp = datetime.fromisoformat(row["reviewed_at"])
            if stamp.tzinfo is None:
                raise PackageError("讲稿审校时间必须包含时区")
        pages = repository.page_map(SimpleNamespace(content=material["content"]))
        if row["status"] != "stale":
            if page_no not in pages or row["page_hash"] != fingerprint(material, page_no, assets):
                raise PackageError("讲稿页面指纹不匹配")
        elif row["page_hash"] is not None:
            raise PackageError("过期讲稿不得声明当前页面指纹")
        audio = row["audio"]
        if audio is None:
            continue
        if not isinstance(audio, dict) or set(audio) != {"asset_id", "text_hash", "seconds"}:
            raise PackageError("讲稿音频字段无效")
        asset = assets.get(audio["asset_id"])
        if row["status"] != "accepted" or audio["text_hash"] != repository.text_hash(row["text"]):
            raise PackageError("音频与已审校讲稿不匹配")
        if asset is None or not asset["media_type"].startswith("audio/") or not asset["path"].startswith(directory(row["material_id"]) + "/audio/"):
            raise PackageError("讲稿音频引用缺失或路径不匹配")
        if audio["asset_id"] in used or type(audio["seconds"]) is not int or not 1 <= audio["seconds"] <= 86400:
            raise PackageError("讲稿音频重复引用或时长无效")
        used.add(audio["asset_id"])
        path = root / asset["path"]
        with path.open("rb") as stream:
            header = stream.read(16)
        extension = path.suffix.lower()
        valid = {
            ".wav": header.startswith(b"RIFF") and header[8:12] == b"WAVE",
            ".mp3": header.startswith(b"ID3") or (len(header) > 1 and header[0] == 255 and header[1] & 224 == 224),
            ".ogg": header.startswith(b"OggS"), ".opus": header.startswith(b"OggS"),
            ".flac": header.startswith(b"fLaC"), ".m4a": header[4:8] == b"ftyp",
            ".aac": header.startswith(b"ADIF") or (len(header) > 1 and header[0] == 255 and header[1] & 240 == 240),
        }
        if not valid.get(extension) or not path.name.startswith(asset["sha256"] + "-"):
            raise PackageError("音频文件签名或哈希文件名无效")
    if used != {a["id"] for a in bundle["assets"] if a["path"].startswith("narrations/")}:
        raise PackageError("存在未被讲稿引用的音频")
