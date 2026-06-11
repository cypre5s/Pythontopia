"""PaperDesignerAgent：章末大测试的结构化出题官（§3 题面 JSON）。

复用「学」阶段资源生成的个性化基建（``agents.personalization``）：把 8 维画像 + 错题本
高频错因折成 system directive，注入出题 prompt。严格 fail-fast：LLM 返回缺字段/题量不符/
非法答案 → 抛 ``PaperDesignError``（pipeline 转结构化 502），绝不静默降级成文本。

防幻觉：客观题只允许引用「证据页白名单」(``evidence.page_ids``) 内的页码；编程题须给出
可在沙箱通过的 ``reference_solution``。本 agent 只生成；证据校验/沙箱自测/安全审查在三审节点。
"""

from __future__ import annotations

import json
import re
from typing import Any

from pythontopia_graph.agents.personalization import personalization_messages
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.chapter_test import (
    ChapterTestGenerateRequest,
    PaperCounts,
    ProfileBrief,
    QuestionPaper,
)
from pythontopia_graph.schemas.resource import ProfileSnapshot

_MAJOR_LITERALS = {"CS", "NON_CS_STEM", "NON_CS_HUMANITIES", "OTHER"}


class PaperDesignError(RuntimeError):
    """出题阶段失败（无 JSON / 解析失败 / 题量或题型不符）。"""


class PaperDesignerAgent:
    AGENT_NAME = "PaperDesignerAgent"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self,
        request: ChapterTestGenerateRequest,
        evidence: EvidenceBundle,
        revision_notes: list[str] | None = None,
    ) -> QuestionPaper:
        messages = self._prompt(request, evidence, revision_notes)
        try:
            response = await self.provider.chat(
                messages, response_format={"type": "json_object"}
            )
        except Exception as exc:  # noqa: BLE001 - 上游 provider 失败 → 出题失败
            raise PaperDesignError(f"LLM provider error: {exc}") from exc
        paper = self._parse(response.content_json, response.content_text)
        self._assert_counts(paper, request.counts)
        return paper

    # -- prompt ------------------------------------------------------------- #
    def _prompt(
        self,
        request: ChapterTestGenerateRequest,
        evidence: EvidenceBundle,
        revision_notes: list[str] | None,
    ) -> list[dict[str, str]]:
        counts = request.counts
        allowed_pages = evidence.page_ids
        coding_difficulty = self._coding_difficulty(request.profile_brief.knowledge_level)
        snapshot = self._to_snapshot(request)

        system = (
            "你是 PaperDesignerAgent，为「章末大测试」一次性出一整套题，"
            "只输出 JSON 对象，形如 {\"items\":[...]}，不要任何解释或 markdown 包裹。\n"
            "题型与字段（snake_case，严格遵守）：\n"
            "- CHOICE: {qid,type:'CHOICE',stem,options(4个),answer(选项字母如'B'),explain,"
            "kc_code,evidence_page_ids:[int],score}\n"
            "- JUDGE: {qid,type:'JUDGE',stem,answer(true/false),explain,kc_code,"
            "evidence_page_ids:[int],score}\n"
            "- FILL: {qid,type:'FILL',stem(恰好含一个 ____ 空位),answers:[该空的候选答案],"
            "match:'normalized',explain,kc_code,evidence_page_ids:[int],score}\n"
            "- CODING: {qid,type:'CODING',title,statement_markdown,samples:[{input,output}],"
            "hidden_test_cases:[{input,output}],reference_solution,kc_code,score}\n"
            "硬约束：\n"
            "1) 客观题(CHOICE/JUDGE/FILL)必须给出 evidence_page_ids，且只能取自下方"
            "「证据页白名单」，禁止编造任何课件页码/章节/题号。\n"
            "2) CODING 的 reference_solution 必须能对 samples 与 hidden_test_cases 全部产生正确输出"
            "（程序从标准输入读、标准输出写），且只用本章及以前的语法，禁止 import 网络/子进程类模块。\n"
            "3) 不得在题面/选项/样例中出现 hidden_test_cases / answer_key / secret_test 等内部字段名。\n"
            "4) 严格按下方题量出题，qid 全局唯一。\n"
            "5) FILL 题面必须**恰好一个**空位（一段连续下划线）；多个空请拆成多道单空题。"
        )
        whitelist = (
            "证据页白名单(evidence_page_ids 只能取这些): "
            + (", ".join(str(p) for p in allowed_pages) if allowed_pages else "(空)")
        )
        plan = (
            f"本章: 第{request.chapter}章; KC: {', '.join(request.kc_codes)}\n"
            f"题量: CHOICE×{counts.choice}(每题{counts.choice_score}分)、"
            f"JUDGE×{counts.judge}(每题{counts.judge_score}分)、"
            f"FILL×{counts.fill}(每题{counts.fill_score}分)、"
            f"CODING×{counts.coding}(每题{counts.coding_score}分)\n"
            f"编程题目标复杂度: {coding_difficulty}（知识基础={request.profile_brief.knowledge_level}/5，"
            "弱则降复杂度但覆盖相同 KC）"
        )
        targeting = self._targeting_clause(request)

        messages: list[dict[str, str]] = [{"role": "system", "content": system}]
        messages.extend(personalization_messages(snapshot))
        if request.focus_hint:
            messages.append({"role": "system", "content": f"出题侧重: {request.focus_hint}"})
        if targeting:
            messages.append({"role": "system", "content": targeting})
        if revision_notes:
            messages.append(
                {
                    "role": "system",
                    "content": "上一稿被三审打回：\n"
                    + "\n".join(f"- {note}" for note in revision_notes)
                    + "\n请针对性修订后重出整套题。",
                }
            )
        messages.append({"role": "system", "content": whitelist})
        messages.append({"role": "user", "content": plan})
        return messages

    def _targeting_clause(self, request: ChapterTestGenerateRequest) -> str | None:
        tags = [t.strip() for t in request.profile_brief.error_patterns if t and t.strip()]
        tags += [t.strip() for t in request.errorbook_brief.top_error_tags if t and t.strip()]
        kcs = [k.strip() for k in request.errorbook_brief.frequent_error_kcs if k and k.strip()]
        parts: list[str] = []
        if tags:
            parts.append("学生易错点：" + "、".join(dict.fromkeys(tags)) + "（针对性出题、设干扰项）")
        if kcs:
            parts.append("高频错因 KC：" + "、".join(dict.fromkeys(kcs)) + "（适度加大覆盖）")
        return "；".join(parts) if parts else None

    @staticmethod
    def _coding_difficulty(knowledge_level: int) -> str:
        if knowledge_level <= 2:
            return "basic"
        if knowledge_level == 3:
            return "medium"
        return "hard"

    @staticmethod
    def _to_snapshot(request: ChapterTestGenerateRequest) -> ProfileSnapshot:
        brief: ProfileBrief = request.profile_brief
        major = brief.major.strip().upper()
        merged_errors = list(
            dict.fromkeys(
                [*brief.error_patterns, *request.errorbook_brief.top_error_tags]
            )
        )
        insight = (brief.summary or request.errorbook_brief.summary or "").strip()
        return ProfileSnapshot(
            major=major if major in _MAJOR_LITERALS else "OTHER",  # type: ignore[arg-type]
            python_self_rated_level=brief.knowledge_level,
            cognitive_style=brief.cognitive_style,
            error_patterns=merged_errors,
            recent_insight=insight,
        )

    # -- parse -------------------------------------------------------------- #
    def _parse(
        self, content_json: dict[str, Any] | None, content_text: str | None
    ) -> QuestionPaper:
        payload = content_json
        if payload is None and content_text:
            try:
                parsed = json.loads(content_text)
                payload = parsed if isinstance(parsed, dict) else None
            except (ValueError, TypeError):
                payload = None
        if not isinstance(payload, dict):
            raise PaperDesignError("designer returned no JSON object")
        body = payload
        if "items" not in body:
            for key in ("questions", "paper"):
                inner = body.get(key)
                if isinstance(inner, dict) and "items" in inner:
                    body = inner
                    break
        try:
            return QuestionPaper.model_validate(body)
        except Exception as exc:  # noqa: BLE001 - pydantic ValidationError → 出题失败
            raise PaperDesignError(f"paper schema invalid: {exc}") from exc

    @staticmethod
    def _assert_counts(paper: QuestionPaper, counts: PaperCounts) -> None:
        actual = paper.type_counts()
        want = {
            "CHOICE": counts.choice,
            "JUDGE": counts.judge,
            "FILL": counts.fill,
            "CODING": counts.coding,
        }
        mismatch = {k: (want[k], actual.get(k, 0)) for k in want if want[k] != actual.get(k, 0)}
        if mismatch:
            raise PaperDesignError(f"item count mismatch want/got: {mismatch}")
        qids = [item.qid for item in paper.items]
        if len(set(qids)) != len(qids):
            raise PaperDesignError("duplicate qid in paper")
        PaperDesignerAgent._assert_fill_single_blank(paper)

    # 一段连续下划线（≥2 个 _）即一个空位；后端 judgeFill 为单空归一化比对语义（R5）。
    _FILL_BLANK_RE = re.compile(r"_{2,}")

    @staticmethod
    def _assert_fill_single_blank(paper: QuestionPaper) -> None:
        """FILL 题面必须恰好一个空位：多空与后端单空判分语义冲突，fail-fast 触发重试/兜底。"""
        for item in paper.items:
            if getattr(item, "type", None) != "FILL":
                continue
            stem = getattr(item, "stem", "") or ""
            blanks = len(PaperDesignerAgent._FILL_BLANK_RE.findall(stem))
            if blanks != 1:
                raise PaperDesignError(
                    f"FILL stem must contain exactly one blank, got {blanks} (qid={item.qid})"
                )
