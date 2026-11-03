import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from .db import get_db
from .models import AuthSession, User

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
    return {"id": user.id, "email": user.email, "name": user.name, "role": user.role}


def as_utc(value):
    return value.replace(tzinfo=timezone.utc) if value.tzinfo is None else value.astimezone(timezone.utc)


def current_user(credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)], db: Annotated[Session, Depends(get_db)]) -> User:
    if credentials is None:
        raise HTTPException(401, "请先登录")
    session = db.get(AuthSession, hashlib.sha256(credentials.credentials.encode()).hexdigest())
    if not session or as_utc(session.expires_at) <= datetime.now(timezone.utc):
        raise HTTPException(401, "登录已过期")
    user = db.get(User, session.user_id)
    if not user:
        raise HTTPException(401, "账号不可用")
    return user


def require_teacher(user: Annotated[User, Depends(current_user)]) -> User:
    if user.role not in {"teacher", "admin"}:
        raise HTTPException(403, "需要教师权限")
    return user


class LoginInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    email: str = Field(min_length=3, max_length=254)
    password: str = Field(min_length=10, max_length=200)


class RegisterInput(LoginInput):
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
    email = body.email.strip().lower()
    if "@" not in email:
        raise HTTPException(422, "请输入有效邮箱")
    user = User(email=email, name=body.name.strip(), password_hash=hash_password(body.password), role="student")
    db.add(user)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "邮箱已注册")
    return issue(db, user)


@router.post("/login")
def login(body: LoginInput, db: Annotated[Session, Depends(get_db)]):
    user = db.scalar(select(User).where(User.email == body.email.strip().lower()))
    if not user or not verify_password(body.password, user.password_hash):
        raise HTTPException(401, "邮箱或密码错误")
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
