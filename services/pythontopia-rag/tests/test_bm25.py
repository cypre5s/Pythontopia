from __future__ import annotations

from pythontopia_rag.index.bm25 import Bm25Index, tokenize
from pythontopia_rag.index.chunker import Chunk


def _chunk(
    chunk_id: str,
    text: str,
    *,
    kc_codes: list[str] | None = None,
    chapter_id: int | None = 1,
    embed_text: str = "",
) -> Chunk:
    return Chunk(
        chunk_id=chunk_id,
        page_id=int(chunk_id.split("-")[0].lstrip("pc") or 1),
        page_no=1,
        document_id=1,
        chapter_id=chapter_id,
        kc_codes=kc_codes or [],
        text=text,
        token_estimate=max(1, len(text) // 4),
        embed_text=embed_text,
    )


def _corpus() -> list[Chunk]:
    return [
        _chunk("1-c0", "print() 函数用于输出内容到屏幕，print('hello') 会打印 hello。"),
        _chunk("2-c0", "input() 函数从键盘读取一行字符串，常与 int() 联用。"),
        _chunk("3-c0", "for 循环用于遍历序列，for i in range(10) 会迭代 10 次。", chapter_id=4),
        _chunk("4-c0", "while 循环在条件为真时重复执行循环体。", chapter_id=4),
        _chunk("5-c0", "列表 list 是可变序列，支持 append 和 pop 操作。", kc_codes=["PY43-KC-030"]),
    ]


def test_tokenize_keeps_code_identifiers_and_cjk_bigrams() -> None:
    tokens = tokenize("使用 print() 输出，for 循环遍历。")
    assert "print" in tokens
    assert "for" in tokens
    assert "使用" in tokens  # CJK bigram
    assert "循环" in tokens
    assert "遍历" in tokens
    # 标点不应黏连在标识符上
    assert "print(" not in tokens


def test_tokenize_keeps_single_cjk_char() -> None:
    assert "变" in tokenize("变 x = 1")


def test_tokenize_keeps_python_operators() -> None:
    tokens = tokenize("单个等号 = 是赋值，两个等号 == 是比较，还有 != 和 **。")
    assert "=" in tokens
    assert "==" in tokens
    assert "!=" in tokens
    assert "**" in tokens


def test_bm25_operator_query_hits_operator_chunk() -> None:
    index = Bm25Index()
    chunks = [
        _chunk("1-c0", "单个等号 = 表示赋值，两个等号 == 是比较运算符。"),
        _chunk("2-c0", "while 和 for 都是循环语句，两者有区别。"),
    ]
    index.build(chunks)
    # 纯运算符查询只应命中含 == 的块
    pure = index.search("==", top_k=2)
    assert [chunk_id for chunk_id, _ in pure] == ["1-c0"]
    # 混合问句中运算符词项也参与召回（修复前 1-c0 完全不可达）
    mixed = index.search("=和==有什么区别", top_k=2)
    assert "1-c0" in {chunk_id for chunk_id, _ in mixed}


def test_bm25_exact_term_hits_print_chunk() -> None:
    index = Bm25Index()
    index.build(_corpus())
    results = index.search("print", top_k=5)
    assert results
    assert results[0][0] == "1-c0"
    assert results[0][1] > 0


def test_bm25_uses_embed_text_when_present() -> None:
    chunks = [
        _chunk("9-c0", "本页讲解输出语句。", embed_text="《Python语言基础》第9页：print函数\n本页讲解输出语句。"),
        _chunk("8-c0", "本页讲解键盘输入。"),
    ]
    index = Bm25Index()
    index.build(chunks)
    results = index.search("print", top_k=5)
    assert [chunk_id for chunk_id, _ in results] == ["9-c0"]


def test_bm25_no_match_returns_empty() -> None:
    index = Bm25Index()
    index.build(_corpus())
    assert index.search("奥利给", top_k=5) == []


def test_bm25_empty_index_returns_empty() -> None:
    index = Bm25Index()
    assert index.built_chunks == 0
    assert index.search("print", top_k=5) == []


def test_bm25_respects_kc_and_chapter_filters() -> None:
    index = Bm25Index()
    index.build(_corpus())

    kc_hits = index.search("列表 循环", top_k=5, kc_codes=["PY43-KC-030"])
    assert [chunk_id for chunk_id, _ in kc_hits] == ["5-c0"]

    chapter_hits = index.search("循环", top_k=5, chapter_ids=[4])
    assert {chunk_id for chunk_id, _ in chapter_hits} == {"3-c0", "4-c0"}


def test_bm25_build_replaces_previous_state() -> None:
    index = Bm25Index()
    index.build(_corpus())
    assert index.built_chunks == 5
    index.build(_corpus()[:1])
    assert index.built_chunks == 1
    assert index.search("input", top_k=5) == []
    assert index.chunk("2-c0") is None
    assert index.chunk("1-c0") is not None


def test_bm25_rare_term_outranks_common_term() -> None:
    chunks = [
        _chunk("1-c0", "函数 函数 函数 定义"),
        _chunk("2-c0", "函数 定义"),
        _chunk("3-c0", "函数 调用"),
        _chunk("4-c0", "递归 函数 调用自身"),
    ]
    index = Bm25Index()
    index.build(chunks)
    results = index.search("递归", top_k=4)
    assert results[0][0] == "4-c0"
    assert len(results) == 1
