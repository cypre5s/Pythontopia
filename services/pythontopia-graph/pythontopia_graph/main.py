import logging
import os
from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from pythontopia_graph.agents.curriculum_mapper_agent import CurriculumMapperAgent
from pythontopia_graph.agents.profile_agent import ProfileAgent
from pythontopia_graph.llm.provider import (
    DryRunProvider,
    LlmProviderNotConfigured,
    load_pythontopia_env,
    provider_from_env,
    provider_label,
)
from pythontopia_graph.routes.boss import router as boss_router
from pythontopia_graph.routes.admin_config import router as admin_config_router
from pythontopia_graph.routes.chapter_test import router as chapter_test_router
from pythontopia_graph.routes.code_debug_runtime import router as code_debug_runtime_router
from pythontopia_graph.routes.courseware import router as courseware_router
from pythontopia_graph.routes.debug import router as debug_router
from pythontopia_graph.routes.feynman import router as feynman_router
from pythontopia_graph.routes.intent import router as intent_router
from pythontopia_graph.routes.npc_dialog import router as npc_dialog_router
from pythontopia_graph.routes.orchestrator import router as orchestrator_router
from pythontopia_graph.routes.path import router as path_router
from pythontopia_graph.routes.reflection import router as reflection_router
from pythontopia_graph.routes.evaluation import router as evaluation_router
from pythontopia_graph.routes.resource import router as resource_router
from pythontopia_graph.routes.resource_lifecycle_runtime import router as resource_lifecycle_runtime_router
from pythontopia_graph.routes.streaming import router as streaming_router
from pythontopia_graph.routes.student_learning_runtime import router as student_learning_runtime_router
from pythontopia_graph.routes.system_evolution_runtime import router as system_evolution_runtime_router
from pythontopia_graph.routes.tutor import router as tutor_router
from pythontopia_graph.routes.tutor_workflow import router as tutor_workflow_router
from pythontopia_graph.routes.video import router as video_router
from pythontopia_graph.routes.video_insight import router as video_insight_router
from pythontopia_graph.routes.world_state_runtime import router as world_state_runtime_router
from pythontopia_graph.schemas.curriculum import MapperInput, MapperOutput
from pythontopia_graph.internal_auth import InternalTokenMiddleware
from pythontopia_graph.observability import init_tracing
from pythontopia_graph.schemas.profile import ProfileAgentInput, ProfileAgentOutput
from pythontopia_graph.tools.browse.contract import BrowseRequest, BrowseResult
from pythontopia_graph.tools.browse.executor import browse_runtime_status, execute_browse


class HealthResponse(BaseModel):
    status: str


# 容器内 uvicorn 只配置自己的 logger，root 默认 WARNING 且无 handler——harness 的
# tool_call 审计 INFO 日志会被整体吞掉（治理可观测性失效）。这里兜底配置 root：
# 已有 handler 时 basicConfig 是 no-op，不会重复输出。
logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logging.getLogger("autogen_core").setLevel(logging.WARNING)
logging.getLogger("autogen_core.events").setLevel(logging.WARNING)
logging.getLogger("httpx").setLevel(logging.WARNING)

LOG = logging.getLogger(__name__)
_TRUTHY = frozenset({"1", "true", "yes", "on"})


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    # OpenTelemetry GenAI 可观测性：幂等初始化；缺 opentelemetry 依赖时为 no-op，不影响启动与主链路。
    init_tracing()
    # 启动时只加载本项目 deploy/.env，避免读取 Alethicode 的凭据。
    load_pythontopia_env()
    try:
        _app.state.llm_provider = provider_from_env()
        LOG.info("LLM provider 已初始化: %s", provider_label())
    except Exception as exc:  # noqa: BLE001 - 启动期容错，路由各自再兜底
        _app.state.llm_provider = None
        LOG.warning("LLM provider 初始化失败，路由将各自兜底: %s", type(exc).__name__)
    yield


app = FastAPI(title="Pythontopia Graph", version="0.1.0", lifespan=lifespan)
app.add_middleware(InternalTokenMiddleware)


@app.exception_handler(RequestValidationError)
async def request_validation_error(
    _request: Request, exception: RequestValidationError
) -> JSONResponse:
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
        content={
            "detail": [
                {
                    "loc": list(error["loc"]),
                    "msg": error["msg"],
                    "type": error["type"],
                }
                for error in exception.errors()
            ]
        },
    )


@app.exception_handler(LlmProviderNotConfigured)
async def llm_provider_not_configured(
    _request: Request, _exception: LlmProviderNotConfigured
) -> JSONResponse:
    return JSONResponse(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        content={"detail": "DeepSeek provider is not configured"},
    )


app.include_router(resource_router)
app.include_router(admin_config_router)
app.include_router(path_router)
app.include_router(debug_router)
app.include_router(evaluation_router)
app.include_router(boss_router)
app.include_router(chapter_test_router)
app.include_router(code_debug_runtime_router)
app.include_router(streaming_router)
app.include_router(student_learning_runtime_router)
app.include_router(tutor_router)
app.include_router(tutor_workflow_router)
app.include_router(courseware_router)
app.include_router(orchestrator_router)
app.include_router(intent_router)
app.include_router(reflection_router)
app.include_router(video_router)
app.include_router(video_insight_router)
app.include_router(npc_dialog_router)
app.include_router(feynman_router)
app.include_router(resource_lifecycle_runtime_router)
app.include_router(world_state_runtime_router)
app.include_router(system_evolution_runtime_router)


@app.get("/healthz", response_model=HealthResponse)
def healthz() -> HealthResponse:
    return HealthResponse(status="ok")


@app.get("/readyz", response_model=HealthResponse)
def readyz(request: Request) -> HealthResponse:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is None or isinstance(provider, DryRunProvider):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="real LLM provider is not ready",
        )
    if os.environ.get("BROWSE_ENABLED", "").strip().lower() in _TRUTHY:
        browse_ready, reason = browse_runtime_status()
        if not browse_ready:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=reason,
            )
    return HealthResponse(status="ready")


@app.post("/agents/browse/run", response_model=BrowseResult)
async def browse_run(request: BrowseRequest) -> BrowseResult:
    """内部调用的有界只读查证端点；未显式启用时 fail closed。"""
    if os.environ.get("BROWSE_ENABLED", "").strip().lower() not in _TRUTHY:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="BROWSE 未启用",
        )
    return await execute_browse(request)


@app.post("/agents/profile/turn", response_model=ProfileAgentOutput)
async def profile_turn(request: ProfileAgentInput) -> ProfileAgentOutput:
    agent = ProfileAgent(provider_from_env())
    return await agent.run(request)


@app.post("/agents/curriculum/map", response_model=MapperOutput)
async def curriculum_map(request: MapperInput) -> MapperOutput:
    return CurriculumMapperAgent().map(request)
