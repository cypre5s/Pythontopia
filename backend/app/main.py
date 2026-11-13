from contextlib import asynccontextmanager
import asyncio
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
from .config import CORS_ORIGINS, dev_tools_enabled
from .db import SessionLocal
from .course_cleanup import cleanup_worker
from . import auth, courses, classrooms, quests, practice, assistant, storage, character_assets, curriculum, story, assessments, student_accounts, classroom_courses, course_exchange, world_authoring, dev_tools


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
    app.add_middleware(
        CORSMiddleware,
        allow_origins=CORS_ORIGINS,
        allow_credentials=False,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=["Authorization", "Content-Type", "Range"],
    )
    for module in (auth, courses, classrooms, quests, practice, assistant, storage, character_assets, curriculum, story, assessments, student_accounts, classroom_courses, course_exchange, world_authoring):
        app.include_router(module.router, prefix="/api")
    app.include_router(dev_tools.preview_router, prefix="/api")
    if dev_tools_enabled():
        app.include_router(dev_tools.router, prefix="/api")
    app.add_api_route("/api/health", health, methods=["GET"])
    return app


app = create_app()
