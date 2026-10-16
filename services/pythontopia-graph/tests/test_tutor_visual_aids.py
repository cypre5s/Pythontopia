"""TutorAgent 图解选型（mermaid / ascii / svg）与消毒校验测试。

FakeProvider 直接返回结构化 `{kind, source}`，不触网；恶意/非法产出必须被拒绝并
回退确定式 mermaid（绝不下发带毒源码）。
"""

from __future__ import annotations

from typing import Any

from pythontopia_graph.agents.tutor_agent import TutorAgent
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse


class DiagramStubProvider(LlmProvider):
    """chat() 返回预置 JSON；chat_stream 不参与本测试。"""

    def __init__(self, payload: dict[str, Any]) -> None:
        self._payload = payload

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=self._payload)

    async def chat_stream(self, messages, **kwargs):  # type: ignore[override]
        raise AssertionError("chat_stream 不应被 build_diagram 调用")
        yield ""  # pragma: no cover


VALID_SVG = (
    '<svg viewBox="0 0 100 60" xmlns="http://www.w3.org/2000/svg">'
    '<rect x="4" y="4" width="40" height="20" fill="#f1c453"/>'
    '<text x="8" y="18">lst[0]</text></svg>'
)


def test_wants_diagram_triggers_for_ascii_and_svg() -> None:
    assert TutorAgent.wants_diagram("用 ascii 画一下列表索引")
    assert TutorAgent.wants_diagram("给我一张字符画演示切片")
    assert TutorAgent.wants_diagram("用 svg 画个坐标示意")
    assert not TutorAgent.wants_diagram("for 循环怎么用")


async def test_build_diagram_ascii_kind() -> None:
    art = "┌───┬───┐\n│ 0 │ 1 │\n└───┴───┘"
    agent = TutorAgent(DiagramStubProvider({"kind": "ascii", "source": art}))

    aids = await agent.build_diagram("用字符画讲列表索引")

    assert len(aids) == 1
    aid = aids[0]
    assert aid.kind == "ascii"
    assert aid.ascii_art == art
    assert aid.caption.startswith("字符示意图")


async def test_build_diagram_valid_svg_kind() -> None:
    agent = TutorAgent(DiagramStubProvider({"kind": "svg", "source": VALID_SVG}))

    aids = await agent.build_diagram("用 svg 画列表内存示意")

    assert aids[0].kind == "svg"
    assert aids[0].svg is not None
    assert aids[0].svg.startswith("<svg")


async def test_malicious_svg_falls_back_to_mermaid() -> None:
    """带 <script> / 事件属性 / 外链 的 svg 一律拒绝 → 回退确定式 mermaid。"""
    for bad in (
        '<svg viewBox="0 0 10 10"><script>alert(1)</script></svg>',
        '<svg viewBox="0 0 10 10"><rect onload="alert(1)"/></svg>',
        '<svg viewBox="0 0 10 10"><a href="https://evil.example"><text>x</text></a></svg>',
        '<svg viewBox="0 0 10 10"><rect fill="url(https://evil.example/paint.svg#x)"/></svg>',
        '<svg viewBox="0 0 10 10"><rect fill="u\\72l(https://evil.example/paint.svg#x)"/></svg>',
        '<svg viewBox="0 0 10 10"><foreignObject><body/></foreignObject></svg>',
        "<svg><rect/></svg>",  # 缺 viewBox 也拒绝（保证可缩放渲染）
    ):
        agent = TutorAgent(DiagramStubProvider({"kind": "svg", "source": bad}))
        aids = await agent.build_diagram("画个图")
        assert aids[0].kind == "mermaid", bad
        assert aids[0].svg is None
        assert aids[0].mermaid is not None


async def test_oversize_ascii_falls_back_to_mermaid() -> None:
    too_long = "\n".join("x" * 10 for _ in range(60))
    agent = TutorAgent(DiagramStubProvider({"kind": "ascii", "source": too_long}))

    aids = await agent.build_diagram("画个图")

    assert aids[0].kind == "mermaid"


async def test_ascii_with_html_tags_rejected() -> None:
    agent = TutorAgent(
        DiagramStubProvider({"kind": "ascii", "source": "<img src=x onerror=alert(1)>"})
    )

    aids = await agent.build_diagram("画个图")

    assert aids[0].kind == "mermaid"


async def test_legacy_mermaid_payload_still_works() -> None:
    """旧 schema {"mermaid": "..."} 兼容：仍按 mermaid 解析。"""
    src = 'flowchart TD\n  A["for 循环"] --> B["循环体"]'
    agent = TutorAgent(DiagramStubProvider({"mermaid": src}))

    aids = await agent.build_diagram("画个 for 循环流程图")

    assert aids[0].kind == "mermaid"
    assert aids[0].mermaid == src


def test_sanitize_ascii_strips_fences() -> None:
    art = "```text\n[0][1][2]\n └──┘\n```"
    assert TutorAgent._sanitize_ascii(art) == "[0][1][2]\n └──┘"


def test_sanitize_svg_extracts_from_prose() -> None:
    text = f"给你画好了：\n```svg\n{VALID_SVG}\n```\n以上。"
    assert TutorAgent._sanitize_svg(text) == VALID_SVG


def test_sanitize_svg_allows_local_paint_server_references() -> None:
    svg = (
        '<svg viewBox="0 0 10 10"><defs><linearGradient id="paint">'
        '<stop offset="0" stop-color="#fff"/></linearGradient></defs>'
        '<rect fill="url(#paint)"/></svg>'
    )

    assert TutorAgent._sanitize_svg(svg) == svg
