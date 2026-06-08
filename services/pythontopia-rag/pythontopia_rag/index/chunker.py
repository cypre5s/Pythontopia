from __future__ import annotations

import re
from dataclasses import dataclass

DEFAULT_MIN_CHARS = 100
DEFAULT_TARGET_CHARS = 700
DEFAULT_OVERLAP = 100


@dataclass
class Chunk:
    chunk_id: str
    page_id: int
    page_no: int
    document_id: int
    chapter_id: int | None
    kc_codes: list[str]
    text: str
    token_estimate: int


def _split_by_heading(text: str) -> list[str]:
    if not text:
        return []
    parts: list[str] = []
    buffer: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.rstrip()
        if line.startswith("#") and buffer:
            parts.append("\n".join(buffer).strip())
            buffer = [line]
        else:
            buffer.append(line)
    if buffer:
        joined = "\n".join(buffer).strip()
        if joined:
            parts.append(joined)
    return parts


def _sliding_window(text: str, target: int, overlap: int) -> list[str]:
    text = text.strip()
    if len(text) <= target:
        return [text] if text else []
    window = max(target - overlap, 1)
    chunks: list[str] = []
    start = 0
    while start < len(text):
        end = min(start + target, len(text))
        piece = text[start:end].strip()
        if piece:
            chunks.append(piece)
        if end >= len(text):
            break
        start += window
    return chunks


def chunk_page(
    *,
    page_id: int,
    page_no: int,
    document_id: int,
    chapter_id: int | None,
    kc_codes: list[str],
    text: str,
    min_chars: int = DEFAULT_MIN_CHARS,
    target_chars: int = DEFAULT_TARGET_CHARS,
    overlap: int = DEFAULT_OVERLAP,
) -> list[Chunk]:
    """按 markdown 标题切分，再滑动窗口（target_chars/overlap）分块，短于 min_chars 的片段并入前块。"""
    headings = _split_by_heading(text)
    flat_pieces: list[str] = []
    for section in headings:
        flat_pieces.extend(_sliding_window(section, target_chars, overlap))

    if not flat_pieces:
        return []

    merged: list[str] = []
    for piece in flat_pieces:
        if merged and len(piece) < min_chars:
            merged[-1] = (merged[-1] + "\n" + piece).strip()
        else:
            merged.append(piece)

    chunks: list[Chunk] = []
    for index, piece in enumerate(merged):
        if len(piece) < min_chars and chunks:
            chunks[-1].text = (chunks[-1].text + "\n" + piece).strip()
            continue
        chunk_id = f"p{page_id:08d}-c{index:03d}"
        chunks.append(
            Chunk(
                chunk_id=chunk_id,
                page_id=page_id,
                page_no=page_no,
                document_id=document_id,
                chapter_id=chapter_id,
                kc_codes=list(kc_codes),
                text=piece,
                token_estimate=_estimate_tokens(piece),
            )
        )
    return chunks


def _estimate_tokens(text: str) -> int:
    chinese = len(re.findall(r"[\u4e00-\u9fff]", text))
    other = len(text) - chinese
    return max(1, chinese + max(1, other // 4))
