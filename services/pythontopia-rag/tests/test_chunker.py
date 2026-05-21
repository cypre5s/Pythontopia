from __future__ import annotations

import pytest

from pythontopia_rag.index.chunker import chunk_page


def test_chunker_splits_on_heading() -> None:
    text = (
        "# 标题一\n"
        + "中文内容 " * 30
        + "\n## 标题二\n"
        + "for 循环用于遍历序列。" * 20
    )
    chunks = chunk_page(
        page_id=10,
        page_no=2,
        document_id=3,
        chapter_id=5,
        kc_codes=["PY43-KC-001"],
        text=text,
        target_chars=200,
        overlap=40,
        min_chars=80,
    )
    assert len(chunks) >= 2
    assert any("标题一" in chunk.text for chunk in chunks)
    assert any("for" in chunk.text for chunk in chunks)


def test_chunker_assigns_ids_and_metadata() -> None:
    text = "input() 函数用于读取一行字符串。" * 30
    chunks = chunk_page(
        page_id=42,
        page_no=7,
        document_id=2,
        chapter_id=1,
        kc_codes=["PY43-KC-012", "PY43-KC-013"],
        text=text,
        target_chars=150,
        overlap=30,
        min_chars=50,
    )
    assert chunks
    ids = [c.chunk_id for c in chunks]
    assert len(ids) == len(set(ids))
    for chunk in chunks:
        assert chunk.page_id == 42
        assert chunk.page_no == 7
        assert chunk.chapter_id == 1
        assert chunk.kc_codes == ["PY43-KC-012", "PY43-KC-013"]
        assert chunk.token_estimate > 0


def test_chunker_returns_empty_for_blank_text() -> None:
    assert chunk_page(
        page_id=1,
        page_no=1,
        document_id=1,
        chapter_id=None,
        kc_codes=[],
        text="",
    ) == []


def test_chunker_merges_short_tail() -> None:
    long_text = "字符串处理。" * 200
    chunks = chunk_page(
        page_id=99,
        page_no=1,
        document_id=2,
        chapter_id=None,
        kc_codes=["PY43-KC-020"],
        text=long_text,
        target_chars=300,
        overlap=20,
        min_chars=100,
    )
    assert chunks
    assert all(len(chunk.text) >= 100 or chunk is chunks[-1] for chunk in chunks)


@pytest.mark.parametrize(
    "text",
    [
        "input() 函数读取标准输入。\n" + "其后可以使用 int() 转换为整数。" * 5,
        "for i in range(10):\n    print(i)\n" * 20,
    ],
)
def test_chunker_emits_at_least_one_chunk(text: str) -> None:
    chunks = chunk_page(
        page_id=1,
        page_no=1,
        document_id=1,
        chapter_id=None,
        kc_codes=[],
        text=text,
    )
    assert chunks
