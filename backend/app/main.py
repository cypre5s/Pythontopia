import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import text

import app.materials.jobs as materials_jobs
from app import (
    assessments,
    assistant,
    auth,
    character_assets,
    classroom_courses,
    course_settings,
    courses,
    curriculum,
    dev_tools,
    practice,
    student_accounts,
)
from app.classrooms import router as classrooms
from app.course_content import router as course_content
from app.course_packages import router as course_exchange
from app.course_packages.cleanup import cleanup_worker
from app.materials import router as storage
from app.teaching.authoring import router as world_authoring
from app.teaching.quests import router as quests
from app.teaching.story import router as story
from app.teaching.world import router as world

from . import assistant_settings, content_deletions, judge_management, narration, service_settings
from .config import CORS_ORIGINS, dev_tools_enabled
from .db import SessionLocal
from .narration import management as synthesis_management


@asynccontextmanager
async def lifespan(app):
    from .bootstrap import initialize_admin

    initialize_admin()
    assistant.recover_interrupted_calls()
    tasks = [
        asyncio.create_task(practice.judge_worker()),
        asyncio.create_task(materials_jobs.material_worker()),
        asyncio.create_task(cleanup_worker()),
    ]
    yield
    for task in tasks:
        task.cancel()
    await asyncio.gather(*tasks, return_exceptions=True)


def health():
    with SessionLocal() as db:
        db.execute(text("SELECT 1"))
    return {"status": "ok", "service": "pythontopia-reborn"}


def create_app() -> FastAPI:
    app = FastAPI(title="Pythontopia Reborn", version="0.1.0", lifespan=lifespan)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, exc):
        if request.url.path == "/api/teacher/assistant/model" or request.url.path.startswith("/api/teacher/services/"):
            # Pydantic otherwise echoes the entire rejected input, including API keys.
            return JSONResponse(status_code=422, content={"detail": [{key: error[key] for key in ("loc", "msg", "type")} for error in exc.errors()]})
        return await request_validation_exception_handler(request, exc)

    app.add_middleware(
        CORSMiddleware,
        allow_origins=CORS_ORIGINS,
        allow_credentials=False,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=["Authorization", "Content-Type", "Range"],
    )
    for module in (
        auth,
        courses,
        classrooms,
        course_content,
        world,
        quests,
        practice,
        assistant,
        storage,
        character_assets,
        course_settings,
        curriculum,
        story,
        assessments,
        student_accounts,
        classroom_courses,
        course_exchange,
        world_authoring,
    ):
        app.include_router(module.router, prefix="/api")
    # Narration authoring is additive and isolated; see app/narration.
    app.include_router(narration.router, prefix="/api")
    app.include_router(synthesis_management.router, prefix="/api")
    app.include_router(narration.student_router, prefix="/api")
    app.include_router(dev_tools.preview_router, prefix="/api")
    from app.experience.router import router as experience_router

    app.include_router(experience_router, prefix="/api/teacher-town")
    app.include_router(judge_management.router, prefix="/api")
    app.include_router(content_deletions.router, prefix="/api")
    app.include_router(assistant_settings.router, prefix="/api")
    app.include_router(service_settings.router, prefix="/api")
    if dev_tools_enabled():
        app.include_router(dev_tools.router, prefix="/api")
        app.include_router(experience_router, prefix="/api/dev")
    app.add_api_route("/api/health", health, methods=["GET"])
    return app


app = create_app()
