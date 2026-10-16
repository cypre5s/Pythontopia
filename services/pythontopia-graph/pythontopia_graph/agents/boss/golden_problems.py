"""7 章 × 4 阶段 golden 兜底题库。

LLM 出题或审查失败时由 ``get_golden_problem`` 提供保底题；每章同主题、stage 难度递增。
修改后须重跑 ``tests/agents/boss/test_golden_problems.py`` 验证审查通过。
"""

from __future__ import annotations

from typing import Callable

from .kc_map import chapter_for_kc
from .schemas import BossProblem, TestCase


def _problem(
    chapter: int,
    stage: int,
    kc_code: str,
    difficulty: int,
    title: str,
    statement: str,
    reference_solution: str,
    test_cases: list[TestCase],
    hints: list[str],
    problem_id: str | None = None,
) -> BossProblem:
    return BossProblem(
        problem_id=problem_id or f"golden-c{chapter}-s{stage}",
        chapter=chapter,
        stage=stage,
        kc_code=kc_code,
        title=title,
        statement=statement,
        reference_solution=reference_solution,
        test_cases=test_cases,
        difficulty=difficulty,
        hints=hints,
        metadata={"source": "golden_fallback"},
    )


# ───────── 第 1 章 入门：print / input / 变量 / 注释 ─────────
def _chapter1(stage: int) -> BossProblem:
    if stage == 1:
        return _problem(
            chapter=1,
            stage=1,
            kc_code="PY43-KC-001",
            difficulty=1,
            title="输出中国计算机发展成就",
            statement=(
                "请用 print() 函数输出“神威·太湖之光”，它是中国计算机发展成就之一。"
                "字符串内容应与样例一致，不需要读取输入。\n\n"
                "样例输入：（无）\n样例输出：神威·太湖之光"
            ),
            reference_solution='print("神威·太湖之光")',
            test_cases=[
                TestCase("", "神威·太湖之光", is_sample=True),
                TestCase("ignored", "神威·太湖之光"),
                TestCase("\n\n", "神威·太湖之光"),
            ],
            hints=["print 函数会在末尾自动换行", "字符串两边要带引号"],
        )
    if stage == 2:
        return _problem(
            chapter=1,
            stage=2,
            kc_code="PY43-KC-002",
            difficulty=2,
            title="读取名字打招呼",
            statement="读入一行字符串作为名字 name，输出 `Hello, {name}!`。",
            reference_solution='name = input()\nprint(f"Hello, {name}!")',
            test_cases=[
                TestCase("Py", "Hello, Py!", is_sample=True),
                TestCase("Anna", "Hello, Anna!"),
                TestCase("镇长", "Hello, 镇长!"),
            ],
            hints=["用 input() 读一行", "f-string 可以拼变量"],
        )
    if stage == 3:
        return _problem(
            chapter=1,
            stage=3,
            kc_code="PY43-KC-004",
            difficulty=2,
            title="加减乘除",
            statement="读入两个空格分隔的整数 a b，输出 a+b 的结果。",
            reference_solution="a, b = map(int, input().split())\nprint(a + b)",
            test_cases=[
                TestCase("2 3", "5", is_sample=True),
                TestCase("10 20", "30"),
                TestCase("-5 7", "2"),
            ],
            hints=["input().split() 拆出两段字符串", "map(int, ...) 转 int"],
        )
    # stage 4：章 1 无循环/条件，用三变量拼接提难度
    return _problem(
        chapter=1,
        stage=4,
        kc_code="PY43-KC-005",
        difficulty=3,
        title="变量名片",
        statement=(
            "依次读入 3 行：name、age、city；输出一行 `name(age) lives in city`。\n"
            "（注释提示：每行 input 都对应一行字符串，print 整行输出即可）"
        ),
        reference_solution=(
            "name = input()\nage = input()\ncity = input()\n"
            "print(f'{name}({age}) lives in {city}')"
        ),
        test_cases=[
            TestCase("Py\n18\nHangzhou", "Py(18) lives in Hangzhou", is_sample=True),
            TestCase("Anna\n21\nShanghai", "Anna(21) lives in Shanghai"),
            TestCase("Bob\n9\nDongHua", "Bob(9) lives in DongHua"),
        ],
        hints=["print + f-string 拼接 3 个变量", "每行 input() 读一段"],
    )


# ───────── 第 2 章 数据类型：list / dict / str / int / float ─────────
def _chapter2(stage: int) -> BossProblem:
    if stage == 1:
        return _problem(
            chapter=2,
            stage=1,
            kc_code="PY43-KC-006",
            difficulty=2,
            title="列表求和",
            statement="读入一行空格分隔的整数列表，输出它们的总和。",
            reference_solution="nums = list(map(int, input().split()))\nprint(sum(nums))",
            test_cases=[
                TestCase("1 2 3 4 5", "15", is_sample=True),
                TestCase("10 -5 3", "8"),
                TestCase("0", "0"),
            ],
            hints=["list(map(int, ...))", "内置 sum()"],
        )
    if stage == 2:
        return _problem(
            chapter=2,
            stage=2,
            kc_code="PY43-KC-008",
            difficulty=2,
            title="dict 查值",
            statement=(
                "已知字典 `d = {'name': 'Py', 'age': '18', 'city': 'DongHua'}`。\n"
                "读入一行 query；输出 `d.get(query, 'not found')`。"
            ),
            reference_solution=(
                "d = {'name': 'Py', 'age': '18', 'city': 'DongHua'}\n"
                "q = input()\nprint(d.get(q, 'not found'))"
            ),
            test_cases=[
                TestCase("name", "Py", is_sample=True),
                TestCase("age", "18"),
                TestCase("missing", "not found"),
            ],
            hints=["dict.get(key, default) 提供兜底", "字典字面量使用 {}"],
        )
    if stage == 3:
        return _problem(
            chapter=2,
            stage=3,
            kc_code="PY43-KC-010",
            difficulty=3,
            title="字符串切片",
            statement="读入一行字符串 s 与一个整数 k；输出 s 的最后 k 个字符。",
            reference_solution="s = input()\nk = int(input())\nprint(s[-k:])",
            test_cases=[
                TestCase("Pythontopia\n3", "pia", is_sample=True),
                TestCase("hello\n5", "hello"),
                TestCase("ab\n1", "b"),
            ],
            hints=["s[-k:] 取末尾 k 个", "k 可能等于 len(s)"],
        )
    # stage 4：章 2 无循环/import，用三浮点求平均
    return _problem(
        chapter=2,
        stage=4,
        kc_code="PY43-KC-013",
        difficulty=3,
        title="3 浮点平均",
        statement="读入一行 3 个空格分隔的浮点数 a b c，输出 (a+b+c)/3 保留 2 位小数。",
        reference_solution=(
            "a, b, c = input().split()\n"
            "avg = (float(a) + float(b) + float(c)) / 3\n"
            "print(f'{avg:.2f}')"
        ),
        test_cases=[
            TestCase("1.0 2.0 3.0", "2.00", is_sample=True),
            TestCase("2.5 2.5 2.5", "2.50"),
            TestCase("7.789 0 0", "2.60"),
        ],
        hints=["input().split() 拆 3 段", "f-string 保留 2 位小数 {:.2f}"],
    )


# ───────── 第 3 章 控制流：if / elif / else / and / or ─────────
def _chapter3(stage: int) -> BossProblem:
    if stage == 1:
        return _problem(
            chapter=3,
            stage=1,
            kc_code="PY43-KC-016",
            difficulty=2,
            title="奇偶判断",
            statement="读入一个整数 n，n 为偶数输出 `even`，奇数输出 `odd`。",
            reference_solution="n = int(input())\nprint('even' if n % 2 == 0 else 'odd')",
            test_cases=[
                TestCase("4", "even", is_sample=True),
                TestCase("7", "odd"),
                TestCase("0", "even"),
            ],
            hints=["n % 2 == 0 判偶", "三元表达式 a if cond else b"],
        )
    if stage == 2:
        return _problem(
            chapter=3,
            stage=2,
            kc_code="PY43-KC-017",
            difficulty=3,
            title="分数登记",
            statement=(
                "读入 0..100 的整数 score：>=90 输出 A，>=75 输出 B，>=60 输出 C，否则 D。"
            ),
            reference_solution=(
                "s = int(input())\n"
                "if s >= 90:\n    print('A')\n"
                "elif s >= 75:\n    print('B')\n"
                "elif s >= 60:\n    print('C')\n"
                "else:\n    print('D')"
            ),
            test_cases=[
                TestCase("95", "A", is_sample=True),
                TestCase("60", "C"),
                TestCase("42", "D"),
            ],
            hints=["if / elif / else 链", "从高到低判断"],
        )
    if stage == 3:
        return _problem(
            chapter=3,
            stage=3,
            kc_code="PY43-KC-020",
            difficulty=3,
            title="闰年判断",
            statement="读入年份 y，符合闰年规则输出 yes，否则 no（能被 4 整除且不被 100 整除，或能被 400 整除）。",
            reference_solution=(
                "y = int(input())\n"
                "leap = (y % 4 == 0 and y % 100 != 0) or (y % 400 == 0)\n"
                "print('yes' if leap else 'no')"
            ),
            test_cases=[
                TestCase("2024", "yes", is_sample=True),
                TestCase("1900", "no"),
                TestCase("2000", "yes"),
            ],
            hints=["布尔表达式可以括号组合", "and / or 优先级"],
        )
    return _problem(
        chapter=3,
        stage=4,
        kc_code="PY43-KC-022",
        difficulty=4,
        title="温度警告",
        statement=(
            "读入两个空格分隔的浮点数 t h（温度°C / 湿度%）：t>=35 且 h>=70 输出 `red`；"
            "t>=35 或 h>=70 输出 `yellow`；否则 `green`。"
        ),
        reference_solution=(
            "t, h = map(float, input().split())\n"
            "if t >= 35 and h >= 70:\n    print('red')\n"
            "elif t >= 35 or h >= 70:\n    print('yellow')\n"
            "else:\n    print('green')"
        ),
        test_cases=[
                TestCase("36.0 80.0", "red", is_sample=True),
                TestCase("36.0 50.0", "yellow"),
                TestCase("20.0 40.0", "green"),
        ],
        hints=["and 优先于 or", "比较运算符 >= "],
    )


# ───────── 第 4 章 循环：for / while / break / continue ─────────
def _chapter4(stage: int) -> BossProblem:
    if stage == 1:
        return _problem(
            chapter=4,
            stage=1,
            kc_code="PY43-KC-026",
            difficulty=2,
            title="计算阶乘",
            statement="读入正整数 n，输出 n! 的值。",
            reference_solution=(
                "n = int(input())\nans = 1\nfor i in range(1, n + 1):\n    ans *= i\nprint(ans)"
            ),
            test_cases=[
                TestCase("5", "120", is_sample=True),
                TestCase("1", "1"),
                TestCase("7", "5040"),
            ],
            hints=["range(1, n+1)", "ans *= i 累乘"],
        )
    if stage == 2:
        return _problem(
            chapter=4,
            stage=2,
            kc_code="PY43-KC-027",
            difficulty=3,
            title="读到 0 为止",
            statement="不断读入整数，遇到 0 停止；输出读入过程中遇到的所有非零数之和。",
            reference_solution=(
                "total = 0\nwhile True:\n    x = int(input())\n    if x == 0:\n        break\n    total += x\nprint(total)"
            ),
            test_cases=[
                TestCase("3\n5\n0", "8", is_sample=True),
                TestCase("1\n0", "1"),
                TestCase("10\n-3\n5\n0", "12"),
            ],
            hints=["while True + break", "读到 0 跳出循环"],
        )
    if stage == 3:
        return _problem(
            chapter=4,
            stage=3,
            kc_code="PY43-KC-031",
            difficulty=3,
            title="跳过偶数",
            statement="读入 N（首行）与 N 行整数；用 continue 跳过偶数，输出所有奇数之和。",
            reference_solution=(
                "n = int(input())\ntotal = 0\nfor _ in range(n):\n    x = int(input())\n    if x % 2 == 0:\n        continue\n    total += x\nprint(total)"
            ),
            test_cases=[
                TestCase("4\n1\n2\n3\n4", "4", is_sample=True),
                TestCase("3\n5\n7\n9", "21"),
                TestCase("2\n2\n4", "0"),
            ],
            hints=["continue 跳过本次迭代", "偶数 x % 2 == 0"],
        )
    return _problem(
        chapter=4,
        stage=4,
        kc_code="PY43-KC-033",
        difficulty=4,
        title="星期打印（综合）",
        statement=(
            "读入正整数 N，按周一→周日循环输出 N 行；每行格式 `Day{i}: {day}`，i 从 1 起。"
        ),
        reference_solution=(
            "n = int(input())\ndays = ['Mon','Tue','Wed','Thu','Fri','Sat','Sun']\n"
            "for i in range(n):\n    print(f'Day{i + 1}: {days[i % 7]}')"
        ),
        test_cases=[
            TestCase("3", "Day1: Mon\nDay2: Tue\nDay3: Wed", is_sample=True),
            TestCase("8", "Day1: Mon\nDay2: Tue\nDay3: Wed\nDay4: Thu\nDay5: Fri\nDay6: Sat\nDay7: Sun\nDay8: Mon"),
            TestCase("1", "Day1: Mon"),
        ],
        hints=["list 索引取模", "f-string 拼接"],
    )


# ───────── 第 5 章 函数：def / return / 参数 / lambda ─────────
def _chapter5(stage: int) -> BossProblem:
    if stage == 1:
        return _problem(
            chapter=5,
            stage=1,
            kc_code="PY43-KC-036",
            difficulty=2,
            title="加倍函数",
            statement="定义函数 `double(x)` 返回 2 倍；读入整数 n，输出 double(n)。",
            reference_solution="def double(x):\n    return x * 2\nn = int(input())\nprint(double(n))",
            test_cases=[
                TestCase("3", "6", is_sample=True),
                TestCase("0", "0"),
                TestCase("-4", "-8"),
            ],
            hints=["def 定义函数", "return 返回结果"],
        )
    if stage == 2:
        return _problem(
            chapter=5,
            stage=2,
            kc_code="PY43-KC-038",
            difficulty=3,
            title="两数较大者",
            statement="定义函数 `max2(a, b)` 返回较大值；读入两数空格分隔，输出 max2 结果。",
            reference_solution="def max2(a, b):\n    return a if a >= b else b\na, b = map(int, input().split())\nprint(max2(a, b))",
            test_cases=[
                TestCase("3 5", "5", is_sample=True),
                TestCase("7 1", "7"),
                TestCase("-2 -4", "-2"),
            ],
            hints=["三元表达式", "函数接两个参数"],
        )
    if stage == 3:
        return _problem(
            chapter=5,
            stage=3,
            kc_code="PY43-KC-042",
            difficulty=3,
            title="带默认参数的打折",
            statement="定义 `pay(price, discount=0.9)`，返回 price*discount；读入第一行 price，第二行 discount，输出结果保留 2 位小数。",
            reference_solution=(
                "def pay(price, discount=0.9):\n    return price * discount\n"
                "p = float(input())\nd = float(input())\nprint(f'{pay(p, d):.2f}')"
            ),
            test_cases=[
                TestCase("100\n0.8", "80.00", is_sample=True),
                TestCase("50\n1.0", "50.00"),
                TestCase("10\n0.5", "5.00"),
            ],
            hints=["默认参数 discount=0.9", "f-string 保留 2 位小数"],
        )
    return _problem(
        chapter=5,
        stage=4,
        kc_code="PY43-KC-044",
        difficulty=4,
        title="累加器函数",
        statement="定义 `total(*nums)` 返回所有传入数字之和；读入一行空格分隔的整数，散开传入并输出结果。",
        reference_solution=(
            "def total(*nums):\n    return sum(nums)\n"
            "nums = list(map(int, input().split()))\nprint(total(*nums))"
        ),
        test_cases=[
            TestCase("1 2 3", "6", is_sample=True),
            TestCase("10", "10"),
            TestCase("-5 5 0", "0"),
        ],
        hints=["*args 接收任意多个参数", "调用时 *nums 解包"],
    )


# ───────── 第 6 章 类：class / self / 继承 / 方法 ─────────
def _chapter6(stage: int) -> BossProblem:
    if stage == 1:
        return _problem(
            chapter=6,
            stage=1,
            kc_code="PY43-KC-046",
            difficulty=3,
            title="Point 类",
            statement=(
                "定义 `class Point` 含 __init__(x, y) 与方法 distance() 返回到原点欧氏距离；\n"
                "读入两整数 x y，输出 distance 结果保留 2 位小数。"
            ),
            reference_solution=(
                "class Point:\n    def __init__(self, x, y):\n        self.x = x\n        self.y = y\n"
                "    def distance(self):\n        return (self.x * self.x + self.y * self.y) ** 0.5\n"
                "x, y = map(int, input().split())\np = Point(x, y)\nprint(f'{p.distance():.2f}')"
            ),
            test_cases=[
                TestCase("3 4", "5.00", is_sample=True),
                TestCase("0 0", "0.00"),
                TestCase("6 8", "10.00"),
            ],
            hints=["class Point", "self.x / self.y 存属性"],
        )
    if stage == 2:
        return _problem(
            chapter=6,
            stage=2,
            kc_code="PY43-KC-048",
            difficulty=3,
            title="计数器方法",
            statement="定义 `class Counter`：init 时 count=0；add() 自增 1；value() 返回当前值。读入整数 n，调用 n 次 add 后输出 value。",
            reference_solution=(
                "class Counter:\n    def __init__(self):\n        self.count = 0\n"
                "    def add(self):\n        self.count += 1\n"
                "    def value(self):\n        return self.count\n"
                "n = int(input())\nc = Counter()\nfor _ in range(n):\n    c.add()\nprint(c.value())"
            ),
            test_cases=[
                TestCase("3", "3", is_sample=True),
                TestCase("0", "0"),
                TestCase("10", "10"),
            ],
            hints=["__init__ 中初始化属性", "方法第一个参数是 self"],
        )
    if stage == 3:
        return _problem(
            chapter=6,
            stage=3,
            kc_code="PY43-KC-050",
            difficulty=4,
            title="继承学生类",
            statement=(
                "定义 `class Person`：__init__(name)，方法 hello() 返回 'Hi'；\n"
                "定义 `class Student(Person)`：重写 hello() 返回 `f'Hi I am student {self.name}'`。\n"
                "读入名字 name，构造 Student 并输出其 hello()。"
            ),
            reference_solution=(
                "class Person:\n    def __init__(self, name):\n        self.name = name\n"
                "    def hello(self):\n        return 'Hi'\n"
                "class Student(Person):\n    def hello(self):\n        return f'Hi I am student {self.name}'\n"
                "n = input()\ns = Student(n)\nprint(s.hello())"
            ),
            test_cases=[
                TestCase("Py", "Hi I am student Py", is_sample=True),
                TestCase("Anna", "Hi I am student Anna"),
                TestCase("LiHua", "Hi I am student LiHua"),
            ],
            hints=["class Sub(Parent) 表示继承", "子类可重写方法"],
        )
    return _problem(
        chapter=6,
        stage=4,
        kc_code="PY43-KC-054",
        difficulty=4,
        title="super 调用",
        statement=(
            "定义 `class A` 含 greet() 返回 'A'；\n"
            "`class B(A)` 重写 greet() 返回 super().greet() + 'B'。读入数字 n，输出 B 的实例 greet() 重复 n 遍（每行一次）。"
        ),
        reference_solution=(
            "class A:\n    def greet(self):\n        return 'A'\n"
            "class B(A):\n    def greet(self):\n        return super().greet() + 'B'\n"
            "n = int(input())\nb = B()\nfor _ in range(n):\n    print(b.greet())"
        ),
        test_cases=[
            TestCase("2", "AB\nAB", is_sample=True),
            TestCase("1", "AB"),
            TestCase("3", "AB\nAB\nAB"),
        ],
        hints=["super().method() 调父类", "for 循环重复打印"],
    )


# ───────── 第 7 章 模块 / IO / 异常 ─────────
def _chapter7(stage: int) -> BossProblem:
    if stage == 1:
        return _problem(
            chapter=7,
            stage=1,
            kc_code="PY43-KC-056",
            difficulty=3,
            title="数学库平方根",
            statement="`import math`；读入正浮点数 x，输出 sqrt(x) 保留 2 位小数。",
            reference_solution="import math\nx = float(input())\nprint(f'{math.sqrt(x):.2f}')",
            test_cases=[
                TestCase("4", "2.00", is_sample=True),
                TestCase("2", "1.41"),
                TestCase("9", "3.00"),
            ],
            hints=["import math", "math.sqrt"],
        )
    if stage == 2:
        return _problem(
            chapter=7,
            stage=2,
            kc_code="PY43-KC-058",
            difficulty=3,
            title="JSON 解析",
            statement="`import json`；读入一行 JSON 字符串（保证是对象），输出其 `name` 字段。",
            reference_solution="import json\ns = input()\nd = json.loads(s)\nprint(d['name'])",
            test_cases=[
                TestCase('{"name": "Py", "age": 18}', "Py", is_sample=True),
                TestCase('{"name": "Anna"}', "Anna"),
                TestCase('{"id": 1, "name": "Bob"}', "Bob"),
            ],
            hints=["json.loads 把字符串转 dict", "d['key'] 取字段"],
        )
    if stage == 3:
        return _problem(
            chapter=7,
            stage=3,
            kc_code="PY43-KC-060",
            difficulty=4,
            title="异常兜底",
            statement=(
                "读入一行字符串 s：尝试 int(s) 并输出，若 ValueError 输出 `not int`。"
            ),
            reference_solution=(
                "s = input()\ntry:\n    n = int(s)\n    print(n)\n"
                "except ValueError:\n    print('not int')"
            ),
            test_cases=[
                TestCase("42", "42", is_sample=True),
                TestCase("hello", "not int"),
                TestCase("-7", "-7"),
            ],
            hints=["try / except ValueError", "int() 不能转字符串时抛 ValueError"],
        )
    return _problem(
        chapter=7,
        stage=4,
        kc_code="PY43-KC-062",
        difficulty=5,
        title="random 取样（综合）",
        statement=(
            "`import random`；读入两个整数 n 和 seed；先 `random.seed(seed)`，"
            "再用 `random.randint(1, 100)` 取 n 个数；按生成顺序逐行输出。"
        ),
        reference_solution=(
            "import random\nn, seed = map(int, input().split())\nrandom.seed(seed)\n"
            "for _ in range(n):\n    print(random.randint(1, 100))"
        ),
        test_cases=[
            TestCase("3 42", "82\n15\n4", is_sample=True),
            TestCase("1 1", "18"),
            TestCase("2 7", "42\n20"),
        ],
        hints=["random.seed 让结果可复现", "for _ in range(n)"],
    )


_CHAPTER_BUILDERS: dict[int, Callable[[int], BossProblem]] = {
    1: _chapter1,
    2: _chapter2,
    3: _chapter3,
    4: _chapter4,
    5: _chapter5,
    6: _chapter6,
    7: _chapter7,
}


def get_golden_problem(chapter: int, stage: int) -> BossProblem:
    """按章号 1..7、stage 1..4 返回 golden 题；越界 clamp 至第 1 章第 1 关。"""

    c = max(1, min(7, chapter))
    s = max(1, min(4, stage))
    builder = _CHAPTER_BUILDERS.get(c, _chapter1)
    return builder(s)


def all_golden_problems() -> list[BossProblem]:
    """返回全部 28 道 golden 题，供测试与离线审查。"""

    out: list[BossProblem] = []
    for chapter in range(1, 8):
        for stage in range(1, 5):
            out.append(get_golden_problem(chapter, stage))
    return out


def get_golden_problem_for_kc(
    chapter: int, stage: int, kc_code: str, difficulty: int
) -> BossProblem | None:
    """Return a same-KC fallback for AI-only programming topics when generation fails."""
    if chapter_for_kc(kc_code) != chapter:
        return None

    if kc_code == "PY43-KC-018":
        return _problem(
            chapter=chapter,
            stage=stage,
            kc_code=kc_code,
            difficulty=difficulty,
            title="格式化成绩",
            statement="读入姓名和成绩，使用 format() 方法控制字符串格式，输出“姓名: 成绩”，成绩保留 1 位小数。",
            reference_solution='name = input()\nscore = float(input())\nprint("{}: {:.1f}".format(name, score))',
            test_cases=[
                TestCase("林\n86.26", "林: 86.3", is_sample=True),
                TestCase("Mia\n0", "Mia: 0.0"),
                TestCase("张三\n100", "张三: 100.0"),
            ],
            hints=["用 format() 的位置参数填入姓名和成绩", "格式说明符 :.1f 保留一位小数"],
            problem_id=f"golden-c{chapter}-s{stage}-{kc_code}",
        )
    if kc_code == "PY43-KC-023":
        return _problem(
            chapter=chapter,
            stage=stage,
            kc_code=kc_code,
            difficulty=difficulty,
            title="格式化商品信息",
            statement="读入商品名和单价，使用 f-string 进行字符串格式化，输出“商品名:单价”，单价保留 2 位小数。",
            reference_solution=(
                "# 字符串格式化：用 f-string 控制小数位\n"
                'product = input()\nprice = float(input())\nprint(f"{product}:{price:.2f}")'
            ),
            test_cases=[
                TestCase("茶\n12.5", "茶:12.50", is_sample=True),
                TestCase("笔记本\n0", "笔记本:0.00"),
                TestCase("book\n3.456", "book:3.46"),
            ],
            hints=["f-string 用花括号嵌入变量", "格式说明符 :.2f 保留两位小数"],
            problem_id=f"golden-c{chapter}-s{stage}-{kc_code}",
        )
    if kc_code == "PY43-KC-039":
        return _problem(
            chapter=chapter,
            stage=stage,
            kc_code=kc_code,
            difficulty=difficulty,
            title="按随机种子抽取列表元素",
            statement="第一行输入随机种子，第二行输入空格分隔的列表元素。导入 random 模块并从列表随机选取一个元素输出。",
            reference_solution="import random\nseed = int(input())\nitems = input().split()\nrandom.seed(seed)\nprint(random.choice(items))",
            test_cases=[
                TestCase("0\napple pear mango", "pear", is_sample=True),
                TestCase("7\nred blue green", "blue"),
                TestCase("42\na b c", "c"),
            ],
            hints=["用 random.seed() 固定随机序列", "random.choice() 从列表中选取一个元素"],
            problem_id=f"golden-c{chapter}-s{stage}-{kc_code}",
        )
    if kc_code == "PY43-KC-063":
        return _problem(
            chapter=chapter,
            stage=stage,
            kc_code=kc_code,
            difficulty=difficulty,
            title="用模块计算最大公约数",
            statement="通过模块化编程导入 math 模块，读取两个整数，并调用 math.gcd() 输出它们的最大公约数。",
            reference_solution=(
                "# 模块化编程：调用 math 模块的功能\n"
                "import math\na, b = map(int, input().split())\nprint(math.gcd(a, b))"
            ),
            test_cases=[
                TestCase("18 24", "6", is_sample=True),
                TestCase("7 13", "1"),
                TestCase("-12 8", "4"),
            ],
            hints=["先导入 math 模块", "用 math.gcd() 调用模块提供的功能"],
            problem_id=f"golden-c{chapter}-s{stage}-{kc_code}",
        )
    return None
