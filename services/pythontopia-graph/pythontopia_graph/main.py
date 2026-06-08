import logging
from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI
from pydantic import BaseModel

from pythontopia_graph.agents.curriculum_mapper_agent import CurriculumMapperAgent
from pythontopia_graph.agents.profile_agent import ProfileAgent
from pythontopia_graph.llm.provider import (
    load_alethicode_env,
    load_pythontopia_env,
    provider_from_env,
    provider_label,
)
from pythontopia_graph.routes.boss import router as boss_router
from pythontopia_graph.routes.debug import router as debug_router
from pythontopia_graph.routes.intent import router as intent_router
from pythontopia_graph.routes.orchestrator import router as orchestrator_router
from pythontopia_graph.routes.path import router as path_router
from pythontopia_graph.routes.reflection import router as reflection_router
from pythontopia_graph.routes.evaluation import router as evaluation_router
from pythontopia_graph.routes.resource import router as resource_router
from pythontopia_graph.routes.streaming import router as streaming_router
from pythontopia_graph.routes.tutor import router as tutor_router
from pythontopia_graph.routes.video import router as video_router
from pythontopia_graph.schemas.curriculum import MapperInput, MapperOutput
from pythontopia_graph.internal_auth import InternalTokenMiddleware
from pythontopia_graph.schemas.profile import ProfileAgentInput, ProfileAgentOutput


class HealthResponse(BaseModel):
    status: str


LOG = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    # 启动时加载 deploy/.env 与 Alethicode .env，构建共享 LLM provider 供各路由复用；失败不阻塞启动。
    load_pythontopia_env()
    load_alethicode_env()
    try:
        _app.state.llm_provider = provider_from_env()
        LOG.info("LLM provider 已初始化: %s", provider_label())
    except Exception as exc:  # noqa: BLE001 - 启动期容错，路由各自再兜底
        _app.state.llm_provider = None
        LOG.warning("LLM provider 初始化失败，路由将各自兜底: %s", exc)
    yield


app = FastAPI(title="Pythontopia Graph", version="0.1.0", lifespan=lifespan)
app.add_middleware(InternalTokenMiddleware)
app.include_router(resource_router)
app.include_router(path_router)
app.include_router(debug_router)
app.include_router(evaluation_router)
app.include_router(boss_router)
app.include_router(streaming_router)
app.include_router(tutor_router)
app.include_router(orchestrator_router)
app.include_router(intent_router)
app.include_router(reflection_router)
app.include_router(video_router)


@app.get("/healthz", response_model=HealthResponse)
def healthz() -> HealthResponse:
    return HealthResponse(status="ok")


@app.post("/agents/profile/turn", response_model=ProfileAgentOutput)
async def profile_turn(request: ProfileAgentInput) -> ProfileAgentOutput:
    agent = ProfileAgent(provider_from_env())
    return await agent.run(request)


@app.post("/agents/curriculum/map", response_model=MapperOutput)
async def curriculum_map(request: MapperInput) -> MapperOutput:
    return CurriculumMapperAgent().map(request)
