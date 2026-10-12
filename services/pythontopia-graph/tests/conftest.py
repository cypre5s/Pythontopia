"""Pytest defaults: isolate graph route tests from host PYT_RAG_ADMIN_TOKEN."""
from __future__ import annotations

import pytest
from pythontopia_graph.schemas.chapter_test import QuestionPaper, SelfTestReport


@pytest.fixture(autouse=True)
def _isolate_internal_token_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """Unless a test sets its own token, behave like dev without service auth.

    内部鉴权现在默认 fail-closed（无令牌即拒绝）；测试默认显式开启 dev 放行逃生舱，
    使不关心鉴权的路由测试照常运行，而 test_internal_auth.py 自行设置令牌验证强制路径。
    """
    monkeypatch.delenv("PYT_RAG_ADMIN_TOKEN", raising=False)
    monkeypatch.setenv("PYT_INTERNAL_AUTH_OPTIONAL", "1")


@pytest.fixture
def chapter_test_sandbox(monkeypatch: pytest.MonkeyPatch) -> None:
    """Stub remote judging in paper graph tests; Piston contract is tested separately."""
    def self_test(paper: QuestionPaper) -> SelfTestReport:
        coding = paper.coding_items()
        case_count = sum(len(item.samples) + len(item.hidden_test_cases) for item in coding)
        bad_solutions = {"print('wrong')", "print(0)"}
        passed = all(item.reference_solution not in bad_solutions for item in coding)
        return SelfTestReport(
            passed=passed,
            coding_items=len(coding),
            cases_total=case_count,
            cases_passed=case_count if passed else 0,
            reasons=[] if passed else ["self_test:p1:expected output mismatch"],
        )

    for target in (
        "pythontopia_graph.agents.paper_reviews.sandbox_self_test",
        "pythontopia_graph.agents.paper_designer.sandbox_self_test",
        "pythontopia_graph.agents.paper_pipeline.sandbox_self_test",
    ):
        monkeypatch.setattr(target, self_test)
