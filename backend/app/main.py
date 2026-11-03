from contextlib import asynccontextmanager
import asyncio
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
from .config import CORS_ORIGINS
from .db import SessionLocal
from . import auth, courses, classrooms, quests, practice, assistant, storage, dialogues


@asynccontextmanager
async def lifespan(app):
    assistant.recover_interrupted_calls()
    tasks = [asyncio.create_task(practice.judge_worker()), asyncio.create_task(storage.material_worker())]
    yield
    for task in tasks:
        task.cancel()
    await asyncio.gather(*tasks, return_exceptions=True)


app = FastAPI(title="Pythontopia Reborn", version="0.1.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET", "POST", "PUT", "DELETE"],
    allow_headers=["Authorization", "Content-Type", "Range"],
)
for module in (auth, courses, classrooms, quests, practice, assistant, storage, dialogues):
    app.include_router(module.router, prefix="/api")


@app.get("/api/health")
def health():
    with SessionLocal() as db:
        db.execute(text("SELECT 1"))
    return {"status": "ok", "service": "pythontopia-reborn"}
