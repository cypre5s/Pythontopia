"""代码找茬 bug 样本库（curated + 可选离线生成增强）。

为「费曼反演 · 代码找茬」提供丰富、可日常教学的 bug 题库：

* 以**学生真实错误库**（误解/失败类型 ``tag``）与课程包 7 章主题为知识底座；
* 每个样本 :class:`BugSample` 同时给出 ``buggy_code`` 与 ``fixed_code``，二者**仅在
  体现该误解的 bug 行不同、其余行逐字一致**——于是 :func:`bug_line_indices` 用行级
  diff 即可零伪造地标出真正的 bug 行（绝不臆造行号）；
* 代码长度统一控制在 6-14 行，比单行 demo 更接近真实练习片段；
* 每类误解可有多个变体，随机抽取（``rng`` 可注入以便测试确定化）让「换一局」有变化。

运行期完全离线可用（不需网络/不需 key）；若存在离线生成的 JSON 题库
（``pythontopia_graph/data/feynman_bug_bank.generated.json``，由
``scripts/generate_bug_bank.py`` 产出），则在 curated 之上**追加融合**，缺文件/损坏一律
fail-soft 忽略。

防作弊：``hint_md`` 只给苏格拉底式引导，不直接点出哪一行、也不给完整答案。

``bug_line_indices`` 定义于本模块（被 :mod:`...feynman_tutee_agent` 单向 re-export），
以打破 agent ↔ bug_bank 的循环依赖。
"""

from __future__ import annotations

import difflib
import json
import random
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Difficulty = Literal["easy", "medium", "hard"]

# 默认（未播种）随机源：production 下让多次「换一局」抽到不同变体；测试注入自带 rng 确定化。
_DEFAULT_RNG = random.Random()

# 代码长度区间（与 feynman_tutee_agent 的 LLM 目标一致）：教学向片段，6-14 行。
MIN_LINES = 6
MAX_LINES = 14

# 离线大规模生成题库（可选）。缺失/损坏一律忽略，curated 始终可用。
_GENERATED_PATH = (
    Path(__file__).resolve().parents[2] / "data" / "feynman_bug_bank.generated.json"
)


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


class BugSample(BaseModel):
    """一个「代码找茬」样本：含 bug 的代码 + 正确版 + 元信息（章节/主题/难度/提示）。"""

    model_config = ConfigDict(frozen=True)

    tag: str = Field(min_length=1, description="误解/错误类型标签，与学生真实错误库 failureKind 对齐")
    chapter: int = Field(ge=1, le=7, description="课程包 packId=43 的章节归属（1-7）")
    topic: str = Field(min_length=1, description="知识点主题短语（中文）")
    buggy_code: str = Field(min_length=1)
    fixed_code: str = Field(min_length=1)
    hint_md: str = Field(min_length=1, description="苏格拉底式提示，不直接点出答案/行号")
    difficulty: Difficulty = "medium"


def validate_sample(
    sample: BugSample, *, min_lines: int = MIN_LINES, max_lines: int = MAX_LINES
) -> list[str]:
    """校验一个样本是否满足题库硬约束；返回问题清单（空 = 合格）。

    约束：章节 1-7、buggy/fixed 行数均落在 [min_lines, max_lines]、行数相等、
    仅在 bug 行不同（其余逐字一致）、diff 至少一行、buggy/fixed 均能 ``compile()`` 通过。
    """
    problems: list[str] = []
    if not 1 <= sample.chapter <= 7:
        problems.append(f"chapter {sample.chapter} out of [1,7]")

    buggy_lines = sample.buggy_code.split("\n")
    fixed_lines = sample.fixed_code.split("\n")
    if not min_lines <= len(buggy_lines) <= max_lines:
        problems.append(f"buggy line count {len(buggy_lines)} out of [{min_lines},{max_lines}]")
    if not min_lines <= len(fixed_lines) <= max_lines:
        problems.append(f"fixed line count {len(fixed_lines)} out of [{min_lines},{max_lines}]")
    if len(buggy_lines) != len(fixed_lines):
        problems.append("buggy/fixed line counts differ (must change only bug line content)")

    indices = bug_line_indices(sample.buggy_code, sample.fixed_code)
    if not indices:
        problems.append("no bug line found (buggy equals fixed?)")

    if len(buggy_lines) == len(fixed_lines):
        diff = [i for i in range(len(buggy_lines)) if buggy_lines[i] != fixed_lines[i]]
        if not diff:
            problems.append("buggy and fixed are identical")
        if set(indices) != set(diff):
            problems.append(
                f"bug_line_indices {indices} != per-line diff {diff} (non byte-identical?)"
            )

    for label, code in (("buggy", sample.buggy_code), ("fixed", sample.fixed_code)):
        try:
            compile(code, f"<{label}:{sample.tag}>", "exec")
        except SyntaxError as exc:  # 解析失败的样本一律视为不合格
            problems.append(f"{label} does not compile: {exc}")
    return problems


# ─────────────────────────────────────────────────────────────────────────────
# Curated 题库：手工精选、逐条人审。覆盖 7 章 + 30 种错误类型，多数类型含多个变体。
# 约束：buggy/fixed 仅在 bug 行不同、其余逐字一致；行数 6-14；均可 compile()。
# ─────────────────────────────────────────────────────────────────────────────
_CURATED: list[BugSample] = [
    # ── 第一章：计算思维与计算工具 ────────────────────────────────────────────
    BugSample(
        tag="swap_without_temp",
        chapter=1,
        topic="变量交换（算法思维）",
        difficulty="easy",
        buggy_code="""# 交换两个变量的值
a = 3
b = 7
# 用同时赋值来交换 a 和 b
a, b = b, b
print("交换后 a 应该是 7：", a)
print("交换后 b 应该是 3：", b)
print("交换完成")""",
        fixed_code="""# 交换两个变量的值
a = 3
b = 7
# 用同时赋值来交换 a 和 b
a, b = b, a
print("交换后 a 应该是 7：", a)
print("交换后 b 应该是 3：", b)
print("交换完成")""",
        hint_md="同时赋值时，右边两个值的顺序对吗？两个变量都拿到同一个值了吗？",
    ),
    BugSample(
        tag="precedence_average",
        chapter=1,
        topic="运算符优先级与平均值",
        difficulty="easy",
        buggy_code="""# 计算三科平均分
chinese = 90
math = 80
english = 70
# 平均分应该是三科之和再除以 3
average = chinese + math + english / 3
print("平均分应该是 80")
print("算出来的平均分是", average)""",
        fixed_code="""# 计算三科平均分
chinese = 90
math = 80
english = 70
# 平均分应该是三科之和再除以 3
average = (chinese + math + english) / 3
print("平均分应该是 80")
print("算出来的平均分是", average)""",
        hint_md="除法和加法谁先算？想让“先求和再除以 3”，需要加点什么？",
    ),
    BugSample(
        tag="fencepost_off_by_one",
        chapter=1,
        topic="栅栏问题（计数偏差）",
        difficulty="medium",
        buggy_code="""# 一段 100 米的路，每 10 米种一棵树
# 起点和终点都要种，算一共要几棵树
length = 100
gap = 10
trees = length // gap
print("从起点到终点都种树")
print("需要的树木数量：", trees)""",
        fixed_code="""# 一段 100 米的路，每 10 米种一棵树
# 起点和终点都要种，算一共要几棵树
length = 100
gap = 10
trees = length // gap + 1
print("从起点到终点都种树")
print("需要的树木数量：", trees)""",
        hint_md="数一数：10 米一段、共 10 段，但“树”和“段”的数量一样吗？端点要不要算？",
    ),
    # ── 第二章：变量、数据类型、控制台输入与类型转换 ──────────────────────────
    BugSample(
        tag="input_type_string",
        chapter=2,
        topic="input() 返回字符串与类型转换",
        difficulty="easy",
        buggy_code="""# 读入用户年龄并算出明年的年龄
age = input("请输入你的年龄：")
next_age = age + 1
print("你今年", age, "岁")
print("明年你就", next_age, "岁了")
print("感谢使用")""",
        fixed_code="""# 读入用户年龄并算出明年的年龄
age = int(input("请输入你的年龄："))
next_age = age + 1
print("你今年", age, "岁")
print("明年你就", next_age, "岁了")
print("感谢使用")""",
        hint_md="input() 拿回来的是什么类型？它能直接和数字 1 相加吗？",
    ),
    BugSample(
        tag="input_type_string",
        chapter=2,
        topic="两数输入求和的类型转换",
        difficulty="easy",
        buggy_code="""# 输入两个整数并求和
x = input("第一个整数：")
y = input("第二个整数：")
total = x + y
print("两数之和是", total)
print("再见")""",
        fixed_code="""# 输入两个整数并求和
x = input("第一个整数：")
y = input("第二个整数：")
total = int(x) + int(y)
print("两数之和是", total)
print("再见")""",
        hint_md="如果输入 2 和 3，得到的是 5 还是 '23'？字符串相加会发生什么？",
    ),
    BugSample(
        tag="str_int_compare",
        chapter=2,
        topic="字符串与数字比较",
        difficulty="medium",
        buggy_code="""# 判断用户输入的成绩是否及格
score = input("请输入成绩：")
if score >= 60:
    print("及格")
else:
    print("不及格")""",
        fixed_code="""# 判断用户输入的成绩是否及格
score = int(input("请输入成绩："))
if score >= 60:
    print("及格")
else:
    print("不及格")""",
        hint_md="score 现在是字符串还是数字？字符串能和 60 比大小吗？",
    ),
    # ── 第三章：数值、字符串、编码与进制运算 ──────────────────────────────────
    BugSample(
        tag="int_float_division",
        chapter=3,
        topic="整除与真除法",
        difficulty="easy",
        buggy_code="""# 计算每人能分到多少苹果（希望保留小数）
apples = 10
people = 4
each = apples // people
print("一共", apples, "个苹果，分给", people, "个人")
print("每人分到", each, "个苹果")""",
        fixed_code="""# 计算每人能分到多少苹果（希望保留小数）
apples = 10
people = 4
each = apples / people
print("一共", apples, "个苹果，分给", people, "个人")
print("每人分到", each, "个苹果")""",
        hint_md="// 和 / 有什么区别？想保留 2.5 这样的小数，该用哪一个？",
    ),
    BugSample(
        tag="string_immutable",
        chapter=3,
        topic="字符串不可变",
        difficulty="medium",
        buggy_code="""# 把名字的首字母改成大写
name = "alice"
name[0] = "A"
print("修改后的名字：", name)
print("名字长度：", len(name))
print("完成")""",
        fixed_code="""# 把名字的首字母改成大写
name = "alice"
name = "A" + name[1:]
print("修改后的名字：", name)
print("名字长度：", len(name))
print("完成")""",
        hint_md="字符串能像列表那样按下标赋值修改吗？要换首字母，能不能“重新拼一个”？",
    ),
    BugSample(
        tag="float_equality",
        chapter=3,
        topic="浮点数相等判断",
        difficulty="hard",
        buggy_code="""# 判断 0.1 + 0.2 是不是等于 0.3
a = 0.1
b = 0.2
result = a + b
if result == 0.3:
    print("相等")
else:
    print("不相等（存在浮点误差）")""",
        fixed_code="""# 判断 0.1 + 0.2 是不是等于 0.3
a = 0.1
b = 0.2
result = a + b
if abs(result - 0.3) < 1e-9:
    print("相等")
else:
    print("不相等（存在浮点误差）")""",
        hint_md="把 0.1 + 0.2 打印出来看看是不是正好 0.3？浮点数能用 == 直接比吗？",
    ),
    BugSample(
        tag="string_concat_int",
        chapter=3,
        topic="字符串拼接数字",
        difficulty="easy",
        buggy_code="""# 输出购物小票的一行
item = "苹果"
price = 5
count = 3
line = item + " 总价 " + price * count
print(line)
print("谢谢惠顾")""",
        fixed_code="""# 输出购物小票的一行
item = "苹果"
price = 5
count = 3
line = item + " 总价 " + str(price * count)
print(line)
print("谢谢惠顾")""",
        hint_md="price * count 是数字还是字符串？字符串能直接和数字用 + 连起来吗？",
    ),
    # ── 第四章：流程自动化（分支与循环）──────────────────────────────────────
    BugSample(
        tag="off_by_one_range",
        chapter=4,
        topic="range 右端点不包含",
        difficulty="easy",
        buggy_code="""# 计算 1 到 5 的整数之和
total = 0
for i in range(1, 5):
    total += i
print("1 到 5 的和应该是 15")
print("程序算出来的和是", total)""",
        fixed_code="""# 计算 1 到 5 的整数之和
total = 0
for i in range(1, 6):
    total += i
print("1 到 5 的和应该是 15")
print("程序算出来的和是", total)""",
        hint_md="range(1, 5) 到底取到哪个数？右端点 5 会被包含进来吗？",
    ),
    BugSample(
        tag="off_by_one_range",
        chapter=4,
        topic="range 起点导致漏首元素",
        difficulty="medium",
        buggy_code="""# 逐个打印列表里的每个水果
fruits = ["苹果", "香蕉", "橙子"]
for i in range(1, len(fruits)):
    print("第", i, "个水果：", fruits[i])
print("一共", len(fruits), "个水果")
print("展示完毕")""",
        fixed_code="""# 逐个打印列表里的每个水果
fruits = ["苹果", "香蕉", "橙子"]
for i in range(0, len(fruits)):
    print("第", i, "个水果：", fruits[i])
print("一共", len(fruits), "个水果")
print("展示完毕")""",
        hint_md="列表的第一个元素下标是几？从 1 开始遍历会不会漏掉它？",
    ),
    BugSample(
        tag="is_vs_eq",
        chapter=4,
        topic="is 与 == 的区别",
        difficulty="medium",
        buggy_code="""# 判断两次输入的密码是否一致
first = input("设置密码：")
second = input("确认密码：")
if first is second:
    print("密码设置成功")
else:
    print("两次输入不一致")""",
        fixed_code="""# 判断两次输入的密码是否一致
first = input("设置密码：")
second = input("确认密码：")
if first == second:
    print("密码设置成功")
else:
    print("两次输入不一致")""",
        hint_md="is 比较的是“是不是同一个对象”，== 比较的是“值是否相等”。你想比哪一种？",
    ),
    BugSample(
        tag="and_or_precedence",
        chapter=4,
        topic="逻辑或与真值陷阱",
        difficulty="medium",
        buggy_code="""# 判断今天是不是周末
day = input("今天星期几（用数字）：")
day = int(day)
if day == 6 or 7:
    print("周末，放假！")
else:
    print("工作日")""",
        fixed_code="""# 判断今天是不是周末
day = input("今天星期几（用数字）：")
day = int(day)
if day == 6 or day == 7:
    print("周末，放假！")
else:
    print("工作日")""",
        hint_md="`day == 6 or 7` 里，or 右边的 7 单独看是真还是假？整个条件会不会永远成立？",
    ),
    BugSample(
        tag="augmented_vs_reassign",
        chapter=4,
        topic="累加误写成赋值",
        difficulty="easy",
        buggy_code="""# 累加 1 到 10 的和
total = 0
for i in range(1, 11):
    total = i
print("1 到 10 的和应该是 55")
print("算出来是", total)""",
        fixed_code="""# 累加 1 到 10 的和
total = 0
for i in range(1, 11):
    total += i
print("1 到 10 的和应该是 55")
print("算出来是", total)""",
        hint_md="`total = i` 每轮做了什么？它是在“累加”还是在“覆盖”？",
    ),
    BugSample(
        tag="accumulation_reset",
        chapter=4,
        topic="累加变量被覆盖",
        difficulty="medium",
        buggy_code="""# 把每个词的字符数累加起来
words = ["abc", "de", "fghi"]
total_chars = 0
for word in words:
    total_chars = len(word)
print("总字符数应该是 9")
print("算出来是", total_chars)""",
        fixed_code="""# 把每个词的字符数累加起来
words = ["abc", "de", "fghi"]
total_chars = 0
for word in words:
    total_chars += len(word)
print("总字符数应该是 9")
print("算出来是", total_chars)""",
        hint_md="循环里每一轮都把 total_chars 重新设成当前词的长度，之前累计的去哪了？",
    ),
    BugSample(
        tag="while_infinite",
        chapter=4,
        topic="while 更新方向写反",
        difficulty="medium",
        buggy_code="""# 从 5 开始倒数到 1
i = 5
while i > 0:
    print("倒计时：", i)
    i += 1
print("发射！")""",
        fixed_code="""# 从 5 开始倒数到 1
i = 5
while i > 0:
    print("倒计时：", i)
    i -= 1
print("发射！")""",
        hint_md="要倒数，i 应该越来越大还是越来越小？这样写循环会停下来吗？",
    ),
    BugSample(
        tag="break_continue_misuse",
        chapter=4,
        topic="break 与 continue 混用",
        difficulty="medium",
        buggy_code="""# 找到列表中第一个负数就停止
nums = [3, 7, -2, 9, -5]
for n in nums:
    if n < 0:
        print("找到第一个负数：", n)
        continue
print("查找结束")""",
        fixed_code="""# 找到列表中第一个负数就停止
nums = [3, 7, -2, 9, -5]
for n in nums:
    if n < 0:
        print("找到第一个负数：", n)
        break
print("查找结束")""",
        hint_md="continue 是“跳过这轮继续找”，可你想要的是“找到就停”。该用哪个关键字？",
    ),
    # ── 第五章：数据结构（列表、字典、集合、序列）────────────────────────────
    BugSample(
        tag="list_alias_mutation",
        chapter=5,
        topic="列表别名与拷贝",
        difficulty="medium",
        buggy_code="""# 备份成绩列表后只修改备份
scores = [80, 90, 70]
backup = scores
backup.append(100)
print("原始成绩应保持不变：", scores)
print("备份成绩：", backup)""",
        fixed_code="""# 备份成绩列表后只修改备份
scores = [80, 90, 70]
backup = scores.copy()
backup.append(100)
print("原始成绩应保持不变：", scores)
print("备份成绩：", backup)""",
        hint_md="`backup = scores` 是复制了一份新列表，还是给同一个列表起了别名？",
    ),
    BugSample(
        tag="list_alias_mutation",
        chapter=5,
        topic="清空副本却影响原列表",
        difficulty="medium",
        buggy_code="""# 复制购物清单后再清空副本
cart = ["牛奶", "面包", "鸡蛋"]
saved = cart
saved.clear()
print("原始清单应保留三项：", cart)
print("清空后的副本：", saved)
print("处理完成")""",
        fixed_code="""# 复制购物清单后再清空副本
cart = ["牛奶", "面包", "鸡蛋"]
saved = list(cart)
saved.clear()
print("原始清单应保留三项：", cart)
print("清空后的副本：", saved)
print("处理完成")""",
        hint_md="清空 saved 时原列表也空了，说明两个名字指向的是不是同一个列表？",
    ),
    BugSample(
        tag="mutable_default_arg",
        chapter=5,
        topic="可变默认参数共享",
        difficulty="hard",
        buggy_code="""# 往一个清单里追加任务
def add_task(task, tasks=[]):
    # 把任务加入清单
    tasks.append(task)
    return tasks

print(add_task("写作业"))
print(add_task("跑步"))""",
        fixed_code="""# 往一个清单里追加任务
def add_task(task, tasks=None):
    tasks = [] if tasks is None else tasks
    tasks.append(task)
    return tasks

print(add_task("写作业"))
print(add_task("跑步"))""",
        hint_md="默认值 [] 是每次调用都新建，还是整个函数只创建一次、被多次调用共享？",
    ),
    BugSample(
        tag="dict_keyerror",
        chapter=5,
        topic="字典计数缺省键",
        difficulty="medium",
        buggy_code="""# 统计每个字母出现的次数
letters = ["a", "b", "a", "c", "a"]
counts = {}
for ch in letters:
    counts[ch] = counts[ch] + 1
print("统计结果：", counts)
print("完成")""",
        fixed_code="""# 统计每个字母出现的次数
letters = ["a", "b", "a", "c", "a"]
counts = {}
for ch in letters:
    counts[ch] = counts.get(ch, 0) + 1
print("统计结果：", counts)
print("完成")""",
        hint_md="第一次遇到某个字母时，counts 里还没有它，直接取 counts[ch] 会怎样？",
    ),
    BugSample(
        tag="dict_keyerror",
        chapter=5,
        topic="字典查询不存在的键",
        difficulty="easy",
        buggy_code="""# 查询某个学生的分数
scores = {"小明": 90, "小红": 85}
name = "小刚"
print(name, "的分数是", scores[name])
print("查询结束")
print("再见")""",
        fixed_code="""# 查询某个学生的分数
scores = {"小明": 90, "小红": 85}
name = "小刚"
print(name, "的分数是", scores.get(name, "暂无"))
print("查询结束")
print("再见")""",
        hint_md="字典里没有“小刚”这个键时，用方括号取值会报错吗？有没有更安全的取法？",
    ),
    BugSample(
        tag="list_index_out_of_range",
        chapter=5,
        topic="遍历下标越界",
        difficulty="medium",
        buggy_code="""# 打印列表里每个元素和它的下标
items = ["红", "绿", "蓝"]
for i in range(len(items) + 1):
    print("下标", i, "的元素是", items[i])
print("打印完毕")
print("结束")""",
        fixed_code="""# 打印列表里每个元素和它的下标
items = ["红", "绿", "蓝"]
for i in range(len(items)):
    print("下标", i, "的元素是", items[i])
print("打印完毕")
print("结束")""",
        hint_md="列表长度是 3，合法下标是 0、1、2。range(len+1) 会不会多走一步？",
    ),
    BugSample(
        tag="slice_off_by_one",
        chapter=5,
        topic="切片右端点偏差",
        difficulty="easy",
        buggy_code="""# 取出列表的前三个元素
data = [10, 20, 30, 40, 50]
first_three = data[0:2]
print("前三个应该是 [10, 20, 30]")
print("取出来的是", first_three)
print("完成")""",
        fixed_code="""# 取出列表的前三个元素
data = [10, 20, 30, 40, 50]
first_three = data[0:3]
print("前三个应该是 [10, 20, 30]")
print("取出来的是", first_three)
print("完成")""",
        hint_md="切片 data[0:2] 包含下标 2 的元素吗？想要前三个，右端点该写几？",
    ),
    BugSample(
        tag="list_append_return",
        chapter=5,
        topic="append 返回值为 None",
        difficulty="medium",
        buggy_code="""# 在列表末尾加一个元素并保存结果
nums = [1, 2, 3]
nums = nums.append(4)
print("加完之后的列表：", nums)
print("长度：", len(nums) if nums else 0)
print("完成")""",
        fixed_code="""# 在列表末尾加一个元素并保存结果
nums = [1, 2, 3]
nums.append(4)
print("加完之后的列表：", nums)
print("长度：", len(nums) if nums else 0)
print("完成")""",
        hint_md="list.append() 会返回新列表吗？把它的返回值赋回给 nums，nums 变成了什么？",
    ),
    BugSample(
        tag="nested_list_alias",
        chapter=5,
        topic="二维列表乘法别名",
        difficulty="hard",
        buggy_code="""# 创建一个 3x3 的全零矩阵
matrix = [[0] * 3] * 3
matrix[0][0] = 1
print("只想把左上角改成 1")
for row in matrix:
    print(row)""",
        fixed_code="""# 创建一个 3x3 的全零矩阵
matrix = [[0] * 3 for _ in range(3)]
matrix[0][0] = 1
print("只想把左上角改成 1")
for row in matrix:
    print(row)""",
        hint_md="`[[0]*3]*3` 造出来的三行，是三个独立列表还是同一个列表的三个引用？",
    ),
    # ── 第六章：文件操作与数据分析 ────────────────────────────────────────────
    BugSample(
        tag="file_mode_wrong",
        chapter=6,
        topic="文件打开模式用错",
        difficulty="medium",
        buggy_code="""# 把一行文字写入文件
content = "Hello, Pythontopia"
with open("note.txt", "r") as f:
    f.write(content)
print("已写入 note.txt")
print("完成")""",
        fixed_code="""# 把一行文字写入文件
content = "Hello, Pythontopia"
with open("note.txt", "w") as f:
    f.write(content)
print("已写入 note.txt")
print("完成")""",
        hint_md="open 的第二个参数 'r' 是读还是写？想写入文件，模式应该用哪一个？",
    ),
    BugSample(
        tag="csv_sum_string",
        chapter=6,
        topic="从文本求和忘记转数字",
        difficulty="medium",
        buggy_code="""# 计算一组分数（从文件读来的文本）的平均分
raw = ["88", "92", "79", "85"]
total = 0
for score in raw:
    total += score
average = total / len(raw)
print("平均分：", average)""",
        fixed_code="""# 计算一组分数（从文件读来的文本）的平均分
raw = ["88", "92", "79", "85"]
total = 0
for score in raw:
    total += int(score)
average = total / len(raw)
print("平均分：", average)""",
        hint_md="从文件读到的 \"88\" 是数字还是字符串？把它直接加到 total（整数）上行吗？",
    ),
    BugSample(
        tag="wrong_aggregation_init",
        chapter=6,
        topic="求最值初始值不当",
        difficulty="hard",
        buggy_code="""# 找出温度记录里的最高温
temps = [-3, -7, -1, -5]
highest = 0
for t in temps:
    if t > highest:
        highest = t
print("最高温应该是 -1")
print("算出来是", highest)""",
        fixed_code="""# 找出温度记录里的最高温
temps = [-3, -7, -1, -5]
highest = temps[0]
for t in temps:
    if t > highest:
        highest = t
print("最高温应该是 -1")
print("算出来是", highest)""",
        hint_md="把初始最大值设为 0，可所有温度都是负的，结果会不会被 0 顶住？",
    ),
    # ── 第七章：函数、模块与面向对象 ──────────────────────────────────────────
    BugSample(
        tag="missing_return",
        chapter=7,
        topic="函数缺少返回值",
        difficulty="easy",
        buggy_code="""# 定义一个求平方的函数
def square(x):
    result = x * x
    print(result)

answer = square(5)
print("5 的平方是", answer)""",
        fixed_code="""# 定义一个求平方的函数
def square(x):
    result = x * x
    return result

answer = square(5)
print("5 的平方是", answer)""",
        hint_md="函数里只是 print 出来，外面用 answer 接到的会是平方值还是 None？",
    ),
    BugSample(
        tag="premature_return",
        chapter=7,
        topic="循环内过早 return",
        difficulty="hard",
        buggy_code="""# 统计列表里偶数的个数
def count_even(nums):
    count = 0
    for n in nums:
        if n % 2 == 0:
            count += 1
            return count
    return count

print("偶数个数应该是 3")
print("算出来是", count_even([2, 4, 6, 7]))""",
        fixed_code="""# 统计列表里偶数的个数
def count_even(nums):
    count = 0
    for n in nums:
        if n % 2 == 0:
            count += 1
            continue
    return count

print("偶数个数应该是 3")
print("算出来是", count_even([2, 4, 6, 7]))""",
        hint_md="在循环里第一次数到偶数就 return，会不会还没数完就退出了函数？",
    ),
    BugSample(
        tag="global_assignment",
        chapter=7,
        topic="函数内修改全局变量",
        difficulty="hard",
        buggy_code="""# 用函数累加一个全局计数器
counter = 0

def increase():
    # 让计数器加一
    counter = counter + 1
    return counter

print(increase())""",
        fixed_code="""# 用函数累加一个全局计数器
counter = 0

def increase():
    global counter
    counter = counter + 1
    return counter

print(increase())""",
        hint_md="函数里给 counter 赋值，Python 会把它当成局部变量还是全局变量？要改全局需要声明什么？",
    ),
    BugSample(
        tag="shadow_builtin_sum",
        chapter=7,
        topic="变量名覆盖内置函数 sum",
        difficulty="medium",
        buggy_code="""# 计算列表元素之和
def total_of(values):
    sum = 0
    for v in values:
        sum = sum + v
    return sum(values)

print("和应该是 6")
print("算出来是", total_of([1, 2, 3]))""",
        fixed_code="""# 计算列表元素之和
def total_of(values):
    sum = 0
    for v in values:
        sum = sum + v
    return sum

print("和应该是 6")
print("算出来是", total_of([1, 2, 3]))""",
        hint_md="变量名 sum 已经覆盖了内置函数 sum，最后 return 时你想返回累加结果还是再调用一次？",
    ),
    BugSample(
        tag="mutable_default_arg",
        chapter=7,
        topic="登记名单的可变默认参数",
        difficulty="hard",
        buggy_code="""# 给班级登记学生名单
def enroll(student, roster=[]):
    # 把学生加入名单
    roster.append(student)
    return roster

print(enroll("小明"))
print(enroll("小红"))""",
        fixed_code="""# 给班级登记学生名单
def enroll(student, roster=None):
    roster = [] if roster is None else roster
    roster.append(student)
    return roster

print(enroll("小明"))
print(enroll("小红"))""",
        hint_md="第二次调用 enroll 时，名单里为什么会有上一次的学生？默认 [] 被谁共享了？",
    ),
]


def _load_generated(path: Path | None = None) -> list[BugSample]:
    """读取离线生成的 JSON 题库并逐条校验；缺文件/损坏/不合格一律 fail-soft 跳过。

    JSON 形如 ``{"samples": [ {BugSample 字段...}, ... ]}`` 或直接是样本列表。
    """
    target = path or _GENERATED_PATH
    try:
        text = target.read_text(encoding="utf-8")
    except OSError:
        return []
    try:
        raw = json.loads(text)
    except (ValueError, TypeError):
        return []
    items = raw.get("samples") if isinstance(raw, dict) else raw
    if not isinstance(items, list):
        return []
    loaded: list[BugSample] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        try:
            sample = BugSample.model_validate(item)
        except Exception:  # noqa: BLE001 - 非法样本直接丢弃，绝不污染 curated
            continue
        if validate_sample(sample):  # 非空 = 有问题 → 跳过
            continue
        loaded.append(sample)
    return loaded


# curated 在前、generated 追加在后（顺序稳定，便于注入 rng 后确定化抽样）。
_ALL_SAMPLES: list[BugSample] = [*_CURATED, *_load_generated()]


def all_samples() -> list[BugSample]:
    """返回全部样本（curated + 已融合的 generated）。返回副本，外部不可改内部状态。"""
    return list(_ALL_SAMPLES)


def samples_for_tag(tag: str) -> list[BugSample]:
    """返回某误解 ``tag`` 的全部变体（顺序稳定）；无匹配返回空列表。"""
    return [s for s in _ALL_SAMPLES if s.tag == tag]


def samples_for_chapter(chapter: int) -> list[BugSample]:
    """返回某章节（1-7）的全部样本（顺序稳定）；无匹配返回空列表。"""
    return [s for s in _ALL_SAMPLES if s.chapter == chapter]


def pick_sample_for_tag(
    tag: str, *, rng: random.Random | None = None
) -> BugSample | None:
    """从匹配 ``tag`` 的变体中随机抽一个；``rng`` 可注入以确定化（测试用）。无匹配返回 None。"""
    matches = samples_for_tag(tag)
    if not matches:
        return None
    return (rng or _DEFAULT_RNG).choice(matches)


def pick_sample_for_chapter(
    chapter: int, *, rng: random.Random | None = None
) -> BugSample | None:
    """从某章节样本中随机抽一个；``rng`` 可注入以确定化（测试用）。无匹配返回 None。"""
    matches = samples_for_chapter(chapter)
    if not matches:
        return None
    return (rng or _DEFAULT_RNG).choice(matches)


def distinct_tags() -> tuple[str, ...]:
    """题库里出现过的全部误解 ``tag``（按首次出现顺序去重）。"""
    seen: dict[str, None] = {}
    for sample in _ALL_SAMPLES:
        seen.setdefault(sample.tag, None)
    return tuple(seen.keys())


__all__ = [
    "BugSample",
    "Difficulty",
    "MAX_LINES",
    "MIN_LINES",
    "all_samples",
    "bug_line_indices",
    "distinct_tags",
    "pick_sample_for_chapter",
    "pick_sample_for_tag",
    "samples_for_chapter",
    "samples_for_tag",
    "validate_sample",
]
