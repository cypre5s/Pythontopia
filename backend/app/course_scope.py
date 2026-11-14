"""Resolve the single content namespace for a classroom; never mix cloned and source rows."""

from fastapi import HTTPException
from sqlalchemy import select, text

from .models import CourseInstance, Npc


def lock_course(db, classroom_id, *, exclusive=False):
    """Fence content activation against requests holding old definition references.

    PostgreSQL transaction advisory locks release on commit/rollback. Separate
    namespace avoids coupling the gate to the assistant's classroom row budget lock.
    """
    if db.get_bind().dialect.name == "postgresql":
        function = "pg_advisory_xact_lock" if exclusive else "pg_advisory_xact_lock_shared"
        db.execute(text(f"SELECT {function}(41729, :classroom_id)"), {"classroom_id": classroom_id})


def instance_id(db, classroom_id):
    lock_course(db, classroom_id)
    key = db.scalar(select(CourseInstance.id).where(CourseInstance.classroom_id == classroom_id))
    if key is None:
        raise HTTPException(409, "本班尚未创建或导入课程，请联系教师")
    return key


def course_clause(db, model, classroom_id):
    """A classroom can only address its explicit course instance."""
    key = instance_id(db, classroom_id)
    return model.course_instance_id == key


def belongs_to_course(db, item, classroom_id):
    return item is not None and getattr(item, "deleted_at", None) is None and item.course_instance_id == instance_id(db, classroom_id)


def require_course_content(db, item, classroom_id):
    if not belongs_to_course(db, item, classroom_id):
        raise HTTPException(403, "内容不属于当前班级课程")
    return item


def npc_by_stable_id(db, classroom_id, stable_id):
    """Resolve the course character referenced by its portable stable ID."""
    return db.scalar(select(Npc).where(course_clause(db, Npc, classroom_id), Npc.stable_id == stable_id))
