"""Generate explicitly synthetic legacy records for tests, never teaching material.

Only reference IDs and the regression graph shape are retained in Git. Bulk page
text, knowledge explanations, examples and coding exercises are artificial.
"""
import argparse
import json
import os
from pathlib import Path

FIXTURES = Path(__file__).resolve().parent / "fixtures"
DEFAULT_OUTPUT = Path(__file__).resolve().parents[2] / ".tmp" / "test-course-sources"


def generate_sources(output=DEFAULT_OUTPUT):
    output = Path(output)
    shape = json.loads((FIXTURES / "course-shape.json").read_text(encoding="utf-8"))
    records = {
        "course_chapter": [{**row, "pack_id": 43} for row in shape["chapters"]],
        "course_document": [{**row, "pack_id": 43, "original_filename": f"test-document-{row['id']}.pdf"}
                            for row in shape["documents"]],
        "course_page": [{**row, "pack_id": 43, "title": f"测试页{row['page_no']}",
                         "content_markdown": f"测试资料第{row['page_no']}页：Python、计算思维、抽象与自动化。此文本仅供自动化验收。"}
                        for row in shape["page_ids"]],
        "course_kc": [{**row, "pack_id": 43, "kc_name": f"测试知识点{row['id']}", "prerequisite_codes": [],
                       "summary_markdown": "自动化验收用知识点说明"} for row in shape["knowledge_points"]],
        "course_example": [{"id": key, "pack_id": 43, "title": f"测试示例{key}", "code": "print('test')",
                            "page_refs": [], "explanation_markdown": "人工构造的测试示例"} for key in shape["example_ids"]],
        "course_problem": [{**row, "pack_id": 43, "title": f"测试编程题{row['id']}",
                            "statement_markdown": "测试题：读取一行并原样输出。",
                            "samples": [{"input": word + "\n", "expected_output": word + "\n"} for word in ("hello", "python", "test")],
                            "test_cases": [{"input": word + "\n", "expected_output": word + "\n", "is_sample": True} for word in ("hello", "python", "test")] + [
                                           {"input": "private\n", "expected_output": "private\n", "is_sample": False}],
                            "reference_solution_code": "print(input())"} for row in shape["problem_ids"]],
    }
    (output / "json").mkdir(parents=True, exist_ok=True)
    for name, rows in records.items():
        (output / "json" / (name + ".json")).write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")
    return output.resolve()


def configure_test_sources():
    """Honor an explicit external fixture source; otherwise create synthetic data."""
    if not os.environ.get("REBORN_COURSE_ROOT"):
        os.environ["REBORN_COURSE_ROOT"] = str(generate_sources())
    os.environ.setdefault("REBORN_CONTENT_ROOT", str(FIXTURES / "teaching-scripts"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    print(generate_sources(args.output))
