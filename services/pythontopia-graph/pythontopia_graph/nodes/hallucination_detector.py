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
    _check_difficulty_declarations(draft, reasons)

    return GroundingResult(passed=not reasons, reasons=reasons)


# 难度合法集合（与 ProblemSpec.difficulty Literal 对齐）。
_VALID_DIFFICULTIES = {"basic", "medium", "hard"}

# 题面/标题里的中英文难度词 → 规范标签，用于比对文本声明与 difficulty 字段。
_DIFFICULTY_ALIASES: dict[str, str] = {
    "basic": "basic", "简单": "basic", "基础": "basic", "入门": "basic", "容易": "basic",
    "medium": "medium", "中等": "medium", "中级": "medium", "进阶": "medium",
    "hard": "hard", "困难": "hard", "高级": "hard", "挑战": "hard",
}

# 显式难度声明：「难度：X」「难度 为 X」「difficulty: X」（须带分隔符，避免误伤「难度较高」等行文）。
# 中文捕 1-2 字（基础/困难/中等…），英文捕单词，其后由 token 边界天然截断。
_DIFFICULTY_DECL_PATTERN = re.compile(
    r"(?:难度\s*[:：为]\s*|difficulty\s*[:=]\s*)([A-Za-z]+|[\u4e00-\u9fff]{1,2})",
    re.IGNORECASE,
)


def _check_difficulty_declarations(draft: ResourceDraft, reasons: list[str]) -> None:
    """校验练习题的难度声明（plan 3.2 step 2）：

    - difficulty 字段必须属于 {basic, medium, hard}（schema 已约束，留作纵深防御）；
    - 题面/标题/提示里的显式难度声明（「难度：X」）须是已知标签，且与 difficulty 字段一致，
      拦截「未知标签」与「字段与题面自相矛盾」两类幻觉。
    """
    if not isinstance(draft.practice, PracticeContent):
        return
    for problem in draft.practice.problems:
        declared = str(getattr(problem, "difficulty", "") or "").strip().lower()
        if declared and declared not in _VALID_DIFFICULTIES:
            reasons.append(
                f"Practice '{problem.title}' invalid difficulty label: {declared}"
            )
        text = " ".join([problem.title, problem.statement_md, *problem.hints])
        for raw in _DIFFICULTY_DECL_PATTERN.findall(text):
            token = raw.strip().lower()
            mapped = _DIFFICULTY_ALIASES.get(token)
            if mapped is None:
                reasons.append(
                    f"Practice '{problem.title}' unknown difficulty declaration: {raw}"
                )
            elif declared in _VALID_DIFFICULTIES and mapped != declared:
                reasons.append(
                    f"Practice '{problem.title}' contradictory difficulty: "
                    f"declared={declared}, text says {raw}"
                )


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
