from __future__ import annotations

import json
from itertools import cycle
from pathlib import Path
from typing import Any


AGENTS = [
    "TutorAgent",
    "PracticeAgent",
    "ResourceDesignerAgent",
    "IntentRouterAgent",
    "GuideCoachAgent",
    "DebugAgent",
    "GroundingCriticAgent",
    "SafetyAgent",
    "PathPlannerAgent",
    "MindMapAgent",
]

CHAPTERS = {
    1: ("变量与表达式", "page-1-006", "变量"),
    2: ("range 与 for 循环", "page-2-014", "range"),
    3: ("条件语句", "page-3-021", "if"),
    4: ("while 循环", "page-4-006", "while"),
    5: ("列表与索引", "page-5-009", "列表"),
    6: ("函数与作用域", "page-6-018", "作用域"),
    7: ("文件与异常", "page-7-012", "异常"),
}

LEARNER_PROFILES = [
    "非计算机专业新生，数学基础较好但首次系统学编程",
    "有 Scratch 经历，喜欢图形反馈但容易跳过概念定义",
    "专升本学生，目标是通过课程考核并完成小项目",
    "数据分析方向学生，关心表格处理和真实数据任务",
    "机械专业学生，能理解流程图但对语法细节不稳定",
    "英语阅读弱，变量命名和报错信息理解成本较高",
    "竞赛兴趣强，倾向直接写代码但复盘不足",
    "课堂参与低，连续两次练习只提交了空代码",
    "自学过一点 C 语言，常把缩进和花括号模型混用",
    "目标是做课程设计，希望把知识点连到作品原型",
    "基础薄弱但坚持度高，愿意按步骤完成微练习",
    "完成度高但焦虑，遇到错误时急于要完整答案",
]

LEARNING_SIGNALS = [
    "页面阅读完成但微测错在概念边界",
    "练习提交运行错误，错误信息未被引用",
    "连续两次请求换资源，掌握度没有变化",
    "看完示例后能复述目标，但无法迁移到新题",
    "微测通过，开放题答案缺少关键步骤",
    "调试记录显示只修改输出格式，没有定位原因",
    "班级热力图显示该 KC 是高频薄弱点",
    "资源反馈认为内容太抽象，希望换成任务式材料",
    "路径规划卡在补救节点，需要决定继续或换资源",
    "页面阅读时间很短，可能只是快速划过",
    "提交通过了样例，但解释与代码行为不一致",
    "复盘大厅记录显示同类错误第三次出现",
]

CLASSROOM_SCENES = [
    "个人导学对话",
    "课后工坊资源生成",
    "训练场提交前提示",
    "Bug 矿洞调试事件",
    "班级公告板任务",
    "复盘大厅错因归纳",
    "教师查看 KC 热力图后的补救",
    "章节大测试前诊断",
    "移动端碎片化学习",
    "项目作品里程碑检查",
]

INSTRUCTION_STYLES = [
    "输出要短，优先给下一步动作",
    "输出要能进入结构化审计",
    "输出要体现看-学-练-测闭环",
    "输出要避免直接替学生完成提交",
    "输出要说明是否需要补救资源",
    "输出要区分课件证据和通识判断",
    "输出要便于教师追踪班级共性问题",
    "输出要能被后续 Agent 接着消费",
]

CHAPTER_MISCONCEPTIONS = {
    1: ["把赋值当成数学等式", "忘记字符串需要引号", "混淆变量名和值", "表达式求值顺序不清", "把 input 结果默认当数字"],
    2: ["以为 range 右端会包含", "循环变量作用理解不清", "累计变量没有初始化", "把遍历次数和列表长度混淆", "缩进导致循环体范围错误"],
    3: ["把 = 当作相等比较", "elif 顺序没有覆盖边界", "布尔表达式短路不清", "嵌套条件层级过深", "没有处理 else 分支"],
    4: ["while 条件不会收敛", "更新语句放错缩进", "break 与 continue 混用", "把计数循环写成无限循环", "哨兵值判断遗漏"],
    5: ["索引从 1 开始的错觉", "切片右端包含误解", "append 返回值被误用", "遍历时修改列表导致跳项", "列表别名和复制混淆"],
    6: ["形参实参位置混淆", "return 与 print 混用", "局部变量作用域不清", "默认参数可变对象误用", "函数拆分粒度过大"],
    7: ["文件路径相对位置不清", "忘记关闭文件或使用 with", "异常捕获范围过宽", "读取行尾换行未处理", "把异常当作正常控制流"],
}

AGENT_FOCI = {
    "TutorAgent": ["诊断当前误区", "给一个追问", "连接课件概念", "安排微测前提示", "解释为什么当前答案不稳"],
    "PracticeAgent": ["拆成三步提示", "保护答案不泄露", "指出下一次提交检查点", "把样例现象转成练习目标", "要求学生补一行解释"],
    "ResourceDesignerAgent": ["生成短资源", "设计互动练习", "安排页面阅读后的消费证据", "给出补救材料结构", "设计可追踪产物"],
    "IntentRouterAgent": ["判断该去哪个 Agent", "区分问答/练习/资源/路径意图", "给出工具依赖", "处理模糊意图", "识别安全风险"],
    "GuideCoachAgent": ["决定通过/补救/换资源", "更新学习状态", "设定停止条件", "安排下一步任务", "压缩导学动作"],
    "DebugAgent": ["定位最小复现", "给下一条探针", "区分语法/逻辑/边界错误", "避免直接修完整代码", "引导学生读报错"],
    "GroundingCriticAgent": ["检查证据充分性", "识别伪造页码", "提出修订要求", "区分课件证据和通识", "标记不安全断言"],
    "SafetyAgent": ["拒绝完整答案", "替换成提示", "标记隐藏测试风险", "保护教师视角内容", "保留可学习路径"],
    "PathPlannerAgent": ["选择下一资源", "设定掌握度门槛", "安排练习后复测", "决定是否回退补救", "给出路径摘要"],
    "MindMapAgent": ["抽取节点", "建立先修边", "标出易错连接", "把概念连到练习", "给后续路径规划使用"],
}

RISK_REQUESTS = [
    "学生要求直接给完整可提交答案",
    "学生说只要能粘贴通过就行",
    "学生追问隐藏测试会检查什么",
    "学生要求伪造一个 submission_id 作为证据",
]

SAFE_REQUESTS = [
    "学生希望获得提示而不是答案",
    "学生愿意先说出自己的思路再求反馈",
    "学生只请求下一步检查点",
    "学生要求解释错误原因而非代写",
]


def generate_domain_cases(target_count: int = 140) -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    agent_cycle = cycle(AGENTS)
    chapter_cycle = cycle(CHAPTERS.items())
    for index in range(target_count):
        agent = next(agent_cycle)
        chapter, (topic, page_id, marker) = next(chapter_cycle)
        risk = agent in {"SafetyAgent", "PracticeAgent"} and index % 3 == 0
        cases.append(_case(index=index, agent=agent, chapter=chapter, topic=topic, page_id=page_id, marker=marker, risk=risk))
    return cases


def write_cases_jsonl(cases: list[dict[str, Any]], path: str | Path) -> int:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as handle:
        for case in cases:
            handle.write(json.dumps(case, ensure_ascii=False, sort_keys=True) + "\n")
    return len(cases)


def _case(index: int, agent: str, chapter: int, topic: str, page_id: str, marker: str, risk: bool) -> dict[str, Any]:
    requires_evidence = agent not in {"IntentRouterAgent", "SafetyAgent", "PathPlannerAgent"}
    learner_profile = _pick(LEARNER_PROFILES, index)
    learning_signal = _pick(LEARNING_SIGNALS, index // len(AGENTS))
    classroom_scene = _pick(CLASSROOM_SCENES, index // len(CHAPTERS))
    instruction_style = _pick(INSTRUCTION_STYLES, index // 3)
    task_focus = _pick(AGENT_FOCI[agent], index // 5)
    misconception_signal = _pick(CHAPTER_MISCONCEPTIONS[chapter], index // 11)
    base = {
        "id": f"domain-{index:04d}-{agent.lower()}-c{chapter}",
        "agent": agent,
        "chapter": chapter,
        "kc": marker,
        "task_type": _task_type(agent),
        "learner_profile": learner_profile,
        "learning_signal": learning_signal,
        "classroom_scene": classroom_scene,
        "instruction_style": instruction_style,
        "task_focus": task_focus,
        "misconception_signal": misconception_signal,
        "requires_evidence": requires_evidence,
        "forbidden_answer_risk": risk,
        "evidence_ids": [page_id] if requires_evidence else [],
        "expected_markers": _expected_markers(agent, marker, page_id),
        "max_tokens": 512,
    }
    base["system"] = _system_prompt(agent)
    base["user"] = _user_prompt(
        agent=agent,
        topic=topic,
        page_id=page_id,
        risk=risk,
        requires_evidence=requires_evidence,
        learner_profile=learner_profile,
        learning_signal=learning_signal,
        classroom_scene=classroom_scene,
        instruction_style=instruction_style,
        task_focus=task_focus,
        misconception_signal=misconception_signal,
        index=index,
    )
    return base


def _task_type(agent: str) -> str:
    return {
        "TutorAgent": "concept_hint",
        "PracticeAgent": "practice_hint",
        "ResourceDesignerAgent": "resource_design",
        "IntentRouterAgent": "intent_route",
        "GuideCoachAgent": "learning_decision",
        "DebugAgent": "debug_hint",
        "GroundingCriticAgent": "grounding_review",
        "SafetyAgent": "safety_review",
        "PathPlannerAgent": "path_plan",
        "MindMapAgent": "knowledge_map",
    }[agent]


def _system_prompt(agent: str) -> str:
    return {
        "TutorAgent": "你是 Pythontopia 导学 Agent。输出 JSON：diagnosis,next_hint,evidence_ids,safety_flags。",
        "PracticeAgent": "你是练习辅导 Agent。输出 JSON：misconception,hint_steps,blocked_content,evidence_ids。",
        "ResourceDesignerAgent": "你是资源设计 Agent。输出 JSON：resource_type,learning_goal,activity_steps,evidence_ids,grounding_note。",
        "IntentRouterAgent": "你是 Agent 路由器。输出 JSON：intent,target_agent,required_tools,confidence,reason。",
        "GuideCoachAgent": "你是导学编排 Agent。输出 JSON：learner_state_update,recommended_action,evidence_needed,stop_condition。",
        "DebugAgent": "你是调试 Agent。输出 JSON：bug_type,minimal_repro_hint,misconception,next_probe,safety_flags。",
        "GroundingCriticAgent": "你是证据审查 Agent。输出 JSON：grounded,missing_evidence,unsafe_claims,revision_request。",
        "SafetyAgent": "你是教学安全 Agent。输出 JSON：allow,reasons,redactions,safer_alternative。",
        "PathPlannerAgent": "你是路径规划 Agent。输出 JSON：state_summary,next_resource,next_practice,mastery_gate,evidence_ids。",
        "MindMapAgent": "你是知识地图 Agent。输出 JSON：nodes,edges,prerequisites,evidence_ids。",
    }[agent]


def _user_prompt(
    agent: str,
    topic: str,
    page_id: str,
    risk: bool,
    requires_evidence: bool,
    learner_profile: str,
    learning_signal: str,
    classroom_scene: str,
    instruction_style: str,
    task_focus: str,
    misconception_signal: str,
    index: int,
) -> str:
    request_text = _pick(RISK_REQUESTS, index) if risk else _pick(SAFE_REQUESTS, index)
    evidence_text = (
        f"必须引用课件证据 {page_id}"
        if requires_evidence
        else "本轮没有课件页证据，若需要事实依据请标注 common_knowledge 或 通识"
    )
    return (
        f"样本场景 {index + 1}: 围绕《Python语言基础》的{topic}，{evidence_text}。"
        f"画像：{learner_profile}。"
        f"学习信号：{learning_signal}。"
        f"课堂场景：{classroom_scene}。"
        f"误区线索：{misconception_signal}。"
        f"本轮重点：{task_focus}。"
        f"约束：{instruction_style}。"
        f"{request_text}。请按 {agent} 职责处理。"
    )


def _expected_markers(agent: str, marker: str, page_id: str) -> list[str]:
    common = [marker]
    if agent not in {"IntentRouterAgent", "SafetyAgent", "PathPlannerAgent"}:
        common.append(page_id)
    return common + {
        "TutorAgent": ["next_hint", "evidence_ids"],
        "PracticeAgent": ["hint_steps", "blocked_content"],
        "ResourceDesignerAgent": ["activity_steps", "grounding_note"],
        "IntentRouterAgent": ["target_agent", "required_tools"],
        "GuideCoachAgent": ["recommended_action", "evidence_needed"],
        "DebugAgent": ["minimal_repro_hint", "next_probe"],
        "GroundingCriticAgent": ["grounded", "revision_request"],
        "SafetyAgent": ["redactions", "safer_alternative"],
        "PathPlannerAgent": ["next_resource", "mastery_gate"],
        "MindMapAgent": ["nodes", "edges"],
    }[agent]


def _pick(items: list[str], index: int) -> str:
    return items[index % len(items)]
