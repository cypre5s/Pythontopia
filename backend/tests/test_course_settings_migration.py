"""Upgrade removes the old stored key once, without touching other course data."""
from sqlalchemy.orm import Session

from app.course_exchange_models import CoursePackageState
from app.models import Classroom, CourseInstance, User
from test_curriculum_migration import migrate, sqlite_engine


def test_persisted_default_is_renamed_once(tmp_path):
    path = tmp_path / "settings.sqlite"
    url = "sqlite:///" + path.as_posix()
    migrate(url, "upgrade", "ea30c9b172f4")
    engine = sqlite_engine(path)
    with Session(engine) as db:
        teacher = User(student_id="871955", name="教师", role="teacher", password_hash="unused")
        db.add(teacher)
        db.flush()
        classroom = Classroom(name="保留班级", teacher_id=teacher.id, join_code="settings-migration")
        db.add(classroom)
        db.flush()
        instance = CourseInstance(classroom_id=classroom.id, name="课程", package_id="test", package_version="2")
        db.add(instance)
        db.flush()
        instance_id = instance.id
        db.add(CoursePackageState(course_instance_id=instance_id, configuration={
            "assistant": {"library_npc_id": "guide", "enabled": False, "limits": {"model_calls": 1}}, "_assets": []}))
        db.commit()
    engine.dispose()
    migrate(url, "upgrade", "head")
    migrate(url, "upgrade", "head")
    with Session(sqlite_engine(path)) as db:
        config = db.get(CoursePackageState, instance_id).configuration
        assert config == {"assistant": {"default_npc_id": "guide", "enabled": False, "limits": {"model_calls": 1}}, "_assets": []}
