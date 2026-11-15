"""Persisted course world configuration and player building access evidence."""

from fastapi import HTTPException
from sqlalchemy import select

from app.course_exchange_models import CoursePackageState
from app.course_instances import classroom_course
from app.models import BuildingUnlock
from app.teaching.world.rules import interior_buildings


def configuration(db, classroom_id):
    instance = classroom_course(db, classroom_id)
    state = db.get(CoursePackageState, instance.id) if instance else None
    return state.configuration if state else {}


def unlocked_buildings(db, user, classroom_id):
    instance = classroom_course(db, classroom_id)
    ids = (
        set(
            db.scalars(
                select(BuildingUnlock.building_id).where(
                    BuildingUnlock.user_id == user.id, BuildingUnlock.classroom_id == classroom_id, BuildingUnlock.course_instance_id == instance.id
                )
            )
        )
        if instance
        else set()
    )
    return ids


def unlock_building(db, user, classroom_id, building_id):
    if building_id not in interior_buildings():
        raise HTTPException(409, "建筑没有室内")
    instance = classroom_course(db, classroom_id)
    if instance is None:
        raise HTTPException(409, "班级尚未导入课程")
    key = (user.id, classroom_id, instance.id, building_id)
    if not db.get(BuildingUnlock, key):
        db.add(BuildingUnlock(user_id=user.id, classroom_id=classroom_id, course_instance_id=instance.id, building_id=building_id))
