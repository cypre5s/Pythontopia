from fastapi import FastAPI
from pydantic import BaseModel

from pythontopia_rag.app_state import create_default_state
from pythontopia_rag.routes.query import router as query_router


class HealthResponse(BaseModel):
    status: str


app = FastAPI(title="Pythontopia RAG", version="0.1.0")
app.state.rag = create_default_state()
app.include_router(query_router)


@app.get("/healthz", response_model=HealthResponse)
def healthz() -> HealthResponse:
    return HealthResponse(status="ok")
