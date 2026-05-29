from __future__ import annotations

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
    AGENT_NAME = "StoryboardAgent"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
    ) -> ResourceDraft:
        messages = self._prompt(request, evidence)
        response = await self.provider.chat(messages)
        return self._build(request, response.content_json, evidence)

    def _prompt(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle,
    ) -> list[dict[str, str]]:
        evidence_hint = ""
        if evidence.chunks:
            refs = ", ".join(
                f"page_id={c.page_id} (页 {c.page_no})" for c in evidence.chunks[:5]
            )
            evidence_hint = f"\n可用课件证据页: {refs}。请在分镜中标注 evidence_page_id。"
        return [
            {
                "role": "system",
                "content": (
                    "你是 StoryboardAgent，输出 JSON，3-5 个分镜。"
                    "每个分镜 duration_seconds ≤ 90，visual_hint 不得包含真实人物姓名或知名品牌。"
                    "每个分镜可附带 evidence_page_id（int|null）和 narration_tts_voice（默认 x4_yezi）。"
                    + evidence_hint
                ),
            },
            {"role": "user", "content": ",".join(request.kc_codes)},
        ]

    def _build(
        self, request: ResourceProduceRequest, content_json: dict | None,
        evidence: EvidenceBundle | None = None,
    ) -> ResourceDraft:
        content = self._build_content(request, content_json)
        if evidence and evidence.chunks and content.scenes:
            for scene in content.scenes:
                if scene.evidence_page_id is None and evidence.chunks:
                    scene.evidence_page_id = evidence.chunks[0].page_id
        return ResourceDraft(
            title=f"动画脚本：{','.join(request.kc_codes[:3])}",
            summary="不渲染视频；输出结构化分镜",
            content_format="STRUCT_JSON",
            storyboard=content,
        )

    def _build_content(
        self, request: ResourceProduceRequest, content_json: dict | None
    ) -> StoryboardContent:
        if isinstance(content_json, dict) and "storyboard" in content_json:
            try:
                candidate = StoryboardContent.model_validate(content_json["storyboard"])
                if self._clean(candidate):
                    return candidate
            except Exception:  # noqa: BLE001
                pass
        return self._fallback(request)

    def _clean(self, content: StoryboardContent) -> bool:
        for scene in content.scenes:
            for banned in BANNED_VISUAL_HINTS:
                if banned in scene.visual_hint:
                    return False
        return True

    def _fallback(self, request: ResourceProduceRequest) -> StoryboardContent:
        kc = request.kc_codes[0]
        scenes = [
            StoryboardScene(
                title=f"序幕：{kc} 是什么",
                narration=f"用一个比喻说明 {kc}。",
                visual_hint="像素小镇外景，主角面向告示牌。",
                kc_codes=[kc],
                duration_seconds=20,
            ),
            StoryboardScene(
                title=f"主线：{kc} 怎么用",
                narration=f"展示 {kc} 的最小示例并解释。",
                visual_hint="特写代码编辑器，旁边出现像素 NPC 讲解。",
                kc_codes=[kc],
                duration_seconds=45,
            ),
            StoryboardScene(
                title=f"收束：{kc} 易错点",
                narration="列出 2 个常见错误并给出修复方法。",
                visual_hint="像素黑板，列出错误清单。",
                kc_codes=[kc],
                duration_seconds=25,
            ),
        ]
        return StoryboardContent(scenes=scenes)
