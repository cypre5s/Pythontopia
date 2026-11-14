"""Administrator-owned model connection. Secrets never enter course packages."""
import os
import time
from pathlib import Path
from typing import Annotated, Literal
from urllib.parse import urlparse

from cryptography.fernet import Fernet, InvalidToken
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import select
from sqlalchemy.orm import Session

from .auth import require_teacher
from .db import get_db
from .integration_models import AssistantModelSettings
from .models import User

router = APIRouter(prefix="/teacher/assistant/model", tags=["assistant-settings"])
DEFAULTS = {"context_window": 32768, "compression_threshold": 24576, "model_calls": 6,
            "tool_calls": 12, "output_tokens": 2048, "timeout_seconds": 120, "request_tokens": 262144}
CAPS = {"model_calls": 20, "tool_calls": 40, "output_tokens": 8192, "timeout_seconds": 300, "request_tokens": 2000000}


class ModelInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    expected_revision: int = Field(ge=0)
    base_url: str = Field(max_length=500)
    model: str = Field(min_length=1, max_length=200)
    api_key: str | None = Field(default=None, max_length=4096)
    clear_key: bool = False
    reasoning_effort: Literal["", "none", "minimal", "low", "medium", "high"] = ""
    context_window: int = Field(default=32768, ge=8192, le=2000000)
    compression_threshold: int = Field(default=24576, ge=4096, le=1900000)
    model_calls: int = Field(default=6, ge=1, le=20)
    tool_calls: int = Field(default=12, ge=0, le=40)
    output_tokens: int = Field(default=2048, ge=128, le=8192)
    timeout_seconds: int = Field(default=120, ge=10, le=300)
    request_tokens: int = Field(default=262144, ge=8192, le=2000000)

    @model_validator(mode="after")
    def valid(self):
        url = urlparse(self.base_url)
        if url.scheme not in {"http", "https"} or not url.hostname or url.username or url.password or url.query or url.fragment:
            raise ValueError("请输入不含凭据、查询参数的 HTTP(S) API 地址")
        if self.compression_threshold + self.output_tokens + 2048 >= self.context_window:
            raise ValueError("压缩阈值须为回复和工具预留至少 2048 tokens")
        if self.request_tokens < self.context_window:
            raise ValueError("单次累计预算不能小于上下文上限")
        if self.clear_key and self.api_key:
            raise ValueError("不能同时清除和替换密钥")
        return self


def cipher(create=False):
    root = Path(os.getenv("REBORN_STORAGE_ROOT", "storage"))
    path = Path(os.getenv("REBORN_AI_MASTER_KEY_FILE", str(root / ".secrets" / "assistant.key")))
    try:
        if create and not path.exists():
            path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            try:
                fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            except FileExistsError:
                pass
            else:
                with os.fdopen(fd, "wb") as stream:
                    stream.write(Fernet.generate_key())
                    stream.flush()
                    os.fsync(stream.fileno())
        return Fernet(path.read_bytes())
    except (OSError, ValueError):
        raise HTTPException(503, "模型密钥存储不可用，请检查私有主密钥文件") from None


def configuration(db=None, *, include_key=True):
    row = db.get(AssistantModelSettings, 1) if db is not None else None
    if row is None:
        return {**DEFAULTS, "base_url": os.getenv("REBORN_AI_BASE_URL", "https://api.openai.com/v1"),
                "model": os.getenv("REBORN_AI_MODEL", ""), "api_key": os.getenv("REBORN_AI_API_KEY", "") if include_key else "",
                "reasoning_effort": os.getenv("REBORN_AI_REASONING_EFFORT", "")}
    try:
        key = cipher().decrypt(row.encrypted_key.encode()).decode() if row.encrypted_key and include_key else ""
    except InvalidToken:
        raise HTTPException(503, "模型密钥无法解密，请恢复对应主密钥文件") from None
    return {**DEFAULTS, **row.configuration, "api_key": key}


def model_kwargs(config, output_tokens):
    result = {"api_key": config["api_key"], "base_url": config["base_url"] or None, "model": config["model"]}
    if config.get("reasoning_effort"):
        result["reasoning_effort"] = config["reasoning_effort"]
    if output_tokens is not None:
        if urlparse(config["base_url"] or "").hostname == "api.deepseek.com":
            result["extra_body"] = {"max_tokens": output_tokens}
        else:
            result["max_tokens"] = output_tokens
    return result


def require_admin(user):
    if user.role != "admin":
        raise HTTPException(403, "只有管理员可以管理全站模型连接")


@router.get("")
def read_model(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    require_admin(user)
    row = db.get(AssistantModelSettings, 1)
    config = configuration(db, include_key=False)
    config.pop("api_key")
    configured = bool(row.encrypted_key) if row else bool(os.getenv("REBORN_AI_API_KEY"))
    return {**config, "revision": row.revision if row else 0, "key_configured": configured,
            "key_mask": "••••••••" if configured else "", "source": "database" if row else "environment",
            "token_counting": "模型支持时使用 tokenizer，否则保守估算"}


@router.put("")
def save_model(body: ModelInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    require_admin(user)
    # The admin row also serializes the first insert, when there is no settings row yet.
    db.scalar(select(User).where(User.role == "admin").order_by(User.id).limit(1).with_for_update())
    row = db.get(AssistantModelSettings, 1, with_for_update=True)
    if body.expected_revision != (row.revision if row else 0):
        raise HTTPException(409, "模型设置已修改，请重新加载，草稿已保留")
    key = "" if body.clear_key else (body.api_key or configuration(db)["api_key"])
    if row is None:
        row = AssistantModelSettings(id=1, revision=0)
        db.add(row)
    row.configuration = body.model_dump(exclude={"expected_revision", "api_key", "clear_key"})
    row.encrypted_key = cipher(create=True).encrypt(key.encode()).decode() if key else ""
    row.revision += 1
    db.commit()
    return read_model(db, user)


@router.post("/test")
async def test_model(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    from langchain_openai import ChatOpenAI
    require_admin(user)
    config = configuration(db)
    if not config["api_key"]:
        raise HTTPException(503, "请先保存模型密钥")
    db.rollback()
    started = time.monotonic()
    try:
        result = await ChatOpenAI(**model_kwargs(config, 16), timeout=15, max_retries=0).ainvoke("请只回复 OK")
    except Exception:
        raise HTTPException(502, "模型连接测试失败，请检查地址、模型和密钥") from None
    return {"ok": True, "elapsed_ms": round((time.monotonic() - started) * 1000),
            "tokens": (result.usage_metadata or {}).get("total_tokens", 0)}
