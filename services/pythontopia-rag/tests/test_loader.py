from __future__ import annotations

from pathlib import Path
from typing import Any

from pythontopia_rag.data import loader


class FakeCursor:
    def __init__(self, rows: list[dict[str, Any]]) -> None:
        self.rows = rows

    def __enter__(self) -> "FakeCursor":
        return self

    def __exit__(self, *_args: object) -> None:
        return None

    def execute(self, _sql: str, _params: dict[str, object]) -> None:
        return None

    def fetchall(self) -> list[dict[str, Any]]:
        return self.rows


class FakeConnection:
    def __init__(self, rows: list[dict[str, Any]]) -> None:
        self.rows = rows

    def __enter__(self) -> "FakeConnection":
        return self

    def __exit__(self, *_args: object) -> None:
        return None

    def cursor(self, *_args: object, **_kwargs: object) -> FakeCursor:
        return FakeCursor(self.rows)


def test_load_pages_maps_database_rows(monkeypatch: Any, tmp_path: Path) -> None:
    rows = [
        {
            "page_id": 10682,
            "kc_code": "PY43-KC-001",
            "document_id": 186,
            "content_markdown": "计算思维与人工智能",
        },
        {
            "page_id": 10683,
            "kc_code": "PY43-KC-002",
            "document_id": 186,
            "content_markdown": "程序设计语言基础",
        },
    ]

    monkeypatch.setattr(loader.psycopg, "connect", lambda *_args, **_kwargs: FakeConnection(rows))

    result = loader.load_pages("postgresql://example", tmp_path / "pages.parquet", dry_run=True)

    assert result.count == 2
    assert result.pages[0].page_id == 10682
    assert result.pages[0].kc_code == "PY43-KC-001"
    assert result.pages[0].document_id == 186
    assert result.pages[0].content_markdown == "计算思维与人工智能"
    assert not (tmp_path / "pages.parquet").exists()
