"""OrchestratorAgent（学习总管）：多智能体对话入口，不直接生成课件内容。

职责：understand（意图/歧义）、decompose（看→学→练→测计划）、extract_profile（对话即画像）、reply_stream（Markdown 回复）。
JSON 方法经 content_json→围栏文本回落解析；provider 不可用时确定性结构化兜底，不退化为自由对话。
"""

from __future__ import annotations

import json
import re
from collections.abc import AsyncIterator
from typing import Any, TypedDict

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.orchestrator import (
    OrchestratorRequest,
    Phase,
    PlanStep,
    ProfileSignal,
)

AGENT_NAME = "OrchestratorAgent"

# 对最终回复做泄答关键词扫描（与 TutorAgent 同词表）。
SAFETY_KEYWORDS = ("answer_key", "hidden_test", "完整答案", "直接给我代码")

# 后端 learner_profile.dimensions 的 8 个 JSONB 维度 key。
PROFILE_DIMENSION_KEYS = (
    "professional_background",
    "knowledge_base",
    "learning_goal",
    "cognitive_style",
    "learning_preference",
    "error_pattern",
    "practical_ability",
    "emotion_persistence",
)

# 容忍 LLM 返回的 ProfileDraft 风格维度别名。
_DIMENSION_ALIASES = {
    "major": "professional_background",
    "base_level": "knowledge_base",
    "error_patterns": "error_pattern",
    "practice_capability": "practical_ability",
    "emotion": "emotion_persistence",
    "preference": "learning_preference",
    "goal": "learning_goal",
}

PHASE_ORDER: tuple[Phase, ...] = ("watch", "learn", "practice", "assess")

# 各阶段映射到专职 Agent 与前端面板深链。
_PHASE_AGENT: dict[Phase, str] = {
    "watch": "StoryboardAgent",
    "learn": "ResourceDesignerAgent",
    "practice": "PracticeAgent",
    "assess": "PathPlannerAgent",
}
_PHASE_PANEL: dict[Phase, str] = {
    "watch": "lecture",
    "learn": "library",
    "practice": "training",
    "assess": "problems",
}
_PHASE_LABEL: dict[Phase, str] = {
    "watch": "看",
    "learn": "学",
    "practice": "练",
    "assess": "测",
}
_DEFAULT_TITLE: dict[Phase, str] = {
    "watch": "看视频：B站精选讲解 + 微课",
    "learn": "学讲义：吃透核心概念",
    "practice": "练一练：巩固本节技能",
    "assess": "测一测：检验掌握程度",
}

RESOURCE_TYPES = (
    "HANDOUT",
    "MINDMAP",
    "PRACTICE",
    "CODE_LAB",
    "READING",
    "STORYBOARD",
)

# provider 不可用时的确定性关键词兜底集合。
_NEGOTIATE_KEYWORDS = (
    "太难",
    "好难",
    "难了",
    "听不懂",
    "不会",
    "太复杂",
    "太简单",
    "太容易",
    "会了",
    "无聊",
    "换个例子",
    "换一个",
    "换种",
    "另一个例子",
    "跳过",
    "下一个",
    "skip",
)
_AMBIGUOUS_MESSAGES = (
    "不知道",
    "随便",
    "你决定",
    "都行",
    "帮我",
    "help",
    "?",
    "？",
)


class Understanding(TypedDict):
    intent: str
    ambiguous: bool
    clarifying_question: str | None
    goal: str
    focus: str


def _strip_json_fence(text: str) -> str:
    """剥离可选的 ```json … ``` 围栏后再 json.loads。"""

    fence = re.match(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", text, re.DOTALL)
    if fence:
        return fence.group(1)
    return text


class OrchestratorAgent:
    """学习总管：意图理解、路径拆解、画像抽取与流式回复；JSON 输出始终结构化。"""

    AGENT_NAME = AGENT_NAME

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    # ── intent & ambiguity ────────────────────────────────────────────────
    async def understand(self, req: OrchestratorRequest) -> Understanding:
        messages = self._understand_messages(req)
        data = await self._chat_json(messages)

        intent = str(data.get("intent") or "plan").strip().lower()
        goal = str(data.get("goal") or req.user_message).strip()
        focus = str(data.get("focus") or "").strip()
        ambiguous = bool(data.get("ambiguous", False))
        clarifying_raw = data.get("clarifying_question")
        clarifying = str(clarifying_raw).strip() if isinstance(clarifying_raw, str) else None

        # 关键词协商检测优先于可能过时/离线的 provider 判定。
        if self._looks_like_negotiation(req.user_message):
            intent = "negotiate"
            ambiguous = False
            clarifying = None
        elif not data and self._looks_ambiguous(req.user_message):
            ambiguous = True

        if ambiguous and not clarifying:
            clarifying = (
                "你想先从哪个主题入手？比如输入输出、循环还是函数？"
                "也告诉我你的目标（期末备考 / 做项目 / 纯兴趣）。"
            )

        return Understanding(
            intent=intent,
            ambiguous=ambiguous,
            clarifying_question=clarifying if ambiguous else None,
            goal=goal,
            focus=focus,
        )

    # ── plan decomposition ────────────────────────────────────────────────
    async def decompose(self, req: OrchestratorRequest, goal: str) -> list[PlanStep]:
        allowed_set = set(req.course_kc_index)
        default_kcs = self._select_kcs(req)
        messages = self._decompose_messages(req, goal, default_kcs)
        data = await self._chat_json(messages)
        by_phase = self._index_llm_steps(data.get("steps"))

        plan: list[PlanStep] = []
        for index, phase in enumerate(PHASE_ORDER):
            raw = by_phase.get(phase, {})
            title = self._clean_text(raw.get("title"), self._DEFAULT_TITLE_FOR(phase))
            step_kcs = self._ground_kcs(raw.get("kc_codes"), allowed_set) or default_kcs
            detail = self._clean_text(
                raw.get("detail"), self._default_detail(phase, goal, step_kcs)
            )
            resource_type = (
                self._pick_resource_type(raw.get("resource_type"), req)
                if phase == "learn"
                else None
            )
            plan.append(
                PlanStep(
                    id=f"s{index + 1}-{phase}",
                    phase=phase,
                    title=title,
                    detail=detail,
                    agent=_PHASE_AGENT[phase],
                    kc_codes=step_kcs,
                    panel=_PHASE_PANEL[phase],
                    status="pending",
                    resource_type=resource_type,
                )
            )
        return plan

    # ── profile extraction（对话即画像）────────────────────────────────────
    async def extract_profile(self, req: OrchestratorRequest) -> list[ProfileSignal]:
        messages = self._profile_messages(req)
        data = await self._chat_json(messages)
        raw_signals = data.get("profile_signals")
        if not isinstance(raw_signals, list):
            raw_signals = data.get("signals")

        signals: list[ProfileSignal] = []
        seen: set[str] = set()
        if isinstance(raw_signals, list):
            for item in raw_signals:
                signal = self._coerce_signal(item)
                if signal is not None and signal.dimension not in seen:
                    signals.append(signal)
                    seen.add(signal.dimension)

        if not signals:
            signals = self._heuristic_profile(req)
        return signals

    # ── natural-language reply (streamed) ─────────────────────────────────
    async def reply_stream(
        self,
        req: OrchestratorRequest,
        plan: list[PlanStep],
        clarifying: str | None,
    ) -> AsyncIterator[str]:
        messages = self._reply_messages(req, plan, clarifying)
        async for token in self.provider.chat_stream(messages):
            yield token

    @staticmethod
    def scan_safety(reply_md: str) -> tuple[bool, list[str]]:
        reasons = [f"SAFETY_KEYWORD:{kw}" for kw in SAFETY_KEYWORDS if kw in reply_md]
        return (not reasons, reasons)

    # ── provider plumbing ─────────────────────────────────────────────────
    async def _chat_json(self, messages: list[dict[str, str]]) -> dict[str, Any]:
        try:
            response = await self.provider.chat(
                messages, response_format={"type": "json_object"}, temperature=0.3
            )
        except TypeError:
            try:
                response = await self.provider.chat(messages, temperature=0.3)
            except TypeError:
                response = await self.provider.chat(messages)
        return self._coerce_json(response)

    @staticmethod
    def _coerce_json(response: LlmResponse) -> dict[str, Any]:
        if response.content_json is not None:
            return response.content_json
        if response.content_text:
            try:
                parsed = json.loads(_strip_json_fence(response.content_text))
            except json.JSONDecodeError:
                return {}
            if isinstance(parsed, dict):
                return parsed
        return {}

    # ── prompt builders ───────────────────────────────────────────────────
    def _understand_messages(self, req: OrchestratorRequest) -> list[dict[str, str]]:
        messages: list[dict[str, str]] = [
            {
                "role": "system",
                "content": (
                    "你是 Pythontopia「学习总管」的意图理解模块，只输出 JSON 对象。"
                    "判断学生最新消息的意图并概括目标，schema:"
                    '{"intent":"plan|negotiate|continue|chitchat",'
                    '"ambiguous":true/false,"clarifying_question":"…或null",'
                    '"goal":"一句话目标","focus":"主题关键词"}。'
                    "negotiate=对已有计划提太难/太简单/换例子/跳过等调整。"
                    "ambiguous=目标模糊到无法规划。不要编造课程内容。"
                ),
            }
        ]
        if req.profile_brief:
            messages.append({"role": "system", "content": f"学生画像: {req.profile_brief}"})
        messages.extend(self._history_messages(req))
        messages.append({"role": "user", "content": req.user_message})
        return messages

    def _decompose_messages(
        self, req: OrchestratorRequest, goal: str, default_kcs: list[str]
    ) -> list[dict[str, str]]:
        allowed = ", ".join(req.course_kc_index[:24]) or "（空，请勿编造 KC）"
        messages: list[dict[str, str]] = [
            {
                "role": "system",
                "content": (
                    "你是 Pythontopia「学习总管」的路径拆解模块，只输出 JSON 对象。"
                    "把目标拆成严格有序的「看→学→练→测」四阶段，每阶段一步，schema:"
                    '{"steps":[{"phase":"watch|learn|practice|assess",'
                    '"title":"<=20字","detail":"<=80字","kc_codes":["…"],'
                    '"resource_type":"HANDOUT|MINDMAP|PRACTICE|CODE_LAB|READING|STORYBOARD"}]}。'
                    "kc_codes 只能取自允许列表，列表为空则留空数组；"
                    "resource_type 仅 learn 阶段需要，按画像偏好选择；"
                    "watch（看）阶段优先推荐 B站精选讲解视频/微课，帮助学生建立直观印象。"
                ),
            },
            {"role": "system", "content": f"允许 KC 列表: {allowed}"},
        ]
        if req.profile_brief:
            messages.append({"role": "system", "content": f"学生画像: {req.profile_brief}"})
        if default_kcs:
            messages.append(
                {"role": "system", "content": f"建议优先覆盖的 KC: {', '.join(default_kcs)}"}
            )
        messages.append({"role": "user", "content": f"学习目标：{goal}"})
        return messages

    def _profile_messages(self, req: OrchestratorRequest) -> list[dict[str, str]]:
        keys = ", ".join(PROFILE_DIMENSION_KEYS)
        messages: list[dict[str, str]] = [
            {
                "role": "system",
                "content": (
                    "你是 Pythontopia「学习总管」的画像抽取模块（对话即画像），只输出 JSON。"
                    f"维度 key 只能用：{keys}。schema:"
                    '{"profile_signals":[{"dimension":"…","value":"中文短语",'
                    '"confidence":0~1}]}。'
                    "只抽取消息中可观测的信号，无法抽取就返回空数组，禁止编造。"
                ),
            }
        ]
        messages.extend(self._history_messages(req))
        messages.append({"role": "user", "content": req.user_message})
        return messages

    def _reply_messages(
        self,
        req: OrchestratorRequest,
        plan: list[PlanStep],
        clarifying: str | None,
    ) -> list[dict[str, str]]:
        messages: list[dict[str, str]] = [
            {
                "role": "system",
                "content": (
                    "你是 Pythontopia 的「学习总管」，统筹学生的看/学/练/测节奏。"
                    "规则：1) 用中文 Markdown，语气像耐心的班主任；"
                    "2) 只做规划与引导，绝不给出练习题的完整答案代码；"
                    "3) 这是规划对话，没有课件证据时不要编造页码；"
                    "4) 根据画像调整难度与措辞。"
                ),
            }
        ]
        if req.profile_brief:
            messages.append({"role": "system", "content": f"学生画像: {req.profile_brief}"})
        if clarifying:
            messages.append(
                {
                    "role": "system",
                    "content": (
                        "学生目标尚不明确，请用一两句话友好地把下面的澄清问题问出来，"
                        f"不要罗列计划：{clarifying}"
                    ),
                }
            )
        elif plan:
            messages.append(
                {
                    "role": "system",
                    "content": (
                        "本轮已为学生规划如下步骤，请用自然语言介绍这个安排并鼓励 ta "
                        "从第一步开始：\n" + self._plan_outline(plan)
                    ),
                }
            )
        messages.extend(self._history_messages(req))
        messages.append({"role": "user", "content": req.user_message})
        return messages

    @staticmethod
    def _history_messages(req: OrchestratorRequest) -> list[dict[str, str]]:
        return [{"role": turn.role, "content": turn.content} for turn in req.history[-12:]]

    @staticmethod
    def _plan_outline(plan: list[PlanStep]) -> str:
        lines: list[str] = []
        for index, step in enumerate(plan):
            label = _PHASE_LABEL.get(step.phase, step.phase)
            lines.append(f"{index + 1}. [{label}] {step.title} —— {step.detail}")
        return "\n".join(lines)

    # ── deterministic helpers ─────────────────────────────────────────────
    @staticmethod
    def _looks_like_negotiation(message: str) -> bool:
        lowered = message.lower()
        return any(keyword in lowered for keyword in _NEGOTIATE_KEYWORDS)

    @staticmethod
    def _looks_ambiguous(message: str) -> bool:
        stripped = message.strip()
        if len(stripped) <= 3:
            return True
        return any(token in stripped.lower() for token in _AMBIGUOUS_MESSAGES)

    def _select_kcs(self, req: OrchestratorRequest) -> list[str]:
        allowed = list(dict.fromkeys(req.course_kc_index))
        if not allowed:
            return []
        allowed_set = set(allowed)
        chosen: list[str] = []
        # 优先级：记忆矿脉到期的 decaying_kcs（最该复习）> 最近失败 > 最近接触。
        for code in [*req.decaying_kcs, *req.recent_failures, *req.recent_kc_codes]:
            if code in allowed_set and code not in chosen:
                chosen.append(code)
        if not chosen:
            chosen = allowed[:2]
        return chosen[:3]

    @staticmethod
    def _ground_kcs(codes: Any, allowed_set: set[str]) -> list[str]:
        if not allowed_set or not isinstance(codes, list):
            return []
        grounded: list[str] = []
        for code in codes:
            text = str(code)
            if text in allowed_set and text not in grounded:
                grounded.append(text)
        return grounded

    @staticmethod
    def _index_llm_steps(raw_steps: Any) -> dict[str, dict[str, Any]]:
        by_phase: dict[str, dict[str, Any]] = {}
        if not isinstance(raw_steps, list):
            return by_phase
        for item in raw_steps:
            if not isinstance(item, dict):
                continue
            phase = str(item.get("phase", "")).strip().lower()
            if phase in PHASE_ORDER and phase not in by_phase:
                by_phase[phase] = item
        return by_phase

    @staticmethod
    def _clean_text(value: Any, fallback: str) -> str:
        if isinstance(value, str) and value.strip():
            return value.strip()
        return fallback

    @staticmethod
    def _DEFAULT_TITLE_FOR(phase: Phase) -> str:
        return _DEFAULT_TITLE[phase]

    @staticmethod
    def _default_detail(phase: Phase, goal: str, kcs: list[str]) -> str:
        kc_part = ("，覆盖 " + "、".join(kcs)) if kcs else ""
        templates: dict[Phase, str] = {
            "watch": f"围绕「{goal}」观看 B站精选讲解视频与微课{kc_part}，先建立直观印象。",
            "learn": f"研读讲义或思维导图{kc_part}，理解关键概念与示例。",
            "practice": f"完成配套练习{kc_part}，把概念真正用起来。",
            "assess": f"正式提交评估{kc_part}，确认是否达标。",
        }
        return templates[phase]

    @staticmethod
    def _pick_resource_type(raw: Any, req: OrchestratorRequest) -> str:
        if isinstance(raw, str) and raw.strip().upper() in RESOURCE_TYPES:
            return raw.strip().upper()
        brief = (req.profile_brief or "").lower()
        if any(token in brief for token in ("视频", "video", "动画")):
            return "STORYBOARD"
        if any(token in brief for token in ("导图", "mindmap")):
            return "MINDMAP"
        if any(token in brief for token in ("代码", "code", "实操", "动手")):
            return "CODE_LAB"
        if any(token in brief for token in ("阅读", "reading", "文字")):
            return "READING"
        return "HANDOUT"

    @staticmethod
    def _coerce_signal(item: Any) -> ProfileSignal | None:
        if not isinstance(item, dict):
            return None
        dimension = str(item.get("dimension", "")).strip()
        dimension = _DIMENSION_ALIASES.get(dimension, dimension)
        if dimension not in PROFILE_DIMENSION_KEYS:
            return None
        value = str(item.get("value", "")).strip()
        if not value:
            return None
        try:
            confidence = float(item.get("confidence", 0.5))
        except (TypeError, ValueError):
            confidence = 0.5
        confidence = max(0.0, min(1.0, confidence))
        return ProfileSignal(dimension=dimension, value=value, confidence=confidence)

    @staticmethod
    def _heuristic_profile(req: OrchestratorRequest) -> list[ProfileSignal]:
        message = req.user_message
        signals: list[ProfileSignal] = []
        if any(token in message for token in ("太难", "听不懂", "不会", "挫败", "放弃")):
            signals.append(
                ProfileSignal(
                    dimension="emotion_persistence",
                    value="近期遇到挫折，需要鼓励与降低难度",
                    confidence=0.4,
                )
            )
        if any(token in message for token in ("期末", "考试", "考研", "项目", "工作", "兴趣")):
            signals.append(
                ProfileSignal(
                    dimension="learning_goal",
                    value=f"目标线索：{message[:40]}",
                    confidence=0.5,
                )
            )
        if any(token in message for token in ("视频", "代码", "导图", "文字", "阅读")):
            signals.append(
                ProfileSignal(
                    dimension="learning_preference",
                    value=f"偏好线索：{message[:40]}",
                    confidence=0.4,
                )
            )
        return signals
