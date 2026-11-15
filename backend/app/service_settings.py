"""Admin-owned authoring and speech connections; encrypted, independently versioned.

The existing configuration store reserves id 1 for the student assistant, 2 for
authoring and 3 for speech. Environment values initialize an unsaved service only.
"""
from typing import Annotated, Literal
from urllib.parse import urlparse

from cryptography.fernet import InvalidToken
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import select
from sqlalchemy.orm import Session

from .assistant_settings import cipher, require_admin
from .auth import require_teacher
from .db import get_db
from .integration_models import AssistantModelSettings
from .models import User

Service = Literal["authoring", "speech"]
IDS = {"authoring": 2, "speech": 3}
router = APIRouter(prefix="/teacher/services", tags=["service-settings"])


class ConnectionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    expected_revision: int = Field(ge=0)
    base_url: str = Field(max_length=500)
    api_key: str | None = Field(default=None, max_length=4096)
    clear_key: bool = False
    timeout: int = Field(default=120, ge=10, le=600)

    @model_validator(mode="after")
    def valid_connection(self):
        url = urlparse(self.base_url)
        if self.base_url and (url.scheme not in {"http", "https"} or not url.hostname
                              or url.username or url.password or url.query or url.fragment):
            raise ValueError("请输入不含凭据和查询参数的 HTTP(S) API 地址")
        if self.clear_key and self.api_key:
            raise ValueError("不能同时清除和替换密钥")
        self.base_url = self.base_url.rstrip("/")
        return self


class AuthoringInput(ConnectionInput):
    model: str = Field(min_length=1, max_length=200)
    vision_model: str = Field(min_length=1, max_length=200)
    effort: Literal["none", "minimal", "low", "medium", "high"] = "none"
    max_tokens: int = Field(default=8000, ge=128, le=65536)
    daily_pages: int = Field(default=300, ge=1, le=10000)


class SpeechInput(ConnectionInput):
    protocol: Literal["openai", "deepseek"] = "openai"
    model: str = Field(max_length=200)
    voice: str = Field(min_length=1, max_length=200)
    format: Literal["mp3", "wav", "pcm", "opus", "flac", "aac"] = "mp3"
    speed: float = Field(default=1, ge=0.25, le=4)

    @model_validator(mode="after")
    def valid_speech(self):
        if self.protocol == "deepseek" and (self.format not in {"wav", "pcm"} or self.speed != 1):
            raise ValueError("DeepSeek 语音使用 wav/pcm，合成语速固定为 1")
        if self.protocol == "openai" and not self.model.strip():
            raise ValueError("请填写语音模型")
        return self


def environment(service: Service):
    from .narration.defaults import authoring_environment, speech_environment
    return authoring_environment() if service == "authoring" else speech_environment()


def configuration(service: Service, db: Session | None = None, *, include_key=True):
    row = db.get(AssistantModelSettings, IDS[service]) if db is not None else None
    if row is None:
        value = dict(environment(service))
        if not include_key:
            value["api_key"] = ""
        return value
    try:
        key = cipher().decrypt(row.encrypted_key.encode()).decode() if include_key and row.encrypted_key else ""
    except InvalidToken:
        raise HTTPException(503, "服务密钥无法解密，请恢复对应主密钥文件") from None
    return {**row.configuration, "api_key": key}


@router.get("/{service}")
def read_service(service: Service, db: Annotated[Session, Depends(get_db)],
                 user: Annotated[User, Depends(require_teacher)]):
    require_admin(user)
    row = db.get(AssistantModelSettings, IDS[service])
    value = configuration(service, db, include_key=False)
    value.pop("api_key", None)
    configured = bool(row.encrypted_key) if row else bool(environment(service)["api_key"])
    return {**value, "revision": row.revision if row else 0, "key_configured": configured,
            "source": "database" if row else "environment"}


def save(service, body, db, user):
    require_admin(user)
    db.scalar(select(User).where(User.role == "admin").order_by(User.id).limit(1).with_for_update())
    row = db.get(AssistantModelSettings, IDS[service], with_for_update=True)
    if body.expected_revision != (row.revision if row else 0):
        raise HTTPException(409, "服务设置已修改，请重新加载；未保存草稿已保留")
    key = "" if body.clear_key else (body.api_key or configuration(service, db)["api_key"])
    if row is None:
        row = AssistantModelSettings(id=IDS[service], revision=0)
        db.add(row)
    row.configuration = body.model_dump(exclude={"expected_revision", "api_key", "clear_key"})
    row.encrypted_key = cipher(create=True).encrypt(key.encode()).decode() if key else ""
    row.revision += 1
    db.commit()
    return read_service(service, db, user)


@router.put("/authoring")
def save_authoring(body: AuthoringInput, db: Annotated[Session, Depends(get_db)],
                   user: Annotated[User, Depends(require_teacher)]):
    return save("authoring", body, db, user)


@router.put("/speech")
def save_speech(body: SpeechInput, db: Annotated[Session, Depends(get_db)],
                user: Annotated[User, Depends(require_teacher)]):
    return save("speech", body, db, user)
