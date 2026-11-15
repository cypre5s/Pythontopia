"""Manage classrooms, memberships and isolated teacher preview identities."""

import secrets

from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth import hash_password, issue, preview_classroom, token_hash
from app.classrooms.access import classroom_payload, own_classroom
from app.classrooms.contracts import ClassroomInput, JoinInput
from app.models import Classroom, Membership, TownPreviewStudent, User
from app.teacher_scope import teacher_instance_id


def list_classrooms(db: Session, user: User):
    preview = db.get(TownPreviewStudent, user.id)
    if preview:
        classroom = preview_classroom(db, preview)
        return [classroom_payload(classroom, user, db.get(User, classroom.teacher_id).name)]
    memberships = select(Membership.classroom_id).where(Membership.user_id == user.id)
    query = select(Classroom, User.name).join(User, User.id == Classroom.teacher_id)
    if user.role != "admin":
        query = query.where((Classroom.teacher_id == user.id) | Classroom.id.in_(memberships))
    return [classroom_payload(item, user, name) for item, name in db.execute(query)]


def create_classroom(body: ClassroomInput, db: Session, user: User):
    item = Classroom(name=body.name.strip(), teacher_id=user.id, join_code=secrets.token_urlsafe(9))
    db.add(item)
    db.commit()
    return classroom_payload(item, user, user.name)


def open_town_session(classroom_id: int, db: Session, user: User, credentials: HTTPAuthorizationCredentials):
    teacher_instance_id(db, user, classroom_id, exclusive=True)
    preview = db.scalar(
        select(TownPreviewStudent).where(
            TownPreviewStudent.teacher_id == user.id,
            TownPreviewStudent.classroom_id == classroom_id,
        )
    )
    if preview:
        student = db.get(User, preview.student_user_id)
        if not student or student.role != "student" or student.must_change_password:
            raise HTTPException(409, "本班的体验学生账号不可用")
    else:
        student = User(
            student_id=f"{secrets.randbelow(10**32):032d}",
            name="教师体验学生",
            role="student",
            password_hash=hash_password(secrets.token_urlsafe(32)),
        )
        db.add(student)
        db.flush()
        db.add(TownPreviewStudent(student_user_id=student.id, teacher_id=user.id, classroom_id=classroom_id))
    return issue(db, student, preview_teacher_token_hash=token_hash(credentials.credentials))


def join_classroom(body: JoinInput, db: Session, user: User):
    if db.get(TownPreviewStudent, user.id):
        raise HTTPException(403, "体验学生只能进入教师选择的班级")
    classroom = db.scalar(select(Classroom).where(Classroom.join_code == body.join_code.strip()))
    if not classroom:
        raise HTTPException(404, "加入码无效")
    if not db.get(Membership, (user.id, classroom.id)):
        db.add(Membership(user_id=user.id, classroom_id=classroom.id))
        db.commit()
    teacher = db.get(User, classroom.teacher_id)
    return classroom_payload(classroom, user, teacher.name if teacher else None)


def remove_member(classroom_id: int, student_id: int, db: Session, user: User):
    own_classroom(db, user, classroom_id)
    item = db.get(Membership, (student_id, classroom_id))
    if item:
        db.delete(item)
        db.commit()
    return {"ok": True}
