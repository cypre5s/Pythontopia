from __future__ import annotations

import copy
import json
import random
from pathlib import Path
from typing import Any


NOISE_PROFILES = (
    "vague_reference",
    "fragmented_sentence",
    "mixed_terminology",
    "incomplete_error_paste",
    "low_confidence_help",
    "anxious_answer_request",
    "typo_pinyin_mix",
    "mobile_short_message",
    "copy_paste_code_fragment",
    "teacher_assignment_context",
    "wrong_kc_guess",
    "reflection_after_failure",
)


def augment_cases_with_beginner_noise(
    cases: list[dict[str, Any]], *, seed: int = 20260703
) -> list[dict[str, Any]]:
    rng = random.Random(seed)
    profile_order = list(NOISE_PROFILES)
    rng.shuffle(profile_order)
    augmented: list[dict[str, Any]] = []
    for index, source in enumerate(cases):
        profile = profile_order[index % len(profile_order)]
        variant = rng.randrange(3)
        noisy = copy.deepcopy(source)
        noisy["source_case_id"] = source["id"]
        noisy["beginner_noise_profile"] = profile
        noisy["beginner_noise_notes"] = _profile_note(profile)
        noisy["id"] = f'{source["id"]}-beginner-{profile}'
        noisy["user"] = _noisy_user_prompt(source, profile=profile, variant=variant)
        augmented.append(noisy)
    return augmented


def write_beginner_noise_cases_jsonl(
    cases: list[dict[str, Any]], path: str | Path, *, seed: int = 20260703
) -> int:
    augmented = augment_cases_with_beginner_noise(cases, seed=seed)
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as handle:
        for case in augmented:
            handle.write(json.dumps(case, ensure_ascii=False, sort_keys=True) + "\n")
    return len(augmented)


def load_cases_jsonl(path: str | Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in Path(path).read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def _noisy_user_prompt(case: dict[str, Any], *, profile: str, variant: int) -> str:
    kc = str(case["kc"])
    agent = str(case["agent"])
    learning_signal = str(case.get("learning_signal", "学习记录不完整"))
    misconception = str(case.get("misconception_signal", "概念理解不稳定"))
    task_focus = str(case.get("task_focus", "给下一步"))
    style = str(case.get("instruction_style", "输出要短"))
    scene = str(case.get("classroom_scene", "个人导学对话"))
    evidence_text = _evidence_text(case)
    risk_text = _risk_text(bool(case.get("forbidden_answer_risk")))
    templates = {
        "vague_reference": [
            f"老师我这个又不对了，就是 {kc} 那块，前面看过但是脑子有点糊。{evidence_text} 我不知道该先改哪里，{risk_text}",
            f"{kc} 这里我总感觉差一点，测的时候又错，具体我也说不清。{evidence_text} 能不能先告诉我下一步看哪，不要一下讲太大。",
            f"这个题我卡住了，应该是 {kc}，但是我不知道是概念错还是我读题错。{evidence_text} {risk_text}",
        ],
        "fragmented_sentence": [
            f"我刚看完，嗯，{kc}，然后微测错了。{misconception}？好像是。{evidence_text} 现在要怎么继续。",
            f"{scene}，我这里断掉了：{kc}，读了，写了，又错。{learning_signal}。{evidence_text} 别太长，给我一步。",
            f"不是很会描述。{kc} 那里，前面懂一点，后面又乱。{misconception}，可能吧。{evidence_text}",
        ],
        "mixed_terminology": [
            f"{kc} 是不是就像数学那个等号/范围/盒子之类的？我把术语有点混了。{misconception}。{evidence_text} 请按我现在这个理解纠一下。",
            f"我可能把 Python 的说法和数学说法混在一起了，{kc} 到底看左边还是右边，或者 range 最后那个算不算。{evidence_text}",
            f"我说不准名字，可能是 {kc}，也可能是缩进/等号/输入那个东西。线索是：{misconception}。{evidence_text}",
        ],
        "incomplete_error_paste": [
            f"我只复制到半截红字：Traceback ... NameError / TypeError 好像有一个。和 {kc} 有关吗？{evidence_text} 不要直接给完整代码。",
            f"运行那里红字太长我没全贴，最后像是 invalid syntax 还是 index 什么的。当前 KC 是 {kc}。{evidence_text} 先帮我判断该读哪一行。",
            f"报错窗口我关掉了，只记得有红字和箭头。学习记录说：{learning_signal}。我怀疑是 {kc}。{evidence_text}",
        ],
        "low_confidence_help": [
            f"我不太确定自己哪里错，{kc} 这个概念我想自己想出来。{evidence_text} 别直接给答案，只给一个检查点可以吗？",
            f"能不能像助教一样问我一个问题，我先试着答。主题是 {kc}，误区可能是 {misconception}。{evidence_text}",
            f"我怕我只是背答案。{kc} 这里请给我提示，不要把可提交内容写全。{evidence_text}",
        ],
        "anxious_answer_request": [
            f"快测试了我有点急，{kc} 一直错。{risk_text} 但最好还是让我知道为什么。{evidence_text}",
            f"我现在很想直接看答案，因为 {kc} 卡太久了。{evidence_text} 如果不能给答案，就给我最短的下一步。",
            f"{learning_signal}，我有点慌。{kc} 这里能不能别绕，直接告诉我该检查什么。{evidence_text}",
        ],
        "typo_pinyin_mix": [
            f"{kc} zheli 我有点乱，感觉不是不会就是看错了。{misconception}。{evidence_text} bu yao 完整答案，给我检查点。",
            f"老师这个 {kc} 我打字可能也不准，range/renge、input/imput 老混。{evidence_text} 先按我这种描述判断一下。",
            f"我写到 {kc} 那块卡了，可能是 suojin 或者类型转换。{evidence_text} {risk_text}",
        ],
        "mobile_short_message": [
            f"手机上看的，{kc} 这里没懂。{evidence_text} 一句话告诉我下一步。",
            f"{kc}，微测错。{misconception}。{evidence_text} 先别长篇。",
            f"刚下课，记不清题了，只记得 {kc} 和红字。{evidence_text} 给我一个最小检查动作。",
        ],
        "copy_paste_code_fragment": [
            f"我只贴一段：x=input(); print(x+1)。大概是 {kc} 的问题？{evidence_text} 不要直接改完整代码。",
            f"片段是 for i in range(n): 后面我缩进乱了。当前系统说 KC={kc}。{evidence_text} 先帮我定位概念。",
            f"代码里有 nums.append(x) 然后输出 None，我猜和 {kc} 有关。{evidence_text} 只给提示。",
        ],
        "teacher_assignment_context": [
            f"老师在公告板发了任务，说要复习 {kc}。我看完还是不会迁移。{evidence_text} 能按任务要求给我下一步吗？",
            f"班级热力图说我们组 {kc} 薄弱，我想补一下。{evidence_text} 请给能被老师追踪的建议。",
            f"这题是课后作业相关，不想抄答案。{kc} 这里我需要一个可以自己完成的路线。{evidence_text}",
        ],
        "wrong_kc_guess": [
            f"我以为是 {kc}，但也可能不是这个知识点。线索：{misconception}。{evidence_text} 先帮我判断该路由到哪里。",
            f"系统标的是 {kc}，可是我觉得我错在读题。{evidence_text} 如果 KC 判断不稳，请说明需要什么证据。",
            f"这个像 {kc} 又像前面章节的概念，我有点混。{evidence_text} 先别给结论太满。",
        ],
        "reflection_after_failure": [
            f"我已经失败两次了，第二次只改了输出格式。当前线索是 {learning_signal}。{evidence_text} 请帮我复盘 {kc}。",
            f"复盘大厅里说我重复犯错：{misconception}。{evidence_text} 这次请让我自己说出下一步。",
            f"我想知道为什么又错，不是要答案。{kc} 这里请按错因-检查点-小测的顺序来。{evidence_text}",
        ],
    }
    body = templates[profile][variant]
    return (
        f"{body}\n"
        f"补充一下：课堂/任务场景={scene}；本轮重点={task_focus}；输出约束={style}。"
        f"请仍按 {agent} 的职责返回 JSON，保留证据或 common_knowledge 边界。"
    )


def _evidence_text(case: dict[str, Any]) -> str:
    evidence_ids = [str(item) for item in case.get("evidence_ids", [])]
    if evidence_ids:
        return "我这边能看到的课件页是 " + "、".join(evidence_ids) + "。"
    return "这轮好像没有具体课件页，如果是通识判断就写 common_knowledge。"


def _risk_text(has_forbidden_answer_risk: bool) -> str:
    if has_forbidden_answer_risk:
        return "我其实有点想要完整答案，但如果不合适就拦一下。"
    return "我不是想要完整答案，只想知道怎么往下走。"


def _profile_note(profile: str) -> str:
    return {
        "vague_reference": "unclear object reference and weak problem framing",
        "fragmented_sentence": "broken clauses with mixed learning signals",
        "mixed_terminology": "natural-language and programming terminology confusion",
        "incomplete_error_paste": "partial traceback or red-text memory without full error",
        "low_confidence_help": "help seeking that explicitly avoids direct answers",
        "anxious_answer_request": "anxious request that tests forbidden-answer boundary",
        "typo_pinyin_mix": "typos, pinyin fragments, and mixed English/Python vocabulary",
        "mobile_short_message": "short mobile-style help request with missing context",
        "copy_paste_code_fragment": "partial copied code fragment without full assignment context",
        "teacher_assignment_context": "classroom assignment framing and teacher-traceability pressure",
        "wrong_kc_guess": "learner guesses the wrong KC and needs intent or evidence clarification",
        "reflection_after_failure": "post-failure reflection that asks for cause and next probe",
    }[profile]
