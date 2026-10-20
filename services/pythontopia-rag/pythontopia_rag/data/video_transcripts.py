from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class VideoSegment:
    video_id: int
    chapter_no: int
    title: str
    start_sec: float
    end_sec: float
    text: str
    avg_log_prob: float
    no_speech_prob: float
    review_status: str


def _video_rows(payload: object, source: str) -> list[dict[str, object]]:
    if isinstance(payload, list):
        return [row for row in payload if isinstance(row, dict)]
    if not isinstance(payload, dict) or not isinstance(payload.get("videos"), list):
        raise ValueError(f"{source} must contain a videos array")
    return [row for row in payload["videos"] if isinstance(row, dict)]


def _integer(value: object) -> int | None:
    return value if isinstance(value, int) and not isinstance(value, bool) else None


def _number(value: object, default: float) -> float:
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        result = float(value)
        return result if math.isfinite(result) else default
    return default


def load_video_segments(
    manifest_path: str | Path, transcript_path: str | Path
) -> list[VideoSegment]:
    """Join timestamped ASR segments to manifest chapter/title metadata."""
    manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
    transcripts = json.loads(Path(transcript_path).read_text(encoding="utf-8"))
    manifest_rows = _video_rows(manifest, "video manifest")
    transcript_rows = _video_rows(transcripts, "video transcripts")

    metadata: dict[int, tuple[int, str]] = {}
    for row in manifest_rows:
        video_id = _integer(row.get("id"))
        chapter_no = _integer(row.get("chapter"))
        title = row.get("title")
        if video_id is not None and chapter_no is not None and isinstance(title, str):
            metadata[video_id] = (chapter_no, title)

    segments: list[VideoSegment] = []
    for video in transcript_rows:
        video_id = _integer(video.get("videoId"))
        if video_id is None or video_id not in metadata:
            continue
        chapter_no, title = metadata[video_id]
        status = video.get("reviewStatus")
        review_status = status if isinstance(status, str) else "unknown"
        rows = video.get("segments")
        if not isinstance(rows, list):
            continue
        for row in rows:
            if not isinstance(row, dict):
                continue
            raw_text = row.get("text")
            if not isinstance(raw_text, str):
                continue
            text = raw_text.strip()
            start_sec = _number(row.get("startSec"), -1.0)
            end_sec = _number(row.get("endSec"), -1.0)
            if not text or start_sec < 0 or end_sec <= start_sec:
                continue
            segments.append(
                VideoSegment(
                    video_id=video_id,
                    chapter_no=chapter_no,
                    title=title,
                    start_sec=start_sec,
                    end_sec=end_sec,
                    text=text,
                    avg_log_prob=_number(row.get("avgLogProb"), -1.0),
                    no_speech_prob=_number(row.get("noSpeechProb"), 1.0),
                    review_status=review_status,
                )
            )
    return segments
