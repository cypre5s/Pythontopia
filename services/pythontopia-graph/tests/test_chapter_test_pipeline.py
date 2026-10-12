"""章末大测试生成图：dry-run 合规题面 + 三审失败各分支（hermetic：mock LLM + mock RAG）。"""

from __future__ import annotations

import copy

import pytest

from pythontopia_graph.agents.paper_golden import build_golden_paper
from pythontopia_graph.agents import paper_reviews
from pythontopia_graph.agents.paper_pipeline import (
    ChapterTestGraph,
    ChapterTestGraphError,
)
from pythontopia_graph.agents.paper_reviews import (
    grounding_critic,
    safety_critic,
)
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceBundle,
    RagHit,
    StaticEvidenceFetcher,
)
from pythontopia_graph.schemas.chapter_test import (
    ChapterTestGenerateRequest,
    CodingItem,
    CodingSample,
    JudgeItem,
    PaperCounts,
    PreferredCodingQuestion,
    QuestionPaper,
)

pytestmark = pytest.mark.usefixtures("chapter_test_sandbox")


class StubProvider(LlmProvider):
    """确定式 mock provider：依次吐预置 content_json（None 模拟无 JSON）。"""

    def __init__(self, payloads: list[dict | None]) -> None:
        self._payloads = list(payloads)
        self.calls = 0

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        self.calls += 1
        payload = self._payloads.pop(0) if self._payloads else None
        return LlmResponse(content_json=payload)


def _request(**kw) -> ChapterTestGenerateRequest:
    base: dict = dict(user_id=1, chapter=1, kc_codes=["PY43-KC-001", "PY43-KC-002"])
    base.update(kw)
    return ChapterTestGenerateRequest(**base)


def _fetcher(pairs: list[tuple[int, int, str]]) -> StaticEvidenceFetcher:
    hits = [
        RagHit(
            chunk_id=f"c{pid}",
            page_id=pid,
            page_no=no,
            chapter_id=1,
            kc_codes=[kc],
            text=f"第{no}页讲解 {kc}",
            score=0.8,
        )
        for pid, no, kc in pairs
    ]
    return StaticEvidenceFetcher(hits)


_DEFAULT_PAIRS = [(101, 12, "PY43-KC-001"), (102, 13, "PY43-KC-002")]


def _golden_dict(counts: PaperCounts, pages: list[int]) -> dict:
    paper = build_golden_paper(
        chapter=1,
        kc_codes=["PY43-KC-001", "PY43-KC-002"],
        counts=counts,
        evidence_page_ids=pages,
    )
    return paper.model_dump(mode="json")


# --------------------------------------------------------------------------- #
# 成功路径 + dry-run 合规题面断言
# --------------------------------------------------------------------------- #
@pytest.mark.asyncio
async def test_pipeline_dry_run_produces_contract_compliant_paper() -> None:
    paper_dict = _golden_dict(PaperCounts(), [101, 102])
    graph = ChapterTestGraph(
        provider=StubProvider([paper_dict]),
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )
    resp = await graph.run(_request())

    assert resp.status == "SUCCESS"
    paper = resp.questions
    # 默认构成：5 选 + 3 判 + 3 填 + 1 编程 = 12 题 / 100 分
    assert len(paper.items) == 12
    assert paper.type_counts() == {"CHOICE": 5, "JUDGE": 3, "FILL": 3, "CODING": 1}
    assert paper.total_score() == 100

    evidence_set = {101, 102}
    for item in paper.objective_items():
        assert item.evidence_page_ids, f"{item.qid} 客观题必须带证据页"
        assert set(item.evidence_page_ids) <= evidence_set

    coding = paper.coding_items()
    assert len(coding) == 1
    assert coding[0].reference_solution
    assert coding[0].hidden_test_cases

    meta = resp.generator_meta
    assert meta.run_id.startswith("chtest-")
    assert meta.grounding.passed and meta.safety.passed and meta.self_test.passed
    assert meta.self_test.coding_items == 1
    assert meta.self_test.cases_total == 3  # 1 sample + 2 hidden
    assert meta.self_test.cases_passed == 3
    assert meta.evidence_page_ids == [101, 102]
    assert meta.total_score == 100
    assert meta.agent.startswith("PaperDesignerAgent")
    assert {step.node for step in meta.graph_trace} >= {
        "evidence_fetcher",
        "paper_designer",
        "grounding_critic",
        "sandbox_self_test",
        "safety_critic",
    }
    assert resp.profile_snapshot["profile_brief"]["knowledge_level"] == 3


@pytest.mark.asyncio
async def test_pipeline_honors_custom_counts() -> None:
    counts = PaperCounts(choice=2, judge=1, fill=1, coding=1)
    graph = ChapterTestGraph(
        provider=StubProvider([_golden_dict(counts, [101, 102])]),
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )
    resp = await graph.run(_request(counts=counts))
    assert resp.questions.type_counts() == {"CHOICE": 2, "JUDGE": 1, "FILL": 1, "CODING": 1}
    assert resp.questions.total_score() == 2 * 8 + 5 + 5 + 30


@pytest.mark.asyncio
async def test_pipeline_normalizes_numeric_qids_from_llm() -> None:
    counts = PaperCounts(choice=1, judge=0, fill=0, coding=0)
    payload = _golden_dict(counts, [101, 102])
    payload["items"][0]["qid"] = 1
    graph = ChapterTestGraph(
        provider=StubProvider([payload]),
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )

    resp = await graph.run(_request(counts=counts))

    assert resp.questions.items[0].qid == "1"


@pytest.mark.asyncio
async def test_pipeline_retries_only_failed_generation_chunk() -> None:
    counts = PaperCounts(choice=1, judge=0, fill=0, coding=0)
    valid = _golden_dict(counts, [101, 102])
    provider = StubProvider([None, valid])
    graph = ChapterTestGraph(
        provider=provider,
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )

    resp = await graph.run(_request(counts=counts))

    assert provider.calls == 2
    assert len(resp.questions.items) == 1


@pytest.mark.asyncio
async def test_pipeline_repairs_only_questions_that_match_exclusions() -> None:
    counts = PaperCounts(choice=2, judge=0, fill=0, coding=0)
    initial = _golden_dict(counts, [101, 102])
    excluded_stem = initial["items"][0]["stem"]
    initial["items"][1]["stem"] = "保留题：表达式 8 // 3 的结果是什么？"
    replacement = _golden_dict(
        PaperCounts(choice=1, judge=0, fill=0, coding=0), [101, 102]
    )
    replacement["items"][0]["stem"] = "补充题：表达式 8 % 3 的结果是什么？"
    provider = StubProvider([initial, replacement])
    graph = ChapterTestGraph(
        provider=provider,
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )

    resp = await graph.run(
        _request(counts=counts, excluded_questions=[excluded_stem])
    )

    stems = [item.stem for item in resp.questions.items]
    assert provider.calls == 2
    assert excluded_stem not in stems
    assert "保留题：表达式 8 // 3 的结果是什么？" in stems
    assert "补充题：表达式 8 % 3 的结果是什么？" in stems


@pytest.mark.asyncio
async def test_pipeline_repairs_near_duplicate_wording_from_quiz() -> None:
    counts = PaperCounts(choice=1, judge=0, fill=0, coding=0)
    excluded_stem = "在Python中，下列哪个是合法的变量名？"
    near_duplicate = _golden_dict(counts, [101, 102])
    near_duplicate["items"][0]["stem"] = "在Python中，以下哪个是合法的变量名？"
    replacement = _golden_dict(counts, [101, 102])
    replacement["items"][0]["stem"] = "表达式 8 % 3 的计算结果是什么？"
    provider = StubProvider([near_duplicate, replacement])
    graph = ChapterTestGraph(
        provider=provider,
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )

    resp = await graph.run(
        _request(counts=counts, excluded_questions=[excluded_stem])
    )

    assert provider.calls == 2
    assert resp.questions.items[0].stem == "表达式 8 % 3 的计算结果是什么？"


@pytest.mark.asyncio
async def test_pipeline_repairs_only_coding_question_that_fails_self_test() -> None:
    counts = PaperCounts(choice=1, judge=0, fill=0, coding=1)
    initial = _golden_dict(counts, [101, 102])
    initial_coding = next(item for item in initial["items"] if item["type"] == "CODING")
    initial_coding["reference_solution"] = "print('wrong')"
    replacement = _golden_dict(
        PaperCounts(choice=0, judge=0, fill=0, coding=1), [101, 102]
    )
    provider = StubProvider([initial, replacement])
    graph = ChapterTestGraph(
        provider=provider,
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )

    resp = await graph.run(_request(counts=counts))

    assert provider.calls == 2
    assert resp.generator_meta.self_test.passed
    assert len(resp.questions.items) == 2


@pytest.mark.asyncio
async def test_pipeline_splits_final_99_questions_into_three_batches() -> None:
    chunk = PaperCounts(choice=10, judge=10, fill=10, coding=3)
    payload = _golden_dict(chunk, [101, 102])
    payloads = []
    for batch in range(1, 4):
        variant = copy.deepcopy(payload)
        marker = ("甲", "乙", "丙")[batch - 1] * 40
        for item in variant["items"]:
            if item["type"] == "CODING":
                item["title"] = f"{marker}{item['title']}"
                item["statement_markdown"] = f"{marker}。{item['statement_markdown']}"
            else:
                item["stem"] = f"{marker}。{item['stem']}"
        payloads.append(variant)
    provider = StubProvider(payloads)
    graph = ChapterTestGraph(
        provider=provider,
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )

    resp = await graph.run(
        _request(counts=PaperCounts(choice=30, judge=30, fill=30, coding=9))
    )

    assert provider.calls == 3
    assert len(resp.questions.items) == 99
    assert resp.questions.type_counts() == {
        "CHOICE": 30,
        "JUDGE": 30,
        "FILL": 30,
        "CODING": 9,
    }
    qids = [item.qid for item in resp.questions.items]
    assert len(qids) == len(set(qids))


@pytest.mark.asyncio
async def test_pipeline_rejects_questions_reused_from_chapter_quiz() -> None:
    counts = PaperCounts(choice=1, judge=0, fill=0, coding=0)
    payload = _golden_dict(counts, [101, 102])
    reused_stem = payload["items"][0]["stem"]
    graph = ChapterTestGraph(
        provider=StubProvider([payload, payload, payload]),
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )

    with pytest.raises(ChapterTestGraphError) as exc:
        await graph.run(
            _request(counts=counts, excluded_questions=[reused_stem])
        )

    assert exc.value.detail.stage == "paper_designer"
    assert "repair exhausted" in exc.value.detail.message.lower()


@pytest.mark.asyncio
async def test_pipeline_prioritizes_problem_bank_coding_questions() -> None:
    counts = PaperCounts(choice=1, judge=0, fill=0, coding=1)
    generated = _golden_dict(
        PaperCounts(choice=1, judge=0, fill=0, coding=0), [101, 102]
    )
    bank = PreferredCodingQuestion(
        source_problem_id=476,
        title="圆面积计算",
        statement_markdown="读入半径并输出圆面积。",
        samples=[CodingSample(input="1", output="3.1415")],
        hidden_test_cases=[CodingSample(input="2", output="12.5660")],
        reference_solution=(
            "r = float(input())\nprint(f'{3.1415 * r * r:.4f}')"
        ),
        kc_code="PY43-KC-002",
    )
    provider = StubProvider([generated])
    graph = ChapterTestGraph(
        provider=provider,
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )

    resp = await graph.run(
        _request(counts=counts, preferred_coding_questions=[bank])
    )

    assert provider.calls == 1
    coding = resp.questions.coding_items()
    assert len(coding) == 1
    assert coding[0].qid == "bank-476"
    assert coding[0].title == "圆面积计算"


# --------------------------------------------------------------------------- #
# 失败分支 → 结构化错误（路由转 502）
# --------------------------------------------------------------------------- #
@pytest.mark.asyncio
async def test_designer_no_json_fails_at_designer_stage() -> None:
    graph = ChapterTestGraph(
        provider=StubProvider([None]),
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )
    with pytest.raises(ChapterTestGraphError) as exc:
        await graph.run(_request())
    assert exc.value.detail.stage == "paper_designer"


@pytest.mark.asyncio
async def test_count_mismatch_fails_at_designer_stage() -> None:
    # provider 吐 1/1/1/1，但请求要默认 5/3/3/1 → 题量不符
    mismatched = _golden_dict(PaperCounts(choice=1, judge=1, fill=1, coding=1), [101, 102])
    graph = ChapterTestGraph(
        provider=StubProvider([mismatched]),
        evidence_fetcher=_fetcher(_DEFAULT_PAIRS),
    )
    with pytest.raises(ChapterTestGraphError) as exc:
        await graph.run(_request())
    assert exc.value.detail.stage == "paper_designer"


@pytest.mark.asyncio
async def test_objective_unknown_page_fails_grounding() -> None:
    # golden 客观题盖了 [999]，但 RAG 只有 101 → 引用审查失败
    bad = _golden_dict(PaperCounts(choice=1, judge=0, fill=0, coding=0), [999])
    graph = ChapterTestGraph(
        provider=StubProvider([bad]),
        evidence_fetcher=_fetcher([(101, 12, "PY43-KC-001")]),
    )
    with pytest.raises(ChapterTestGraphError) as exc:
        await graph.run(_request(counts=PaperCounts(choice=1, judge=0, fill=0, coding=0)))
    detail = exc.value.detail
    assert detail.stage == "grounding_critic"
    assert detail.grounding is not None and not detail.grounding.passed


@pytest.mark.asyncio
async def test_objective_without_evidence_fails_grounding() -> None:
    # RAG 完全无命中却出客观题 → 失败（防幻觉：不允许凭空给客观题）
    bad = _golden_dict(PaperCounts(choice=1, judge=0, fill=0, coding=0), [101])
    graph = ChapterTestGraph(
        provider=StubProvider([bad]),
        evidence_fetcher=StaticEvidenceFetcher([]),
    )
    with pytest.raises(ChapterTestGraphError) as exc:
        await graph.run(_request(counts=PaperCounts(choice=1, judge=0, fill=0, coding=0)))
    assert exc.value.detail.stage == "grounding_critic"


@pytest.mark.asyncio
async def test_bad_reference_solution_fails_after_targeted_repair_exhausted() -> None:
    paper = QuestionPaper(
        items=[
            CodingItem(
                qid="p1",
                title="两数之和",
                statement_markdown="读入两个整数，输出它们的和。",
                samples=[CodingSample(input="1 2", output="3")],
                hidden_test_cases=[CodingSample(input="2 2", output="4")],
                reference_solution="print(0)",  # 错误参考解
                kc_code="PY43-KC-001",
                score=30,
            )
        ]
    )
    counts = PaperCounts(choice=0, judge=0, fill=0, coding=1)
    graph = ChapterTestGraph(
        provider=StubProvider(
            [
                paper.model_dump(mode="json"),
                paper.model_dump(mode="json"),
                paper.model_dump(mode="json"),
            ]
        ),
        evidence_fetcher=StaticEvidenceFetcher([]),  # 无客观题，无需证据
    )
    with pytest.raises(ChapterTestGraphError) as exc:
        await graph.run(_request(counts=counts))
    detail = exc.value.detail
    assert detail.stage == "paper_designer"
    assert "repair exhausted" in detail.message


@pytest.mark.asyncio
async def test_pii_in_stem_fails_safety() -> None:
    paper = QuestionPaper(
        items=[
            JudgeItem(
                qid="j1",
                stem="如有疑问请联系 test@example.com：Python 区分大小写。",
                answer=True,
                kc_code="PY43-KC-001",
                evidence_page_ids=[101],
                score=5,
            )
        ]
    )
    counts = PaperCounts(choice=0, judge=1, fill=0, coding=0)
    graph = ChapterTestGraph(
        provider=StubProvider([paper.model_dump(mode="json")]),
        evidence_fetcher=_fetcher([(101, 12, "PY43-KC-001")]),
    )
    with pytest.raises(ChapterTestGraphError) as exc:
        await graph.run(_request(counts=counts))
    detail = exc.value.detail
    assert detail.stage == "safety_critic"
    assert detail.safety is not None and "PII_DETECTED" in detail.safety.reasons


def test_numeric_coding_sample_is_not_misclassified_as_pii() -> None:
    paper = QuestionPaper(
        items=[
            CodingItem(
                qid="p1",
                title="整数拆分",
                statement_markdown="读入一个整数并原样输出。",
                samples=[CodingSample(input="13812345678", output="13812345678")],
                hidden_test_cases=[CodingSample(input="1", output="1")],
                reference_solution="print(input())",
                kc_code="PY43-KC-001",
                score=30,
            )
        ]
    )

    assert safety_critic(paper).passed


# --------------------------------------------------------------------------- #
# golden 兜底构造器 + 三审节点（不经 LLM）
# --------------------------------------------------------------------------- #
def test_golden_paper_passes_all_reviews() -> None:
    paper = build_golden_paper(
        chapter=1,
        kc_codes=["PY43-KC-001"],
        counts=PaperCounts(),
        evidence_page_ids=[101],
    )
    evidence = EvidenceBundle(
        chunks=[
            RagHit(
                chunk_id="c101",
                page_id=101,
                page_no=12,
                chapter_id=1,
                kc_codes=["PY43-KC-001"],
                text="第12页",
                score=0.9,
            )
        ]
    )
    assert grounding_critic(paper, evidence).passed
    self_test = paper_reviews.sandbox_self_test(paper)
    assert self_test.passed and self_test.cases_passed == self_test.cases_total
    assert safety_critic(paper).passed


def test_golden_paper_rejects_objective_without_pages() -> None:
    with pytest.raises(ValueError):
        build_golden_paper(
            chapter=1,
            kc_codes=["PY43-KC-001"],
            counts=PaperCounts(choice=1, judge=0, fill=0, coding=0),
            evidence_page_ids=[],
        )
