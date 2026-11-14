from contextlib import asynccontextmanager
import asyncio
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.responses import JSONResponse
from sqlalchemy import text
from .config import CORS_ORIGINS, dev_tools_enabled
from .db import SessionLocal
from .course_cleanup import cleanup_worker
from . import judge_management, content_deletions, assistant_settings
from . import auth, courses, classrooms, quests, practice, assistant, storage, character_assets, course_settings, curriculum, story, assessments, student_accounts, classroom_courses, course_exchange, world_authoring, dev_tools


@asynccontextmanager
async def lifespan(app):
    from .bootstrap import initialize_admin
    initialize_admin()
    assistant.recover_interrupted_calls()
    tasks = [asyncio.create_task(practice.judge_worker()), asyncio.create_task(storage.material_worker()),
             asyncio.create_task(cleanup_worker())]
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
        if request.url.path == "/api/teacher/assistant/model":
            # Pydantic otherwise echoes the entire rejected input, including API keys.
            return JSONResponse(status_code=422, content={"detail": [{key: error[key] for key in ("loc", "msg", "type")}
                                                                      for error in exc.errors()]})
        return await request_validation_exception_handler(request, exc)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=CORS_ORIGINS,
        allow_credentials=False,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=["Authorization", "Content-Type", "Range"],
    )
    for module in (auth, courses, classrooms, quests, practice, assistant, storage, character_assets, course_settings, curriculum, story, assessments, student_accounts, classroom_courses, course_exchange, world_authoring):
        app.include_router(module.router, prefix="/api")
    app.include_router(dev_tools.preview_router, prefix="/api")
    app.include_router(judge_management.router, prefix="/api")
    app.include_router(content_deletions.router, prefix="/api")
    app.include_router(assistant_settings.router, prefix="/api")
    if dev_tools_enabled():
        app.include_router(dev_tools.router, prefix="/api")
    app.add_api_route("/api/health", health, methods=["GET"])
    return app


app = create_app()
