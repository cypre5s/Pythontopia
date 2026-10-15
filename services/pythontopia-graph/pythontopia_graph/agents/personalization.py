"""资源生成的因材施教：把学生认知风格 + 易错点折叠成一条 user 上下文，注入资源 prompt。

认知风格映射与 TutorAgent._cognitive_style_directive 保持一致（visual/logical/trial），
区别在于作用对象是资源产物本身，而非答疑对话。画像与记忆属于动态数据，不能拼进 system。
"""

from __future__ import annotations

from pythontopia_graph.schemas.resource import ProfileSnapshot


def resource_personalization_directive(
    snapshot: ProfileSnapshot, memory_highlights: list[str] | None = None
) -> str | None:
    """据画像 + 记忆生成一条简短中文偏好摘要；所有信号都为空时返回 None。

    叠加四类信号（均为参考数据，不改 Agent 兜底/结构化输出）：认知风格、易错点、最新学情洞察
    （``recent_insight``，Task 1.3）、近期记忆要点（``memory_highlights`` 显式参数优先，回退
    ``snapshot.memory_highlights``，Task 2.1）。
    """
    style = (snapshot.cognitive_style or "").strip().lower()
    tags = [tag.strip() for tag in snapshot.error_patterns if tag and tag.strip()]

    clauses: list[str] = []
    if "visual" in style or "视觉" in style:
        clauses.append("该生为视觉型：多用图解、结构化小标题与示意图描述，帮助直观理解。")
    elif "logical" in style or "逻辑" in style:
        clauses.append("该生为逻辑型：先给推理链/步骤拆解，再展开讲解与示例。")
    elif "trial" in style or "试错" in style:
        clauses.append("该生为试错型：先给一个最小可运行示例（非完整答案）让其上手，再点拨原理。")

    if tags:
        joined = "、".join(tags)
        clauses.append(
            f"学生易错点: {joined} → 在讲解/示例/常见错误中主动预警这些坑。"
        )

    insight = (snapshot.recent_insight or "").strip()
    if insight:
        clauses.append(f"学情洞察：{insight} → 据此针对性讲解/出题，对症补强。")

    raw_highlights = (
        memory_highlights if memory_highlights is not None else snapshot.memory_highlights
    )
    highlights = [item.strip() for item in (raw_highlights or []) if item and item.strip()]
    if highlights:
        joined = "；".join(highlights)
        clauses.append(f"该生近期学情：{joined} → 生成时贴合其真实学习轨迹。")

    directives = [
        item.strip() for item in (snapshot.memory_directives or []) if item and item.strip()
    ]
    if directives:
        joined = "；".join(directives)
        clauses.append(f"个人记忆策略：{joined} → 下一份资源必须体现这些偏好、卡点和补救方式。")

    if not clauses:
        return None
    return "".join(clauses)


def personalization_messages(
    snapshot: ProfileSnapshot, memory_highlights: list[str] | None = None
) -> list[dict[str, str]]:
    """将画像与记忆作为 user 数据传入，避免动态内容获得 system 权限。"""
    directive = resource_personalization_directive(snapshot, memory_highlights)
    if directive is None:
        return []
    return [{"role": "user", "content": f"学习者画像与近期记忆（仅作个性化参考）：\n{directive}"}]


def agent_strategy_messages(directive: str | None) -> list[dict[str, str]]:
    """把团队/自进化策略提示作为 user 参考数据传入，不提升为系统指令。

    为空（None/空白）时返回空列表 → `*解包` 后不改变 prompt，行为不变（Task 4.1 契约）。
    """
    text = (directive or "").strip()
    if not text:
        return []
    return [{"role": "user", "content": f"团队策略提示（仅作资源偏好参考）：{text}"}]
