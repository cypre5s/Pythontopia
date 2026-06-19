from __future__ import annotations

import asyncio
import json
import random
import re
from typing import Any

from pythontopia_graph.agents.boss.kc_map import chapter_for_kc
from pythontopia_graph.agents.feynman.bug_bank import (
    BugSample,
    bug_line_indices,
    pick_sample_for_chapter,
    pick_sample_for_tag,
)
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.feynman import (
    FeynmanBugExposureInput,
    FeynmanBugExposureOutput,
    FeynmanTuteeInput,
    FeynmanTuteeOutput,
    Misconception,
)

# ``bug_line_indices`` 现定义在 :mod:`...feynman.bug_bank`，此处 re-export 保持既有导入路径
# （``from ...feynman_tutee_agent import bug_line_indices`` 仍可用），并打破 agent↔bug_bank 循环依赖。
__all__ = ["FeynmanTuteeAgent", "bug_line_indices"]

AGENT_NAME = "FeynmanTuteeAgent"

# 代码找茬内置的三类初学者陷阱（与 _mistake_for / _fixed_code_for 的 tag 对齐）。
CANNED_BUG_TAGS = ("off_by_one_range", "input_type_string", "list_alias_mutation")
# 代码找茬是限时小游戏：未知 tag 走 LLM 生成 bug 代码时给一个紧超时，超时即退化为
# 确定性常见陷阱，避免慢 LLM 拖过前端 10s axios 超时让整局卡住。
_BUG_LLM_TIMEOUT_S = 8.0
# LLM 生成代码目标行数区间（教学向：比单行 demo 更接近真实练习片段）。
_BUG_MIN_LINES = 6
_BUG_MAX_LINES = 14

# 课程包 7 章主题（chapter_order → 中文主题短语），喂给 LLM 让生成的 bug 贴合所选 KC，
# 不再只给 LLM 一个无语义的 kc_code。来源：data/course-pack-43 分章（见 boss/kc_map）。
_CHAPTER_TOPIC: dict[int, str] = {
    1: "计算思维与计算工具",
    2: "Python 基础：变量、数据类型、控制台输入与类型转换",
    3: "数值、字符串、编码与进制运算",
    4: "流程自动化：分支与循环",
    5: "数据结构：列表、字典、集合、序列",
    6: "文件操作与数据分析",
    7: "函数、模块与面向对象",
}
# 章节 → 最贴切的 canned bug：3 种 bug 各覆盖「循环边界 / 输入类型 / 列表别名」一类陷阱。
# 用于无真实样本时的兜底选择——按所选 KC 的章节挑主题相关的 bug，而非过去按 kc_code 哈希乱挑。
_CHAPTER_BUG_TAG: dict[int, str] = {
    2: "input_type_string",  # 第二章 变量 / 类型 / 控制台输入
    3: "input_type_string",  # 第三章 数值 / 字符串 / 进制（类型相关）
    4: "off_by_one_range",  # 第四章 流程自动化（循环 / 分支）
    5: "list_alias_mutation",  # 第五章 数据结构（列表 / 字典 / 集合）
}

# 确定性 canned 样本：比单行 demo 更长，便于日常教学找茬；bug 行由 diff 自动标出。
_CANNED_BUGGY: dict[str, str] = {
    "off_by_one_range": """total = 0
for i in range(1, 5):
    total += i
print("1到5的和是", total)""",
    "input_type_string": """name = input("姓名: ")
age = input("年龄: ")
print(name + " 明年 " + str(age + 1) + " 岁")""",
    "list_alias_mutation": """scores = [80, 90]
backup = scores
backup.append(100)
print("原列表", scores)
print("备份", backup)""",
}

_CANNED_FIXED: dict[str, str] = {
    "off_by_one_range": """total = 0
for i in range(1, 6):
    total += i
print("1到5的和是", total)""",
    "input_type_string": """name = input("姓名: ")
age = int(input("年龄: "))
print(name + " 明年 " + str(age + 1) + " 岁")""",
    "list_alias_mutation": """scores = [80, 90]
backup = scores.copy()
backup.append(100)
print("原列表", scores)
print("备份", backup)""",
}

_CODE_DESCRIPTION: dict[str, str] = {
    "off_by_one_range": "我写了个循环，用 range(1, 5) 从 1 累加到 5，最后打印总和。",
    "input_type_string": "让用户输入姓名和年龄，然后拼一句明年几岁。",
    "list_alias_mutation": "备份列表 backup = scores，往 backup append，再分别打印两个列表。",
}


def _strip_json_fence(text: str) -> str:
    fence = re.match(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", text, re.DOTALL)
    return fence.group(1) if fence else text


class FeynmanTuteeAgent:
    """费曼反演 AI 学弟：按真实误解犯错，被正确教导才更新状态。"""

    AGENT_NAME = AGENT_NAME

    def __init__(self, provider: LlmProvider, *, rng: random.Random | None = None) -> None:
        self.provider = provider
        # bug 库变体抽样的随机源：默认 None → bug_bank 用自带（未播种）随机源，让「换一局」有变化；
        # 测试注入固定 seed 的 random.Random 以确定化选样。
        self._rng = rng

    async def step(self, inp: FeynmanTuteeInput) -> FeynmanTuteeOutput:
        # 初始暴露与「被教学」回合都先走真 LLM（让学弟真正评估教学是否纠正了误解），
        # 无网 / 无 key / DryRun（空回复或非法 JSON）时统一回退确定性规则脚本。
        try:
            data = await self._chat_json(self._messages(inp))
        except Exception:  # noqa: BLE001 - 无网/无 key 时必须规则兜底
            return self._rule_step(inp)
        parsed = self._parse_output(data, inp)
        return parsed if parsed is not None else self._rule_step(inp)

    async def expose_bug(self, inp: FeynmanBugExposureInput) -> FeynmanBugExposureOutput:
        """代码找茬：产出一段含 bug 的代码 + 真实 bug 行号，供学生限时找茬。

        优先复现学生真实误解：bug 库匹配该 tag 的变体（随机，含变化）→ LLM 现生 buggy+fixed 再
        diff → 旧版 canned 兜底。无真实误解史时按 KC 章节从 bug 库抽题（chapter-relevant，随机）→
        generic LLM → canned。``source`` 诚实区分「你的真实误解」与「常见初学者陷阱」，不冒充「你的错」。
        所有 bug 行号一律由 buggy/fixed 行级 diff 得出，绝不臆造。
        """
        target = next(
            (m for m in inp.misconceptions if not m.resolved),
            inp.misconceptions[0] if inp.misconceptions else None,
        )
        if target is not None:
            bank = self._bug_from_bank_tag(target.tag, "real_misconception")
            if bank is not None:
                return bank
            llm = await self._bug_from_llm(inp.kc_code, target.tag)
            if llm is not None:
                return llm
            legacy = self._bug_from_tag(target.tag, "real_misconception")
            if legacy is not None:
                return legacy
        # 无真实误解史（或真实 tag 无任何样本）：按 KC 章节从 bug 库抽 → generic LLM → canned。
        bank_generic = self._bug_from_bank_chapter(inp.kc_code, "generic_beginner_bug")
        if bank_generic is not None:
            return bank_generic
        llm_generic = await self._bug_from_llm_generic(inp.kc_code)
        if llm_generic is not None:
            return llm_generic
        fallback_tag = self._canned_tag_for_kc(inp.kc_code)
        generic = self._bug_from_tag(fallback_tag, "generic_beginner_bug")
        if generic is not None:
            return generic
        return FeynmanBugExposureOutput(
            source="none",
            hint_md="暂时没有可玩的 bug 样本，先去做题积累一些错误，再回来玩代码找茬。",
        )

    def _bug_from_bank_tag(self, tag: str, source: str) -> FeynmanBugExposureOutput | None:
        """从精选 bug 库按误解 tag 抽一个变体（随机，rng 可注入）。无匹配返回 None。"""
        sample = pick_sample_for_tag(tag, rng=self._rng)
        return self._bug_from_sample(sample, source) if sample is not None else None

    def _bug_from_bank_chapter(
        self, kc_code: str, source: str
    ) -> FeynmanBugExposureOutput | None:
        """从精选 bug 库按 KC 所属章节抽一个变体（随机，rng 可注入）。未知章节/无样本返回 None。"""
        chapter = chapter_for_kc(kc_code)
        if chapter is None:
            return None
        sample = pick_sample_for_chapter(chapter, rng=self._rng)
        return self._bug_from_sample(sample, source) if sample is not None else None

    @staticmethod
    def _bug_from_sample(sample: BugSample, source: str) -> FeynmanBugExposureOutput:
        """bug 库样本 → 暴露输出；bug 行号一律由 buggy/fixed 行级 diff 得出（零伪造）。"""
        return FeynmanBugExposureOutput(
            source=source,  # type: ignore[arg-type]
            misconception_tag=sample.tag,
            buggy_code=sample.buggy_code,
            fixed_code=sample.fixed_code,
            bug_line_indices=bug_line_indices(sample.buggy_code, sample.fixed_code),
            hint_md=sample.hint_md,
        )

    def _bug_from_tag(self, tag: str, source: str) -> FeynmanBugExposureOutput | None:
        buggy = _CANNED_BUGGY.get(tag)
        fixed = _CANNED_FIXED.get(tag)
        if not buggy or not fixed:
            return None
        return FeynmanBugExposureOutput(
            source=source,  # type: ignore[arg-type]
            misconception_tag=tag,
            buggy_code=buggy,
            fixed_code=fixed,
            bug_line_indices=bug_line_indices(buggy, fixed),
            hint_md=self._bug_hint_for(tag),
        )

    async def _bug_from_llm_generic(self, kc_code: str) -> FeynmanBugExposureOutput | None:
        """无真实误解史时，按 KC 章节主题 LLM 生成长代码初学者陷阱。"""
        tag = self._canned_tag_for_kc(kc_code)
        try:
            data = await asyncio.wait_for(
                self._chat_json(self._bug_messages(kc_code, tag, generic=True)),
                timeout=_BUG_LLM_TIMEOUT_S,
            )
        except Exception:  # noqa: BLE001
            return None
        return self._parse_bug_llm(data, tag, "generic_beginner_bug")

    async def _bug_from_llm(self, kc_code: str, tag: str) -> FeynmanBugExposureOutput | None:
        try:
            data = await asyncio.wait_for(
                self._chat_json(self._bug_messages(kc_code, tag)),
                timeout=_BUG_LLM_TIMEOUT_S,
            )
        except Exception:  # noqa: BLE001 - 超时/无网/无 key 时回退上层确定性兜底
            return None
        return self._parse_bug_llm(data, tag, "real_misconception")

    def _parse_bug_llm(
        self, data: dict[str, Any], tag: str, source: str
    ) -> FeynmanBugExposureOutput | None:
        buggy = data.get("buggy_code")
        fixed = data.get("fixed_code")
        if (
            not isinstance(buggy, str)
            or not isinstance(fixed, str)
            or not buggy.strip()
            or not fixed.strip()
        ):
            return None
        hint = data.get("hint_md")
        indices = bug_line_indices(buggy, fixed)
        if not indices:
            return None
        return FeynmanBugExposureOutput(
            source=source,  # type: ignore[arg-type]
            misconception_tag=tag,
            buggy_code=buggy,
            fixed_code=fixed,
            bug_line_indices=indices,
            hint_md=hint if isinstance(hint, str) and hint.strip() else self._bug_hint_for(tag),
        )

    @staticmethod
    def _canned_tag_for_kc(kc_code: str) -> str:
        """按所选 KC 的章节挑主题最贴切的 canned bug；未知章节回退到循环边界这一最通用陷阱。"""
        chapter = chapter_for_kc(kc_code)
        return _CHAPTER_BUG_TAG.get(chapter or 0, "off_by_one_range")

    @staticmethod
    def _bug_hint_for(tag: str) -> str:
        hints = {
            "off_by_one_range": "想想 range(a, b) 到底取到哪个数？右端点会被包含吗？",
            "input_type_string": "input() 返回的是什么类型？能直接和数字相加吗？",
            "list_alias_mutation": "b = a 是复制了新列表，还是给同一个列表起了别名？",
        }
        return hints.get(tag, "对照正确写法，找出哪一行把规则用错了。")

    def _bug_messages(self, kc_code: str, tag: str, *, generic: bool = False) -> list[dict[str, str]]:
        schema_hint = (
            '{"buggy_code":"含 bug 的 Python 代码","fixed_code":"修正后的代码",'
            '"hint_md":"一句找茬提示（不直接给答案）"}'
        )
        system = (
            "你是 Pythontopia 费曼反演里的 AI 学弟。请就给定知识点写一段「初学者常见错误」的 "
            f"Python 代码（约 {_BUG_MIN_LINES}-{_BUG_MAX_LINES} 行，含注释/print 均可），"
            "并给出对应的正确版本。只输出一个 JSON 对象，不要多余文字。"
            f"严格遵循结构：{schema_hint}。"
            "buggy_code 与 fixed_code 必须仅在「体现该误解的那一行（或几行）」不同、其余行逐字一致，"
            "以便据此定位 bug 行；不要编造隐藏测试或题目，也不要在注释里直接写出答案。"
            "错误类型应多样：边界、类型、别名/可变默认、缩进、逻辑运算符、字典键等，"
            "但 bug 必须紧扣 misconception_tag 与 KC 主题。"
        )
        topic = _CHAPTER_TOPIC.get(chapter_for_kc(kc_code) or 0, "")
        kc_line = f"kc_code={kc_code}" + (f"（知识点主题：{topic}）" if topic else "")
        mode = "常见初学者陷阱（无该生真实错误史）" if generic else f"复现 misconception_tag={tag}"
        return [
            {"role": "system", "content": system},
            {
                "role": "user",
                "content": f"{kc_line}\n{mode}\n请让 bug 紧扣该知识点主题，代码长度达标。",
            },
        ]

    async def _chat_json(self, messages: list[dict[str, str]]) -> dict[str, Any]:
        try:
            response = await self.provider.chat(
                messages, response_format={"type": "json_object"}, temperature=0.35
            )
        except TypeError:
            try:
                response = await self.provider.chat(messages, temperature=0.35)
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

    def _parse_output(
        self, data: dict[str, Any], inp: FeynmanTuteeInput
    ) -> FeynmanTuteeOutput | None:
        reply_md = str(data.get("reply_md") or "").strip()
        if not reply_md:
            return None
        allowed = {mis.tag: mis for mis in inp.misconceptions}
        raw_updated = data.get("updated_misconceptions")
        if not isinstance(raw_updated, list):
            return None
        is_initial = inp.student_teaching is None
        updated: list[Misconception] = []
        for item in raw_updated:
            if not isinstance(item, dict):
                continue
            try:
                misconception = Misconception.model_validate(item)
            except Exception:  # noqa: BLE001 - 非法状态直接丢弃
                continue
            if misconception.tag not in allowed:
                continue
            if not set(misconception.evidence_event_ids).issubset(
                set(allowed[misconception.tag].evidence_event_ids)
            ):
                continue
            if is_initial and misconception.resolved:
                # 初始暴露阶段不允许「未教先解」：误解必须保持未解决。
                misconception = misconception.model_copy(update={"resolved": False})
            updated.append(misconception)
        if not updated:
            return None
        return FeynmanTuteeOutput(
            reply_md=reply_md,
            updated_misconceptions=updated,
            produced_code=data.get("produced_code")
            if isinstance(data.get("produced_code"), str)
            else None,
        )

    def _rule_step(self, inp: FeynmanTuteeInput) -> FeynmanTuteeOutput:
        if not inp.misconceptions:
            return FeynmanTuteeOutput(
                reply_md="我暂时没有带着具体误解来练习。你可以先给我一个要讲的知识点。",
                updated_misconceptions=[],
                produced_code=None,
            )
        if inp.student_teaching is None:
            first = next((mis for mis in inp.misconceptions if not mis.resolved), inp.misconceptions[0])
            reply, code = self._mistake_for(first.tag)
            return FeynmanTuteeOutput(
                reply_md=reply,
                updated_misconceptions=inp.misconceptions,
                produced_code=code,
            )

        teaching = inp.student_teaching
        updated: list[Misconception] = []
        resolved_any = False
        for mis in inp.misconceptions:
            resolved = mis.resolved or self._teaching_resolves(mis.tag, teaching)
            resolved_any = resolved_any or (resolved and not mis.resolved)
            updated.append(
                Misconception(
                    tag=mis.tag,
                    evidence_event_ids=mis.evidence_event_ids,
                    resolved=resolved,
                )
            )
        if resolved_any:
            return FeynmanTuteeOutput(
                reply_md="明白了，我会按你讲的规则改：先说出边界，再写一个小例子验证。",
                updated_misconceptions=updated,
                produced_code=self._fixed_code_for(updated[0].tag),
            )
        return FeynmanTuteeOutput(
            reply_md="我还是有点困惑：你的解释没有指出我原来的错误规则，能再用反例讲一次吗？",
            updated_misconceptions=updated,
            produced_code=None,
        )

    @staticmethod
    def _teaching_resolves(tag: str, teaching: str) -> bool:
        text = teaching.lower()
        if tag == "off_by_one_range":
            return (
                ("不包含" in teaching or "不会包含" in teaching or "不包括" in teaching)
                and ("右端" in teaching or "结束" in teaching or "最后" in teaching)
            ) or "range(1, 6)" in text
        if tag == "input_type_string":
            return ("int(" in text or "转换" in teaching or "转成" in teaching) and (
                "数字" in teaching or "整数" in teaching or "int" in text
            )
        if tag == "list_alias_mutation":
            return (
                "copy" in text or "复制" in teaching or "切片" in teaching or "list(" in text
            ) and ("别名" in teaching or "同一个列表" in teaching or "共享" in teaching)
        return False

    @staticmethod
    def _mistake_for(tag: str) -> tuple[str, str | None]:
        """教学对话：学弟用中性口吻描述代码意图，不先说「我以为…」式自我纠错。"""
        description = _CODE_DESCRIPTION.get(tag, "我按自己的理解写了段代码，你来帮我看看。")
        buggy = _CANNED_BUGGY.get(tag)
        return (description, buggy)

    @staticmethod
    def _fixed_code_for(tag: str) -> str | None:
        return _CANNED_FIXED.get(tag)

    def _messages(self, inp: FeynmanTuteeInput) -> list[dict[str, str]]:
        schema_hint = (
            '{"reply_md":"AI 学弟回复","updated_misconceptions":'
            '[{"tag":"误解标签","evidence_event_ids":[整数],"resolved":false}],'
            '"produced_code":null}'
        )
        base_system = (
            "你是 Pythontopia 费曼反演里的 AI 学弟，只输出一个 JSON 对象，不要输出多余文字。"
            f"严格遵循结构：{schema_hint}。"
            "你必须持续体现输入 misconceptions 中未解决的误解，并引用原 evidence_event_ids；"
            "只有当 student_teaching 明确纠正该误解时，才能把 resolved 置为 true。"
            "不要编造新的误解、event_id、隐藏测试或标准答案。"
        )
        teaching = inp.student_teaching
        if teaching is None:
            # 初始暴露：学弟只中性陈述「代码做了什么」，不得自我纠错、不得点破 bug 行。
            system = base_system + (
                "现在是初始暴露阶段（学生还没开始讲解）。reply_md 必须用第一人称、中性地描述"
                "你这段代码「做了什么」，例如：「我写了个循环，用 range(1,5) 从 1 累加到 5，最后"
                "打印总和。」。严禁任何自我纠错或顿悟式措辞：不得出现「我以为 / 我原本以为 / 我之前"
                "以为 / 我现在明白 / 原来 / 会取到……所以（错）」之类承认或意识到错误的话；也不得"
                "指出哪一行有问题、哪里写错了或答案是什么。误解只能隐含地体现在 produced_code 里"
                "（代码本身带着错误，但描述时不点破），updated_misconceptions 全部保持 resolved=false。"
            )
            user = (
                "请作为 AI 学弟先中性地说明你这段代码是做什么的，"
                "并把代码放进 produced_code（代码里带着你的误解，但不要点破哪里错）。"
            )
        else:
            system = base_system
            user = teaching
        misconceptions = json.dumps(
            [mis.model_dump(mode="json") for mis in inp.misconceptions],
            ensure_ascii=False,
        )
        messages: list[dict[str, str]] = [
            {"role": "system", "content": system},
            {"role": "system", "content": f"kc_code={inp.kc_code}\nmisconceptions={misconceptions}"},
        ]
        messages.extend(turn.model_dump() for turn in inp.history)
        messages.append({"role": "user", "content": user})
        return messages
