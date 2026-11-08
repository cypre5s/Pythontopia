"""External import and explicit archive cleanup use only isolated test databases."""

import json
from pathlib import Path

import pytest
from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import Session

from app.content_cleanup import cleanup_content
from app.content_relations import (
    activity_questions, material_chapter_ids, set_activity_questions, set_question_chapters,
)
from app.db import Base
from app.integration_models import AssistantSession
from app.models import (
    Activity, AssessmentAttempt, Chapter, Material, Question, QuestionSecret, Submission,
)
from app.seed import seed


def test_external_small_course_is_not_bound_to_original_pack_counts(tmp_path):
    package = tmp_path / "external-course"
    source = package / "json"
    content = tmp_path / "external-content"
    source.mkdir(parents=True)
    (content / "chapters").mkdir(parents=True)
    values = {
        "course_chapter": [{"id": 8001, "title": "外部章节", "order_index": 1, "course_id": 88}],
        "course_document": [{"id": 9001, "original_filename": "真实资料.pdf", "sort_order": 1}],
        "course_page": [{"id": 10001, "document_id": 9001, "page_no": 1, "title": "原始页", "content_markdown": "真实来源文本"}],
        "course_kc": [], "course_example": [], "course_problem": [],
    }
    for name, rows in values.items():
        (source / f"{name}.json").write_text(json.dumps(rows), encoding="utf-8")
    (content / "chapters/course.yaml").write_text(
        "schema_version: 1\nnpcs: []\nactivities: []\nquests: []\n", encoding="utf-8")
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with Session(engine) as db:
        expected = {"documents": 1, "pages": 1, "chapters": 1, "knowledge_points": 0, "examples": 0, "problems": 0}
        assert seed(db, package, content_root=content) == expected
        assert seed(db, source, content_root=content) == expected
        assert db.get(Chapter, 8001).course_id == 88
        assert material_chapter_ids(db, 9001) == [8001]
        assert db.get(Material, 9001).content["pages"][0]["id"] == 10001
        assert db.scalar(select(func.count()).select_from(Question)) == 0
    engine.dispose()


def test_demo_import_only_selects_current_questions_and_supports_reuse(db, monkeypatch):
    from app import seed as importer
    from app.chapter_content import load_content

    assert db.scalar(select(func.count()).select_from(Question)) == 6
    assert not list(db.scalars(select(Activity).where(Activity.id.like("archive-%"))))
    assert db.get(Question, "course-476") is None
    content = load_content()
    practice = next(row for row in content["activities"] if row["id"] == "ch1-practice")
    assessment = next(row for row in content["activities"] if row["id"] == "ch1-assessment")
    assessment["content"]["question_ids"].append(practice["content"]["question_ids"][0])
    monkeypatch.setattr(importer, "load_content", lambda: content)
    seed(db)
    seed(db)
    assert [row.id for row in activity_questions(db, assessment["id"])] == assessment["content"]["question_ids"]
    assert "question_ids" not in db.get(Activity, assessment["id"]).content
    assert db.scalar(select(func.count()).select_from(Question)) == 6


def test_import_rejects_oversized_assessment_without_changing_current_content(db, monkeypatch):
    from copy import deepcopy
    from app import seed as importer
    from app.chapter_content import load_content

    content = load_content()
    assessment = next(row for row in content["activities"] if row["id"] == "ch1-assessment")
    original = deepcopy(db.get(Activity, assessment["id"]).content)
    assessment["content"]["question_ids"] = [f"new-{number}" for number in range(101)]
    monkeypatch.setattr(importer, "load_content", lambda: content)
    with pytest.raises(ValueError, match="100"):
        seed(db)
    assert db.get(Activity, assessment["id"]).content == original
    assert db.scalar(select(func.count()).select_from(Question)) == 6


def _legacy(db, suffix):
    activity = Activity(id=f"archive-{suffix}", chapter_id=179, title="旧归档", kind="practice",
                        assistant_allowed=False, content={"published": False})
    question = Question(id=f"course-{suffix}", title="旧导入题", kind="boolean", prompt="旧题",
                        source_type="imported", status="draft", source={"kind": "course_export"})
    db.add_all([activity, question])
    db.flush()
    db.add(QuestionSecret(question_id=question.id, answer=True, hidden_cases=[]))
    set_question_chapters(db, question.id, [179])
    set_activity_questions(db, activity, [question.id])
    return activity, question


def test_cleanup_previews_then_deletes_only_unused_legacy_archives(db, student, classroom, monkeypatch):
    unused, unused_q = _legacy(db, "991")
    submitted, submitted_q = _legacy(db, "992")
    snapshotted, snapshotted_q = _legacy(db, "993")
    assisted, assisted_q = _legacy(db, "994")
    db.add(Submission(user_id=student["id"], classroom_id=classroom["id"], activity_id=submitted.id,
                      question_id=submitted_q.id, idempotency_key="history", status="accepted", mode="submit", answer=True))
    db.add(AssessmentAttempt(user_id=student["id"], classroom_id=classroom["id"], activity_id="ch1-assessment",
                             state="draft", question_ids=[snapshotted_q.id]))
    db.add(AssistantSession(user_id=student["id"], classroom_id=classroom["id"], activity_id=assisted.id, messages=[]))
    db.commit()
    preview = cleanup_content(db)
    assert preview["mode"] == "dry-run"
    assert preview["questions"] == [unused_q.id]
    assert preview["activities"] == [unused.id, snapshotted.id]
    assert submitted_q.id in preview["skipped_questions"]
    assert snapshotted_q.id in preview["skipped_questions"]
    assert assisted_q.id in preview["skipped_questions"]
    assert db.get(Question, unused_q.id) is not None
    monkeypatch.setenv("REBORN_ENV", "development")
    applied = cleanup_content(db, apply=True)
    assert applied | {"mode": "dry-run"} == preview
    db.expire_all()
    assert db.get(Question, unused_q.id) is None
    assert db.get(QuestionSecret, unused_q.id) is None
    assert db.get(Activity, unused.id) is None
    assert db.get(Question, snapshotted_q.id) is not None
    assert db.get(Activity, submitted.id) is not None
    assert db.get(Activity, assisted.id) is not None
    assert db.scalar(select(func.count()).select_from(Submission)) == 1
    assert [row.id for row in activity_questions(db, "ch1-practice")]


@pytest.mark.parametrize("environment", [None, "production", "staging"])
def test_cleanup_apply_requires_explicit_development(db, monkeypatch, environment):
    if environment is None:
        monkeypatch.delenv("REBORN_ENV", raising=False)
    else:
        monkeypatch.setenv("REBORN_ENV", environment)
    with pytest.raises(ValueError, match="development"):
        cleanup_content(db, apply=True)
    assert cleanup_content(db)["mode"] == "dry-run"


def test_runtime_image_does_not_embed_or_reseed_course_content():
    dockerfile = (Path(__file__).resolve().parents[1] / "Dockerfile").read_text()
    assert "app.seed" not in dockerfile
    assert "course-pack-43" not in dockerfile
    assert "COPY backend/ ./" not in dockerfile
