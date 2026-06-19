from __future__ import annotations

import asyncio
import difflib
import json
import re
from typing import Any

from pythontopia_graph.agents.boss.kc_map import chapter_for_kc
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.schemas.feynman import (
    FeynmanBugExposureInput,
    FeynmanBugExposureOutput,
    FeynmanTuteeInput,
    FeynmanTuteeOutput,
    Misconception,
)

AGENT_NAME = "FeynmanTuteeAgent"

# 代码找茬内置的三类初学者陷阱（与 _mistake_for / _fixed_code_for 的 tag 对齐）。
CANNED_BUG_TAGS = ("off_by_one_range", "input_type_string", "list_alias_mutation")
# 代码找茬是限时小游戏：未知 tag 走 LLM 生成 bug 代码时给一个紧超时，超时即退化为
# 确定性常见陷阱，避免慢 LLM 拖过前端 10s axios 超时让整局卡住。
_BUG_LLM_TIMEOUT_S = 6.0

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


def _strip_json_fence(text: str) -> str:
    fence = re.match(r"^\s*```(?:json)?\s*(.+?)\s*```\s*$", text, re.DOTALL)
    return fence.group(1) if fence else text


def bug_line_indices(buggy: str, fixed: str) -> list[int]:
    """buggy 相对 fixed 的「被替换/删除」行号（0-based），即真正的 bug 行。

    用 difflib 行级序列比对，避免简单按位 diff 在行数变化时误标整段。
    """
    buggy_lines = buggy.split("\n")
    fixed_lines = fixed.split("\n")
    matcher = difflib.SequenceMatcher(a=buggy_lines, b=fixed_lines, autojunk=False)
    indices: list[int] = []
    for tag, i1, i2, _j1, _j2 in matcher.get_opcodes():
        if tag in ("replace", "delete"):
            indices.extend(range(i1, i2))
    return indices


class FeynmanTuteeAgent:
    """费曼反演 AI 学弟：按真实误解犯错，被正确教导才更新状态。"""

    AGENT_NAME = AGENT_NAME

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

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

        优先复现学生真实误解（canned tag → 确定性样本；其它 tag → LLM 生成 buggy+fixed 再 diff），
        无真实误解史时退化为按 KC 确定性选定的常见初学者陷阱（source 诚实标注，不冒充「你的错」）。
        """
        target = next(
            (m for m in inp.misconceptions if not m.resolved),
            inp.misconceptions[0] if inp.misconceptions else None,
        )
        if target is not None:
            real = self._bug_from_tag(target.tag, "real_misconception")
            if real is not None:
                return real
            llm = await self._bug_from_llm(inp.kc_code, target.tag)
            if llm is not None:
                return llm
        fallback_tag = self._canned_tag_for_kc(inp.kc_code)
        generic = self._bug_from_tag(fallback_tag, "generic_beginner_bug")
        if generic is not None:
            return generic
        return FeynmanBugExposureOutput(
            source="none",
            hint_md="暂时没有可玩的 bug 样本，先去做题积累一些错误，再回来玩代码找茬。",
        )

    def _bug_from_tag(self, tag: str, source: str) -> FeynmanBugExposureOutput | None:
        _, buggy = self._mistake_for(tag)
        fixed = self._fixed_code_for(tag)
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

    async def _bug_from_llm(self, kc_code: str, tag: str) -> FeynmanBugExposureOutput | None:
        try:
            data = await asyncio.wait_for(
                self._chat_json(self._bug_messages(kc_code, tag)),
                timeout=_BUG_LLM_TIMEOUT_S,
            )
        except Exception:  # noqa: BLE001 - 超时/无网/无 key 时回退上层确定性兜底
            return None
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
        return FeynmanBugExposureOutput(
            source="real_misconception",
            misconception_tag=tag,
            buggy_code=buggy,
            fixed_code=fixed,
            bug_line_indices=bug_line_indices(buggy, fixed),
            hint_md=hint if isinstance(hint, str) else "",
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

    def _bug_messages(self, kc_code: str, tag: str) -> list[dict[str, str]]:
        schema_hint = (
            '{"buggy_code":"含 bug 的 Python 代码","fixed_code":"修正后的代码",'
            '"hint_md":"一句找茬提示"}'
        )
        system = (
            "你是 Pythontopia 费曼反演里的 AI 学弟。请就给定知识点写一段「初学者常见错误」的 "
            "Python 代码，并给出对应的正确版本。只输出一个 JSON 对象，不要多余文字。"
            f"严格遵循结构：{schema_hint}。"
            "buggy_code 与 fixed_code 必须仅在「体现该误解的那一行（或几行）」不同、其余行逐字一致，"
            "以便据此定位 bug 行；不要编造隐藏测试或题目，也不要在注释里直接写出答案。"
        )
        topic = _CHAPTER_TOPIC.get(chapter_for_kc(kc_code) or 0, "")
        kc_line = f"kc_code={kc_code}" + (f"（知识点主题：{topic}）" if topic else "")
        return [
            {"role": "system", "content": system},
            {
                "role": "user",
                "content": f"{kc_line}\nmisconception_tag={tag}\n请让 bug 紧扣该知识点主题。",
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
        if tag == "off_by_one_range":
            return (
                "我以为 range 的结束值会被包含，所以 range(1, 5) 应该能打印到 5。",
                "for i in range(1, 5):\n    print(i)",
            )
        if tag == "input_type_string":
            return (
                "我以为 input() 得到的就是数字，所以可以直接拿它和 1 相加。",
                "age = input('age: ')\nprint(age + 1)",
            )
        if tag == "list_alias_mutation":
            return (
                "我以为 b = a 会复制一个新列表，所以改 b 不会影响 a。",
                "a = [1, 2]\nb = a\nb.append(3)\nprint(a)",
            )
        return ("我会带着这个误解先试一遍，但还说不清哪里错了。", None)

    @staticmethod
    def _fixed_code_for(tag: str) -> str | None:
        if tag == "off_by_one_range":
            return "for i in range(1, 6):\n    print(i)"
        if tag == "input_type_string":
            return "age = int(input('age: '))\nprint(age + 1)"
        if tag == "list_alias_mutation":
            return "a = [1, 2]\nb = a.copy()\nb.append(3)\nprint(a)"
        return None

    def _messages(self, inp: FeynmanTuteeInput) -> list[dict[str, str]]:
        schema_hint = (
            '{"reply_md":"AI 学弟回复","updated_misconceptions":'
            '[{"tag":"误解标签","evidence_event_ids":[整数],"resolved":false}],'
            '"produced_code":null}'
        )
        system = (
            "你是 Pythontopia 费曼反演里的 AI 学弟，只输出一个 JSON 对象，不要输出多余文字。"
            f"严格遵循结构：{schema_hint}。"
            "你必须持续体现输入 misconceptions 中未解决的误解，并引用原 evidence_event_ids；"
            "只有当 student_teaching 明确纠正该误解时，才能把 resolved 置为 true。"
            "不要编造新的误解、event_id、隐藏测试或标准答案。"
        )
        misconceptions = json.dumps(
            [mis.model_dump(mode="json") for mis in inp.misconceptions],
            ensure_ascii=False,
        )
        messages: list[dict[str, str]] = [
            {"role": "system", "content": system},
            {"role": "system", "content": f"kc_code={inp.kc_code}\nmisconceptions={misconceptions}"},
        ]
        messages.extend(turn.model_dump() for turn in inp.history)
        user = inp.student_teaching or "请作为 AI 学弟先暴露你当前的理解或错误尝试。"
        messages.append({"role": "user", "content": user})
        return messages
