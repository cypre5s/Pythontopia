#!/usr/bin/env python3
"""Generate resumable, provenance-rich course-video transcripts with faster-whisper.

The generated JSON is runtime data, not a mock: every video record carries the source MP4
SHA-256, model identifier, language confidence, timed segments, and review status.  Work files
are written atomically so an interrupted nine-hour corpus run can resume without repeating
completed videos.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
DEFAULT_HOTWORDS = (
    "Python,PyCharm,IDLE,input,print,range,list,tuple,dict,set,for,while,if,else,"
    "elif,def,return,import,模块,函数,变量,列表,元组,字典,集合,循环,条件,异常"
)


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=root / "video_manifest.json")
    parser.add_argument("--video-dir", type=Path, default=root / "videos")
    parser.add_argument("--output", type=Path, default=root / "video_transcripts.json")
    parser.add_argument("--work-dir", type=Path, default=root / ".transcript-work")
    parser.add_argument("--model", default="turbo")
    parser.add_argument("--device", choices=("auto", "cpu", "cuda"), default="cuda")
    parser.add_argument("--compute-type", default="float16")
    parser.add_argument("--beam-size", type=int, default=3)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--language", default="zh")
    parser.add_argument("--hotwords", default=DEFAULT_HOTWORDS)
    parser.add_argument(
        "--limit", type=int, default=0, help="Process at most N videos (0=all)."
    )
    parser.add_argument("--resume", action=argparse.BooleanOptionalAction, default=True)
    return parser.parse_args()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    os.replace(temporary, path)


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def reusable(
    work_path: Path, *, source_sha256: str, model: str
) -> dict[str, Any] | None:
    if not work_path.is_file():
        return None
    try:
        payload = load_json(work_path)
    except (OSError, ValueError):
        return None
    if (
        payload.get("sourceSha256") != source_sha256
        or payload.get("model") != model
        or not payload.get("segments")
    ):
        return None
    return payload


def transcribe_one(
    pipeline: Any,
    entry: dict[str, Any],
    video_path: Path,
    source_sha256: str,
    args: argparse.Namespace,
) -> dict[str, Any]:
    segments, info = pipeline.transcribe(
        str(video_path),
        language=args.language,
        beam_size=args.beam_size,
        batch_size=args.batch_size,
        vad_filter=True,
        hotwords=args.hotwords,
    )
    timed_segments: list[dict[str, Any]] = []
    for segment in segments:
        text = " ".join(segment.text.strip().split())
        start = max(0.0, round(float(segment.start), 3))
        end = max(start, round(float(segment.end), 3))
        if text and end > start:
            timed_segments.append(
                {
                    "startSec": start,
                    "endSec": end,
                    "text": text,
                    "avgLogProb": round(float(segment.avg_logprob), 4),
                    "noSpeechProb": round(float(segment.no_speech_prob), 4),
                }
            )
    if not timed_segments:
        raise RuntimeError(f"video {entry['id']} produced no speech segments")
    return {
        "videoId": int(entry["id"]),
        "objectId": str(entry.get("objectid") or ""),
        "sourceFile": video_path.name,
        "sourceSha256": source_sha256,
        "model": args.model,
        "language": info.language,
        "languageProbability": round(float(info.language_probability), 4),
        "durationSec": round(float(info.duration), 3),
        "durationAfterVadSec": round(float(info.duration_after_vad), 3),
        "reviewStatus": "machine_generated_unreviewed",
        "segments": timed_segments,
    }


def main() -> int:
    args = parse_args()
    manifest = load_json(args.manifest)
    if not isinstance(manifest, list) or not manifest:
        raise SystemExit(f"video manifest is empty or invalid: {args.manifest}")
    entries = manifest[: args.limit] if args.limit > 0 else manifest

    try:
        import faster_whisper
        from faster_whisper import BatchedInferencePipeline, WhisperModel
    except ImportError as exc:
        raise SystemExit(
            'install the mature dependency first: pip install "faster-whisper>=1.2,<2"'
        ) from exc

    model = WhisperModel(args.model, device=args.device, compute_type=args.compute_type)
    pipeline = BatchedInferencePipeline(model=model)
    completed: list[dict[str, Any]] = []
    for index, entry in enumerate(entries, start=1):
        video_path = (args.video_dir / str(entry.get("file") or "")).resolve()
        if not video_path.is_file() or video_path.parent != args.video_dir.resolve():
            raise SystemExit(
                f"video file is missing or unsafe for id={entry.get('id')}: {video_path}"
            )
        source_sha256 = sha256_file(video_path)
        work_path = args.work_dir / f"{int(entry['id'])}.json"
        cached = (
            reusable(work_path, source_sha256=source_sha256, model=args.model)
            if args.resume
            else None
        )
        if cached is not None:
            print(
                f"[{index}/{len(entries)}] reuse video {entry['id']} {entry.get('title', '')}",
                flush=True,
            )
            completed.append(cached)
            continue
        print(
            f"[{index}/{len(entries)}] transcribe video {entry['id']} {entry.get('title', '')}",
            flush=True,
        )
        payload = transcribe_one(pipeline, entry, video_path, source_sha256, args)
        atomic_json(work_path, payload)
        completed.append(payload)

    output = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "generator": {
            "engine": "faster-whisper",
            "engineVersion": getattr(faster_whisper, "__version__", "unknown"),
            "model": args.model,
            "device": args.device,
            "computeType": args.compute_type,
            "beamSize": args.beam_size,
            "batchSize": args.batch_size,
            "vad": "silero",
        },
        "reviewStatus": "machine_generated_unreviewed",
        "videos": sorted(completed, key=lambda item: item["videoId"]),
    }
    atomic_json(args.output, output)
    print(f"wrote {len(completed)} transcripts to {args.output}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
