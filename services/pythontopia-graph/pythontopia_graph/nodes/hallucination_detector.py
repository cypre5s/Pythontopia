from __future__ import annotations

import ast
import re

from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    CodeLabContent,
    GroundingResult,
    HandoutContent,
    MindMapContent,
    PracticeContent,
    ReadingContent,
    ResourceDraft,
    StoryboardContent,
)


def hallucination_detector(
    draft: ResourceDraft,
    evidence: EvidenceBundle,
    valid_kc_codes: set[str],
) -> GroundingResult:
    """规则式防幻觉检查，与 grounding_critic 互补：KC 合法性、CodeLab 语法、Practice sample_io、统计型数字。"""

    reasons: list[str] = []
    referenced_kcs: list[str] = []
    if isinstance(draft.mindmap, MindMapContent):
        referenced_kcs.extend(draft.mindmap.kc_map.keys())
        for related in draft.mindmap.kc_map.values():
            referenced_kcs.extend(related)
    if isinstance(draft.practice, PracticeContent):
        for problem in draft.practice.problems:
            referenced_kcs.extend(problem.kc_codes)
    if isinstance(draft.reading, ReadingContent):
        for item in draft.reading.items:
            referenced_kcs.extend(item.kc_codes)
    if isinstance(draft.storyboard, StoryboardContent):
        for scene in draft.storyboard.scenes:
            referenced_kcs.extend(scene.kc_codes)
    if isinstance(draft.handout, HandoutContent):
        for section in draft.handout.sections:
            for paragraph in section.paragraphs:
                referenced_kcs.extend(_extract_kc_tokens(paragraph))

    unknown = sorted({code for code in referenced_kcs if code and code not in valid_kc_codes})
    if unknown:
        reasons.append("Unknown KC: " + ",".join(unknown))

    if isinstance(draft.code_lab, CodeLabContent):
        for walkthrough in draft.code_lab.walkthroughs:
            try:
                ast.parse(walkthrough.code)
            except SyntaxError as exc:
                reasons.append(f"CodeLab '{walkthrough.title}' code parse failed: {exc.msg}")
    if isinstance(draft.practice, PracticeContent):
        for problem in draft.practice.problems:
            if not problem.sample_io:
                reasons.append(f"Practice '{problem.title}' missing sample_io")

    _check_ungrounded_numbers(draft, evidence, reasons)

    return GroundingResult(passed=not reasons, reasons=reasons)


# 统计型「数字幻觉」高发模式：百分比 与 4 位及以上整数（年份/大额统计）。
# 故意不校验小整数（如「3 种方法 / 10 分钟」属正常表述），以免误伤。
_STAT_NUMBER_PATTERN = re.compile(r"\d+(?:\.\d+)?%|\d{4,}")


def _check_ungrounded_numbers(
    draft: ResourceDraft, evidence: EvidenceBundle, reasons: list[str]
) -> None:
    """校验讲义/拓展阅读正文里的统计型数字是否出现在课件证据中，拦截编造的统计/年份。

    仅在有证据时校验（no_evidence_found 视为「通识知识」，跳过）。
    """
    if evidence is None or evidence.no_evidence_found or not evidence.chunks:
        return
    evidence_text = " ".join(getattr(chunk, "text", "") or "" for chunk in evidence.chunks)
    prose: list[str] = []
    if isinstance(draft.handout, HandoutContent):
        for section in draft.handout.sections:
            prose.extend(section.paragraphs)
    if isinstance(draft.reading, ReadingContent):
        for item in draft.reading.items:
            prose.append(item.summary_md)

    suspicious: set[str] = set()
    for text in prose:
        for token in _STAT_NUMBER_PATTERN.findall(text or ""):
            digits = re.sub(r"\D", "", token)
            if digits and digits not in evidence_text:
                suspicious.add(token)
    if suspicious:
        reasons.append("Ungrounded numbers: " + ",".join(sorted(suspicious)))


def _extract_kc_tokens(text: str) -> list[str]:
    tokens = []
    for chunk in text.split():
        cleaned = chunk.strip(",.;:()[]{}").strip()
        if cleaned.startswith("PY43-KC-"):
            tokens.append(cleaned)
    return tokens
