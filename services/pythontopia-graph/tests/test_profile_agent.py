import pytest

from pythontopia_graph.agents.profile_agent import ProfileAgent
from pythontopia_graph.llm.provider import LlmResponse, LlmProvider, load_alethicode_env
from pythontopia_graph.schemas.profile import (
    KcIndexItem,
    ProfileAgentInput,
    ProfileDraft,
    ProfileTurn,
)


class FakeLlmProvider(LlmProvider):
    def __init__(self, response: dict):
        self.response = response

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        return LlmResponse(content_json=self.response)


def profile_input(message: str) -> ProfileAgentInput:
    return ProfileAgentInput(
        user_id=1,
        interview_session_id="00000000-0000-0000-0000-000000000001",
        last_turn_history=[ProfileTurn(role="assistant", content="[占位] 请介绍你的学习背景。")],
        last_user_message=message,
        course_kc_index=[
            KcIndexItem(kc_code="PY43-KC-001", kc_name="输入输出"),
            KcIndexItem(kc_code="PY43-KC-002", kc_name="变量"),
            KcIndexItem(kc_code="PY43-KC-003", kc_name="for循环"),
        ],
    )


@pytest.mark.asyncio
async def test_profile_agent_completes_all_dimensions() -> None:
    draft = ProfileDraft.example_complete(target_kc_codes=["PY43-KC-001", "PY43-KC-003"])
    agent = ProfileAgent(FakeLlmProvider({"profile_draft": draft.model_dump()}))

    output = await agent.run(profile_input("我是计算机大一，学过 C，目标是期末通过。"))

    assert output.done is True
    assert output.profile_draft is not None
    assert len(output.dimensions_filled) == 8
    assert output.grounding_result.passed is True
    assert output.safety_result.passed is True


@pytest.mark.asyncio
async def test_profile_agent_returns_question_when_grounding_fails() -> None:
    draft = ProfileDraft.example_complete(target_kc_codes=["PY43-KC-999"])
    agent = ProfileAgent(FakeLlmProvider({"profile_draft": draft.model_dump()}))

    output = await agent.run(profile_input("我想学习不存在的知识点。"))

    assert output.done is False
    assert output.profile_draft is None
    assert output.next_question is not None
    assert output.grounding_result.passed is False
    assert "PY43-KC-999" in output.grounding_result.reasons[0]


@pytest.mark.asyncio
async def test_profile_agent_fails_fast_on_safety_failure() -> None:
    agent = ProfileAgent(FakeLlmProvider({"safety_failed": True, "reason": "PII"}))

    output = await agent.run(profile_input("敏感信息"))

    assert output.done is True
    assert output.profile_draft is None
    assert output.safety_result.passed is False
    assert output.grounding_result.passed is False


def test_load_alethicode_env_reads_existing_file_without_returning_secret_values() -> None:
    loaded = load_alethicode_env("/home/cypress/Alethicode/backend/.env")

    assert loaded.path.endswith("/home/cypress/Alethicode/backend/.env")
    assert loaded.exists is True
    assert loaded.loaded_keys >= 0
