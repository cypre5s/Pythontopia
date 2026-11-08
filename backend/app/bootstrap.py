"""Create the first administrator only; startup never rotates existing credentials."""
import os
from sqlalchemy import select, text
from .account_ids import normalize_student_id
from .auth import hash_password
from .db import SessionLocal
from .models import User


def bootstrap_admin(db):
    # Serialize first-start workers before checking for an existing administrator.
    if db.bind.dialect.name == "postgresql":
        db.execute(text("SELECT pg_advisory_xact_lock(718305204)"))
    if db.scalar(select(User.id).where(User.role == "admin").limit(1)):
        return False
    student_id = os.getenv("REBORN_ADMIN_STUDENT_ID", "").strip()
    password = os.getenv("REBORN_ADMIN_PASSWORD", "")
    name = os.getenv("REBORN_ADMIN_NAME", "管理员").strip()
    if not student_id and not password:
        return False
    if not student_id or len(password) < 12 or len(password) > 200 or not 1 <= len(name) <= 80:
        raise ValueError("首次管理员配置无效：需要学号、12–200位密码和1–80字姓名")
    student_id = normalize_student_id(student_id)
    if password == student_id:
        raise ValueError("管理员密码不能与学号相同")
    if db.scalar(select(User.id).where(User.student_id == student_id)):
        raise ValueError("首次管理员学号已被其他账号占用，请使用未注册学号")
    db.add(User(student_id=student_id, name=name, role="admin", password_hash=hash_password(password)))
    db.flush()
    return True


def initialize_admin():
    with SessionLocal.begin() as db:
        bootstrap_admin(db)
