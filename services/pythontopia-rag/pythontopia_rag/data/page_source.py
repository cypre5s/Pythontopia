from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Protocol

from pythontopia_rag.data.loader import PageRecord


@dataclass
class IndexablePage:
    page_id: int
    page_no: int
    document_id: int
    chapter_id: int | None
    kc_codes: list[str]
    title: str
    text: str
    # 章序号与章标题用于上下文化分块的页级前缀（缺省为空，向后兼容旧构造）。
    chapter_no: int | None = None
    chapter_title: str = ""


class PageSource(Protocol):
    async def iter_pages(self) -> Iterable[IndexablePage]:  # pragma: no cover
        ...


@dataclass
class InMemoryPageSource:
    pages: list[IndexablePage]

    async def iter_pages(self) -> Iterable[IndexablePage]:
        return list(self.pages)


def page_records_to_indexable(records: list[PageRecord]) -> list[IndexablePage]:
    """将 loader 的 PageRecord 转为可索引的 IndexablePage。"""

    indexable: list[IndexablePage] = []
    for record in records:
        indexable.append(
            IndexablePage(
                page_id=record.page_id,
                page_no=record.page_no,
                document_id=record.document_id,
                chapter_id=record.chapter_id,
                kc_codes=list(record.kc_codes),
                title=record.title,
                text=record.content_markdown,
                chapter_no=record.chapter_no,
                chapter_title=record.chapter_title,
            )
        )
    return indexable
