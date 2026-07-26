from __future__ import annotations

import json
from typing import Any

from pydantic import ValidationError

from pythontopia_graph.agents.personalization import (
    agent_strategy_messages,
    personalization_messages,
)
from pythontopia_graph.contracts.semantic_ir import (
    ContractCompiler,
    EvidenceBinder,
    EvidenceContext,
    StoryboardSemanticIR,
)
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    ResourceDraft,
    ResourceProduceRequest,
    StoryboardContent,
    StoryboardScene,
)


BANNED_VISUAL_HINTS = ("真实人物", "真人头像", "知名品牌")


class StoryboardAgent:
    """Compile teaching-scene semantics into safe, evidence-bound product scenes."""

    AGENT_NAME = "StoryboardAgent"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceBundle,
        revision_notes: list[str] | None = None,
    ) -> ResourceDraft:
        evidence_context = EvidenceBinder.bind(evidence)
        messages = self._prompt(request, evidence_context, revision_notes)
        response = await self.provider.chat(
            messages,
            guard_context={
                "agent": self.AGENT_NAME,
                "json_schema": StoryboardSemanticIR.model_json_schema(),
                "evidence_ids": evidence_context.allowed_ids,
                "evidence_fields": ["scenes.*.evidence_refs"],
            },
        )
        return self._build(request, response.content_json, evidence_context)

    def _prompt(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceContext | EvidenceBundle,
        revision_notes: list[str] | None = None,
    ) -> list[dict[str, str]]:
        evidence_context = (
            evidence
            if isinstance(evidence, EvidenceContext)
            else EvidenceBinder.bind(evidence)
        )
        return [
            {
                "role": "system",
                "content": (
                    "你是 StoryboardAgent，只输出符合 StoryboardSemanticIR 的 JSON，含 3-5 个 scenes。"
                    "每个 scene 只做教学语义决策：title、narration、visual_intent、"
                    "pacing(brief|normal|deep)、evidence_refs。"
                    "不要生成 duration_seconds、evidence_page_id、kc_codes、voice、image_prompt 等产品字段。"
                    "visual_intent 禁止真实人物、品牌、二维码；"
                    "evidence_refs 最多一个且只能引用 allowed_evidence 中的 evidence_id。"
                ),
            },
            *([{
                "role": "system",
                "content": "上一稿被打回原因：\n" + "\n".join(f"- {r}" for r in revision_notes)
                + "\n请针对性修订，修正上述问题后重新生成。",
            }] if revision_notes else []),
            *personalization_messages(request.profile_snapshot),
            *agent_strategy_messages(request.agent_strategy_directive),
            {
                "role": "user",
                "content": (
                    f"目标 KC: {','.join(request.kc_codes)}; allowed_evidence: "
                    + json.dumps(
                        evidence_context.model_dump(mode="json"), ensure_ascii=False
                    )
                ),
            },
        ]

    def _build(
        self,
        request: ResourceProduceRequest,
        content_json: dict[str, Any] | None,
        evidence: EvidenceContext,
    ) -> ResourceDraft:
        content = self._build_content(request, content_json, evidence)
        if content.scenes:
            for scene in content.scenes:
                # image_prompt 缺省时由 visual_hint 派生，保证每个分镜都能生成插画/合成视频。
                if not scene.image_prompt.strip():
                    scene.image_prompt = self._derive_image_prompt(scene)
        return ResourceDraft(
            title=f"动画脚本：{','.join(request.kc_codes[:3])}",
            summary="结构化分镜 + 文生图插画提示，可合成短视频讲解",
            content_format="STRUCT_JSON",
            storyboard=content,
        )

    @staticmethod
    def _derive_image_prompt(scene: StoryboardScene) -> str:
        """把 visual_hint 包装成像素插画风的文生图提示词。"""
        hint = scene.visual_hint.strip() or scene.title.strip()
        return f"像素教学插画，{hint}，暖色调，无真实人物，无文字水印"

    def _build_content(
        self,
        request: ResourceProduceRequest,
        content_json: dict[str, Any] | None,
        evidence: EvidenceContext,
    ) -> StoryboardContent:
        if isinstance(content_json, dict):
            try:
                semantic_ir = StoryboardSemanticIR.model_validate(content_json)
                return ContractCompiler.compile_storyboard(
                    semantic_ir,
                    evidence=evidence,
                    kc_codes=request.kc_codes,
                )
            except (ValidationError, ValueError):
                pass
        return self._fallback(request, evidence)

    def _clean(self, content: StoryboardContent) -> bool:
        for scene in content.scenes:
            for banned in BANNED_VISUAL_HINTS:
                if banned in scene.visual_hint:
                    return False
        return True

    def _fallback(
        self, request: ResourceProduceRequest, evidence: EvidenceContext
    ) -> StoryboardContent:
        kc = request.kc_codes[0]
        evidence_page_id = evidence.items[0].page_id if evidence.items else None
        scenes = [
            StoryboardScene(
                title=f"序幕：{kc} 是什么",
                narration=f"用一个比喻说明 {kc}。",
                visual_hint="像素小镇外景，主角面向告示牌。",
                kc_codes=[kc],
                duration_seconds=20,
                evidence_page_id=evidence_page_id,
            ),
            StoryboardScene(
                title=f"主线：{kc} 怎么用",
                narration=f"展示 {kc} 的最小示例并解释。",
                visual_hint="特写代码编辑器，旁边出现像素 NPC 讲解。",
                kc_codes=[kc],
                duration_seconds=45,
                evidence_page_id=evidence_page_id,
            ),
            StoryboardScene(
                title=f"收束：{kc} 易错点",
                narration="列出 2 个常见错误并给出修复方法。",
                visual_hint="像素黑板，列出错误清单。",
                kc_codes=[kc],
                duration_seconds=25,
                evidence_page_id=evidence_page_id,
            ),
        ]
        return StoryboardContent(scenes=scenes)
