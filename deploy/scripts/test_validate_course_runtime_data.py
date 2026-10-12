from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from validate_course_runtime_data import CourseDataValidationError, validate


class ValidateCourseRuntimeDataTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "pdfs").mkdir()
        (self.root / "videos").mkdir()
        for document_id in range(186, 193):
            (self.root / "pdfs" / f"{document_id}.pdf").write_bytes(
                b"%PDF" + b"x" * 2048
            )
        self.video = self.root / "videos" / "course.mp4"
        self.video.write_bytes(b"video" * 1024)
        (self.root / "video_manifest.json").write_text(
            json.dumps(
                [
                    {
                        "id": 1,
                        "chapter": 1,
                        "title": "Python 基础",
                        "objectid": "source-1",
                        "file": "course.mp4",
                        "durationSec": 60,
                        "kcCodes": ["PY43-KC-001"],
                    }
                ]
            ),
            encoding="utf-8",
        )
        self.write_transcript(hashlib.sha256(self.video.read_bytes()).hexdigest())

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write_transcript(self, source_sha: str, object_id: str = "source-1") -> None:
        (self.root / "video_transcripts.json").write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "generatedAt": "2026-08-02T00:00:00Z",
                    "generator": {
                        "engine": "faster-whisper",
                        "engineVersion": "1.2.1",
                        "model": "turbo",
                    },
                    "reviewStatus": "machine_generated_unreviewed",
                    "videos": [
                        {
                            "videoId": 1,
                            "objectId": object_id,
                            "sourceFile": "course.mp4",
                            "sourceSha256": source_sha,
                            "model": "turbo",
                            "language": "zh",
                            "reviewStatus": "machine_generated_unreviewed",
                            "segments": [
                                {
                                    "startSec": 0,
                                    "endSec": 10,
                                    "text": "Python 变量",
                                    "avgLogProb": -0.1,
                                    "noSpeechProb": 0,
                                }
                            ],
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )

    def test_valid_data_passes_with_exact_counts(self) -> None:
        summary = validate(self.root)
        self.assertEqual(
            (summary.pdf_count, summary.video_count, summary.transcript_count),
            (7, 1, 1),
        )
        self.assertEqual(summary.segment_count, 1)

    def test_stale_video_hash_fails_closed(self) -> None:
        self.write_transcript("0" * 64)
        with self.assertRaisesRegex(
            CourseDataValidationError, "SHA-256 does not match"
        ):
            validate(self.root)

    def test_stale_source_object_fails_closed(self) -> None:
        self.write_transcript(
            hashlib.sha256(self.video.read_bytes()).hexdigest(), "old-source"
        )
        with self.assertRaisesRegex(CourseDataValidationError, "object id is stale"):
            validate(self.root)

    def test_missing_transcript_file_fails_closed(self) -> None:
        (self.root / "video_transcripts.json").unlink()
        with self.assertRaisesRegex(
            CourseDataValidationError, "cannot read valid JSON"
        ):
            validate(self.root)

    def test_candidate_transcript_can_be_validated_before_install(self) -> None:
        candidate = self.root / "candidate.json"
        candidate.write_bytes((self.root / "video_transcripts.json").read_bytes())
        (self.root / "video_transcripts.json").unlink()

        summary = validate(self.root, candidate)

        self.assertEqual(summary.transcript_count, 1)


if __name__ == "__main__":
    unittest.main()
