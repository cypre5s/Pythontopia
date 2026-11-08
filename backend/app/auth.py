import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import select, delete
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from .config import dev_tools_enabled
from .db import get_db
from .models import AuthSession, User
from .account_ids import normalize_student_id

router = APIRouter(prefix="/auth", tags=["auth"])
bearer = HTTPBearer(auto_error=False)


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.scrypt(password.encode(), salt=salt, n=16384, r=8, p=1)
    return salt.hex() + ":" + digest.hex()


def verify_password(password: str, encoded: str) -> bool:
    salt, digest = encoded.split(":")
    return hmac.compare_digest(hashlib.scrypt(password.encode(), salt=bytes.fromhex(salt), n=16384, r=8, p=1).hex(), digest)


def public_user(user: User) -> dict:
    return {
        "id": user.id,
        "student_id": user.student_id,
        "name": user.name,
        "role": user.role,
        "must_change_password": user.must_change_password,
        "dev_tools_enabled": user.role == "student" and dev_tools_enabled(),
    }


def as_utc(value):
    return value.replace(tzinfo=timezone.utc) if value.tzinfo is None else value.astimezone(timezone.utc)


def current_user(request: Request, credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)], db: Annotated[Session, Depends(get_db)]) -> User:
    if credentials is None:
        raise HTTPException(401, "请先登录")
    session = db.get(AuthSession, hashlib.sha256(credentials.credentials.encode()).hexdigest())
    if not session or as_utc(session.expires_at) <= datetime.now(timezone.utc):
        raise HTTPException(401, "登录已过期")
    user = db.get(User, session.user_id)
    if not user or not user.student_id:
        raise HTTPException(401, "账号不可用")
    if user.must_change_password and request.url.path not in {"/api/auth/me", "/api/auth/password", "/api/auth/logout"}:
        raise HTTPException(403, "请先修改初始密码")
    return user


def require_teacher(user: Annotated[User, Depends(current_user)]) -> User:
    if user.role not in {"teacher", "admin"}:
        raise HTTPException(403, "需要教师权限")
    return user


class LoginInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    student_id: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=200)

    @field_validator("student_id")
    @classmethod
    def valid_student_id(cls, value: str) -> str:
        return normalize_student_id(value)


class RegisterInput(LoginInput):
    password: str = Field(min_length=10, max_length=200)
    name: str = Field(min_length=1, max_length=80)


def issue(db, user):
    token = secrets.token_urlsafe(40)
    db.add(
        AuthSession(token_hash=hashlib.sha256(token.encode()).hexdigest(), user_id=user.id, expires_at=datetime.now(timezone.utc) + timedelta(days=7))
    )
    db.commit()
    return {"access_token": token, "token_type": "bearer", "user": public_user(user)}


@router.post("/register", status_code=201)
def register(body: RegisterInput, db: Annotated[Session, Depends(get_db)]):
    student_id = body.student_id
    user = User(student_id=student_id, name=body.name.strip(), password_hash=hash_password(body.password), role="student")
    db.add(user)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "学号已注册")
    return issue(db, user)


@router.post("/login")
def login(body: LoginInput, db: Annotated[Session, Depends(get_db)]):
    user = db.scalar(select(User).where(User.student_id == body.student_id).with_for_update())
    if not user or not verify_password(body.password, user.password_hash):
        raise HTTPException(401, "学号或密码错误")
    return issue(db, user)


class PasswordInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    current_password: str = Field(min_length=1, max_length=200)
    new_password: str = Field(min_length=10, max_length=200)


@router.post("/password")
def change_password(body: PasswordInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    user = db.scalar(select(User).where(User.id == user.id).with_for_update().execution_options(populate_existing=True))
    if not verify_password(body.current_password, user.password_hash):
        raise HTTPException(400, "当前密码不正确")
    if body.new_password == user.student_id or verify_password(body.new_password, user.password_hash):
        raise HTTPException(422, "新密码不能与学号或当前密码相同")
    user.password_hash = hash_password(body.new_password)
    user.must_change_password = False
    db.execute(delete(AuthSession).where(AuthSession.user_id == user.id))
    return issue(db, user)


@router.get("/me")
def me(user: Annotated[User, Depends(current_user)]):
    return public_user(user)


@router.post("/logout")
def logout(
    credentials: Annotated[HTTPAuthorizationCredentials, Depends(bearer)],
    user: Annotated[User, Depends(current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    session = db.get(AuthSession, hashlib.sha256(credentials.credentials.encode()).hexdigest())
    if session:
        db.delete(session)
        db.commit()
    return {"ok": True}
