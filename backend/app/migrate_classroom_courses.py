"""Explicit operator migration of legacy classroom courses; never run at startup.

--check executes each candidate inside a rolled-back savepoint; rows and learner
records are retained, although PostgreSQL sequences can advance during rehearsal.
--apply commits each classroom independently. Failed classes keep their old state.
"""
import argparse
import json
import sys

from sqlalchemy import select
from sqlalchemy.exc import SQLAlchemyError

from .course_instances import CourseCloneError, classroom_course, clone_current_course, content_mapping
from .models import (
    ActivityProgress, AssessmentAttempt, ChapterRelease, Classroom, CourseInstance, LearningRecord,
    PlayerQuest, StorySession, Submission,
)
from .integration_models import AssistantSession


def _has_course(db, classroom_id):
    # Empty newly-created classrooms must not silently inherit global demo sources.
    for model in (ChapterRelease, PlayerQuest, ActivityProgress, LearningRecord, Submission,
                  AssessmentAttempt, StorySession, AssistantSession):
        if db.scalar(select(model.classroom_id).where(model.classroom_id == classroom_id).limit(1)) is not None:
            return True
    return False


def migrate_classrooms(session_factory, classroom_ids=None, *, apply=False):
    """Return summaries only; all changes to one class commit or roll back together."""
    if classroom_ids is not None:
        if not classroom_ids or any(type(item) is not int or item < 1 for item in classroom_ids):
            raise ValueError("班级ID必须为正整数列表")
        classroom_ids = sorted(set(classroom_ids))
    else:
        with session_factory() as db:
            classroom_ids = list(db.scalars(select(Classroom.id).outerjoin(
                CourseInstance, CourseInstance.classroom_id == Classroom.id).where(CourseInstance.id.is_(None)).order_by(Classroom.id)))
    results = []
    for classroom_id in classroom_ids:
        try:
            with session_factory() as db, db.begin():
                classroom = db.get(Classroom, classroom_id)
                if classroom is None:
                    results.append({"classroom_id": classroom_id, "status": "failed", "reason": "班级不存在"})
                    continue
                existing = classroom_course(db, classroom_id)
                if existing:
                    results.append({"classroom_id": classroom_id, "status": "already_active", "course_instance_id": existing.id})
                    continue
                if not _has_course(db, classroom_id):
                    results.append({"classroom_id": classroom_id, "status": "empty_class", "reason": "无旧课程发布或学习记录，未导入演示内容"})
                    continue
                rehearsal = db.begin_nested()
                try:
                    instance = clone_current_course(db, classroom)
                    counts = {kind: len(rows) for kind, rows in content_mapping(db, instance).items()}
                    result = {"classroom_id": classroom_id, "status": "applied" if apply else "checked", "counts": counts}
                    if apply:
                        result["course_instance_id"] = instance.id
                        rehearsal.commit()
                    else:
                        rehearsal.rollback()
                except Exception:
                    rehearsal.rollback()
                    raise
            results.append(result)
        except CourseCloneError as exc:
            results.append({"classroom_id": classroom_id, "status": "failed", "reason": str(exc)})
        except SQLAlchemyError:
            results.append({"classroom_id": classroom_id, "status": "failed", "reason": "数据库约束或并发操作冲突，班级迁移已回滚"})
    return {"mode": "apply" if apply else "check", "ok": all(row["status"] != "failed" for row in results), "classrooms": results}


def main(argv=None):
    parser = argparse.ArgumentParser(description="Explicit per-class legacy migration; --check rolls back each rehearsal.")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--apply", action="store_true")
    targets = parser.add_mutually_exclusive_group(required=True)
    targets.add_argument("--classroom-id", type=int, action="append", help="Repeat to select specific classes")
    targets.add_argument("--all-legacy", action="store_true", help="Explicitly select all uninstantiated legacy classes")
    args = parser.parse_args(argv)
    from .db import SessionLocal
    from . import course_exchange_models  # noqa: F401 -- register complete metadata

    try:
        result = migrate_classrooms(SessionLocal, args.classroom_id, apply=args.apply)
    except (ValueError, SQLAlchemyError):
        print(json.dumps({"ok": False, "error": "迁移配置无效或无法访问指定数据库"}), file=sys.stderr)
        return 2
    print(json.dumps(result))
    return 0 if result["ok"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
