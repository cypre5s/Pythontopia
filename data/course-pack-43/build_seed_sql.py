#!/usr/bin/env python3
"""Build Flyway seed SQL for Pythontopia course pack 43."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent
JSON_DIR = ROOT / "json"
MIGRATION = (
    ROOT.parents[1] / "backend" / "src" / "main" / "resources" / "db" / "migration"
    / "V006__course_pack_seed_43.sql"
)

EXPECTED = {"document": 7, "page": 561, "chapter": 7, "kc": 65, "example": 46, "problem": 39}


def load(name: str) -> list[dict[str, Any]]:
    path = JSON_DIR / f"course_{name}.json"
    return json.loads(path.read_text(encoding="utf-8"))


def sql_string(value: Any) -> str:
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def sql_json(value: Any) -> str:
    return sql_string(json.dumps(value, ensure_ascii=False))


def sql_text_array(value: list[Any]) -> str:
    if not value:
        return "ARRAY[]::text[]"
    return "ARRAY[" + ", ".join(sql_string(item) for item in value) + "]::text[]"


def sql_bigint_array(value: list[Any]) -> str:
    if not value:
        return "ARRAY[]::bigint[]"
    return "ARRAY[" + ", ".join(str(int(item)) for item in value) + "]::bigint[]"


def validate() -> dict[str, list[dict[str, Any]]]:
    data = {name: load(name) for name in EXPECTED}
    data["pack"] = load("pack")
    expected_pack = data["pack"][0]
    if expected_pack["id"] != 43:
        raise SystemExit(f"course_pack id must be 43, got {expected_pack['id']}")
    for name, expected in EXPECTED.items():
        actual = len(data[name])
        if actual != expected:
            raise SystemExit(f"course_{name}.json expected {expected}, got {actual}")
    return data


def append_header(lines: list[str]) -> None:
    lines.extend(
        [
            "-- Purpose: seed Alethicode packId=43 into Pythontopia course tables.",
            "-- Tables affected: course_pack, course_document, course_chapter, course_page,",
            "-- course_kc, course_example, course_problem.",
            "-- Irreversible risk: re-running this migration overwrites manual edits to pack 43 seed rows.",
            "-- Alethicode sources:",
            "-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V22__bootstrap_language_pack_core.sql",
            "-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V23__language_pack_documents.sql",
            "-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V24__language_pack_pages.sql",
            "-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V25__language_pack_knowledge.sql",
            "-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V26__language_pack_examples.sql",
            "-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V40__language_pack_course_enrichment.sql",
            "",
        ]
    )


def build() -> str:
    data = validate()
    lines: list[str] = []
    append_header(lines)

    pack = data["pack"][0]
    lines.append(
        "INSERT INTO course_pack (id, slug, version, name, primary_language, description, status, "
        "document_count, page_count, chapter_count, kc_count, example_count, problem_count, "
        "course_objective, target_audience, total_hours) VALUES "
        f"({pack['id']}, {sql_string(pack['slug'])}, {pack['version']}, {sql_string(pack['name'])}, "
        f"{sql_string(pack['primary_language'])}, {sql_string(pack['description'])}, "
        f"{sql_string(pack['status'])}, {pack['document_count']}, {pack['page_count']}, "
        f"{pack['chapter_count']}, {pack['kc_count']}, {pack['example_count']}, "
        f"{pack['problem_count']}, {sql_string(pack['course_objective'])}, "
        f"{sql_string(pack['target_audience'])}, {pack['total_hours'] or 'NULL'}) "
        "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, update_time = now();"
    )

    for row in data["document"]:
        lines.append(
            "INSERT INTO course_document (id, pack_id, original_filename, original_path, "
            "canonical_path, preview_pdf_path, file_hash, file_size_bytes, page_count, status, sort_order) "
            f"VALUES ({row['id']}, {row['pack_id']}, {sql_string(row['original_filename'])}, "
            f"{sql_string(row['original_path'])}, {sql_string(row['canonical_path'])}, "
            f"{sql_string(row['preview_pdf_path'])}, {sql_string(row['file_hash'])}, "
            f"{row['file_size_bytes']}, {row['page_count']}, {sql_string(row['status'])}, "
            f"{row['sort_order']}) ON CONFLICT (id) DO UPDATE SET page_count = EXCLUDED.page_count;"
        )

    for row in data["chapter"]:
        lines.append(
            "INSERT INTO course_chapter (id, pack_id, order_index, title, summary_markdown, learning_objectives) "
            f"VALUES ({row['id']}, {row['pack_id']}, {row['order_index']}, {sql_string(row['title'])}, "
            f"{sql_string(row.get('summary'))}, {sql_string(row.get('learning_objectives'))}) "
            "ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;"
        )

    for row in data["page"]:
        lines.append(
            "INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, "
            "content_markdown, text_hash, preview_asset_path, excerpt) "
            f"VALUES ({row['id']}, {row['pack_id']}, {row['document_id']}, "
            f"{row['chapter_id'] or 'NULL'}, {row['page_no']}, {row['chunk_index']}, "
            f"{sql_string(row['title'])}, {sql_string(row['content_markdown'])}, "
            f"{sql_string(row['text_hash'])}, {sql_string(row['preview_asset_path'])}, "
            f"{sql_string(row['excerpt'])}) ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;"
        )

    for row in data["kc"]:
        lines.append(
            "INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, "
            "chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) "
            f"VALUES ({row['id']}, {row['pack_id']}, {sql_string(row['kc_code'])}, "
            f"{sql_string(row['kc_name'])}, {sql_string(row['name_normalized'])}, "
            f"{sql_string(row['name_en'])}, {row['chapter_id']}, {row['chapter_order']}, "
            f"{sql_string(row['difficulty'])}, {sql_text_array(row['prerequisite_codes'])}, "
            f"{sql_string(row['summary_markdown'])}, {sql_bigint_array(row['page_refs'])}) "
            "ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;"
        )

    for row in data["example"]:
        lines.append(
            "INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, "
            "input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) "
            f"VALUES ({row['id']}, {row['pack_id']}, {row['document_id'] or 'NULL'}, "
            f"{sql_string(row['kc_code'])}, {sql_string(row['title'] or '示例')}, {sql_string(row['code'])}, "
            f"{sql_string(row['explanation_markdown'])}, {sql_string(row['input_description'])}, "
            f"{sql_string(row['output_description'])}, {sql_string(row['unit_type'])}, "
            f"{str(row['oj_convertible']).lower()}, {sql_string(row['oj_block_reason'])}, "
            f"{sql_bigint_array(row['page_refs'])}) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;"
        )

    for row in data["problem"]:
        lines.append(
            "INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, "
            "output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, "
            "source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) "
            f"VALUES ({row['id']}, {row['pack_id']}, {sql_string(row['kc_code'])}, "
            f"{sql_string(row['title'])}, {sql_string(row['statement_markdown'])}, "
            f"{sql_string(row['input_description'])}, {sql_string(row['output_description'])}, "
            f"{sql_string(row['difficulty'])}, {sql_json(row['samples'])}::jsonb, '', '[]'::jsonb, "
            f"{sql_string(row['source_pages_json'] or '[]')}::jsonb, {sql_string(row['source_label'])}, "
            f"{sql_string(row['reference_solution_language'])}, {sql_string(row['reference_solution_code'])}, "
            f"{sql_string(row['teaching_explanation'])}, {sql_string(row['common_mistakes_json'] or '[]')}::jsonb) "
            "ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;"
        )

    lines.extend(
        [
            "",
            "-- count_check document=7",
            "-- count_check page=561",
            "-- count_check chapter=7",
            "-- count_check kc=65",
            "-- count_check example=46",
            "-- count_check problem=39",
        ]
    )
    return "\n".join(lines) + "\n"


def main() -> int:
    MIGRATION.write_text(build(), encoding="utf-8")
    print(MIGRATION)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
