from __future__ import annotations

from pythontopia_graph.graph.profile_graph import run_profile_graph
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.schemas.profile import ProfileAgentInput, ProfileAgentOutput


class ProfileAgent:
    """画像访谈 Agent 门面；委托 run_profile_graph 完成结构化画像抽取。"""

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def run(self, input_data: ProfileAgentInput) -> ProfileAgentOutput:
        return await run_profile_graph(self.provider, input_data)
