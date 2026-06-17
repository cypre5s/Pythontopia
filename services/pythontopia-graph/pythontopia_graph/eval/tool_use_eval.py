"""工具调用 eval（MVP Phase E）：守「工具选择质量」防回归。

在有界工具调用循环上跑一组真实任务，度量四项：

- **工具选择正确率**：首个被调用的工具是否落在该任务的可接受工具集；
- **干净终止率**：是否以 ``end_turn`` 收尾（而非触上界/预算）；
- **无未知工具**：调用的工具是否都在白名单内（循环本就过滤，作冗余校验）；
- **平均轮数**：循环效率观测。

provider 可注入：CI 用 scripted 确定性 provider（零外网、零真实 LLM）；离线可对真实
DeepSeek 跑同一套任务，对账"成熟 tool use"是否退化。executor 同样注入（可接真/假 dispatcher）。

这是 Anthropic「评测驱动工具迭代」原则的落地：用真实任务集量化工具选择，发现描述/收敛的回归。
"""

from __future__ import annotations

from pydantic import BaseModel, Field

from pythontopia_graph.graph.tool_loop import ToolCallingLoop, ToolExecutor
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.tools.consolidated import (
    CONSOLIDATED_TOOL_NAMES,
    consolidated_tool_schemas,
)
from pythontopia_graph.tools.registry import Scope

DEFAULT_SYSTEM_PROMPT = (
    "你是 Pythontopia 的学习助手。根据学生这句话，调用最合适的工具来满足需求；"
    "需要时可多步调用，完成后用一句话作答。只在确有需要时调用工具，不要臆造工具。"
)


class ToolUseTask(BaseModel):
    """一条工具选择评测任务。"""

    id: str
    prompt: str
    expected_tools: list[str]  # 可接受的工具（首个被调用的工具命中其一即算选对）
    scope: Scope = "default"


class ToolUseCaseResult(BaseModel):
    """单条任务的评测结果。"""

    task_id: str
    selected_tools: list[str]
    expected_tools: list[str]
    selection_correct: bool
    clean_termination: bool
    no_unknown: bool
    rounds: int
    stop_reason: str

    @property
    def passed(self) -> bool:
        return self.selection_correct and self.clean_termination and self.no_unknown


class ToolUseEvalReport(BaseModel):
    """工具调用 eval 汇总报告。"""

    total: int = 0
    cases: list[ToolUseCaseResult] = Field(default_factory=list)

    @property
    def passed(self) -> int:
        return sum(1 for case in self.cases if case.passed)

    @property
    def pass_rate(self) -> float:
        return self.passed / self.total if self.total else 0.0

    @property
    def selection_accuracy(self) -> float:
        if not self.total:
            return 0.0
        return sum(1 for c in self.cases if c.selection_correct) / self.total

    @property
    def clean_termination_rate(self) -> float:
        if not self.total:
            return 0.0
        return sum(1 for c in self.cases if c.clean_termination) / self.total

    @property
    def avg_rounds(self) -> float:
        if not self.cases:
            return 0.0
        return sum(c.rounds for c in self.cases) / len(self.cases)


# 随仓库的默认任务集（真实诉求 → 期望收敛工具）；覆盖全部 9 个收敛工具，含易混/边界用例。
DEFAULT_TASKS: tuple[ToolUseTask, ...] = (
    # ask（qa / explain_code / fix_bug）
    ToolUseTask(id="qa", prompt="为什么 list 不能作字典的 key？", expected_tools=["ask"]),
    ToolUseTask(id="qa2", prompt="解释一下切片是怎么工作的", expected_tools=["ask"]),
    ToolUseTask(
        id="explain",
        prompt="这段代码什么意思```python\nx=[i for i in range(3)]\n```",
        expected_tools=["ask"],
    ),
    ToolUseTask(
        id="fixbug",
        prompt="报错了 TypeError 怎么办```python\nprint(1+'2')\n```",
        expected_tools=["ask"],
    ),
    # make_resource（handout / mindmap / reading / storyboard）
    ToolUseTask(id="handout", prompt="给我整理一份函数的讲义", expected_tools=["make_resource"]),
    ToolUseTask(id="mindmap", prompt="把第三章画成思维导图", expected_tools=["make_resource"]),
    ToolUseTask(id="reading", prompt="给我一些列表的拓展阅读材料", expected_tools=["make_resource"]),
    ToolUseTask(
        id="storyboard", prompt="把递归做成动画分镜脚本", expected_tools=["make_resource"]
    ),
    # make_practice（practice / code_lab）
    ToolUseTask(id="practice", prompt="出几道循环的练习题", expected_tools=["make_practice"]),
    ToolUseTask(
        id="codelab", prompt="来个能跑的字典示例代码", expected_tools=["make_practice"]
    ),
    # plan_study
    ToolUseTask(id="plan", prompt="帮我规划这周怎么学函数", expected_tools=["plan_study"]),
    ToolUseTask(id="plan2", prompt="给我做个学习计划", expected_tools=["plan_study"]),
    # my_space（progress / resources）
    ToolUseTask(id="progress", prompt="我学得怎么样，哪里薄弱？", expected_tools=["my_space"]),
    ToolUseTask(id="mystudy", prompt="我之前生成的那份讲义在哪", expected_tools=["my_space"]),
    # quiz（test_me / review）
    ToolUseTask(id="quiz", prompt="考考我循环掌握得怎么样", expected_tools=["quiz"]),
    ToolUseTask(id="review", prompt="帮我复习一下快忘的旧知识点", expected_tools=["quiz"]),
    # video
    ToolUseTask(id="video", prompt="找个讲循环的 B 站视频", expected_tools=["video"]),
    # navigate
    ToolUseTask(id="nav_lib", prompt="带我去图书馆", expected_tools=["navigate"]),
    ToolUseTask(id="nav_arena", prompt="我想去闯关塔", expected_tools=["navigate"]),
    # browse
    ToolUseTask(
        id="browse", prompt="去 python 官网查 sorted 的稳定性", expected_tools=["browse"]
    ),
)


async def run_tool_use_eval(
    provider: LlmProvider,
    executor: ToolExecutor,
    tasks: tuple[ToolUseTask, ...] | list[ToolUseTask] = DEFAULT_TASKS,
    *,
    max_steps: int = 4,
    system_prompt: str = DEFAULT_SYSTEM_PROMPT,
    temperature: float | None = None,
) -> ToolUseEvalReport:
    """在有界工具调用循环上跑任务集，产出工具选择质量报告。

    ``temperature`` 传 0 可让真实 LLM 的工具选择更可复现（eval 降噪）。
    """
    valid = set(CONSOLIDATED_TOOL_NAMES)
    cases: list[ToolUseCaseResult] = []
    for task in tasks:
        loop = ToolCallingLoop(
            provider=provider,
            tools=consolidated_tool_schemas(task.scope),
            valid_names=valid,
            max_steps=max_steps,
            temperature=temperature,
        )
        result = await loop.run(
            system_prompt=system_prompt, user_message=task.prompt, executor=executor
        )
        selected = result.tool_trail
        cases.append(
            ToolUseCaseResult(
                task_id=task.id,
                selected_tools=selected,
                expected_tools=task.expected_tools,
                selection_correct=bool(selected) and selected[0] in task.expected_tools,
                clean_termination=result.stop_reason == "end_turn",
                no_unknown=all(tool in valid for tool in selected),
                rounds=result.rounds,
                stop_reason=result.stop_reason,
            )
        )
    return ToolUseEvalReport(total=len(cases), cases=cases)
