from fastapi import FastAPI
from pydantic import BaseModel

from pythontopia_graph.agents.curriculum_mapper_agent import CurriculumMapperAgent
from pythontopia_graph.agents.profile_agent import ProfileAgent
from pythontopia_graph.llm.provider import provider_from_env
from pythontopia_graph.routes.debug import router as debug_router
from pythontopia_graph.routes.path import router as path_router
from pythontopia_graph.routes.evaluation import router as evaluation_router
from pythontopia_graph.routes.resource import router as resource_router
from pythontopia_graph.schemas.curriculum import MapperInput, MapperOutput
from pythontopia_graph.schemas.profile import ProfileAgentInput, ProfileAgentOutput


class HealthResponse(BaseModel):
    status: str


app = FastAPI(title="Pythontopia Graph", version="0.1.0")
app.include_router(resource_router)
app.include_router(path_router)
app.include_router(debug_router)
app.include_router(evaluation_router)


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
