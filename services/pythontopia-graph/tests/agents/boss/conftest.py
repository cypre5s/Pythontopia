"""Use fixed, non-executing responses for Boss unit tests."""

from __future__ import annotations

from contextlib import contextmanager
from typing import Any

import httpx
import pytest
from pythontopia_graph.schemas.chapter_test import QuestionPaper, SelfTestReport


_ADD_TWO_NUMBERS = "a, b = map(int, input().split())\nprint(a + b)"
_PLUS_ONE = "n = int(input())\nprint(n + 1)"
_DOUBLE = "print(int(input()) * 2)"
_ECHO_INPUT = "print(input())"
_MULTILINE_SUM = (
    "n = int(input())\ntotal = 0\nfor _ in range(n):\n"
    "    total += int(input())\nprint(total)"
)
_COUNT_TO_THREE = "for i in range(3):\n    print(i)"
_PRINT_ALWAYS_ZERO = "print('always 0')"
_PRINT_ONE = "x = 1\nprint(x)"
_PRINT_KC001_FACT = 'print("神威·太湖之光")'
_KC001_FACT_PLUS_ONE = "n = int(input())\nprint('神威·太湖之光', n + 1)"
_KC010_LABEL_PLUS_ONE = "x = int(input())\nprint('变量与运算符', x + 1)"
_KC018_FORMAT = 'name = input()\nscore = float(input())\nprint("{}: {:.1f}".format(name, score))'
_KC023_FORMAT = (
    "# 字符串格式化：用 f-string 控制小数位\n"
    'product = input()\nprice = float(input())\nprint(f"{product}:{price:.2f}")'
)
_KC039_RANDOM = (
    "import random\nseed = int(input())\nitems = input().split()\n"
    "random.seed(seed)\nprint(random.choice(items))"
)
_KC063_GCD = (
    "# 模块化编程：调用 math 模块的功能\n"
    "import math\na, b = map(int, input().split())\nprint(math.gcd(a, b))"
)

# Test snippets map to recorded outcomes; unknown source is a fixed runtime error.
_FIXED_STDOUT: dict[tuple[str, str], str] = {
    (_ADD_TWO_NUMBERS, "1 2"): "3\n",
    (_ADD_TWO_NUMBERS, "10 -7"): "3\n",
    (_ADD_TWO_NUMBERS, "0 0"): "0\n",
    (_ADD_TWO_NUMBERS, "10 20"): "30\n",
    (_ADD_TWO_NUMBERS, "100 -50"): "50\n",
    (_ADD_TWO_NUMBERS, "100 200"): "300\n",
    (_ADD_TWO_NUMBERS, "-1 1"): "0\n",
    (_PLUS_ONE, "1"): "2\n",
    (_PLUS_ONE, "3"): "4\n",
    (_PLUS_ONE, "4"): "5\n",
    (_PLUS_ONE, "5"): "6\n",
    (_PLUS_ONE, "9"): "10\n",
    (_DOUBLE, "2"): "4\n",
    (_DOUBLE, "3"): "6\n",
    (_DOUBLE, "4"): "8\n",
    **{(_ECHO_INPUT, value): f"{value}\n" for value in ("a", "b", "c", "x", "y", "z")},
    (_MULTILINE_SUM, "3\n1\n2\n3"): "6\n",
    (_MULTILINE_SUM, "2\n5\n5"): "10\n",
    (_MULTILINE_SUM, "1\n42"): "42\n",
    (_COUNT_TO_THREE, ""): "0\n1\n2\n",
    (_PRINT_ALWAYS_ZERO, "1 2"): "always 0\n",
    (_PRINT_ALWAYS_ZERO, "4 5"): "always 0\n",
    (_PRINT_ALWAYS_ZERO, "0 0"): "always 0\n",
    (_PRINT_ALWAYS_ZERO, ""): "always 0\n",
    **{(_PRINT_ONE, value): "1\n" for value in ("1", "2", "3")},
    **{(_PRINT_KC001_FACT, value): "神威·太湖之光\n" for value in ("", "ignored", "\n\n")},
    (_KC001_FACT_PLUS_ONE, "1"): "神威·太湖之光 2\n",
    (_KC001_FACT_PLUS_ONE, "5"): "神威·太湖之光 6\n",
    (_KC001_FACT_PLUS_ONE, "9"): "神威·太湖之光 10\n",
    (_KC010_LABEL_PLUS_ONE, "1"): "变量与运算符 2\n",
    (_KC010_LABEL_PLUS_ONE, "2"): "变量与运算符 3\n",
    (_KC010_LABEL_PLUS_ONE, "3"): "变量与运算符 4\n",
    (_KC018_FORMAT, "林\n86.26"): "林: 86.3\n",
    (_KC018_FORMAT, "Mia\n0"): "Mia: 0.0\n",
    (_KC018_FORMAT, "张三\n100"): "张三: 100.0\n",
    (_KC023_FORMAT, "茶\n12.5"): "茶:12.50\n",
    (_KC023_FORMAT, "笔记本\n0"): "笔记本:0.00\n",
    (_KC023_FORMAT, "book\n3.456"): "book:3.46\n",
    (_KC039_RANDOM, "0\napple pear mango"): "pear\n",
    (_KC039_RANDOM, "7\nred blue green"): "blue\n",
    (_KC039_RANDOM, "42\na b c"): "c\n",
    (_KC063_GCD, "18 24"): "6\n",
    (_KC063_GCD, "7 13"): "1\n",
    (_KC063_GCD, "-12 8"): "4\n",
}


@pytest.fixture(autouse=True)
def fake_piston_endpoint(monkeypatch: pytest.MonkeyPatch) -> list[tuple[str, str]]:
    """Answer known unit snippets without interpreting or executing source."""
    monkeypatch.setenv("PYT_PISTON_URL", "http://piston.test")
    monkeypatch.setenv("PYT_PISTON_PYTHON_VERSION", "3.11.13")
    submissions: list[tuple[str, str]] = []

    def post(
        url: str,
        *,
        json: dict[str, Any],
        headers: dict[str, str],
        timeout: float,
        trust_env: bool,
    ) -> httpx.Response:
        del headers, timeout
        assert trust_env is False
        source = json["files"][0]["content"]
        stdin = json["stdin"]
        submissions.append((source, stdin))
        stdout = _FIXED_STDOUT.get((source, stdin))
        if source == "while True:\n    pass":
            run = {"status": "TO", "code": -1, "stdout": "", "stderr": "time limit exceeded"}
        elif source == "raise SystemExit(1)":
            run = {"status": "RE", "code": 1, "stdout": "", "stderr": "exit status 1"}
        elif source == "this is not python":
            run = {"status": "RE", "code": 1, "stdout": "", "stderr": "SyntaxError: invalid syntax"}
        elif stdout is not None:
            run = {"status": None, "code": 0, "stdout": stdout, "stderr": ""}
        else:
            run = {
                "status": "RE",
                "code": 1,
                "stdout": "",
                "stderr": "no fixed response for this test snippet",
            }
        return httpx.Response(
            200,
            json={"run": run},
            request=httpx.Request("POST", url),
        )

    @contextmanager
    def stream(method: str, url: str, **kwargs: Any):
        del method
        yield post(url, **kwargs)

    monkeypatch.setattr("pythontopia_graph.agents.boss.execution_sandbox.httpx.stream", stream)
    return submissions


@pytest.fixture
def chapter_test_sandbox(monkeypatch: pytest.MonkeyPatch) -> None:
    """Stub paper review self-tests; their sandbox contract has separate tests."""
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
