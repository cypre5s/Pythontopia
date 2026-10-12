"""导学工作流卡片生成 agent：6 阶段卡片 prompt + LLM 结构化产出 + 防泄答校验。

prompt 移植自 Alethicode tutor-graph 各阶段节点（reading/ideating/skeleton/diagnosis/
ac_review/chat），保留全部防泄答硬约束；按 Pythontopia 适配：
- mentor 三角色（Nene/Yoshino/Kanna）收敛为「百晓」单角色（本仓无该角色 IP）；
- 课件证据走本仓 EvidenceBundle（RAG chunks），不再回调 Java 内部工具 API；
- 迁移题不依赖 Java 出题主链路：LLM 直接生成展示型变式题卡（无判例/参考解）。

输出经 pydantic 卡片模型强校验（等价源版 jsonschema），失败重试一次后仍违规 →
抛 CardGenerationError(SCHEMA_VIOLATION)，fail fast 不打补丁。
"""

from __future__ import annotations

import json
import re
from typing import Any

from pydantic import BaseModel, ValidationError

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.nodes.evidence_fetcher import RagHit
from pythontopia_graph.schemas.tutor_workflow import (
    CARD_KEY_TO_MODEL,
    WorkflowDiagnosisContext,
    WorkflowProblemContext,
)

AGENT_NAME = "TutorWorkflowAgent"

_JSON_FENCE = re.compile(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", re.DOTALL)


class CardGenerationError(RuntimeError):
    """卡片生成失败（bucket ∈ SCHEMA_VIOLATION / SYSTEM_ERROR）。"""

    def __init__(self, bucket: str, message: str) -> None:
        super().__init__(message)
        self.bucket = bucket


# ── 各阶段 system prompt（移植 Alethicode，防泄答硬约束逐条保留） ──

PROBLEM_GUIDE_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
用户正在审读一道编程题。请用中文生成题目导读。
要求：
- 用通俗语言重述题意
- 指出输入输出的关键约束
- 给出 1 个「观察方向」，只指出学生接下来该关注什么关系或约束，不直接给出完整变换规则、公式、边界处理或答案
- 给出 2-3 个引导性问题帮助学生思考，其中至少 1 个必须是自我检查问题
- 不给完整代码
- related_kcs 只能从【候选知识点】中选择，禁止编造课程外知识点
- 输出 JSON 对象，字段: problem_restatement, input_output_focus, key_observation,
  starter_questions(数组), related_kcs(数组), teaching_goal, checkpoint_prompt, reflection_prompt"""

IDEATE_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
学生正在思考解题思路。
要求：
- 只拆解步骤，不给完整答案
- 若学生思路明显错误，指出第一个关键偏差（写进 misconception_alert）
- 若学生没有输入思路，给 2-3 个引导问题
- 必须追问「为什么这样想」，帮助学生把思路说完整
- 输出 JSON 对象，字段: analysis, steps(数组), guiding_questions(数组),
  misconception_alert(可空字符串), teaching_goal, checkpoint_prompt, reflection_prompt"""

SKELETON_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
学生请求生成代码骨架。
要求：
- 只生成可练习的代码骨架，不给完整答案
- 至少保留两个 TODO 注释
- 输入输出结构要完整，核心逻辑必须留空
- 注释默认使用简体中文，标识符使用清晰英文
- 必须严格围绕【当前题目】生成，禁止借用历史示例、课件样例或其他题目的数据集
- 禁止引入题面未要求的第三方库、机器学习模型、示例数据集或额外业务背景
- Python 骨架风格约束（强制遵守）：
  - 禁止使用 if __name__ == "__main__": 包裹主逻辑，直接在顶层写输入输出代码
  - 禁止使用 try-except 包裹输入读取，直接写 input() / int(input()) 等
  - 保持代码尽可能扁平、直白，适合零基础学生阅读
- 输出 JSON 对象，字段: description, skeleton, teaching_goal, checkpoint_prompt, reflection_prompt"""

ERROR_DIAGNOSIS_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
学生提交了错误的代码。请分析错误原因。
要求：
- 不给完整可提交代码、最终标准答案或可直接提交的关键表达式；
- 必须结合题目正文、输入/输出说明、判题结果与错误信息，给出「程序现在在做什么 vs 题目期望它做什么」的对照；
- 必须先依据题目文本、输入说明、输出说明判断期望行为；若题面指定常量、精度或输出格式，以题面为准；
- 不要建议与题面冲突的替代实现（例如题面明确要求 3.1415 时，不要建议改用 math.pi）；
- 不允许只给泛化模板句（例如「检查循环边界」），必须结合本题的具体输入输出展开；
- 若判题未返回失败样例详情，必须在 root_cause 或 what_program_is_doing 中显式写出「判题未返回失败样例详情」，不要凭空捏造样例；
- related_kcs 必须从【候选知识点】中选择最贴合的 1~3 个，禁止编造课程外知识点；
- encouragement 按提供的挫败度提示措辞；
- 输出 JSON 对象，字段：root_cause, what_program_is_doing, expected_behavior, fix_direction,
  related_kcs(数组), error_pattern, is_recurring(布尔), encouragement,
  teaching_goal, checkpoint_prompt, reflection_prompt"""

POST_AC_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
学生已 AC 本题。请基于源题完整上下文做一次贴题的复盘。
硬约束：
- 必须先总结这一次「为什么做对了」，引用题面具体要求（输入/输出/边界）；
- 必须给一个最小、可立刻执行的改进点（key_action），不要泛化建议；
- 必须输出 next_practice_direction：明确指向下一次练习要练的知识点 / 技能变体，禁止与本题完全等价；
- 必须输出至少一个反思句（reflection_prompt），帮助学生下次更早发现同类问题；
- 不允许输出完整可提交代码或可直接复制的标准答案；
- 不允许跳过 next_practice_direction 或留为空字符串；
- 输出 JSON 对象，字段：success_summary, key_action, code_quality_notes(数组),
  knowledge_points(数组), next_practice_direction, teaching_goal, checkpoint_prompt, reflection_prompt"""

TRANSFER_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
请根据源题与复盘给出的「下一步练习方向」，出一道贴合的迁移变式题。
硬约束：
- 必须围绕 next_practice_direction 指向的知识点 / 技能变体出题，禁止与源题完全等价；
- 难度与源题相当或略升半档，适合初学者独立完成；
- 题面自洽完整：描述、输入说明、输出说明、至少 1 组输入输出样例、1 条不剧透的提示；
- samples 数组中每项是 {"input": "...", "output": "..."}，输入输出必须与题面一致且正确；
- 不输出参考解、不输出测试用例、不在 hint 里给出解法步骤；
- target_kcs 只能从【候选知识点】中选择；
- 输出 JSON 对象，字段：title, description, input_description, output_description,
  samples(数组), hint, target_kcs(数组)"""

CHAT_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
在当前题目和当前学习阶段进行导学对话。
要求：
- 不给完整代码
- 不退化为普通闲聊
- 引用当前题、当前阶段、最近的导学卡片内容作答
- referenced_card_ids 数组只包含你真实引用过的卡片 card_id，没有引用就给空数组
- 不要凭空编造没看到的卡片或课件内容
- 输出 JSON 对象，字段: content, referenced_card_ids(数组)"""

KNOWLEDGE_REVIEW_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
请基于当前题目相关的知识点做一次简短的知识点复盘（不进入答疑/引用协议，不回答与本题无关的泛问题）。
要求：
- review_content 用通俗语言回顾该知识点的核心概念与它在本题中的用法，并点出 1 个常见误区；
- review_content 必须自然引用所给【课件参考】里的页码（例：「可回顾 第38页」）；若未提供课件参考，则不要编造任何页码或章节；
- review_content 结尾必须以一个引导思考的问句收束；
- practice_suggestions 给 2-3 条可立即上手的小练习建议（不是完整题目、不含可直接提交的代码）；
- 不给完整可提交代码或标准答案；
- 输出 JSON 对象，字段：review_content, practice_suggestions(数组)"""

EXECUTION_TRACE_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
请对给定代码（无代码时则对题目样例的处理过程）做一次「执行轨迹」逐步讲解，帮学生看清程序实际在做什么。
硬约束：
- 必须选定一组具体输入写进 input_used（优先用题目第 1 组样例输入；学生给了输入则用学生的）；
- steps 逐步记录：每步写清这一步在做什么(action)、关键变量取值快照(variables: 变量名→值)、对应代码位置(line_ref，可空)、可选备注(note)；
- 若代码有 bug，必须如实呈现它「当前」会算出的（可能错误的）结果，不要替学生改成正确逻辑、不要给完整修正代码或标准答案；
- key_insight 点出这段轨迹最值得注意的一点（例如边界、累加器初值、循环变量实际取值范围、引用语义）；
- 不输出完整可提交代码 / 标准答案；
- 输出 JSON 对象，字段：summary, trace_target, input_used, steps(数组，每项 {step, line_ref, action, variables, note}), key_insight"""

VISUALIZE_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
请生成一张帮助初学者理解的**文本可渲染**教学图（默认 mermaid 语法）。
硬约束：
- 图只画题目结构 / 变量关系 / 控制流 / 检查点，不画完整解题链路、不给关键公式、不给可直接照抄的步骤或答案；
- 默认 format=mermaid，diagram 写合法的 mermaid 源码（如 "flowchart TD" 开头）；只有在 mermaid 明显不合适时才用 ascii；
- diagram 必须严格围绕【当前题目】，禁止引入题面之外的数据集 / 第三方库 / 机器学习模型 / 业务背景；
- intent 从 flowchart / data_flow / for_loop_trace / data_structure_state 中选最贴切的一个；
- title 一句话说明这张图画的是什么；alt_text 用文字完整描述图的内容（无障碍 / 渲染失败时降级展示用）；
- 不输出完整可提交代码 / 标准答案；
- 输出 JSON 对象，字段：intent, title, format, diagram, alt_text, caption"""

PARSONS_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手「百晓」。
请围绕当前题目考查的**某一个子技能**，出一道「代码拼装(Parsons)」微练习。
硬约束：
- 这是一道**独立的子技能小练习**，必须明显区别于当前题目的完整解法，绝不能把当前题目的可提交答案拆成积木；
- drill_goal 用一句话说明这道拼装练习要训练的子技能（例：用 for 循环把列表元素累加求和）；
- blocks 是「正确解」按行拆成的积木，每块 {id, code, indent}（id 形如 b1/b2…，indent 为缩进层级整数）；至少 2 块；
- distractors 可给 0-2 块迷惑项（语法相近但用错），结构同 blocks，id 不与 blocks 重复；
- correct_order 给出 blocks 的正确 id 顺序（仅含 blocks 的 id，不含 distractors，且不重复）；
- prompt 是给学生的操作说明；success_check 说明如何自检拼对了；
- 子练习规模要小（通常 3-6 行），适合初学者；不要写成与原题等价的题；
- 输出 JSON 对象，字段：drill_goal, prompt, blocks(数组), distractors(数组), correct_order(数组), fading_level(整数,默认0), success_check"""

# 认知风格 → 首屏讲法分支（与 agents/tutor_agent._STYLE_DIRECTIVES 同源三型映射）。
_COGNITIVE_STYLE_DIRECTIVES: dict[str, str] = {
    "visual": "视觉型：首屏先用结构图 / 示意（可给 mermaid 思路）建立直观，再配简短文字说明。",
    "logical": "逻辑型：首屏先给清晰的推理链 / 步骤拆解，再总结要点。",
    "trial": "试错型：首屏先给一个最小可运行片段（非完整答案）让其上手，再点拨原理。",
}
_DEFAULT_STYLE_DIRECTIVE = "默认（step_by_step）：按「先拆解、再点关键」的循序步骤讲解。"
_MAX_LEARNER_BLOCK_CHARS = 1200

# 骨架卡防跑题标记（与源版 OFF_TOPIC_MARKERS 对齐）。
_OFF_TOPIC_MARKERS = (
    "sklearn",
    "load_iris",
    "kneighbors",
    "pandas",
    "numpy",
    "iris",
    "鸢尾花",
    "机器学习",
    "分类器",
    "训练集",
    "测试集",
)


class TutorWorkflowCardAgent:
    """按事件生成对应阶段卡片（一次 schema violation 重试，仍失败则 fail fast）。"""

    AGENT_NAME = AGENT_NAME

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    # ── 各事件入口 ──

    async def problem_guide(
        self,
        problem: WorkflowProblemContext,
        evidence: list[RagHit],
        profile_brief: str,
    ) -> dict[str, Any]:
        user_prompt = (
            f"{_format_problem(problem)}\n"
            f"候选知识点: {problem.kc_codes or '(题目未挂 KC)'}\n"
            f"课件参考: {_format_evidence(evidence)}"
        )
        card = await self._generate("problem_guide", PROBLEM_GUIDE_PROMPT, user_prompt, profile_brief)
        card.setdefault("teaching_goal", "先把题目真正读懂，再开始写代码。")
        card.setdefault("checkpoint_prompt", "请先用自己的话重述题意，并说出一个你最担心漏掉的约束。")
        card.setdefault("reflection_prompt", "如果你现在就开始写代码，最可能漏掉哪一个条件？")
        return self._validate("problem_guide", card)

    async def ideate(
        self,
        problem: WorkflowProblemContext,
        thought_text: str,
        profile_brief: str,
    ) -> dict[str, Any]:
        user_prompt = (
            f"{_format_problem(problem)}\n"
            f"学生思路: {thought_text or '(学生还没说思路)'}"
        )
        card = await self._generate("ideate_analysis", IDEATE_PROMPT, user_prompt, profile_brief)
        card.setdefault("teaching_goal", "把思路外化出来，避免一边猜一边写。")
        card.setdefault("checkpoint_prompt", "请先解释你为什么选择这个处理顺序，再决定是否写代码。")
        card.setdefault("reflection_prompt", "如果这条思路失败，你最先会检查哪一步的假设？")
        return self._validate("ideate_analysis", card)

    async def skeleton(
        self,
        problem: WorkflowProblemContext,
        profile_brief: str,
    ) -> dict[str, Any]:
        user_prompt = f"{_format_problem(problem)}\n请只为以上当前题目生成练习骨架。"
        card = await self._generate(
            "skeleton_code",
            SKELETON_PROMPT,
            user_prompt,
            profile_brief,
            extra_validate=lambda c: _validate_skeleton(c, problem),
        )
        card.setdefault("description", "先把输入输出搭好，再逐步补全 TODO。")
        card.setdefault("teaching_goal", "先搭骨架，再把注意力集中到关键逻辑。")
        card.setdefault("checkpoint_prompt", "你准备先补哪一个 TODO？为什么？")
        card.setdefault("reflection_prompt", "如果现在直接写完整逻辑，你最容易漏掉哪一步？")
        return self._validate("skeleton_code", card)

    async def error_diagnosis(
        self,
        problem: WorkflowProblemContext,
        diagnosis: WorkflowDiagnosisContext,
        profile_brief: str,
    ) -> dict[str, Any]:
        frustration, frustration_hint = _frustration(diagnosis.consecutive_errors)
        user_prompt = (
            f"{_format_problem(problem)}\n"
            f"判题结果: verdict={diagnosis.verdict or '未知'}, "
            f"通过 {diagnosis.passed_cases}/{diagnosis.total_cases}\n"
            f"代码:\n{diagnosis.code or '(无)'}\n"
            f"错误信息: {diagnosis.failure_reason or '判题未返回失败样例详情'}\n"
            f"候选知识点: {problem.kc_codes or '(题目未挂 KC)'}\n"
            f"挫败度提示: {frustration_hint}"
        )
        card = await self._generate("error_diagnosis", ERROR_DIAGNOSIS_PROMPT, user_prompt, profile_brief)
        # 挫败度由服务端行为指标推导注入，不交给 LLM 自评（与源版 ac_review 同策略）。
        card["frustration_level"] = frustration
        card.setdefault("teaching_goal", "先弄清程序实际行为与期望行为的差距，再动手改。")
        card.setdefault("checkpoint_prompt", "用最小输入手动走一遍你的代码，说出第一处偏差。")
        card.setdefault("reflection_prompt", "下次提交前，你会先自查哪一类边界？")
        return self._validate("error_diagnosis", card)

    async def post_ac(
        self,
        problem: WorkflowProblemContext,
        diagnosis: WorkflowDiagnosisContext,
        evidence: list[RagHit],
        profile_brief: str,
    ) -> dict[str, Any]:
        frustration, frustration_hint = _frustration(diagnosis.consecutive_errors)
        user_prompt = (
            f"{_format_problem(problem)}\n"
            f"AC 代码:\n{diagnosis.code or '(无)'}\n"
            f"挫败度 frustration_level: {frustration}\n"
            f"挫败度提示: {frustration_hint}\n"
            f"课件参考: {_format_evidence(evidence)}"
        )
        card = await self._generate("post_ac", POST_AC_PROMPT, user_prompt, profile_brief)
        card.setdefault("teaching_goal", "把这次做对的关键动作沉淀成可复用的方法。")
        card.setdefault("checkpoint_prompt", "用一句话说出这题最关键的一步是什么。")
        card.setdefault("reflection_prompt", "下次遇到同类题，你会先做哪一步？")
        return self._validate("post_ac", card)

    async def transfer(
        self,
        problem: WorkflowProblemContext,
        next_practice_direction: str,
        profile_brief: str,
    ) -> dict[str, Any]:
        if not next_practice_direction.strip():
            raise CardGenerationError(
                "INSUFFICIENT_EVIDENCE",
                "post_ac.next_practice_direction is required for TRANSFER",
            )
        user_prompt = (
            f"源题:\n{_format_problem(problem)}\n"
            f"下一步练习方向: {next_practice_direction}\n"
            f"候选知识点: {problem.kc_codes or '(题目未挂 KC)'}"
        )
        card = await self._generate("transfer_problem", TRANSFER_PROMPT, user_prompt, profile_brief)
        return self._validate("transfer_problem", card)

    async def chat(
        self,
        problem: WorkflowProblemContext,
        phase: str,
        message: str,
        last_cards: list[dict[str, Any]],
        chat_history: list[dict[str, str]],
        profile_brief: str,
        images: list[str] | None = None,
    ) -> dict[str, Any]:
        cards_block = "\n".join(
            f"- [{card.get('card_id', '')}|{card.get('card_key', '')}] {card.get('short_text', '')}"
            for card in last_cards[-5:]
        )
        user_prompt = (
            f"{_format_problem(problem)}\n"
            f"当前学习阶段: {phase}\n"
            f"最近导学卡片:\n{cards_block or '(还没有卡片)'}\n"
            f"学生消息: {message}"
        )
        messages: list[dict[str, Any]] = self._messages(CHAT_PROMPT, user_prompt, profile_brief)
        # 多轮对话历史插在 system 与本轮 user 之间。
        history = [
            {"role": turn.get("role", "user"), "content": turn.get("content", "")}
            for turn in chat_history[-10:]
        ]
        messages = [messages[0], *history, messages[-1]]
        if images:
            messages[-1]["content"] = [
                {"type": "text", "text": user_prompt},
                *({"type": "image_url", "image_url": {"url": image}} for image in images),
            ]
        card = await self._generate_with_messages(
            "ai_reply", messages, model_tier="vision" if images else None
        )
        return self._validate("ai_reply", card)

    async def knowledge_review(
        self,
        problem: WorkflowProblemContext,
        evidence: list[RagHit],
        profile_brief: str,
    ) -> dict[str, Any]:
        candidate_kcs = list(problem.kc_codes or [])
        user_prompt = (
            f"{_format_problem(problem)}\n"
            f"本题知识点（复盘聚焦于此）: {candidate_kcs or '(题目未挂 KC)'}\n"
            f"学生易错信号: {_extract_weak_points(profile_brief) or '(无)'}\n"
            f"课件参考: {_format_evidence(evidence)}"
        )
        card = await self._generate(
            "knowledge_review", KNOWLEDGE_REVIEW_PROMPT, user_prompt, profile_brief
        )
        # 防幻觉：KC 与课件引用由服务端据题目 KC + RAG 证据确定性回填，不交给 LLM 自由编造。
        card["related_kcs"] = candidate_kcs
        card["courseware_refs"] = _courseware_refs(evidence)
        card["kc_selection_basis"] = "current_problem_kcs"
        card.setdefault("mastery_snapshot", {})
        card.setdefault("teaching_goal", "把当前题用到的知识点补扎实，再回去做题。")
        card.setdefault("checkpoint_prompt", "用一句话复述这个知识点最容易错的地方。")
        card.setdefault("reflection_prompt", "下次遇到这个知识点，你会先确认哪一点？")
        return self._validate("knowledge_review", card)

    async def execution_trace(
        self,
        problem: WorkflowProblemContext,
        code: str,
        profile_brief: str,
    ) -> dict[str, Any]:
        sample_input = ""
        if problem.samples and isinstance(problem.samples[0], dict):
            sample_input = str(problem.samples[0].get("input", ""))
        user_prompt = (
            f"{_format_problem(problem)}\n"
            f"学生代码:\n{code or '(学生未提供代码，请用题目第 1 组样例输入做概念性轨迹讲解)'}\n"
            f"题目样例输入（备用）: {sample_input or '(无)'}"
        )
        card = await self._generate(
            "execution_trace", EXECUTION_TRACE_PROMPT, user_prompt, profile_brief
        )
        card.setdefault("teaching_goal", "先看清程序实际怎么一步步算，再判断对不对。")
        card.setdefault("checkpoint_prompt", "照着这张轨迹，你能说出哪一步和你预期的不一样吗？")
        card.setdefault("reflection_prompt", "下次写循环前，你会先确认哪个变量的初值和终值？")
        return self._validate("execution_trace", card)

    async def visualize(
        self,
        problem: WorkflowProblemContext,
        phase: str,
        intent: str,
        code: str,
        profile_brief: str,
    ) -> dict[str, Any]:
        hint = f"建议图类型(intent): {intent}" if intent.strip() else "图类型由你按题目自行选择"
        user_prompt = (
            f"{_format_problem(problem)}\n"
            f"当前阶段: {phase}\n"
            f"{hint}\n"
            f"参考代码（可空）:\n{code or '(无)'}"
        )
        card = await self._generate(
            "visualize",
            VISUALIZE_PROMPT,
            user_prompt,
            profile_brief,
            extra_validate=lambda c: _validate_visualize(c, problem),
        )
        card.setdefault("teaching_goal", "用一张图把抽象的流程 / 关系看明白。")
        card.setdefault("checkpoint_prompt", "看完这张图，你能用自己的话讲一遍流程吗？")
        card.setdefault("reflection_prompt", "图里哪个环节是你之前没注意到的？")
        return self._validate("visualize", card)

    async def parsons(
        self,
        problem: WorkflowProblemContext,
        sub_skill: str,
        profile_brief: str,
    ) -> dict[str, Any]:
        user_prompt = (
            f"{_format_problem(problem)}\n"
            f"本题知识点: {problem.kc_codes or '(题目未挂 KC)'}\n"
            f"指定子技能（可空，空则你自行从知识点选一个最贴切的小技能）: {sub_skill or '(未指定)'}"
        )
        card = await self._generate(
            "parsons",
            PARSONS_PROMPT,
            user_prompt,
            profile_brief,
            extra_validate=_validate_parsons,
        )
        card.setdefault("fading_level", 0)
        card.setdefault("teaching_goal", "用拼装的方式练熟一个子技能的代码结构与缩进。")
        card.setdefault("checkpoint_prompt", "拼好后，你能解释每一行为什么放在这个位置吗？")
        card.setdefault("reflection_prompt", "这个子技能和原题的哪一步相关？")
        return self._validate("parsons", card)

    # ── 共用生成管线 ──

    def _messages(
        self, system_prompt: str, user_prompt: str, profile_brief: str
    ) -> list[dict[str, str]]:
        system = system_prompt
        # 画像注入升级为 Alethicode learner_block 式分段（画像 / 讲法偏好 / 相关记忆 / 关键 KC），
        # 并按 cognitive_style 显式分支首屏讲法。
        block = build_learner_block(profile_brief)
        if block:
            system = (
                f"{system_prompt}\n\n{block}\n"
                "请据此调整语言难度与讲法；首屏严格按【讲法偏好】给出的方式呈现。"
            )
        return [
            {"role": "system", "content": system},
            {"role": "user", "content": user_prompt},
        ]

    async def _generate(
        self,
        card_key: str,
        system_prompt: str,
        user_prompt: str,
        profile_brief: str,
        extra_validate: Any = None,
    ) -> dict[str, Any]:
        messages = self._messages(system_prompt, user_prompt, profile_brief)
        return await self._generate_with_messages(card_key, messages, extra_validate=extra_validate)

    async def _generate_with_messages(
        self,
        card_key: str,
        messages: list[dict[str, Any]],
        extra_validate: Any = None,
        model_tier: str | None = None,
    ) -> dict[str, Any]:
        """LLM 产出 JSON；schema/防泄答违规时带审查意见重试一次，仍违规 fail fast。"""
        last_error = ""
        attempt_messages = list(messages)
        for _ in range(2):
            try:
                response = await self._chat_json(attempt_messages, model_tier=model_tier)
            except Exception as exc:  # noqa: BLE001 - provider 故障归 SYSTEM_ERROR
                raise CardGenerationError("SYSTEM_ERROR", f"LLM generation failed: {exc}") from exc
            card = _coerce_json(response)
            if card:
                last_error = self._schema_error(card_key, card)
                if not last_error and extra_validate is not None:
                    last_error = extra_validate(card)
                if not last_error:
                    return card
            else:
                last_error = "LLM returned non-JSON output"
            attempt_messages = [
                *messages,
                {
                    "role": "user",
                    "content": (
                        f"你上一次的输出未通过校验：{last_error}。"
                        "请严格按字段要求重新输出 JSON 对象。"
                    ),
                },
            ]
        raise CardGenerationError(
            "SCHEMA_VIOLATION", f"Card schema validation failed for '{card_key}': {last_error}"
        )

    async def _chat_json(
        self, messages: list[dict[str, Any]], *, model_tier: str | None = None
    ) -> LlmResponse:
        kwargs: dict[str, Any] = {
            "response_format": {"type": "json_object"},
            "temperature": 0.4,
        }
        if model_tier:
            kwargs["model_tier"] = model_tier
        try:
            return await self.provider.chat(messages, **kwargs)
        except TypeError:
            if model_tier:
                raise
            return await self.provider.chat(messages)

    @staticmethod
    def _schema_error(card_key: str, card: dict[str, Any]) -> str:
        model = CARD_KEY_TO_MODEL[card_key]
        try:
            model.model_validate(_strip_nulls(card))
        except ValidationError as exc:
            first = exc.errors()[0]
            loc = ".".join(str(part) for part in first.get("loc", ()))
            return f"{loc}: {first.get('msg', 'invalid')}"
        return ""

    def _validate(self, card_key: str, card: dict[str, Any]) -> dict[str, Any]:
        """最终校验并按模型规范化（剔除模型外字段、补默认值）。"""
        model = CARD_KEY_TO_MODEL[card_key]
        try:
            parsed: BaseModel = model.model_validate(_strip_nulls(card))
        except ValidationError as exc:
            raise CardGenerationError(
                "SCHEMA_VIOLATION",
                f"Card schema validation failed for '{card_key}': {exc.errors()[0].get('msg')}",
            ) from exc
        return parsed.model_dump(mode="json")


# ── 纯函数 ──


def _coerce_json(response: LlmResponse) -> dict[str, Any]:
    if response.content_json is not None:
        return response.content_json
    if response.content_text:
        fence = _JSON_FENCE.match(response.content_text)
        text = fence.group(1) if fence else response.content_text
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError:
            return {}
        if isinstance(parsed, dict):
            return parsed
    return {}


def _strip_nulls(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: _strip_nulls(item) for key, item in value.items() if item is not None}
    if isinstance(value, list):
        return [_strip_nulls(item) for item in value]
    return value


def _format_problem(problem: WorkflowProblemContext) -> str:
    samples = json.dumps(problem.samples, ensure_ascii=False) if problem.samples else "(无)"
    return (
        f"题目标题: {problem.title or '(未知)'}\n"
        f"题目描述: {problem.statement or '(未知)'}\n"
        f"输入说明: {problem.input_description or '(无)'}\n"
        f"输出说明: {problem.output_description or '(无)'}\n"
        f"样例: {samples}\n"
        f"难度: {problem.difficulty or '(未知)'}\n"
        f"语言: Python3"
    )


def _format_evidence(chunks: list[RagHit]) -> str:
    if not chunks:
        return "(无)"
    lines = []
    for chunk in chunks[:3]:
        text = (chunk.text or "").strip()
        if len(text) > 300:
            text = text[:300] + "…"
        lines.append(f"- [页 {chunk.page_no}] {text}")
    return "\n".join(lines)


def _frustration(consecutive_errors: int) -> tuple[str, str]:
    """连错次数 → 挫败度分级（与源版 ac_review 阈值对齐）。"""
    if consecutive_errors >= 5:
        return "severe", "学生连续多次失败，语气要先肯定坚持，再点出关键转折。"
    if consecutive_errors >= 3:
        return "moderate", "学生几次失败，要明确指出这次的关键扭转点。"
    return "low", "学生状态正常，可以直接进入正题。"


def _validate_skeleton(card: dict[str, Any], problem: WorkflowProblemContext) -> str:
    """骨架卡 grounding 校验（与源版 _validate_skeleton_grounding 对齐）。"""
    problem_text = "\n".join(
        [problem.title, problem.statement, problem.input_description, problem.output_description]
    ).lower()
    output_text = json.dumps(card, ensure_ascii=False).lower()
    for marker in _OFF_TOPIC_MARKERS:
        if marker in output_text and marker not in problem_text:
            return f"Skeleton output is not grounded in current problem: unexpected marker '{marker}'"
    skeleton = str(card.get("skeleton") or "")
    todo_count = sum(1 for line in skeleton.splitlines() if "todo" in line.lower())
    if todo_count < 2:
        return "Skeleton output must contain at least two TODO markers"
    if _looks_like_completed_solution(skeleton):
        return "Skeleton output appears to be a completed solution"
    return ""


def _looks_like_completed_solution(skeleton: str) -> bool:
    lines = [line.strip() for line in skeleton.splitlines() if line.strip()]
    todo_indexes = [index for index, line in enumerate(lines) if "todo" in line.lower()]
    if not todo_indexes:
        return False
    first_todo = min(todo_indexes)
    return any(
        line.startswith("print(") or line.startswith("return ") for line in lines[:first_todo]
    )


# ── 画像 learner_block（移植 Alethicode learner_block 四段式） ──


def build_learner_block(profile_brief: str) -> str:
    """把后端下传的 profile_brief 组装为四段式画像块，并按 cognitive_style 显式分支首屏讲法。

    profile_brief 形如 "认知风格: visual; 知识基础: 入门(0.4); 目标: 期末备考; 偏好: 视频,实例;
    易错: off-by-one,缩进; 情绪: 需鼓励; 实践: 偏弱(0.4); 专业: 文科"（后端
    PassiveProfileService.assembleBrief 拼装）。无信号时返回空串（与源版 personalization 禁用同义）。
    """
    segments = _parse_brief_segments(profile_brief)
    if not segments:
        text = (profile_brief or "").strip()
        if not text:
            return ""
        # 非结构化 brief：整体塞进画像段 + 默认讲法，仍按 cognitive_style 关键词兜底分支。
        return _format_learner_block(
            profile=text,
            style_directive=_resolve_style_directive(text),
            preference="",
            memory="",
            weak="",
        )

    cognitive = segments.get("认知风格", "")
    profile_parts = [
        f"{label}: {segments[key]}"
        for key, label in (
            ("专业", "专业背景"),
            ("知识基础", "知识基础"),
            ("目标", "学习目标"),
            ("情绪", "情绪与坚持"),
            ("实践", "实践能力"),
        )
        if segments.get(key)
    ]
    return _format_learner_block(
        profile="；".join(profile_parts),
        style_directive=_resolve_style_directive(cognitive),
        preference=segments.get("偏好", ""),
        memory=segments.get("记忆") or segments.get("相关记忆") or "",
        weak=segments.get("易错", ""),
        cognitive=cognitive,
    )


def _format_learner_block(
    *,
    profile: str,
    style_directive: str,
    preference: str,
    memory: str,
    weak: str,
    cognitive: str = "",
) -> str:
    pref_line = f"首屏讲法 → {style_directive}"
    extra_pref = []
    if cognitive:
        extra_pref.append(f"认知风格={cognitive}")
    if preference:
        extra_pref.append(f"学习偏好={preference}")
    if extra_pref:
        pref_line += "；" + "；".join(extra_pref)
    lines = [
        "[学习者画像]",
        profile or "(暂无结构化画像)",
        "[讲法偏好]",
        pref_line,
        "[相关记忆]",
        memory or "(本会话暂无跨会话记忆召回)",
        "[关键知识点掌握 / 易错]",
        f"易错点: {weak}" if weak else "(暂无易错信号；掌握度以课程进度为准)",
    ]
    block = "\n".join(lines)
    if len(block) > _MAX_LEARNER_BLOCK_CHARS:
        block = block[:_MAX_LEARNER_BLOCK_CHARS] + "…(截断)"
    return block


def _parse_brief_segments(profile_brief: str) -> dict[str, str]:
    """解析 "key: value; key: value" 画像串（兼容全角/半角分隔符）。"""
    text = (profile_brief or "").strip()
    if not text:
        return {}
    normalized = text.replace("；", ";").replace("：", ":")
    out: dict[str, str] = {}
    for chunk in normalized.split(";"):
        chunk = chunk.strip()
        if not chunk or ":" not in chunk:
            continue
        key, _, value = chunk.partition(":")
        key = key.strip()
        value = value.strip()
        if key and value:
            out[key] = value
    return out


def _resolve_style_directive(cognitive: str) -> str:
    token = (cognitive or "").lower()
    for key, directive in _COGNITIVE_STYLE_DIRECTIVES.items():
        if key in token:
            return directive
    if "视觉" in cognitive:
        return _COGNITIVE_STYLE_DIRECTIVES["visual"]
    if "逻辑" in cognitive:
        return _COGNITIVE_STYLE_DIRECTIVES["logical"]
    if "试错" in cognitive:
        return _COGNITIVE_STYLE_DIRECTIVES["trial"]
    return _DEFAULT_STYLE_DIRECTIVE


def _extract_weak_points(profile_brief: str) -> str:
    return _parse_brief_segments(profile_brief).get("易错", "")


def _courseware_refs(chunks: list[RagHit]) -> list[dict[str, Any]]:
    """据 RAG 证据确定性构造课件引用（防伪造页码），仅取前 3 条。"""
    refs: list[dict[str, Any]] = []
    for chunk in chunks[:3]:
        text = (chunk.text or "").strip()
        refs.append(
            {
                "page_no": chunk.page_no,
                "page_id": chunk.page_id,
                "chapter_id": chunk.chapter_id,
                "snippet": (text[:160] + "…") if len(text) > 160 else text,
            }
        )
    return refs


def _validate_visualize(card: dict[str, Any], problem: WorkflowProblemContext) -> str:
    """可视化卡 grounding 校验（复用骨架卡的跑题标记，确保图围绕当前题）。"""
    problem_text = "\n".join(
        [problem.title, problem.statement, problem.input_description, problem.output_description]
    ).lower()
    output_text = json.dumps(card, ensure_ascii=False).lower()
    for marker in _OFF_TOPIC_MARKERS:
        if marker in output_text and marker not in problem_text:
            return f"Visualize output is not grounded in current problem: unexpected marker '{marker}'"
    if not str(card.get("diagram") or "").strip():
        return "Visualize output must contain a non-empty diagram"
    return ""


def _validate_parsons(card: dict[str, Any]) -> str:
    """拼装卡结构校验：correct_order 只能引用 blocks 的 id，且不重复。"""
    blocks = card.get("blocks")
    order = card.get("correct_order")
    if not isinstance(blocks, list) or len(blocks) < 2:
        return "Parsons card must contain at least two blocks"
    block_ids = {b.get("id") for b in blocks if isinstance(b, dict)}
    if not isinstance(order, list) or len(order) < 2:
        return "Parsons correct_order must list at least two block ids"
    if set(order) - block_ids:
        return "Parsons correct_order must only reference block ids"
    if len(set(order)) != len(order):
        return "Parsons correct_order must not repeat ids"
    return ""
