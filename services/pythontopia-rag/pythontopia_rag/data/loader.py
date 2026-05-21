from __future__ import annotations

import argparse
import os
from pathlib import Path

import psycopg
import pyarrow as pa
import pyarrow.parquet as pq
from psycopg.rows import dict_row
from pydantic import BaseModel, Field

DEFAULT_DB_URL = "postgresql://pytopia:pytopia_dev_password@localhost:15432/pythontopia"
DEFAULT_OUTPUT = Path(__file__).resolve().parents[2] / "data" / "pages.parquet"


class PageRecord(BaseModel):
    page_id: int = Field(gt=0)
    kc_code: str
    document_id: int = Field(gt=0)
    content_markdown: str


class LoadResult(BaseModel):
    count: int = Field(ge=0)
    output_path: str
    dry_run: bool
    pages: list[PageRecord] = Field(default_factory=list)


def normalize_db_url(db_url: str) -> str:
    if db_url.startswith("jdbc:postgresql://"):
        return "postgresql://" + db_url.removeprefix("jdbc:postgresql://")
    return db_url


def fetch_pages(db_url: str) -> list[PageRecord]:
    sql = """
        SELECT p.id AS page_id,
               COALESCE(MIN(kc.kc_code), '') AS kc_code,
               p.document_id AS document_id,
               p.content_markdown AS content_markdown
        FROM course_page p
        LEFT JOIN course_kc kc
          ON kc.pack_id = p.pack_id
         AND p.id = ANY(kc.page_refs)
        WHERE p.pack_id = 43
        GROUP BY p.id, p.document_id, p.content_markdown
        ORDER BY p.document_id, p.page_no, p.chunk_index, p.id
    """
    with psycopg.connect(normalize_db_url(db_url), row_factory=dict_row) as conn:
        with conn.cursor() as cur:
            cur.execute(sql, {})
            rows = cur.fetchall()
    return [PageRecord.model_validate(row) for row in rows]


def write_parquet(pages: list[PageRecord], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    table = pa.Table.from_pylist([page.model_dump() for page in pages])
    pq.write_table(table, output_path)


def load_pages(db_url: str, output_path: Path, *, dry_run: bool) -> LoadResult:
    pages = fetch_pages(db_url)
    if not dry_run:
        write_parquet(pages, output_path)
    return LoadResult(
        count=len(pages),
        output_path=str(output_path),
        dry_run=dry_run,
        pages=pages,
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Load Pythontopia course pages for later RAG indexing.")
    parser.add_argument("--dry-run", action="store_true", help="Read pages without writing parquet.")
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help="Local parquet cache path. Defaults to services/pythontopia-rag/data/pages.parquet.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    db_url = os.environ.get("PYT_DB_URL", DEFAULT_DB_URL)
    result = load_pages(db_url, args.output, dry_run=args.dry_run)
    print(f"count={result.count} output={result.output_path} dry_run={str(result.dry_run).lower()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
