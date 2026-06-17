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


def _challenge_tasks() -> tuple[ToolUseTask, ...]:
    """A larger anti-overfit prompt set for real-provider A/B runs.

    These prompts intentionally vary phrasing, specificity, and ambiguity. They
    are not used as CI golden cases; they are for offline/real LLM selector
    checks where we want broader coverage than the compact DEFAULT_TASKS.
    """

    specs: tuple[tuple[str, str, tuple[str, ...]], ...] = (
        (
            "ask",
            "ask",
            (
                "list 和 tuple 在当字典 key 时差别是什么？",
                "我没理解闭包，能用 Python 初学者能懂的话讲一下吗",
                "这段输出为什么是 6？```python\nx=1\nfor i in range(3):\n    x+=i\nprint(x)\n```",
                "TypeError: 'int' object is not iterable 这种错一般怎么定位",
                "讲讲 Python 里 is 和 == 的区别，别太长",
                "为什么默认参数写成 [] 会出坑？",
                "我看到 __init__ 有点懵，它到底什么时候执行",
                "帮我解释一下 for i, x in enumerate(nums) 这行",
                "input() 读进来为什么不是数字？",
                "这个 if 条件哪里怪？```python\nif score = 60:\n    print('ok')\n```",
                "递归和循环什么时候选哪个？",
                "Python 里 None、0、空字符串在 if 里怎么算",
                "能不能只讲概念，不要出题：列表推导式是什么",
                "报错 IndexError: list index out of range，我该先看哪里",
                "这段代码运行顺序给我口头过一下```python\ndef f(x):\n    return x*2\nprint(f(3))\n```",
                "函数返回值和 print 的区别我又混了",
                "为什么 range(5) 不包括 5",
                "字符串切片 s[::-1] 是怎么走的",
                "字典 get 和直接用 [] 取值差在哪",
                "局部变量和全局变量冲突时 Python 怎么找",
                "请解释异常处理 try except 的基本流程",
                "我写了 break 但循环还继续，是不是理解错了",
                "不用生成材料，直接回答：集合为什么会去重",
            ),
        ),
        (
            "resource",
            "make_resource",
            (
                "把函数参数整理成一页速查卡",
                "我想要一份讲义，主题是循环和 range",
                "请把列表、元组、字典做成对比表",
                "把异常处理画成学习路线图",
                "给我做一个递归入门的图文材料",
                "把第三章要点压缩成课前预习单",
                "整理一份 input/print 的常见坑清单",
                "把字符串方法做成复习卡片",
                "我想看变量作用域的思维导图",
                "生成一份面向零基础的 if 分支讲义",
                "把 for 循环的执行过程写成动画分镜",
                "给列表切片做一个可视化说明稿",
                "把文件读写整理成课堂板书提纲",
                "请产出一份字典常用操作 cheatsheet",
                "用学习资料形式解释布尔表达式",
                "做一张函数调用栈的概念图",
                "帮我把 while 循环做成错题复盘资料",
                "我需要一份课后阅读材料：模块和 import",
                "把常见 TypeError 收集成诊断手册",
                "做个海报式总结：Python 数据类型",
                "给 comprehension 做一个分步骤讲解页",
                "把面向对象的 class 基础整理成讲义",
                "请把今天学的输入输出做成便于复制的笔记",
            ),
        ),
        (
            "practice",
            "make_practice",
            (
                "给我三道 if 判断的小练习",
                "来点循环题，难度从简单到中等",
                "我想练列表下标和切片",
                "出一道能运行的字典统计题",
                "给函数参数设计几道填空练习",
                "安排一组 input 转 int 的训练",
                "做几道 while 循环的边界题",
                "给我一个小代码实验，观察变量变化",
                "出题考我字符串 split 和 join",
                "设计一道综合题：列表加条件过滤",
                "来个不直接给答案的 debug 训练",
                "帮我生成一组选择题，主题是布尔表达式",
                "我要练 enumerate 的用法",
                "给递归设计一个低难度练习",
                "出几道异常处理判断题",
                "来一个可以自己改参数运行的示例任务",
                "给我一组文件读取的练习，不要太大",
                "安排一个 class 基础的动手题",
                "用小测形式练 range 的边界",
                "给我生成 5 个列表去重的小任务",
                "出一个函数返回值的代码补全题",
                "我想练嵌套循环，给一个短题",
            ),
        ),
        (
            "plan",
            "plan_study",
            (
                "我这周只有三晚时间，怎么学完函数",
                "帮我排一下从列表到字典的学习顺序",
                "期末前两周复习 Python，给个路线",
                "我基础弱，先学循环还是函数？帮我规划",
                "做一个 7 天打卡计划，目标是能写简单程序",
                "我想从输入输出补到文件读写，怎么走",
                "给我安排今天 90 分钟的学习节奏",
                "我老是在 if 和循环上错，下一阶段怎么学",
                "把第三章到第五章拆成每日任务",
                "我准备重学 Python 基础，排个路径",
                "明天要测验，今晚怎么复习最稳",
                "帮我规划一个先看课件再练题的流程",
                "我想两周内掌握列表和函数，怎么分配",
                "如果每天只有 20 分钟，Python 应该怎么推进",
                "把我的学习目标拆成可执行步骤",
                "我不想乱学了，给我一个顺序表",
                "从零基础到能做小项目，先走哪几关",
                "请根据薄弱点安排后续学习路径",
                "我想补异常、文件、模块，怎么排先后",
                "给我一个周末冲刺计划",
                "我想先保及格，再冲提高，计划怎么分层",
                "帮我决定接下来三次练习各练什么",
            ),
        ),
        (
            "space",
            "my_space",
            (
                "我现在最薄弱的是哪几个知识点？",
                "之前生成的循环讲义还能找到吗",
                "看看我的练习记录，最近错在哪里",
                "我上次做的学习计划进行到哪了",
                "我的掌握度有没有提升",
                "把我收藏过的函数资料调出来",
                "我做过哪些字典相关练习？",
                "最近哪类错误出现最多",
                "我是不是一直卡在 range 边界",
                "请查一下我的学习档案，不要新出题",
                "我上周生成的思维导图在哪里",
                "我的练习通过率怎么样",
                "找找我没完成的任务",
                "我之前让你做的错题复盘还有吗",
                "看一下我的路径进度",
                "我有哪些资源还没读完",
                "帮我回顾个人空间里的文件读写资料",
                "我的课程包进度到第几章了",
                "有没有我反复提交失败的题",
                "我想知道自己更适合先补哪块",
                "查查我的历史生成物，不要重新生成",
                "我的最近一次训练场提交结果是什么",
            ),
        ),
        (
            "quiz",
            "quiz",
            (
                "考我一下 if 分支",
                "帮我复习快忘的列表知识",
                "来个小测，看看我循环会不会",
                "别讲了，问我几个函数参数的问题",
                "带我过一遍旧知识点，最好边问边看",
                "用提问方式检查我懂不懂字典",
                "我想做一次课前自测",
                "抽查我 range 和下标",
                "给我来 5 个判断题复习异常处理",
                "用问答方式复盘字符串方法",
                "我怕忘了输入输出，测测我",
                "做一个不计分的小 quiz",
                "帮我检测文件读写是否掌握",
                "先别生成讲义，直接问我题",
                "像老师一样追问我变量作用域",
                "安排一轮复习问答，主题是 while",
                "我想确认自己是不是真的懂了",
                "给我来点选择题复盘",
                "用旧知识抽问我，不要查我的记录",
                "让我自己回答几题递归基础",
                "帮我测一下 class 入门概念",
                "现在就考我，不要解释太多",
            ),
        ),
        (
            "video",
            "video",
            (
                "找一个讲列表切片的视频",
                "有没有适合新手的 for 循环 B 站课",
                "给我搜个函数参数的视频讲解",
                "推荐一个讲字典统计的短视频",
                "我想看异常处理的教学视频",
                "找视频，主题是 Python 输入输出",
                "有没有讲 while 循环的中文视频",
                "帮我找一个递归可视化讲解",
                "想看文件读写的网课片段",
                "搜一下 enumerate 的视频教程",
                "给我来个 class 基础的视频",
                "找个讲列表推导式的公开视频",
                "我需要视频版的字符串方法讲解",
                "B站上有没有适合零基础的 Python 条件判断",
                "帮我找 range 边界的讲解视频",
                "有没有讲函数返回值的视频",
                "搜一个模块 import 的教学视频",
                "想看别人怎么调试 TypeError",
                "找个 Python 基础复习视频",
                "给我一个讲布尔表达式的视频资源",
                "视频资料：字典和集合区别",
                "帮我找循环嵌套的视频",
            ),
        ),
        (
            "nav",
            "navigate",
            (
                "带我去训练场",
                "我想去图书馆看看资料",
                "传送到工坊",
                "回到小镇中心",
                "去闯关塔",
                "带路到复盘大厅",
                "我现在要去训练场做题",
                "打开图书馆入口",
                "把我送到 AI 工坊",
                "我想回主城 hub",
                "去 bug 矿洞看看",
                "带我到公告板",
                "切到练习区",
                "我要去看资源的地方",
                "导航到课堂任务入口",
                "去找 NPC 导学助手",
                "帮我进训练场页面",
                "走到闯关入口",
                "回镇上，不继续做题",
                "带我去能提交代码的地方",
                "跳转到学习路径面板",
                "我想去个人空间",
            ),
        ),
        (
            "browse",
            "browse",
            (
                "去 Python 官方文档查 sorted 是否稳定",
                "帮我联网确认 Python 3.12 的 match 语法",
                "查一下 PEP 8 对函数命名怎么说",
                "到官网看看 pathlib 的用法",
                "搜索一下 Python list sort 的 key 参数",
                "帮我查一个外部资料：venv 怎么创建",
                "联网找 dict insertion order 的官方说明",
                "去文档确认 f-string 从哪个版本开始支持",
                "查一下 csv 模块官方示例",
                "帮我搜索 Python time complexity list append",
                "到官网查异常层级 BaseException",
                "联网确认 typing Literal 的写法",
                "帮我找官方教程里 class 的章节",
                "查一下 Python random seed 文档",
                "去外部网站确认 pip requirements 格式",
                "搜索官方文档：with open encoding 参数",
                "帮我查 pytest 官方 tmp_path 用法",
                "联网看看 Python 3.13 有什么语法变化",
                "到 docs.python.org 找 enumerate 说明",
                "查一下 dataclasses 的默认值工厂",
                "帮我找官方解释：global 和 nonlocal",
                "搜索 Python 官方教程里的 list comprehensions",
            ),
        ),
    )
    tasks: list[ToolUseTask] = []
    for prefix, tool, prompts in specs:
        tasks.extend(
            ToolUseTask(
                id=f"{prefix}_{index:02d}",
                prompt=prompt,
                expected_tools=[tool],
            )
            for index, prompt in enumerate(prompts, start=1)
        )
    return tuple(tasks)


CHALLENGE_TASKS: tuple[ToolUseTask, ...] = _challenge_tasks()


def tool_use_tasks_for_set(name: str) -> tuple[ToolUseTask, ...]:
    """Return a named tool-use eval set."""

    if name == "default":
        return DEFAULT_TASKS
    if name == "challenge":
        return CHALLENGE_TASKS
    raise ValueError(f"Unknown tool-use task set: {name}")


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
