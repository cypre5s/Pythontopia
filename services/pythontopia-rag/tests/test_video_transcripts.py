from __future__ import annotations

import json

from pythontopia_rag.data.video_transcripts import VideoSegment, load_video_segments
from pythontopia_rag.index.video_transcripts import VideoTranscriptIndex


def test_load_video_segments_joins_manifest_metadata(tmp_path) -> None:
    manifest_path = tmp_path / "video_manifest.json"
    transcript_path = tmp_path / "video_transcripts.json"
    manifest_path.write_text(
        json.dumps(
            [{"id": 7, "chapter": 3, "title": "Python 输入"}],
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    transcript_path.write_text(
        json.dumps(
            {
                "videos": [
                    {
                        "videoId": 7,
                        "reviewStatus": "machine_generated_unreviewed",
                        "segments": [
                            {
                                "startSec": 12.5,
                                "endSec": 17.0,
                                "text": "input函数读取一行字符串",
                                "avgLogProb": -0.1,
                                "noSpeechProb": 0.0,
                            }
                        ],
                    }
                ]
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    segments = load_video_segments(manifest_path, transcript_path)

    assert len(segments) == 1
    assert segments[0].video_id == 7
    assert segments[0].chapter_no == 3
    assert segments[0].title == "Python 输入"
    assert segments[0].start_sec == 12.5
    assert segments[0].text == "input函数读取一行字符串"


def test_video_index_ranks_relevant_segments_and_drops_low_quality() -> None:
    segments = [
        VideoSegment(1, 2, "输入函数", 12.0, 18.0, "input函数读取键盘输入", -0.1, 0.0, "unreviewed"),
        VideoSegment(2, 6, "数据分析", 30.0, 36.0, "input函数读取键盘输入", -0.9, 0.0, "unreviewed"),
    ]
    index = VideoTranscriptIndex()
    index.build(segments)

    matches = index.search("input函数", top_k=2)

    assert index.segment_count == 1
    assert len(matches) == 1
    assert matches[0].segment.video_id == 1
    assert matches[0].segment.start_sec == 12.0


def test_video_index_applies_chapter_filter_before_top_k() -> None:
    index = VideoTranscriptIndex()
    index.build(
        [
            VideoSegment(1, 1, "输入函数", 1.0, 5.0, "input函数读取输入", -0.1, 0.0, "unreviewed"),
            VideoSegment(2, 2, "输入函数", 2.0, 6.0, "input函数读取输入", -0.1, 0.0, "unreviewed"),
        ]
    )

    matches = index.search("input函数", top_k=1, chapter_nos={2})

    assert len(matches) == 1
    assert matches[0].segment.video_id == 2
