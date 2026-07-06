from __future__ import annotations

import os

from fastapi import APIRouter, Depends, HTTPException, Request, status

from pythontopia_graph.graph.resource_graph import (
    ResourceGraph,
    ResourceGraphConfig,
    ResourceGraphError,
)
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceFetcher,
    HttpRagEvidenceFetcher,
)
from pythontopia_graph.schemas.resource import (
    ResourceProduceRequest,
    ResourceProduceResponse,
)


router = APIRouter()


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return provider
    return provider_from_env()


def _evidence_fetcher(request: Request) -> EvidenceFetcher:
    fetcher = getattr(request.app.state, "evidence_fetcher", None)
    if fetcher is not None:
        return fetcher
    return HttpRagEvidenceFetcher(os.environ.get("PYT_RAG_URL"))


def _valid_kc_codes(request: Request) -> set[str]:
    codes = getattr(request.app.state, "valid_kc_codes", None)
    if codes is not None:
        return codes
    return set()


@router.post("/agents/resource/produce", response_model=ResourceProduceResponse)
async def produce_resource(
    body: ResourceProduceRequest,
    provider: LlmProvider = Depends(_provider),
    evidence_fetcher: EvidenceFetcher = Depends(_evidence_fetcher),
    valid_kc_codes: set[str] = Depends(_valid_kc_codes),
) -> ResourceProduceResponse:
    graph = ResourceGraph(
        provider=provider,
        evidence_fetcher=evidence_fetcher,
        config=ResourceGraphConfig(valid_kc_codes=valid_kc_codes or set(body.kc_codes)),
    )
    try:
        return await graph.run(body)
    except ResourceGraphError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail={
                "code": "RESOURCE_GRAPH_FAILED",
                "message": str(exc),
                "response": exc.response.model_dump(mode="json"),
            },
        ) from exc
