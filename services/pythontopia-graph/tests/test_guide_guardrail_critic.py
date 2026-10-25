from __future__ import annotations

from pythontopia_graph.agents.guide_guardrail_critic import GuideGuardrailCritic


def test_rejects_submit_ready_solution_in_skeleton() -> None:
    card = {"skeleton": "n=int(input())\nprint(sum(range(1, n + 1)))"}

    result = GuideGuardrailCritic().check("skeleton_code", card)

    assert not result.passed
    assert "submit_ready" in result.reasons


def test_allows_todo_skeleton() -> None:
    card = {
        "skeleton": "n=int(input())\n# TODO: 累加 1..n\n# TODO: 输出答案\nprint(ans)"
    }

    result = GuideGuardrailCritic().check("skeleton_code", card)

    assert result.passed


def test_rejects_error_diagnosis_with_complete_corrected_code() -> None:
    card = {
        "root_cause": "你没有累加。",
        "fix_direction": "完整代码：```python\nn=int(input())\nprint(sum(range(1, n + 1)))\n```",
    }

    result = GuideGuardrailCritic().check("error_diagnosis", card)

    assert not result.passed
    assert "complete_corrected_code" in result.reasons


def test_rejects_transfer_problem_with_reference_solution() -> None:
    card = {
        "title": "迁移题",
        "description": "求和",
        "reference_solution_code": "print(sum(range(1, int(input()) + 1)))",
        "test_cases": [{"input": "1", "output": "1"}],
    }

    result = GuideGuardrailCritic().check("transfer_problem", card)

    assert not result.passed
    assert "reference_solution_leak" in result.reasons
