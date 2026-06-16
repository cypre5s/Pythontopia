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
from pythontopia_graph.agents.paper_reviews import sandbox_self_test
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.chapter_test import (
    ChapterTestGenerateRequest,
    CodingItem,
    PaperCounts,
    PreferredCodingQuestion,
    ProfileBrief,
    QuestionPaper,
)
from pythontopia_graph.schemas.resource import ProfileSnapshot

_MAJOR_LITERALS = {"CS", "NON_CS_STEM", "NON_CS_HUMANITIES", "OTHER"}


class PaperDesignError(RuntimeError):
    """出题阶段失败（无 JSON / 解析失败 / 题量或题型不符）。"""


class PaperDesignerAgent:
    AGENT_NAME = "PaperDesignerAgent"
    _MAX_CHUNK = PaperCounts(choice=10, judge=10, fill=10, coding=3)
    _MAX_CHUNK_ATTEMPTS = 3

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self,
        request: ChapterTestGenerateRequest,
        evidence: EvidenceBundle,
        revision_notes: list[str] | None = None,
    ) -> QuestionPaper:
        chunks = self._split_counts(request.counts)
        generated_items = []
        excluded = list(request.excluded_questions)
        preferred = list(request.preferred_coding_questions)
        used_preferred_ids: set[int] = set()
        for index, counts in enumerate(chunks, start=1):
            selected = self._select_preferred(
                preferred,
                used_preferred_ids,
                excluded,
                counts.coding,
                counts.coding_score,
            )
            used_preferred_ids.update(
                int(item.qid.removeprefix("bank-")) for item in selected
            )
            generated_counts = counts.model_copy(
                update={"coding": counts.coding - len(selected)}
            )
            selected_texts = [self._item_text(item) for item in selected]
            chunk_request = request.model_copy(
                update={
                    "counts": generated_counts,
                    "excluded_questions": [*excluded, *selected_texts],
                    "preferred_coding_questions": [],
                }
            )
            if sum(generated_counts.as_dict().values()) > 0:
                paper = await self._design_chunk_with_retries(
                    chunk_request,
                    evidence,
                    [*excluded, *selected_texts],
                    revision_notes,
                )
                chunk_items = [*paper.items, *selected]
            else:
                chunk_items = list(selected)
            multi_chunk = len(chunks) > 1
            chunk_paper = QuestionPaper(items=chunk_items)
            self._assert_counts(chunk_paper, counts)
            self._assert_no_excluded(chunk_paper, excluded)
            for item in chunk_paper.items:
                generated_items.append(
                    item.model_copy(
                        update={"qid": f"b{index}-{item.qid}" if multi_chunk else item.qid}
                    )
                )
            excluded.extend(self._paper_exclusion_texts(chunk_paper))
        merged = QuestionPaper(items=generated_items)
        self._assert_counts(merged, request.counts)
        self._assert_no_excluded(merged, request.excluded_questions)
        return merged

    async def _design_chunk_with_retries(
        self,
        request: ChapterTestGenerateRequest,
        evidence: EvidenceBundle,
        excluded_questions: list[str],
        revision_notes: list[str] | None,
    ) -> QuestionPaper:
        paper = await self._generate_with_retries(
            request, evidence, revision_notes
        )
        for repair_attempt in range(1, self._MAX_CHUNK_ATTEMPTS + 1):
            duplicates = self._excluded_items(paper, excluded_questions)
            invalid_coding = self._invalid_coding_items(paper)
            invalid_ids = {
                id(item)
                for item in [*duplicates, *invalid_coding]
            }
            invalid_items = [
                item for item in paper.items if id(item) in invalid_ids
            ]
            if not invalid_items:
                return paper
            if repair_attempt >= self._MAX_CHUNK_ATTEMPTS:
                invalid_qids = ",".join(item.qid for item in invalid_items)
                raise PaperDesignError(
                    f"question repair exhausted (qid={invalid_qids})"
                )
            kept = [item for item in paper.items if id(item) not in invalid_ids]
            repair_exclusions = [
                *excluded_questions,
                *(self._item_text(item) for item in kept),
            ]
            repair_request = request.model_copy(
                update={
                    "counts": self._counts_for_items(invalid_items, request.counts),
                    "excluded_questions": repair_exclusions,
                }
            )
            repair_notes = list(revision_notes or [])
            if duplicates:
                repair_notes.append(
                    "以下题号与章节小测题相同或高度近似，必须更换考查角度："
                    + "、".join(item.qid for item in duplicates)
                )
            if invalid_coding:
                repair_notes.append(
                    "以下编程题参考解未通过其样例或隐藏用例，必须重新设计题目、用例和参考解："
                    + "、".join(item.qid for item in invalid_coding)
                )
            replacement = await self._generate_with_retries(
                repair_request, evidence, repair_notes
            )
            replacement_items = [
                item.model_copy(update={"qid": f"r{repair_attempt}-{item.qid}"})
                for item in replacement.items
            ]
            paper = QuestionPaper(items=[*kept, *replacement_items])
            self._assert_counts(paper, request.counts)
        raise PaperDesignError("chunk duplicate repair failed")

    async def _generate_with_retries(
        self,
        request: ChapterTestGenerateRequest,
        evidence: EvidenceBundle,
        revision_notes: list[str] | None,
    ) -> QuestionPaper:
        notes = list(revision_notes or [])
        last_error: PaperDesignError | None = None
        for attempt in range(1, self._MAX_CHUNK_ATTEMPTS + 1):
            try:
                return await self._design_once(request, evidence, notes or None)
            except PaperDesignError as exc:
                last_error = exc
                if attempt >= self._MAX_CHUNK_ATTEMPTS:
                    raise
                notes.append(
                    f"本批第{attempt}稿失败：{str(exc)[:500]}。"
                    "请重新生成本批全部题目并严格修正。"
                )
        raise last_error or PaperDesignError("chunk generation failed")

    @staticmethod
    def _counts_for_items(
        items: list[object], template: PaperCounts
    ) -> PaperCounts:
        counts = {"choice": 0, "judge": 0, "fill": 0, "coding": 0}
        for item in items:
            item_type = str(getattr(item, "type", "")).lower()
            if item_type in counts:
                counts[item_type] += 1
        return PaperCounts(
            **counts,
            choice_score=template.choice_score,
            judge_score=template.judge_score,
            fill_score=template.fill_score,
            coding_score=template.coding_score,
        )

    async def _design_once(
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
        exclusions = self._exclusion_clause(request.excluded_questions)

        messages: list[dict[str, str]] = [{"role": "system", "content": system}]
        messages.extend(personalization_messages(snapshot))
        if request.focus_hint:
            messages.append({"role": "system", "content": f"出题侧重: {request.focus_hint}"})
        if targeting:
            messages.append({"role": "system", "content": targeting})
        if exclusions:
            messages.append({"role": "system", "content": exclusions})
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

    @staticmethod
    def _exclusion_clause(excluded_questions: list[str]) -> str | None:
        cleaned = [text.strip() for text in excluded_questions if text and text.strip()]
        if not cleaned:
            return None
        sample = "\n".join(f"- {text[:300]}" for text in cleaned[:200])
        return (
            "以下题目已在章节小测或本卷前序批次使用。禁止复用相同题干、标题或仅改数字/措辞"
            "的近似题；必须换考查角度：\n" + sample
        )

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
        body = self._normalize_qids(body)
        try:
            return QuestionPaper.model_validate(body)
        except Exception as exc:  # noqa: BLE001 - pydantic ValidationError → 出题失败
            raise PaperDesignError(f"paper schema invalid: {exc}") from exc

    @staticmethod
    def _normalize_qids(body: dict[str, Any]) -> dict[str, Any]:
        items = body.get("items")
        if not isinstance(items, list):
            return body
        normalized_items: list[Any] = []
        for item in items:
            if not isinstance(item, dict):
                normalized_items.append(item)
                continue
            normalized = dict(item)
            qid = normalized.get("qid")
            if isinstance(qid, int) and not isinstance(qid, bool):
                normalized["qid"] = str(qid)
            normalized_items.append(normalized)
        return {**body, "items": normalized_items}

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

    @classmethod
    def _split_counts(cls, counts: PaperCounts) -> list[PaperCounts]:
        remaining = {
            "choice": counts.choice,
            "judge": counts.judge,
            "fill": counts.fill,
            "coding": counts.coding,
        }
        limits = cls._MAX_CHUNK.as_dict()
        chunks: list[PaperCounts] = []
        while any(value > 0 for value in remaining.values()):
            values = {
                key: min(remaining[key], limits[key])
                for key in remaining
            }
            chunks.append(
                PaperCounts(
                    **values,
                    choice_score=counts.choice_score,
                    judge_score=counts.judge_score,
                    fill_score=counts.fill_score,
                    coding_score=counts.coding_score,
                )
            )
            for key, value in values.items():
                remaining[key] -= value
        return chunks

    @staticmethod
    def _signature(text: str) -> str:
        return re.sub(r"[\W_]+", "", text, flags=re.UNICODE).casefold()

    @classmethod
    def _item_signature(cls, item: object) -> str:
        stem = str(getattr(item, "stem", "") or "")
        title = str(getattr(item, "title", "") or "")
        statement = str(getattr(item, "statement_markdown", "") or "")
        return cls._signature(stem or f"{title} {statement}")

    @classmethod
    def _paper_signatures(cls, paper: QuestionPaper) -> list[str]:
        return [
            text
            for item in paper.items
            if (text := cls._item_signature(item))
        ]

    @classmethod
    def _paper_exclusion_texts(cls, paper: QuestionPaper) -> list[str]:
        return [
            text
            for item in paper.items
            if (text := cls._item_text(item))
        ]

    @staticmethod
    def _item_text(item: object) -> str:
        stem = str(getattr(item, "stem", "") or "")
        title = str(getattr(item, "title", "") or "")
        statement = str(getattr(item, "statement_markdown", "") or "")
        return stem or f"{title} {statement}".strip()

    @classmethod
    def _select_preferred(
        cls,
        candidates: list[PreferredCodingQuestion],
        used_ids: set[int],
        excluded_questions: list[str],
        limit: int,
        score: int,
    ) -> list[CodingItem]:
        if limit <= 0:
            return []
        excluded = {
            cls._signature(text)
            for text in excluded_questions
            if text and cls._signature(text)
        }
        selected: list[CodingItem] = []
        selected_signatures: set[str] = set()
        for candidate in candidates:
            if candidate.source_problem_id in used_ids:
                continue
            signature = cls._signature(
                f"{candidate.title} {candidate.statement_markdown}"
            )
            if not signature or signature in excluded or signature in selected_signatures:
                continue
            item = CodingItem(
                qid=f"bank-{candidate.source_problem_id}",
                title=candidate.title,
                statement_markdown=candidate.statement_markdown,
                samples=candidate.samples,
                hidden_test_cases=candidate.hidden_test_cases,
                reference_solution=candidate.reference_solution,
                kc_code=candidate.kc_code,
                score=score,
            )
            # 题库条目也必须先通过同一沙箱自测；历史题若样例/参考解不一致则跳过，
            # 由后续题库候选或 LLM 补足，不能让整卷在三审阶段反复失败。
            if not sandbox_self_test(QuestionPaper(items=[item])).passed:
                continue
            selected.append(item)
            selected_signatures.add(signature)
            if len(selected) >= limit:
                break
        return selected

    @classmethod
    def _assert_no_excluded(
        cls, paper: QuestionPaper, excluded_questions: list[str]
    ) -> None:
        excluded = {
            cls._signature(text)
            for text in excluded_questions
            if text and cls._signature(text)
        }
        for item in paper.items:
            signature = cls._item_signature(item)
            if not signature:
                continue
            if signature in excluded:
                raise PaperDesignError(
                    f"duplicate question signature detected (qid={item.qid})"
                )

    @classmethod
    def _excluded_items(
        cls, paper: QuestionPaper, excluded_questions: list[str]
    ) -> list[object]:
        excluded = {
            cls._comparison_key(text)
            for text in excluded_questions
            if text and cls._comparison_key(text)
        }
        return [
            item
            for item in paper.items
            if cls._comparison_key(cls._item_text(item)) in excluded
        ]

    @staticmethod
    def _comparison_key(text: str) -> str:
        normalized = text.casefold()
        replacements = {
            "以下": "下列",
            "哪一个": "哪个",
            "哪一项": "哪个",
            "哪项": "哪个",
            "说法中正确的是": "说法正确的是",
            "说法是正确的": "说法正确的是",
        }
        for source, target in replacements.items():
            normalized = normalized.replace(source, target)
        normalized = re.sub(r"\d+(?:\.\d+)?", "<num>", normalized)
        return PaperDesignerAgent._signature(normalized)

    @staticmethod
    def _invalid_coding_items(paper: QuestionPaper) -> list[object]:
        return [
            item
            for item in paper.coding_items()
            if not sandbox_self_test(QuestionPaper(items=[item])).passed
        ]

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
