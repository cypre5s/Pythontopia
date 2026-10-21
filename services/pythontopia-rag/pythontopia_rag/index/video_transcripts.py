from __future__ import annotations

import math
from collections import Counter
from dataclasses import dataclass, field

from pythontopia_rag.data.video_transcripts import VideoSegment
from pythontopia_rag.index.bm25 import is_topical_term, tokenize

MIN_AVG_LOG_PROB = -0.6
MAX_NO_SPEECH_PROB = 0.5


@dataclass(frozen=True)
class VideoMatch:
    segment: VideoSegment
    score: float


@dataclass
class VideoTranscriptIndex:
    segments: list[VideoSegment] = field(default_factory=list)
    _tokens: list[set[str]] = field(default_factory=list, init=False)
    _document_frequency: Counter[str] = field(default_factory=Counter, init=False)

    @property
    def segment_count(self) -> int:
        return len(self.segments)

    def build(self, segments: list[VideoSegment]) -> None:
        self.segments = [
            segment
            for segment in segments
            if segment.avg_log_prob >= MIN_AVG_LOG_PROB
            and segment.no_speech_prob <= MAX_NO_SPEECH_PROB
        ]
        self._tokens = [
            set(tokenize(f"{segment.title} {segment.text}")) for segment in self.segments
        ]
        self._document_frequency = Counter(
            token for tokens in self._tokens for token in tokens
        )

    def search(
        self,
        query: str,
        *,
        top_k: int = 2,
        chapter_nos: set[int] | None = None,
    ) -> list[VideoMatch]:
        if top_k <= 0 or not self.segments:
            return []
        query_tokens = {token for token in tokenize(query) if is_topical_term(token)}
        if not query_tokens:
            return []

        matches: list[VideoMatch] = []
        document_count = len(self.segments)
        for segment, tokens in zip(self.segments, self._tokens, strict=True):
            if chapter_nos is not None and segment.chapter_no not in chapter_nos:
                continue
            overlap = query_tokens & tokens
            if not overlap:
                continue
            score = sum(
                math.log1p(
                    (document_count - self._document_frequency[token] + 0.5)
                    / (self._document_frequency[token] + 0.5)
                )
                for token in overlap
            ) / math.sqrt(max(1, len(tokens)))
            matches.append(VideoMatch(segment=segment, score=score))

        matches.sort(key=lambda match: (-match.score, match.segment.chapter_no, match.segment.start_sec))
        by_chapter: dict[int, VideoMatch] = {}
        for match in matches:
            by_chapter.setdefault(match.segment.chapter_no, match)
            if len(by_chapter) >= top_k:
                break
        return list(by_chapter.values())
