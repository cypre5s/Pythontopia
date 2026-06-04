"""资源生成的因材施教：把学生认知风格 + 易错点折叠成一条系统指令，注入「学」阶段
6 个资源 Agent 的生成 prompt。

认知风格映射与 TutorAgent._cognitive_style_directive 保持一致（visual/logical/trial），
区别在于作用对象是资源产物本身，而非答疑对话。两者皆不改变 Agent 的兜底/构建逻辑，
只追加一条 system 提示，因此对结构化输出无副作用。
"""

from __future__ import annotations

from pythontopia_graph.schemas.resource import ProfileSnapshot


def resource_personalization_directive(snapshot: ProfileSnapshot) -> str | None:
    """据画像生成一条简短中文系统指令；认知风格与易错点都为空时返回 None。"""
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

    if not clauses:
        return None
    return "".join(clauses)


def personalization_messages(snapshot: ProfileSnapshot) -> list[dict[str, str]]:
    """有指令则包成单条 system message，否则返回空列表（可直接 `*解包` 进 messages）。"""
    directive = resource_personalization_directive(snapshot)
    if directive is None:
        return []
    return [{"role": "system", "content": directive}]
