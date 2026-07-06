from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from pythontopia_rag.app_state import AppState
from pythontopia_rag.schemas.embed import EmbedRequest, EmbedResponse

router = APIRouter()


def _state(request: Request) -> AppState:
    state: AppState = request.app.state.rag
    return state


@router.post("/embed", response_model=EmbedResponse)
async def embed(body: EmbedRequest, state: AppState = Depends(_state)) -> EmbedResponse:
    """批量文本向量化端点，供后端记忆流/费曼误解的语义相关性检索复用同一 embedder。

    空 texts 直接返回空向量，避免无谓推理；model/dim 始终回填当前 embedder 元信息，
    便于调用方对齐持久化向量维度（512/1024/2048 随 PYT_RAG_EMBEDDER 切换）。
    """
    vectors = await state.embedder.embed(body.texts) if body.texts else []
    return EmbedResponse(model=state.embedder.name, dim=state.embedder.dim, vectors=vectors)
