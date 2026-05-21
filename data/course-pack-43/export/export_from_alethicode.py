#!/usr/bin/env python3
"""Export Alethicode language pack 43 into deterministic JSON seed files."""

from __future__ import annotations

import json
import os
import sys
from collections.abc import Iterable
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any

import psycopg
from psycopg.rows import dict_row

PACK_ID = 43
EXPECTED = {
    "course_pack": 1,
    "course_document": 7,
    "course_page": 561,
    "course_chapter": 7,
    "course_kc": 65,
    "course_example": 46,
    "course_problem": 39,
}

ROOT = Path(__file__).resolve().parents[1]
JSON_DIR = ROOT / "json"


def json_default(value: Any) -> str | float:
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if isinstance(value, Decimal):
        return float(value)
    raise TypeError(f"Object of type {type(value).__name__} is not JSON serializable")


def write_json(name: str, rows: Iterable[dict[str, Any]]) -> int:
    data = list(rows)
    path = JSON_DIR / f"{name}.json"
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2, default=json_default) + "\n",
        encoding="utf-8",
    )
    return len(data)


def fetch_all(conn: psycopg.Connection[Any], sql: str) -> list[dict[str, Any]]:
    with conn.cursor(row_factory=dict_row) as cur:
        cur.execute(sql, {"pack_id": PACK_ID})
        return list(cur.fetchall())


def export(conn: psycopg.Connection[Any]) -> dict[str, int]:
    JSON_DIR.mkdir(parents=True, exist_ok=True)

    counts: dict[str, int] = {}
    counts["course_pack"] = write_json(
        "course_pack",
        fetch_all(
            conn,
            """
            SELECT id, slug, version, name, primary_language, description, status,
                   document_count, page_count, chapter_count, kc_count, example_count,
                   problem_count, course_objective, target_audience, total_hours
            FROM language_pack
            WHERE id = %(pack_id)s
            ORDER BY id
            """,
        ),
    )
    counts["course_document"] = write_json(
        "course_document",
        fetch_all(
            conn,
            """
            SELECT id, language_pack_id AS pack_id, original_filename, original_path,
                   canonical_path, preview_pdf_path, file_hash, file_size_bytes,
                   page_count, status, sort_order
            FROM language_pack_document
            WHERE language_pack_id = %(pack_id)s
            ORDER BY sort_order, id
            """,
        ),
    )
    counts["course_chapter"] = write_json(
        "course_chapter",
        fetch_all(
            conn,
            """
            SELECT id, language_pack_id AS pack_id, chapter_index AS order_index,
                   title, description AS summary, learning_objective AS learning_objectives
            FROM language_pack_chapter
            WHERE language_pack_id = %(pack_id)s
            ORDER BY chapter_index, id
            """,
        ),
    )
    counts["course_page"] = write_json(
        "course_page",
        fetch_all(
            conn,
            """
            SELECT p.id, p.language_pack_id AS pack_id, p.document_id,
                   MIN(kc.chapter_id) AS chapter_id, p.page_no, p.chunk_index,
                   p.page_title AS title,
                   CONCAT_WS(E'\n\n',
                     NULLIF(p.page_title, ''),
                     NULLIF(p.page_text, ''),
                     NULLIF(p.excerpt, '')
                   ) AS content_markdown,
                   p.text_hash, p.preview_asset_path, p.excerpt
            FROM language_pack_page p
            LEFT JOIN language_pack_kc_page_mapping m ON m.page_id = p.id
            LEFT JOIN language_pack_kc kc ON kc.id = m.kc_id
            WHERE p.language_pack_id = %(pack_id)s
            GROUP BY p.id
            ORDER BY p.document_id, p.page_no, p.chunk_index, p.id
            """,
        ),
    )
    counts["course_kc"] = write_json(
        "course_kc",
        fetch_all(
            conn,
            """
            WITH included AS (
              SELECT kc.id
              FROM language_pack_kc kc
              WHERE kc.language_pack_id = %(pack_id)s
                AND EXISTS (
                  SELECT 1 FROM language_pack_kc_page_mapping m WHERE m.kc_id = kc.id
                )
            ),
            numbered AS (
              SELECT kc.id, ROW_NUMBER() OVER (ORDER BY c.chapter_index, kc.id) AS seq
              FROM language_pack_kc kc
              JOIN language_pack_chapter c ON c.id = kc.chapter_id
              WHERE kc.id IN (SELECT id FROM included)
            )
            SELECT kc.id, kc.language_pack_id AS pack_id,
                   'PY43-KC-' || LPAD(numbered.seq::text, 3, '0') AS kc_code,
                   kc.name AS kc_name, kc.name_normalized, kc.name_en, kc.chapter_id,
                   c.chapter_index AS chapter_order, 'basic' AS difficulty,
                   kc.description AS summary_markdown,
                   COALESCE(
                     ARRAY_AGG('PY43-KC-' || LPAD(pre.seq::text, 3, '0')
                       ORDER BY pre.seq)
                       FILTER (WHERE pre.seq IS NOT NULL),
                     ARRAY[]::text[]
                   ) AS prerequisite_codes,
                   COALESCE(
                     ARRAY_AGG(DISTINCT m.page_id ORDER BY m.page_id)
                       FILTER (WHERE m.page_id IS NOT NULL),
                     ARRAY[]::bigint[]
                   ) AS page_refs
            FROM language_pack_kc kc
            JOIN numbered ON numbered.id = kc.id
            JOIN language_pack_chapter c ON c.id = kc.chapter_id
            LEFT JOIN language_pack_kc_page_mapping m ON m.kc_id = kc.id
            LEFT JOIN language_pack_kc_prerequisite prereq ON prereq.kc_id = kc.id
            LEFT JOIN numbered pre ON pre.id = prereq.prerequisite_kc_id
            GROUP BY kc.id, numbered.seq, c.chapter_index
            ORDER BY numbered.seq
            """,
        ),
    )
    counts["course_example"] = write_json(
        "course_example",
        fetch_all(
            conn,
            """
            WITH included_kc AS (
              SELECT kc.id, 'PY43-KC-' || LPAD(
                       ROW_NUMBER() OVER (ORDER BY c.chapter_index, kc.id)::text, 3, '0'
                     ) AS kc_code
              FROM language_pack_kc kc
              JOIN language_pack_chapter c ON c.id = kc.chapter_id
              WHERE kc.language_pack_id = %(pack_id)s
                AND EXISTS (
                  SELECT 1 FROM language_pack_kc_page_mapping m WHERE m.kc_id = kc.id
                )
            )
            SELECT e.id, e.language_pack_id AS pack_id, e.document_id,
                   COALESCE(MIN(ik.kc_code), '') AS kc_code,
                   NULLIF(e.source_title, '') AS title,
                   e.normalized_body AS code,
                   e.evidence_excerpt AS explanation_markdown,
                   e.input_description, e.output_description, e.unit_type,
                   e.oj_convertible, e.oj_block_reason,
                   COALESCE(
                     ARRAY_AGG(DISTINCT p.id ORDER BY p.id)
                       FILTER (WHERE p.id IS NOT NULL),
                     ARRAY[]::bigint[]
                   ) AS page_refs
            FROM language_pack_example e
            LEFT JOIN language_pack_example_kc_mapping ek ON ek.example_id = e.id
            LEFT JOIN included_kc ik ON ik.id = ek.kc_id
            LEFT JOIN language_pack_page p
              ON p.document_id = e.document_id
             AND p.page_no BETWEEN COALESCE(e.page_range_start, p.page_no)
                               AND COALESCE(e.page_range_end, p.page_no)
            WHERE e.language_pack_id = %(pack_id)s
            GROUP BY e.id
            ORDER BY e.id
            """,
        ),
    )
    counts["course_problem"] = write_json(
        "course_problem",
        fetch_all(
            conn,
            """
            WITH included_kc AS (
              SELECT kc.id, 'PY43-KC-' || LPAD(
                       ROW_NUMBER() OVER (ORDER BY c.chapter_index, kc.id)::text, 3, '0'
                     ) AS kc_code
              FROM language_pack_kc kc
              JOIN language_pack_chapter c ON c.id = kc.chapter_id
              WHERE kc.language_pack_id = %(pack_id)s
                AND EXISTS (
                  SELECT 1 FROM language_pack_kc_page_mapping m WHERE m.kc_id = kc.id
                )
            )
            SELECT p.id, m.language_pack_id AS pack_id,
                   COALESCE(ik.kc_code, '') AS kc_code,
                   p.title, p.description AS statement_markdown,
                   p.input_description, p.output_description,
                   p.samples, p.difficulty, p.source AS source_label,
                   p.reference_solution_language, p.reference_solution_code,
                   gl.source_pages_json, gl.related_kc_ids_json,
                   gl.teaching_explanation, gl.common_mistakes_json
            FROM language_pack_problem_mapping m
            JOIN problem p ON p.id = m.problem_id
            LEFT JOIN language_pack_problem_generation_log gl ON gl.id = m.generation_log_id
            LEFT JOIN included_kc ik ON ik.id = gl.kc_id
            WHERE m.language_pack_id = %(pack_id)s
              AND p.visible = true
            ORDER BY p.id
            """,
        ),
    )
    return counts


def main() -> int:
    db_url = os.environ.get("ALETHICODE_DB_URL")
    if not db_url:
        print("ALETHICODE_DB_URL is required", file=sys.stderr)
        return 2

    with psycopg.connect(db_url, row_factory=dict_row) as conn:
        conn.execute("SET TRANSACTION READ ONLY")
        counts = export(conn)

    failed = False
    for name, expected in EXPECTED.items():
        actual = counts.get(name, 0)
        if actual != expected:
            print(f"{name}: expected {expected}, got {actual}", file=sys.stderr)
            failed = True
        else:
            print(f"{name}: {actual}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
