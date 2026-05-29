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


class PageSource(Protocol):
    async def iter_pages(self) -> Iterable[IndexablePage]:  # pragma: no cover
        ...


@dataclass
class InMemoryPageSource:
    pages: list[IndexablePage]

    async def iter_pages(self) -> Iterable[IndexablePage]:
        return list(self.pages)


def page_records_to_indexable(records: list[PageRecord]) -> list[IndexablePage]:
    """Adapter from the existing dry-run loader records to IndexablePage."""

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
            )
        )
    return indexable
