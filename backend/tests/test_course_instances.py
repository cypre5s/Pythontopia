"""Class-owned clones preserve source definitions and learner evidence."""
from copy import deepcopy
from datetime import datetime, timezone

import pytest
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.course_instances import content_mapping

from fixture_instances import CourseCloneError, clone_current_course
from app.integration_models import AssistantSession, SubmissionPayload
from app.models import (
    Activity, ActivityProgress, ActivityQuestion, AssessmentAttempt, Chapter, ChapterRelease, Classroom,
    CourseInstance, LearningRecord, Material, Membership, Npc, PlayerQuest, Question,
    QuestionSecret, QuestDefinition, StoryDefinition, StorySession, Submission, User, WorldFlag,
)
from test_curriculum_migration import migrate, sqlite_engine


def classes(db):
    teacher = User(student_id="885001", name="Teacher", role="teacher", password_hash="test-only")
    student = User(student_id="885002", name="Student", role="student", password_hash="test-only")
    db.add_all([teacher, student])
    db.flush()
    first = Classroom(name="Alpha", teacher_id=teacher.id, join_code="clone-alpha")
    second = Classroom(name="Beta", teacher_id=teacher.id, join_code="clone-beta")
    db.add_all([first, second])
    db.flush()
    for classroom in (first, second):
        db.add(Membership(user_id=student.id, classroom_id=classroom.id))
        db.add(ChapterRelease(classroom_id=classroom.id, chapter_id=179, status="published"))
    db.commit()
    return teacher, student, first, second


def test_same_teacher_classes_get_independent_content_and_idempotent_clone(db):
    teacher, _, alpha, beta = classes(db)
    draft = Question(id="unused-draft", owner_id=teacher.id, title="Unselected", kind="short_answer", prompt="draft", status="draft")
    db.add(draft)
    db.add(Material(title="Unclassified", owner_id=teacher.id, content={"pages": []}))
    db.commit()
    one = clone_current_course(db, alpha)
    two = clone_current_course(db, beta)
    db.commit()
    a, b = content_mapping(db, one), content_mapping(db, two)
    assert a["chapter"]["179"] != b["chapter"]["179"]
    assert a["activity"]["ch1-practice"] != b["activity"]["ch1-practice"]
    assert "unused-draft" in a["question"]
    assert len(a["material"]) == 8
    question = db.get(Question, a["question"]["unused-draft"])
    question.title = "Only Alpha changes"
    db.commit()
    assert db.get(Question, b["question"]["unused-draft"]).title == "Unselected"
    assert db.get(Question, "unused-draft").title == "Unselected"
    assert clone_current_course(db, alpha).id == one.id
    assert db.scalar(select(func.count()).select_from(CourseInstance)) == 2
    assert db.get(Chapter, a["chapter"]["179"]).order_index == db.get(Chapter, b["chapter"]["179"]).order_index
    links = list(db.scalars(select(ActivityQuestion).where(ActivityQuestion.activity_id == a["activity"]["ch1-practice"])))
    assert [row.question_id for row in sorted(links, key=lambda item: item.order_index)] == [
        a["question"]["ch1-abstraction"], a["question"]["ch1-computation"], a["question"]["ch1-model"]]
    assert db.get(QuestionSecret, a["question"]["ch1-abstraction"]).answer == db.get(QuestionSecret, "ch1-abstraction").answer


def test_existing_learning_evidence_is_remapped_without_regrading(db):
    _, student, alpha, beta = classes(db)
    stamp = datetime(2025, 1, 2, tzinfo=timezone.utc)
    submission = Submission(id="saved-submission", user_id=student.id, classroom_id=alpha.id, activity_id="ch1-practice",
        question_id="ch1-abstraction", idempotency_key="unchanged-key", status="dispatch_unknown", answer="keep answer",
        judge_token="test-only-token", updated_at=stamp, feedback={"score": 100})
    db.add(submission)
    db.flush()
    db.add(SubmissionPayload(submission_id=submission.id, cases=[{"input": "private", "expected_output": "kept"}], public_count=0))
    db.add(AssessmentAttempt(id="saved-attempt", user_id=student.id, classroom_id=alpha.id,
        activity_id="ch1-assessment", state="submitted", revision=7, question_ids=["ch1-abstraction"],
        answers={"ch1-abstraction": "keep answer"}, submission_ids={"ch1-abstraction": submission.id}, updated_at=stamp))
    db.add(ActivityProgress(user_id=student.id, classroom_id=alpha.id, activity_id="ch1-lesson", page_no=34,
        visited_pages=[10715], completed=True))
    db.add(LearningRecord(id="saved-reading", user_id=student.id, classroom_id=alpha.id, activity_id="ch1-lesson", data={"page_id": 10715}))
    db.add(PlayerQuest(user_id=student.id, classroom_id=alpha.id, quest_id="arrival", status="complete"))
    db.add(StorySession(id="saved-story", user_id=student.id, classroom_id=alpha.id, story_id="first-steps",
        npc_key="librarian_doc", node_id="open-lesson", status="waiting", revision=5, pending_activity_id="ch1-lesson",
        return_mapping={"completed": "librarian-after"}, receipts=[{"id": "event", "signature": "unchanged"}]))
    db.add(AssistantSession(id="saved-assistant", user_id=student.id, classroom_id=alpha.id, activity_id="ch1-lesson",
        material_id=186, messages=[{"role": "assistant", "content": "keep prose", "citations": [{"material_id": 186, "page_id": 10715}]}]))
    db.add(WorldFlag(user_id=student.id, classroom_id=alpha.id, key="dialogue:completed:mayor_py:story:first-steps:welcome"))
    db.add(ActivityProgress(user_id=student.id, classroom_id=beta.id, activity_id="ch1-lesson", page_no=42, visited_pages=[10723]))
    db.commit()
    db.refresh(submission)
    old_updated = submission.updated_at
    instance = clone_current_course(db, alpha)
    db.commit()
    ids = content_mapping(db, instance)
    db.refresh(submission)
    assert (submission.id, submission.idempotency_key, submission.status, submission.judge_token, submission.answer) == (
        "saved-submission", "unchanged-key", "dispatch_unknown", "test-only-token", "keep answer")
    assert submission.updated_at == old_updated
    assert submission.activity_id == ids["activity"]["ch1-practice"]
    assert db.get(SubmissionPayload, submission.id).cases[0]["expected_output"] == "kept"
    attempt = db.get(AssessmentAttempt, "saved-attempt")
    assert (attempt.state, attempt.revision) == ("submitted", 7)
    assert attempt.answers == {ids["question"]["ch1-abstraction"]: "keep answer"}
    assert attempt.submission_ids == {ids["question"]["ch1-abstraction"]: "saved-submission"}
    progress = db.get(ActivityProgress, (student.id, alpha.id, ids["activity"]["ch1-lesson"]))
    assert progress.visited_pages == [10715] and progress.completed
    assert db.get(ActivityProgress, (student.id, beta.id, "ch1-lesson")).visited_pages == [10723]
    story = db.get(StorySession, "saved-story")
    assert (story.story_id, story.npc_key, story.pending_activity_id) == (
        ids["story"]["first-steps"], ids["npc"]["librarian_doc"], ids["activity"]["ch1-lesson"])
    assert story.revision == 5 and story.receipts == [{"id": "event", "signature": "unchanged"}]
    assistant = db.get(AssistantSession, "saved-assistant")
    assert assistant.messages[0]["content"] == "keep prose"
    assert assistant.messages[0]["citations"][0] == {"material_id": ids["material"]["186"], "page_id": 10715}
    quest = db.get(QuestDefinition, ids["quest"]["arrival"])
    assert quest.definition["id"] == quest.id
    assert quest.definition["nodes"][0]["dialogue_id"] == f"story:{ids['story']['first-steps']}:welcome"
    assert db.scalar(select(WorldFlag).where(WorldFlag.classroom_id == alpha.id)).key == (
        f"dialogue:completed:{ids['npc']['mayor_py']}:story:{ids['story']['first-steps']}:welcome")


def test_bad_cross_owner_reference_rolls_back_the_entire_clone(db):
    teacher, _, alpha, _ = classes(db)
    foreign = User(student_id="885003", name="Other", role="teacher", password_hash="test")
    db.add(foreign)
    db.flush()
    chapter = Chapter(title="Private", order_index=99, owner_id=foreign.id)
    db.add(chapter)
    db.flush()
    db.add(Activity(id="invalid-owned-activity", title="Invalid", kind="lesson", owner_id=teacher.id,
        chapter_id=chapter.id, npc_key="professor", content={}))
    db.commit()
    with pytest.raises(CourseCloneError):
        clone_current_course(db, alpha)
    assert db.scalar(select(func.count()).select_from(CourseInstance)) == 0
    assert db.scalar(select(func.count()).select_from(Chapter).where(Chapter.course_instance_id.is_not(None))) == 0
    assert db.get(Chapter, 179).course_instance_id is None


def test_sources_and_other_teachers_private_definitions_are_not_changed(db):
    _, _, alpha, _ = classes(db)
    other = User(student_id="885004", name="Other", role="teacher", password_hash="test")
    db.add(other)
    db.flush()
    db.add(Question(id="other-private", title="Private", prompt="secret", kind="short_answer", owner_id=other.id))
    before = deepcopy(db.get(Material, 186).content)
    db.commit()
    instance = clone_current_course(db, alpha)
    db.commit()
    ids = content_mapping(db, instance)
    assert "other-private" not in ids["question"]
    assert db.get(Material, 186).content == before
    assert db.get(Material, ids["material"]["186"]).content["pages"] == before["pages"]
    assert db.get(StoryDefinition, "first-steps").course_instance_id is None
    assert db.get(Npc, "mayor_py").course_instance_id is None


def test_course_instance_and_stable_id_uniqueness(db):
    _, _, alpha, _ = classes(db)
    instance = clone_current_course(db, alpha)
    db.commit()
    with pytest.raises(IntegrityError), db.begin_nested():
        db.add(CourseInstance(classroom_id=alpha.id, package_id="other", name="Other", package_version="1"))
        db.flush()
    with pytest.raises(IntegrityError), db.begin_nested():
        db.add(Material(title="duplicate", course_instance_id=instance.id, stable_id="186"))
        db.flush()


def test_schema_migration_preserves_existing_data_and_is_repeatable(tmp_path):
    path = tmp_path / "before.sqlite"
    url = "sqlite:///" + path.as_posix()
    migrate(url, "upgrade", "d7213a8f9c06")
    engine = sqlite_engine(path)
    with engine.begin() as connection:
        connection.exec_driver_sql("INSERT INTO users(id,student_id,name,role,password_hash) VALUES(1,'123','Teacher','teacher','test')")
        connection.exec_driver_sql("INSERT INTO classrooms(id,name,teacher_id,join_code) VALUES(1,'Saved',1,'saved')")
        connection.exec_driver_sql("INSERT INTO chapters(id,title,order_index,course_id) VALUES(179,'Saved chapter',1,43)")
    engine.dispose()
    migrate(url, "upgrade", "head")
    migrate(url, "upgrade", "head")
    migrate(url, "check")
    engine = sqlite_engine(path)
    with Session(engine) as session:
        assert session.get(Chapter, 179).title == "Saved chapter"
        assert session.get(Chapter, 179).course_instance_id is None
        assert session.get(Classroom, 1).name == "Saved"
        instance = clone_current_course(session, 1)
        session.commit()
        assert session.get(Chapter, content_mapping(session, instance)["chapter"]["179"]).title == "Saved chapter"
    engine.dispose()


def test_invalid_private_material_reference_rolls_back_after_allocation(db):
    teacher, _, alpha, _ = classes(db)
    other = User(student_id="885005", name="Other", role="teacher", password_hash="test")
    db.add(other)
    db.flush()
    material = Material(title="Foreign", owner_id=other.id, content={})
    db.add(material)
    db.flush()
    db.add(Activity(id="cross-private", title="Invalid reference", kind="lesson", owner_id=teacher.id,
        chapter_id=179, material_id=material.id, npc_key="professor", content={}))
    db.commit()
    with pytest.raises(CourseCloneError):
        clone_current_course(db, alpha)
    assert db.scalar(select(func.count()).select_from(CourseInstance)) == 0
    for model in (Chapter, Material, Question, Activity, Npc):
        assert db.scalar(select(func.count()).select_from(model).where(model.course_instance_id.is_not(None))) == 0
    assert db.get(ChapterRelease, (alpha.id, 179)) is not None


def test_public_flow_is_copied_without_inventing_default_chapter(db):
    _, _, alpha, _ = classes(db)
    db.add(StoryDefinition(id="public-test-flow", chapter_id=None, quest_id=None,
        content={"mode": "ambient", "bindings": {}, "entries": {"mayor_py": [{"when": {"always": True}, "node": "hello"}]},
                 "nodes": [{"id": "hello", "type": "dialogue", "text": "Hello"}]}))
    db.commit()
    instance = clone_current_course(db, alpha)
    db.commit()
    ids = content_mapping(db, instance)
    story = db.get(StoryDefinition, ids["story"]["public-test-flow"])
    assert story.chapter_id is None
    assert set(story.content["entries"]) == {ids["npc"]["mayor_py"]}
    assert db.scalar(select(func.count()).select_from(Chapter).where(Chapter.course_instance_id == instance.id)) == 7


def test_cloned_arrival_definitions_keep_valid_story_and_goal_links(db):
    from app.quests import validate_definition
    from app.story import validate_story

    _, _, alpha, _ = classes(db)
    instance = clone_current_course(db, alpha)
    db.commit()
    ids = content_mapping(db, instance)
    quest = db.get(QuestDefinition, ids["quest"]["arrival"])
    validate_definition(quest.definition, db)
    for story in db.scalars(select(StoryDefinition).where(StoryDefinition.course_instance_id == instance.id)):
        owner = db.get(QuestDefinition, story.quest_id) if story.quest_id else None
        validate_story(story.content, db, owner.definition if owner else None, story_id=story.id)


@pytest.mark.parametrize("status", ["running", "cancel_requested"])
def test_clone_refuses_in_flight_assistant_stream(db, status):
    from app.integration_models import AssistantCall

    _, student, alpha, _ = classes(db)
    session = AssistantSession(user_id=student.id, classroom_id=alpha.id, activity_id="ch1-lesson", material_id=186)
    db.add(session)
    db.flush()
    call = AssistantCall(user_id=student.id, classroom_id=alpha.id, session_id=session.id, status=status)
    db.add(call)
    db.commit()
    with pytest.raises(CourseCloneError, match="助教回复"):
        clone_current_course(db, alpha)
    assert db.scalar(select(func.count()).select_from(CourseInstance)) == 0
    assert session.activity_id == "ch1-lesson" and call.status == status


@pytest.fixture
def pg_course_engine():
    import os
    from uuid import uuid4
    from sqlalchemy import create_engine, text
    from sqlalchemy.engine import make_url

    configured = os.getenv("REBORN_TEST_DATABASE_URL")
    if not configured:
        pytest.skip("Requires an explicitly configured isolated Reborn PostgreSQL connection")
    url = make_url(configured)
    assert url.get_backend_name() == "postgresql" and "reborn" in url.database.lower()
    schema = "reborn_clone_" + uuid4().hex
    admin = create_engine(url)
    with admin.begin() as connection:
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))
    scoped = url.update_query_dict({"options": f"-csearch_path={schema}"})
    engine = create_engine(scoped)
    try:
        migrate(scoped.render_as_string(hide_password=False), "upgrade", "head")
        from fixture_seed import seed
        with Session(engine) as session:
            seed(session)
        yield engine
    finally:
        engine.dispose()
        with admin.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))
        admin.dispose()


def test_postgres_concurrent_same_class_clones_one_instance(pg_course_engine):
    from concurrent.futures import ThreadPoolExecutor
    from threading import Barrier

    with Session(pg_course_engine, expire_on_commit=False) as db:
        _, _, alpha, _ = classes(db)
        class_id = alpha.id
    gate = Barrier(2)

    def clone():
        with Session(pg_course_engine) as db:
            gate.wait(timeout=10)
            instance = clone_current_course(db, class_id)
            result = instance.id
            db.commit()
            return result

    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(clone), pool.submit(clone)]
        ids = [future.result(timeout=30) for future in futures]
    assert ids[0] == ids[1]
    with Session(pg_course_engine) as db:
        assert db.scalar(select(func.count()).select_from(CourseInstance)) == 1
        assert db.scalar(select(func.count()).select_from(Chapter).where(Chapter.course_instance_id == ids[0])) == 7


def test_postgres_fresh_head_matches_models_and_repeated_source_import(pg_course_engine):
    from app.course_instances import CONTENT_MODELS
    from fixture_seed import seed
    url = pg_course_engine.url.render_as_string(hide_password=False)
    migrate(url, "upgrade", "head")
    migrate(url, "check")
    with Session(pg_course_engine) as db:
        before = {model.__tablename__: db.scalar(select(func.count()).select_from(model)) for model in CONTENT_MODELS}
        first = seed(db)
        second = seed(db)
        after = {model.__tablename__: db.scalar(select(func.count()).select_from(model)) for model in CONTENT_MODELS}
        assert first == second and before == after
        assert before["chapters"] == 7 and before["knowledge_points"] == 65


def test_postgres_two_classes_clone_without_sequence_collisions(pg_course_engine):
    from concurrent.futures import ThreadPoolExecutor
    from threading import Barrier

    with Session(pg_course_engine, expire_on_commit=False) as db:
        _, _, alpha, beta = classes(db)
        class_ids = [alpha.id, beta.id]
    gate = Barrier(2)

    def clone(class_id):
        with Session(pg_course_engine) as db:
            gate.wait(timeout=10)
            instance = clone_current_course(db, class_id)
            ids = content_mapping(db, instance)
            db.commit()
            return ids

    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(clone, class_id) for class_id in class_ids]
        one, two = [future.result(timeout=30) for future in futures]
    for kind in ("chapter", "knowledge_point", "material", "example", "activity", "question"):
        assert set(one[kind].values()).isdisjoint(two[kind].values())
    with Session(pg_course_engine) as db:
        assert db.get(Chapter, 179).course_instance_id is None
        assert db.get(Material, 186).course_instance_id is None


def test_postgres_active_course_read_fences_clone_activation(pg_course_engine):
    from concurrent.futures import ThreadPoolExecutor
    from time import monotonic, sleep
    from sqlalchemy import text
    from app.course_scope import instance_id
    from fastapi import HTTPException

    with Session(pg_course_engine, expire_on_commit=False) as db:
        _, _, alpha, _ = classes(db)
        class_id = alpha.id

    def clone():
        with Session(pg_course_engine) as db:
            result = clone_current_course(db, class_id).id
            db.commit()
            return result

    with Session(pg_course_engine) as reader, ThreadPoolExecutor(max_workers=1) as pool:
        with pytest.raises(HTTPException) as missing:
            instance_id(reader, class_id)  # the request still holds its transaction gate until rollback
        assert missing.value.status_code == 409
        future = pool.submit(clone)
        waiting = False
        deadline = monotonic() + 10
        try:
            while monotonic() < deadline:
                with pg_course_engine.connect() as inspector:
                    waiting = bool(inspector.scalar(text("SELECT count(*) FROM pg_locks WHERE locktype='advisory' "
                        "AND classid=41729 AND objid=:id AND NOT granted"), {"id": class_id}))
                if waiting:
                    break
                sleep(0.02)
            assert waiting and not future.done()
        finally:
            reader.rollback()
        assert future.result(timeout=30) is not None
    with Session(pg_course_engine) as db:
        assert instance_id(db, class_id) is not None
