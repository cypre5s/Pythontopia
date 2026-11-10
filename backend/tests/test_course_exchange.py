"""Portable private exchanges preserve definitions and isolate classroom learning state."""
from uuid import uuid4
import zipfile

import pytest
from sqlalchemy import select

from app.course_exchange import load_bundle, unpack_archive
from app.course_instances import clone_current_course, content_mapping
from app.course_package import PackageError
from app.models import (ActivityProgress, Classroom, CourseInstance, Material, Question, QuestionSecret)


@pytest.fixture
def exchange(db, classroom, teacher, monkeypatch, tmp_path):
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "storage"))
    monkeypatch.setenv("REBORN_COURSE_EXCHANGE_ROOT", str(tmp_path / "exchange"))
    original = db.get(Classroom, classroom["id"])
    instance = clone_current_course(db, original)
    db.add(Question(id=f"draft-{uuid4().hex}", stable_id="unassigned-draft", course_instance_id=instance.id,
                    owner_id=teacher["id"], title="未分配草稿", kind="boolean", prompt="待审核", source_type="ai_generated",
                    status="draft", options=[], samples=[], source={}))
    db.flush()
    draft = db.scalar(select(Question).where(Question.course_instance_id == instance.id, Question.stable_id == "unassigned-draft"))
    db.add(QuestionSecret(question_id=draft.id, answer=True, hidden_cases=[]))
    target = Classroom(name="新班级", teacher_id=teacher["id"], join_code=uuid4().hex[:24])
    db.add(target)
    db.commit()
    return instance, target


def endpoint(cid, suffix):
    return f"/api/teacher/classrooms/{cid}/course/{suffix}"


def export(client, teacher, instance):
    response = client.get(endpoint(instance.classroom_id, "export"), headers=teacher["headers"])
    assert response.status_code == 200, response.text
    assert response.headers["cache-control"] == "private, no-store"
    assert response.headers["content-length"] == str(len(response.content))
    return response.content


def preview(client, teacher, cid, payload):
    response = client.post(endpoint(cid, "imports/preview"), headers=teacher["headers"], files={"file": ("course.zip", payload, "application/zip")})
    assert response.status_code == 200, response.text
    return response.json()


def apply(client, teacher, cid, item, request_id=None):
    return client.post(endpoint(cid, f"imports/{item['id']}/apply"), headers=teacher["headers"],
                       json={"request_id": request_id or str(uuid4()), "expected_revision": item["expected_revision"]})


def test_complete_roundtrip_draft_private_answers_and_repeated_apply(client, db, teacher, exchange, tmp_path):
    from app.models import Activity, KnowledgePoint

    source, target = exchange
    source_maps = content_mapping(db, source)
    source_activity = db.get(Activity, source_maps["activity"]["ch1-lesson"])
    cross_chapter_point = db.scalar(select(KnowledgePoint).where(
        KnowledgePoint.course_instance_id == source.id, KnowledgePoint.chapter_id != source_activity.chapter_id))
    assert cross_chapter_point is not None
    point_stable_id = cross_chapter_point.stable_id or str(cross_chapter_point.id)
    source_activity.content = {**source_activity.content, "knowledge_point_ids": [cross_chapter_point.id]}
    db.commit()
    payload = export(client, teacher, source)
    item = preview(client, teacher, target.id, payload)
    assert item["expected_revision"] == 0 and item["can_apply"]
    request_id = str(uuid4())
    response = apply(client, teacher, target.id, item, request_id)
    assert response.status_code == 200, response.text
    imported = db.get(CourseInstance, response.json()["instance_id"])
    assert imported.classroom_id == target.id
    maps = content_mapping(db, imported)
    imported_activity = db.get(Activity, maps["activity"]["ch1-lesson"])
    assert imported_activity.content["knowledge_point_ids"] == [maps["knowledge_point"][point_stable_id]]
    assert db.get(KnowledgePoint, imported_activity.content["knowledge_point_ids"][0]).chapter_id != imported_activity.chapter_id
    assert "unassigned-draft" in maps["question"]
    question = db.get(Question, maps["question"]["unassigned-draft"])
    assert question.status == "draft" and question.source_type == "ai_generated"
    assert db.get(QuestionSecret, question.id).answer is True
    assert apply(client, teacher, target.id, item, request_id).json()["duplicate"] is True
    assert apply(client, teacher, target.id, item).status_code == 409
    again = export(client, teacher, imported)
    bundles = []
    for i, raw in enumerate((payload, again)):
        archive, directory = tmp_path / f"{i}.zip", tmp_path / str(i)
        archive.write_bytes(raw)
        directory.mkdir()
        unpack_archive(archive, directory)
        bundles.append(load_bundle(directory))
    for kind in bundles[0]["data"]:
        assert sorted(bundles[0]["data"][kind], key=lambda row: row["id"]) == sorted(bundles[1]["data"][kind], key=lambda row: row["id"]), kind
    assert bundles[0]["secrets"] == bundles[1]["secrets"]
    assert not db.scalars(select(ActivityProgress).where(ActivityProgress.classroom_id == target.id)).all()


def test_revision_conflict_and_wrong_class_preview_rejected(client, db, teacher, exchange):
    source, target = exchange
    item = preview(client, teacher, source.classroom_id, export(client, teacher, source))
    assert apply(client, teacher, target.id, item).status_code == 404
    source.revision += 1
    db.commit()
    assert apply(client, teacher, source.classroom_id, item).status_code == 409


def test_same_package_keeps_activity_progress_and_ids(client, db, teacher, student, exchange):
    source, _ = exchange
    maps = content_mapping(db, source)
    activity_id = maps["activity"]["ch1-lesson"]
    progress = ActivityProgress(user_id=student["id"], classroom_id=source.classroom_id, activity_id=activity_id,
                                page_no=42, visited_pages=[10723], completed=False)
    db.add(progress)
    db.commit()
    item = preview(client, teacher, source.classroom_id, export(client, teacher, source))
    assert apply(client, teacher, source.classroom_id, item).status_code == 200
    db.refresh(progress)
    assert progress.page_no == 42 and progress.visited_pages == [10723]
    assert content_mapping(db, source)["activity"]["ch1-lesson"] == activity_id


@pytest.mark.parametrize("name", ["../escape", "/absolute", "C:/escape", "a\\escape", "con.txt", "a/../b"])
def test_malicious_paths_rejected(name, tmp_path):
    archive = tmp_path / "malicious.zip"
    with zipfile.ZipFile(archive, "w") as output:
        info = zipfile.ZipInfo("placeholder")
        info.filename = name
        output.writestr(info, b"bad")
    root = tmp_path / "out"
    root.mkdir()
    with pytest.raises(PackageError):
        unpack_archive(archive, root)


def test_symlinks_and_duplicate_members_rejected(tmp_path):
    for variant in ("symlink", "duplicate"):
        archive = tmp_path / f"{variant}.zip"
        with zipfile.ZipFile(archive, "w") as output:
            info = zipfile.ZipInfo("link")
            info.external_attr = (0o120777 << 16)
            output.writestr(info if variant == "symlink" else "item", "target")
            if variant == "duplicate":
                output.writestr("ITEM", "other")
        root = tmp_path / variant
        root.mkdir()
        with pytest.raises(PackageError):
            unpack_archive(archive, root)


def test_missing_media_blocks_complete_export(client, db, teacher, exchange):
    source, _ = exchange
    material = db.scalar(select(Material).where(Material.course_instance_id == source.id))
    material.kind, material.storage_key = "pdf", "missing/file.pdf"
    db.commit()
    response = client.get(endpoint(source.classroom_id, "export"), headers=teacher["headers"])
    assert response.status_code == 422


def test_student_cannot_export_private_answers(client, student, exchange):
    source, target = exchange
    assert client.get(endpoint(source.classroom_id, "export"), headers=student["headers"]).status_code == 403
    assert client.post(endpoint(target.id, "imports/preview"), headers=student["headers"],
                       files={"file": ("a.zip", b"bad")}).status_code == 403


def rewrite_archive(payload, transform):
    from io import BytesIO
    source, result = BytesIO(payload), BytesIO()
    with zipfile.ZipFile(source) as archive:
        entries = {name: archive.read(name) for name in archive.namelist()}
    transform(entries)
    with zipfile.ZipFile(result, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, raw in entries.items():
            archive.writestr(name, raw)
    return result.getvalue()


def test_real_media_and_unassigned_sources_roundtrip(client, db, teacher, exchange, tmp_path):
    import hashlib
    import json
    from app.course_exchange_models import CoursePackageState
    from app.storage import get_storage
    source, target = exchange
    material = db.scalar(select(Material).where(Material.course_instance_id == source.id))
    raw = "这是真实课件原文件".encode()
    original = tmp_path / "original.txt"
    original.write_bytes(raw)
    get_storage().put("test/original.txt", original)
    material.storage_key = "test/original.txt"
    db.commit()
    payload = export(client, teacher, source)
    extra = b"unused source, retained for editing"
    def attach(entries):
        asset = {"id": "unused-source", "path": "assets/documents/unused.txt", "media_type": "text/plain",
                 "size_bytes": len(extra), "sha256": hashlib.sha256(extra).hexdigest(),
                 "source": "author original", "license": "CC BY 4.0"}
        entries["assets/index.jsonl"] += (json.dumps(asset) + "\n").encode()
        entries[asset["path"]] = extra
    payload = rewrite_archive(payload, attach)
    item = preview(client, teacher, target.id, payload)
    result = apply(client, teacher, target.id, item)
    assert result.status_code == 200, result.text
    imported = db.get(CourseInstance, result.json()["instance_id"])
    assets = db.get(CoursePackageState, imported.id).configuration["_assets"]
    assert any(asset["id"] == "unused-source" and asset["license"] == "CC BY 4.0" for asset in assets)
    from io import BytesIO
    with zipfile.ZipFile(BytesIO(export(client, teacher, imported))) as archive:
        index = [json.loads(line) for line in archive.read("assets/index.jsonl").decode().splitlines()]
        assert {archive.read(asset["path"]) for asset in index} == {raw, extra}
        assert all("storage_key" not in asset for asset in index)


@pytest.mark.parametrize("change", ["dangling", "duplicate", "unknown", "cycle", "answer"])
def test_invalid_domain_package_never_creates_instance(client, db, teacher, exchange, change):
    import json
    import yaml
    source, target = exchange
    def corrupt(entries):
        if change == "unknown":
            entries["curriculum/secret-config.yaml"] = b"credentials: forbidden"
        elif change == "duplicate":
            entries["manifest.yaml"] += b"\nname: duplicate\n"
        elif change == "answer":
            entries["content/secrets/answers.jsonl"] = b""
        elif change == "dangling":
            rows = [json.loads(line) for line in entries["content/materials.jsonl"].decode().splitlines()]
            rows[0]["chapter_ids"] = ["missing-chapter"]
            entries["content/materials.jsonl"] = ("\n".join(json.dumps(row) for row in rows)).encode()
        else:
            name = next(name for name in entries if "/quests/" in name)
            row = yaml.safe_load(entries[name])
            row["definition"]["nodes"][0]["dependencies"] = [row["definition"]["nodes"][0]["id"]]
            entries[name] = yaml.safe_dump(row).encode()
    payload = rewrite_archive(export(client, teacher, source), corrupt)
    response = client.post(endpoint(target.id, "imports/preview"), headers=teacher["headers"], files={"file": ("course.zip", payload)})
    assert response.status_code == 422, response.text
    assert db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == target.id)) is None


def test_history_blocks_course_replacement_and_removal(client, db, teacher, student, exchange):
    import json
    import yaml
    source, _ = exchange
    maps = content_mapping(db, source)
    db.add(ActivityProgress(user_id=student["id"], classroom_id=source.classroom_id,
                            activity_id=maps["activity"]["ch1-lesson"], visited_pages=[10715]))
    db.commit()
    original = export(client, teacher, source)
    def replace(entries):
        manifest = yaml.safe_load(entries["manifest.yaml"])
        manifest["package_id"] = "other-package"
        entries["manifest.yaml"] = yaml.safe_dump(manifest).encode()
    def remove(entries):
        for name in ("content/questions/questions-001.jsonl", "content/secrets/answers.jsonl"):
            rows = [json.loads(line) for line in entries[name].decode().splitlines()]
            entries[name] = ("\n".join(json.dumps(row) for row in rows if row["id"] != "unassigned-draft") + "\n").encode()
    for transform in (replace, remove):
        item = preview(client, teacher, source.classroom_id, rewrite_archive(original, transform))
        assert not item["can_apply"] and item["blockers"]
        assert apply(client, teacher, source.classroom_id, item).status_code == 409
    assert content_mapping(db, source)["question"]["unassigned-draft"] == maps["question"]["unassigned-draft"]


def test_storage_failure_rolls_back_every_definition(client, db, teacher, exchange, tmp_path, monkeypatch):
    from app import course_exchange
    from app.course_exchange_models import CourseImportPreview
    from app.storage import get_storage
    source, target = exchange
    material = db.scalar(select(Material).where(Material.course_instance_id == source.id))
    original = tmp_path / "original.txt"
    original.write_text("original", encoding="utf-8")
    adapter = get_storage()
    adapter.put("test/original.txt", original)
    material.storage_key = "test/original.txt"
    db.commit()
    item = preview(client, teacher, target.id, export(client, teacher, source))
    def fail_put(*args):
        raise OSError("simulated storage failure")
    monkeypatch.setattr(adapter, "put", fail_put)
    monkeypatch.setattr(course_exchange, "get_storage", lambda: adapter)
    response = apply(client, teacher, target.id, item)
    assert response.status_code == 422
    assert db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == target.id)) is None
    assert db.get(CourseImportPreview, item["id"]).result is None


def test_teacher_cannot_use_another_teachers_class_or_preview(client, db, teacher, exchange):
    from app.auth import issue
    from app.models import User
    source, target = exchange
    item = preview(client, teacher, target.id, export(client, teacher, source))
    other = User(student_id="91827461827365", name="其他教师", role="teacher", password_hash=db.get(User, teacher["id"]).password_hash)
    db.add(other)
    db.commit()
    headers = {"Authorization": "Bearer " + issue(db, other)["access_token"]}
    assert client.get(endpoint(source.classroom_id, "export"), headers=headers).status_code == 403
    assert client.post(endpoint(target.id, f"imports/{item['id']}/apply"), headers=headers,
                       json={"request_id": str(uuid4()), "expected_revision": 0}).status_code == 403


def test_request_id_deduplicates_across_new_previews(client, db, teacher, exchange):
    source, target = exchange
    payload = export(client, teacher, source)
    first = preview(client, teacher, target.id, payload)
    request_id = str(uuid4())
    result = apply(client, teacher, target.id, first, request_id)
    assert result.status_code == 200
    second = preview(client, teacher, target.id, payload)
    duplicate = apply(client, teacher, target.id, second, request_id)
    assert duplicate.status_code == 200 and duplicate.json()["duplicate"]
    assert duplicate.json()["revision"] == result.json()["revision"]


def test_pending_story_update_has_concrete_impact_and_blocks(client, db, teacher, student, exchange):
    import yaml
    from app.models import StorySession
    source, _ = exchange
    maps = content_mapping(db, source)
    db.add(StorySession(user_id=student["id"], classroom_id=source.classroom_id,
                        story_id=maps["story"]["first-steps"], npc_key=maps["npc"]["mayor_py"],
                        node_id="test-pending", status="waiting", pending_activity_id=maps["activity"]["ch1-lesson"],
                        revision=0, return_mapping={}, receipts=[]))
    db.commit()
    def edit(entries):
        for name in entries:
            if "/flows/" in name:
                row = yaml.safe_load(entries[name])
                if row["id"] == "first-steps":
                    next(node for node in row["content"]["nodes"] if node["type"] == "dialogue")["text"] = "修改对白"
                    entries[name] = yaml.safe_dump(row, allow_unicode=True).encode()
    item = preview(client, teacher, source.classroom_id, rewrite_archive(export(client, teacher, source), edit))
    assert not item["can_apply"]
    assert any(row["kind"] == "story" and row["stable_id"] == "first-steps" and row["affected_records"] == 1
               for row in item["learning_impact"])
    assert apply(client, teacher, source.classroom_id, item).status_code == 409


def test_zip_update_cannot_bypass_task_reset_confirmation(client, db, teacher, student, exchange):
    import yaml
    from app.models import PlayerQuest

    source, _ = exchange
    quest_id = content_mapping(db, source)["quest"]["arrival"]
    db.add(PlayerQuest(user_id=student["id"], classroom_id=source.classroom_id, quest_id=quest_id))
    db.commit()

    def edit(entries):
        for name, value in entries.items():
            if "/quests/" not in name:
                continue
            row = yaml.safe_load(value)
            if row["id"] == "arrival":
                row["definition"]["nodes"][0]["optional"] = True
                entries[name] = yaml.safe_dump(row, allow_unicode=True).encode()

    item = preview(client, teacher, source.classroom_id, rewrite_archive(export(client, teacher, source), edit))
    assert not item["can_apply"]
    assert any(row["kind"] == "quest" and row["stable_id"] == "arrival" and row["affected_records"] == 1
               for row in item["learning_impact"])
    assert apply(client, teacher, source.classroom_id, item).status_code == 409


def test_assessment_question_order_locked_by_formal_attempt(client, db, teacher, student, exchange):
    import yaml
    from app.models import AssessmentAttempt
    source, _ = exchange
    maps = content_mapping(db, source)
    db.add(AssessmentAttempt(user_id=student["id"], classroom_id=source.classroom_id,
                             activity_id=maps["activity"]["ch1-assessment"], state="draft", question_ids=[], answers={}, submission_ids={}))
    db.commit()
    def edit(entries):
        for name in entries:
            if name.startswith("content/activities/"):
                row = yaml.safe_load(entries[name])
                if row["id"] == "ch1-assessment":
                    row["question_ids"] = list(reversed(row["question_ids"]))
                    entries[name] = yaml.safe_dump(row).encode()
    item = preview(client, teacher, source.classroom_id, rewrite_archive(export(client, teacher, source), edit))
    assert not item["can_apply"]
    assert any(row["kind"] == "activity" and row["affected_records"] == 1 for row in item["learning_impact"])


def test_preview_resource_is_portable_and_storage_keys_are_private(client, db, teacher, exchange, tmp_path):
    import json
    from io import BytesIO
    from app.storage import get_storage
    source, target = exchange
    material = db.scalar(select(Material).where(Material.course_instance_id == source.id))
    original, rendered = tmp_path / "original.ppt", tmp_path / "preview.pdf"
    original.write_bytes(bytes.fromhex("d0cf11e0a1b11ae1") + b"test original")
    rendered.write_bytes(b"%PDF-1.7\nprivate conversion test")
    get_storage().put("private/original.ppt", original)
    get_storage().put("private/preview.pdf", rendered)
    material.kind, material.storage_key = "presentation", "private/original.ppt"
    material.content = {**material.content, "preview_key": "private/preview.pdf"}
    db.commit()
    payload = export(client, teacher, source)
    with zipfile.ZipFile(BytesIO(payload)) as archive:
        rows = [json.loads(line) for line in archive.read("content/materials.jsonl").decode().splitlines()]
        item = next(row for row in rows if row["id"] == material.stable_id)
        assert item["preview_asset_id"] and "preview_key" not in item["content"]
        assert b"private/preview.pdf" not in archive.read("content/materials.jsonl")
    item = preview(client, teacher, target.id, payload)
    result = apply(client, teacher, target.id, item)
    assert result.status_code == 200, result.text
    imported = db.get(CourseInstance, result.json()["instance_id"])
    material_id = content_mapping(db, imported)["material"][material.stable_id]
    assert db.get(Material, material_id).content["preview_key"].startswith("course-assets/")


def test_exchange_files_persist_privately_after_response(client, teacher, exchange, monkeypatch, tmp_path):
    from app.course_exchange import exchange_root
    source, target = exchange
    payload = export(client, teacher, source)
    preview(client, teacher, target.id, payload)
    assert len(list((exchange_root() / "exports").glob("*.zip"))) == 1
    assert len(list((exchange_root() / "imports").glob("*.zip"))) == 1
    assert not list(exchange_root().glob("course-*"))


# This fixture creates and drops only a random schema in the explicitly configured
# isolated Reborn test database; production/default connections are never used.
from test_course_instances import pg_course_engine as pg_course_engine  # noqa: E402


def test_postgres_repeated_apply_is_serialized_and_idempotent(pg_course_engine, tmp_path, monkeypatch):
    from concurrent.futures import ThreadPoolExecutor
    from datetime import timedelta
    from threading import Barrier
    from sqlalchemy.orm import Session
    from app.course_exchange import ApplyInput, _digest, apply_course, exchange_path, write_package
    from app.course_exchange_models import CourseImportPreview
    from app.models import User, now
    from test_course_instances import classes
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "media"))
    monkeypatch.setenv("REBORN_COURSE_EXCHANGE_ROOT", str(tmp_path / "exchange"))
    with Session(pg_course_engine, expire_on_commit=False) as db:
        teacher_row, _, source, target = classes(db)
        instance = clone_current_course(db, source)
        db.commit()
        directory = tmp_path / "package"
        directory.mkdir()
        write_package(db, instance, directory)
        archive = exchange_path("imports/parallel.zip")
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as writer:
            for path in directory.rglob("*"):
                if path.is_file():
                    writer.write(path, path.relative_to(directory).as_posix())
        item = CourseImportPreview(teacher_id=teacher_row.id, classroom_id=target.id, expected_revision=0,
                                   archive_sha256=_digest(archive), storage_key="imports/parallel.zip", summary={},
                                   expires_at=now() + timedelta(hours=1))
        db.add(item)
        db.commit()
        teacher_id, class_id, preview_id = teacher_row.id, target.id, item.id
    barrier, request_id = Barrier(2), uuid4()
    def perform():
        with Session(pg_course_engine) as db:
            user = db.get(User, teacher_id)
            barrier.wait(timeout=20)
            return apply_course(class_id, preview_id, ApplyInput(request_id=request_id, expected_revision=0), db, user)
    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(lambda _: perform(), range(2)))
    assert sorted(row["duplicate"] for row in results) == [False, True]
    assert results[0]["instance_id"] == results[1]["instance_id"]
    with Session(pg_course_engine) as db:
        imported = db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == class_id))
        assert imported.revision == 1
        assert len(db.scalars(select(CourseInstance).where(CourseInstance.classroom_id == class_id)).all()) == 1


def test_postgres_same_instance_reorders_and_removes_unreferenced_content(pg_course_engine, tmp_path, monkeypatch):
    from sqlalchemy.orm import Session
    from app.course_exchange import import_bundle, write_package
    from app.models import Chapter
    from test_course_instances import classes
    monkeypatch.setenv("REBORN_STORAGE_ROOT", str(tmp_path / "media"))
    with Session(pg_course_engine) as db:
        _, _, source, _ = classes(db)
        instance = clone_current_course(db, source)
        db.add(Question(id="pg-disposable", stable_id="disposable", course_instance_id=instance.id, title="草稿", kind="boolean",
                        prompt="draft", status="draft", source_type="imported", options=[], samples=[], source={}))
        db.flush()
        db.add(QuestionSecret(question_id="pg-disposable", answer=None, hidden_cases=[]))
        db.commit()
        root = tmp_path / "package"
        root.mkdir()
        write_package(db, instance, root)
        bundle = load_bundle(root)
        bundle["data"]["question"] = [row for row in bundle["data"]["question"] if row["id"] != "disposable"]
        bundle["secrets"] = [row for row in bundle["secrets"] if row["id"] != "disposable"]
        first, second = bundle["data"]["chapter"][:2]
        first["order_index"], second["order_index"] = second["order_index"], first["order_index"]
        import_bundle(db, source, instance, bundle, root)
        db.commit()
        assert db.get(Question, "pg-disposable") is None
        mapping = content_mapping(db, instance)
        assert db.get(Chapter, mapping["chapter"][first["id"]]).order_index == first["order_index"]


def test_teaching_roles_are_part_of_complete_package(client, db, teacher, exchange):
    from app.course_exchange_models import CoursePackageState
    source, target = exchange
    state = db.get(CoursePackageState, source.id)
    policy = {"schema_version": 1, "roles": {"coach": {"name": "课程教练", "instructions": "先提问再给提示",
              "limits": {"model_calls": 1, "output_tokens": 128}}}, "activity_roles": {"ch1-lesson": "coach"}}
    state.configuration = {**state.configuration, "assistant": policy}
    db.commit()
    item = preview(client, teacher, target.id, export(client, teacher, source))
    result = apply(client, teacher, target.id, item)
    assert result.status_code == 200, result.text
    stored = db.get(CoursePackageState, result.json()["instance_id"])
    assert stored.configuration["assistant"] == policy

@pytest.mark.parametrize("kind", ["zip", "directory"])
def test_available_mounted_package_uses_normal_preview_and_apply(client, db, teacher, exchange, kind):
    from app.course_exchange import exchange_root

    source, target = exchange
    payload = export(client, teacher, source)
    mounted = exchange_root() / "operator-course.zip"
    mounted.write_bytes(payload)
    if kind == "directory":
        folder = exchange_root() / "operator-course"
        folder.mkdir()
        unpack_archive(mounted, folder)
        mounted.unlink()
    listed = client.get(endpoint(target.id, "packages"), headers=teacher["headers"])
    assert listed.status_code == 200, listed.text
    items = listed.json()["items"]
    assert len(items) == 1 and items[0]["source"] == "mounted"
    assert not {"path", "storage_key", "answers", "secrets"} & set(items[0])
    response = client.post(endpoint(target.id, "imports/available/preview"), headers=teacher["headers"],
                           json={"package_key": items[0]["key"]})
    assert response.status_code == 200, response.text
    item = response.json()
    assert item["expected_revision"] == 0 and item["can_apply"]
    assert db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == target.id)) is None
    assert apply(client, teacher, target.id, item).status_code == 200
    assert db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == target.id)).package_id == source.package_id


def test_available_upload_is_private_and_can_be_previewed_again(client, teacher, student, exchange):
    source, target = exchange
    item = preview(client, teacher, target.id, export(client, teacher, source))
    listed = client.get(endpoint(target.id, "packages"), headers=teacher["headers"]).json()["items"]
    assert len(listed) == 1 and listed[0]["key"] == "uploaded-" + item["id"]
    response = client.post(endpoint(target.id, "imports/available/preview"), headers=teacher["headers"],
                           json={"package_key": listed[0]["key"]})
    assert response.status_code == 200, response.text
    assert response.json()["id"] != item["id"]
    assert len(client.get(endpoint(target.id, "packages"), headers=teacher["headers"]).json()["items"]) == 1
    assert client.get(endpoint(source.classroom_id, "packages"), headers=teacher["headers"]).json()["items"] == []
    assert client.post(endpoint(source.classroom_id, "imports/available/preview"), headers=teacher["headers"],
                       json={"package_key": listed[0]["key"]}).status_code == 404
    assert client.get(endpoint(target.id, "packages"), headers=student["headers"]).status_code == 403
    assert client.post(endpoint(target.id, "imports/available/preview"), headers=student["headers"],
                       json={"package_key": listed[0]["key"]}).status_code == 403


def test_available_packages_never_expose_other_teacher_uploads(client, db, teacher, exchange):
    from app.auth import issue
    from app.models import User

    source, target = exchange
    item = preview(client, teacher, target.id, export(client, teacher, source))
    other = User(student_id="87811934201267", name="另一教师", role="teacher", password_hash=db.get(User, teacher["id"]).password_hash)
    db.add(other)
    db.flush()
    headers = {"Authorization": "Bearer " + issue(db, other)["access_token"]}
    db.commit()
    assert client.get(endpoint(target.id, "packages"), headers=headers).status_code == 403
    assert client.post(endpoint(target.id, "imports/available/preview"), headers=headers,
                       json={"package_key": "uploaded-" + item["id"]}).status_code == 403


def test_mounted_selection_is_stale_after_change_and_files_remain_private(client, teacher, exchange):
    from app.course_exchange import exchange_root

    source, target = exchange
    mounted = exchange_root() / "mounted.zip"
    mounted.write_bytes(export(client, teacher, source))
    item = client.get(endpoint(target.id, "packages"), headers=teacher["headers"]).json()["items"][0]
    mounted.write_bytes(b"invalid archive")
    response = client.post(endpoint(target.id, "imports/available/preview"), headers=teacher["headers"], json={"package_key": item["key"]})
    assert response.status_code == 404
    assert str(exchange_root()) not in response.text
    for key in ("../exports/private.zip", str(mounted), "mounted-" + "0" * 64):
        rejected = client.post(endpoint(target.id, "imports/available/preview"), headers=teacher["headers"], json={"package_key": key})
        assert rejected.status_code in {404, 422}


def test_mounted_catalog_does_not_list_private_exports_or_oversized_packages(client, teacher, exchange, monkeypatch):
    import app.course_exchange as exchange_module

    source, target = exchange
    payload = export(client, teacher, source)
    (exchange_module.exchange_root() / "large.zip").write_bytes(payload)
    monkeypatch.setattr(exchange_module, "MAX_ARCHIVE", 10)
    listed = client.get(endpoint(target.id, "packages"), headers=teacher["headers"])
    assert listed.status_code == 200
    assert listed.json()["items"] == []
    assert listed.json()["unavailable"] == 1




def test_available_preview_is_snapshot_and_apply_rechecks_revision(client, db, teacher, exchange):
    from app.course_exchange import exchange_root

    source, _ = exchange
    mounted = exchange_root() / "snapshot.zip"
    mounted.write_bytes(export(client, teacher, source))
    key = client.get(endpoint(source.classroom_id, "packages"), headers=teacher["headers"]).json()["items"][0]["key"]
    response = client.post(endpoint(source.classroom_id, "imports/available/preview"), headers=teacher["headers"], json={"package_key": key})
    assert response.status_code == 200, response.text
    mounted.write_bytes(b"subsequent replacement must not be used")
    source.revision += 1
    db.commit()
    assert apply(client, teacher, source.classroom_id, response.json()).status_code == 409


def test_uploaded_catalog_tampering_is_rejected(client, teacher, exchange):
    from app.course_exchange import exchange_path
    from app.course_exchange_models import CourseImportPreview

    source, target = exchange
    item = preview(client, teacher, target.id, export(client, teacher, source))
    # Lookup only the authenticated upload record; no API accepts a storage path.
    from sqlalchemy.orm import object_session
    db = object_session(source)
    record = db.get(CourseImportPreview, item["id"])
    exchange_path(record.storage_key).write_bytes(b"tampered")
    response = client.post(endpoint(target.id, "imports/available/preview"), headers=teacher["headers"],
                           json={"package_key": "uploaded-" + item["id"]})
    assert response.status_code == 422 and "校验失败" in response.text
    assert db.scalar(select(CourseInstance).where(CourseInstance.classroom_id == target.id)) is None


def test_catalog_reader_rejects_outside_files_and_reparse_points(tmp_path, monkeypatch):
    from pathlib import Path
    from types import SimpleNamespace
    from app.course_catalog import regular_reader

    root = tmp_path / "private"
    root.mkdir()
    outside = tmp_path / "other.zip"
    outside.write_bytes(b"private")
    with pytest.raises(PackageError, match="越界"):
        with regular_reader(outside, root):
            pass
    target = root / "link.zip"
    target.write_bytes(b"placeholder")
    original = Path.lstat
    def linked(path):
        value = original(path)
        return SimpleNamespace(st_mode=value.st_mode, st_file_attributes=0x400) if path == target else value
    monkeypatch.setattr(Path, "lstat", linked)
    with pytest.raises(PackageError, match="链接"):
        with regular_reader(target, root):
            pass
