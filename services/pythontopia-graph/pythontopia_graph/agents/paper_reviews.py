"""章末大测试三审节点：引用审查、沙箱自测、安全审查。

复用既有三审基建，不重复造轮子：
- 引用审查（grounding）：客观题 ``evidence_page_ids`` 必须落在 RAG 命中页集合内（``EvidenceBundle``）。
- 沙箱自测（self_test）：复用判题沙箱 ``agents.boss.evaluator.evaluate_student_code`` 跑
  ``reference_solution`` 过全部 samples + hidden_test_cases（§4.1 编程题防幻觉）。
- 安全审查（safety）：复用 ``nodes.safety_critic`` 的泄答/歧视/PII 常量，扫学生可见题面与参考解。
"""

from __future__ import annotations

from pythontopia_graph.agents.boss.evaluator import evaluate_student_code
from pythontopia_graph.agents.boss.schemas import TestCase as BossTestCase
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.nodes.safety_critic import (
    ANSWER_LEAK_KEYWORDS,
    DISCRIMINATION_KEYWORDS,
    PII_PATTERNS,
)
from pythontopia_graph.schemas.chapter_test import (
    GroundingReport,
    QuestionPaper,
    SafetyReport,
    SelfTestReport,
)

# 与 nodes.safety_critic 的 Handout 危险代码检查保持一致（os.system / rm -rf）。
_DANGEROUS_CODE_TOKENS = ("os.system", "rm -rf")


def grounding_critic(paper: QuestionPaper, evidence: EvidenceBundle) -> GroundingReport:
    """客观题(CHOICE/JUDGE/FILL)必须携带 RAG 校验过的 evidence_page_ids。

    无客观题 → 通过；有客观题但 RAG 无证据 → 失败（不允许凭空给客观题，防幻觉）。
    """
    objective = paper.objective_items()
    if not objective:
        return GroundingReport(passed=True, no_evidence_found=evidence.no_evidence_found)

    evidence_pages = set(evidence.page_ids)
    if evidence.no_evidence_found or not evidence_pages:
        return GroundingReport(
            passed=False,
            no_evidence_found=evidence.no_evidence_found,
            reasons=["no RAG evidence available to ground objective items"],
        )

    reasons: list[str] = []
    for item in objective:
        if not item.evidence_page_ids:
            reasons.append(f"missing_evidence:{item.qid}")
            continue
        unknown = [pid for pid in item.evidence_page_ids if pid not in evidence_pages]
        for pid in unknown:
            reasons.append(f"unknown_evidence_page:{item.qid}:{pid}")
    return GroundingReport(passed=not reasons, reasons=reasons, no_evidence_found=False)


def sandbox_self_test(paper: QuestionPaper) -> SelfTestReport:
    """逐个 CODING 题在判题沙箱跑 reference_solution，过 samples + hidden_test_cases 全部用例。"""
    coding = paper.coding_items()
    reasons: list[str] = []
    cases_total = 0
    cases_passed = 0
    for item in coding:
        cases = [BossTestCase(input=s.input, expected_output=s.output) for s in item.samples]
        cases += [
            BossTestCase(input=h.input, expected_output=h.output)
            for h in item.hidden_test_cases
        ]
        result = evaluate_student_code(item.reference_solution, cases)
        cases_total += result.total_count
        cases_passed += result.passed_count
        if not result.passed:
            if result.runtime_error:
                reasons.append(f"self_test:{item.qid}:{result.runtime_error}")
            elif result.first_failure is not None:
                f = result.first_failure
                detail = (
                    f"timeout(case{f.index})"
                    if f.timed_out
                    else f"case{f.index} expected={f.expected!r} got={f.actual!r}"
                )
                reasons.append(f"self_test:{item.qid}:{detail}")
            else:
                reasons.append(f"self_test:{item.qid}:failed")
    return SelfTestReport(
        passed=not reasons,
        coding_items=len(coding),
        cases_total=cases_total,
        cases_passed=cases_passed,
        reasons=reasons,
    )


def safety_critic(paper: QuestionPaper) -> SafetyReport:
    """泄答关键词（学生可见题面）、歧视、PII（含解析）、参考解危险代码。"""
    visible_parts: list[str] = []   # 学生可见题面：泄答关键词扫描
    full_parts: list[str] = []      # 可见 + 解析/答案：PII / 歧视扫描
    pii_parts: list[str] = []       # 不含纯数值测试用例，避免把输入数据误判为手机号/证件号
    code_parts: list[str] = []      # 参考解：危险代码扫描

    for item in paper.items:
        if item.type == "CHOICE":
            visible_parts.extend([item.stem, *item.options])
            full_parts.append(item.explain)
            pii_parts.extend([item.stem, *item.options, item.explain])
        elif item.type == "JUDGE":
            visible_parts.append(item.stem)
            full_parts.append(item.explain)
            pii_parts.extend([item.stem, item.explain])
        elif item.type == "FILL":
            visible_parts.append(item.stem)
            full_parts.extend([item.explain, *item.answers])
            pii_parts.extend([item.stem, item.explain, *item.answers])
        elif item.type == "CODING":
            visible_parts.extend([item.title, item.statement_markdown])
            pii_parts.extend([item.title, item.statement_markdown])
            for sample in item.samples:
                visible_parts.extend([sample.input, sample.output])
            code_parts.append(item.reference_solution)

    visible = "\n".join(p for p in visible_parts if p)
    full = "\n".join(p for p in (visible_parts + full_parts) if p)
    pii_text = "\n".join(p for p in pii_parts if p)
    code = "\n".join(code_parts).lower()

    reasons: list[str] = []
    lowered_visible = visible.lower()
    for keyword in ANSWER_LEAK_KEYWORDS:
        if keyword in lowered_visible:
            reasons.append(f"ANSWER_LEAK:{keyword}")
    for keyword in DISCRIMINATION_KEYWORDS:
        if keyword in full:
            reasons.append(f"DISCRIMINATION:{keyword}")
    for pattern in PII_PATTERNS:
        if pattern.search(pii_text):
            reasons.append("PII_DETECTED")
            break
    for token in _DANGEROUS_CODE_TOKENS:
        if token in code:
            reasons.append(f"DANGEROUS_CODE:{token}")
    return SafetyReport(passed=not reasons, reasons=reasons)
