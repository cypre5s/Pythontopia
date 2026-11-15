"""Validate uploaded names, media signatures and upload limits."""

from __future__ import annotations

import os
import zipfile
from pathlib import Path

MAX_UPLOAD = int(os.getenv("REBORN_MAX_UPLOAD_BYTES", str(128 * 1024 * 1024)))


KINDS = {".txt": "text", ".md": "text", ".pdf": "pdf", ".ppt": "presentation", ".pptx": "presentation", ".mp4": "video", ".webm": "video"}


class UploadValidationError(ValueError):
    """The supplied file does not match a supported media format."""


def validate_upload(path: Path, suffix: str):
    with path.open("rb") as stream:
        header = stream.read(16)
    if suffix not in KINDS:
        raise UploadValidationError("仅支持 PDF、PPT/PPTX、MP4/WebM 和 UTF-8 文本")
    if suffix == ".pdf" and not header.startswith(b"%PDF-"):
        raise UploadValidationError("PDF 文件签名无效")
    if suffix == ".ppt" and not header.startswith(bytes.fromhex("d0cf11e0a1b11ae1")):
        raise UploadValidationError("PPT 文件签名无效")
    if suffix == ".pptx":
        try:
            with zipfile.ZipFile(path) as archive:
                items = archive.infolist()
                if len(items) > 10000 or sum(x.file_size for x in items) > 256 * 1024 * 1024 or "ppt/presentation.xml" not in archive.namelist():
                    raise ValueError("无效或过大的 PPTX")
        except (ValueError, zipfile.BadZipFile) as exc:
            raise UploadValidationError("PPTX 文件无效或解压大小过大") from exc
    if suffix == ".mp4" and header[4:8] != b"ftyp":
        raise UploadValidationError("MP4 文件签名无效")
    if suffix == ".webm" and not header.startswith(bytes.fromhex("1a45dfa3")):
        raise UploadValidationError("WebM 文件签名无效")
    if suffix in {".txt", ".md"}:
        try:
            with path.open(encoding="utf-8-sig") as stream:
                if "\x00" in stream.read(500000):
                    raise ValueError("binary")
        except (UnicodeError, ValueError) as exc:
            raise UploadValidationError("文本必须使用 UTF-8 编码") from exc
