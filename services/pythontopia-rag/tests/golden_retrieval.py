"""检索黄金集：固定语料 + 标注查询，用于确定性 Recall@K 回归。

语料为 38 页代表性课件 fixture，主题、章节结构取自真实课程底座 packId=43
《Python语言基础》（7 章：计算工具与计算思维 / Python 语言基础(2) / 计算机信息表示及计算 /
流程自动化 / 数据结构 / 数据存取与分析 / 归纳与抽象），页面 id 为 fixture 自编号，
不冒充真实课件页码。查询覆盖概念词 / 代码词 / 易混词 / 口语化问句四类。
"""

from __future__ import annotations

from dataclasses import dataclass

from pythontopia_rag.data.page_source import IndexablePage

_CHAPTERS: dict[int, str] = {
    1: "第一章：计算工具与计算思维",
    2: "第二章：Python 语言基础(2)",
    3: "第三章：计算机信息表示及计算",
    4: "第四章：流程自动化",
    5: "第五章：数据结构",
    6: "第六章：数据存取与分析",
    7: "第七章：归纳与抽象",
}


def _page(
    page_id: int, chapter_no: int, page_no: int, title: str, text: str
) -> IndexablePage:
    return IndexablePage(
        page_id=page_id,
        page_no=page_no,
        document_id=chapter_no,
        chapter_id=chapter_no,
        kc_codes=[f"PY43-KC-{page_id:03d}"],
        title=title,
        text=text,
        chapter_no=chapter_no,
        chapter_title=_CHAPTERS[chapter_no],
    )


def fixture_pages() -> list[IndexablePage]:
    return [
        _page(
            101, 1, 5, "计算思维概述",
            "计算思维是运用计算机科学的基础概念求解问题、设计系统的思维方式。"
            "抽象和自动化是计算思维的两大核心。我们把问题分解成可以由计算机一步步执行的步骤，"
            "这个过程称为算法设计。",
        ),
        _page(
            102, 1, 12, "Python语言简介",
            "Python 是一种解释型高级编程语言，语法简洁，拥有丰富的第三方库，"
            "广泛用于数据分析、人工智能与自动化脚本。Python 程序由解释器逐行执行，"
            "不需要先编译成机器码。",
        ),
        _page(
            103, 1, 18, "第一个Python程序",
            '在 IDLE 中输入 print("Hello, World!") 并运行，屏幕会输出 Hello, World!。'
            "print() 是 Python 内置的输出函数，把括号中的内容显示到屏幕上。",
        ),
        _page(
            104, 2, 8, "变量与赋值",
            "变量是给数据起的名字，用赋值号 = 把右边的值存入左边的变量，例如 x = 10。"
            "注意单个等号 = 表示赋值，两个等号 == 才是比较两个值是否相等的比较运算符，"
            "初学者常把 = 和 == 混淆。",
        ),
        _page(
            105, 2, 15, "标识符与关键字",
            "标识符由字母、数字和下划线组成，不能以数字开头，区分大小写。"
            "for、while、def、if 这些关键字有特殊含义，不能用作变量名。",
        ),
        _page(
            106, 2, 22, "基本数据类型",
            "Python 常用数据类型包括整数 int、浮点数 float、布尔值 bool 与字符串 str。"
            "布尔值只有 True 和 False 两个取值。type() 函数可以查看一个值的类型。",
        ),
        _page(
            107, 2, 31, "input()函数",
            'input() 函数从键盘读取用户输入的一行字符串，例如 name = input("请输入姓名:")。'
            "input() 返回的总是字符串类型，需要数字时要配合 int() 或 float() 转换。",
        ),
        _page(
            108, 2, 33, "类型转换",
            "int()、float()、str() 可以在数字与字符串之间转换类型，"
            '例如 age = int(input("请输入年龄:")) 把键盘输入的字符串转换成整数，'
            "str(95) 把数字转换成字符串。eval() 函数可以直接求出字符串表达式的值。",
        ),
        _page(
            109, 2, 40, "print()函数与输出",
            "print() 函数把内容输出到屏幕，可以一次输出多个值，用 sep 指定分隔符，"
            '用 end 指定结尾字符。例如 print("成绩", 95) 会同时输出文字和变量的值。',
        ),
        _page(
            110, 2, 45, "字符串格式化",
            "字符串格式化可以用 format() 方法或 f-string。"
            'f"平均分是{avg:.2f}" 会把变量 avg 保留两位小数嵌入字符串，'
            "format() 方法用 {} 作为占位符填充要输出的值。",
        ),
        _page(
            111, 2, 52, "注释与缩进",
            "以 # 开头的内容是注释，解释器会忽略，注释用来解释代码的意图。"
            "Python 用缩进表示代码块层次，同一代码块必须保持相同的缩进，通常是 4 个空格。",
        ),
        _page(
            112, 2, 58, "运算符与表达式",
            "算术运算符包括 + - * / // % **。比较运算符如 ==、!=、>、< 比较两个值并返回布尔值。"
            "逻辑运算符 and、or、not 用于组合多个条件。",
        ),
        _page(
            113, 3, 10, "进制与进制转换",
            "计算机内部用二进制表示信息。bin()、oct()、hex() 函数把整数转换成二进制、"
            '八进制、十六进制字符串，int("1010", 2) 把二进制字符串转换回十进制，'
            "这就是常见的进制转换方法。",
        ),
        _page(
            114, 3, 24, "浮点数与精度",
            "浮点数在计算机中是近似存储的，0.1 + 0.2 并不精确等于 0.3。"
            "round() 函数可以对计算结果四舍五入保留指定位数，例如 round(3.14159, 2)。",
        ),
        _page(
            115, 4, 12, "条件判断",
            "if 语句让程序根据条件做选择：条件为真执行 if 分支，否则进入 elif 或 else 分支。"
            "例如根据成绩判断等级就用 if-elif-else 多分支结构。",
        ),
        _page(
            116, 4, 20, "遍历循环: for语句",
            "for 循环用于遍历序列中的每个元素，让程序重复执行一段代码。"
            "例如 for i in range(10): 会把循环体重复执行 10 次，"
            "range(1, 101) 生成 1 到 100 的整数序列。",
        ),
        _page(
            117, 4, 27, "无限循环: while语句",
            "while 循环在条件为真时反复执行循环体，适合循环次数事先未知的场景；"
            "for 循环适合已知次数或遍历序列，这是 while 和 for 的主要区别。"
            "while True 配合 break 可以实现交互式重复输入。",
        ),
        _page(
            118, 4, 33, "break与continue",
            "break 立即结束整个循环，continue 跳过本次迭代直接进入下一次循环，"
            "这是 break 和 continue 的区别。两者都只影响最内层循环。",
        ),
        _page(
            119, 4, 38, "循环嵌套与死循环",
            "循环里面再套循环称为嵌套循环，常用于打印九九乘法表。"
            "while 条件永远为真且没有 break 时会形成死循环，程序无法结束。",
        ),
        _page(
            120, 4, 46, "异常处理",
            "程序运行出错会抛出异常，用 try-except 语句捕获错误并给出友好提示，"
            "例如 except ValueError 处理用户输入不是数字的情况，避免程序崩溃。",
        ),
        _page(
            121, 5, 8, "列表list基础",
            "列表 list 是可变的有序序列，用方括号创建，例如 scores = [90, 85, 77]。"
            "append() 方法在列表末尾添加元素，pop() 取出并删除元素，insert() 在指定位置插入。",
        ),
        _page(
            122, 5, 14, "列表切片与遍历",
            "通过下标访问列表元素，下标从 0 开始；切片 scores[1:3] 取出子列表。"
            "用 for 循环可以遍历列表的每个元素。len() 函数返回列表的元素个数，"
            "统计列表里有多少个元素就用 len()。",
        ),
        _page(
            123, 5, 21, "元组tuple",
            "元组 tuple 用圆括号创建，是不可变序列；列表可以修改，"
            "而元组创建后不能增删改元素，这是列表和元组的主要区别。"
            "只有一个元素的元组要写成 (x,)。",
        ),
        _page(
            124, 5, 28, "字典dict",
            '字典 dict 用键值对存储数据，例如 student = {"name": "李雷", "age": 19}。'
            "通过键访问对应的值，get() 方法在键不存在时返回默认值而不报错，"
            "setdefault() 可以同时设置默认值。",
        ),
        _page(
            125, 5, 35, "集合set与去重",
            "集合 set 中的元素不重复。把列表转换成集合 set(lst) 可以快速去掉列表里重复的元素，"
            "再用 list() 转回列表，这是最常用的去重方法。",
        ),
        _page(
            126, 5, 41, "列表排序",
            "sort() 方法在原列表上就地排序，sorted() 函数返回新的有序列表而不改变原列表，"
            "这是 sort 和 sorted 的区别。reverse=True 表示降序排列。",
        ),
        _page(
            127, 6, 9, "文件打开与写入",
            'open() 函数打开文件，模式 "r" 表示读取、"w" 表示写入、"a" 表示追加。'
            "write() 方法把字符串写入文件，把数据保存到文件后要调用 close() 关闭文件。",
        ),
        _page(
            128, 6, 15, "文件读取",
            "read() 一次读取整个文件内容，readline() 读取一行，"
            "readlines() 返回包含所有行的列表。逐行处理大文件时可以直接 for line in f 遍历文件对象。",
        ),
        _page(
            129, 6, 22, "with语句",
            'with open("data.txt") as f: 语句块结束后会自动关闭文件，'
            "即使发生异常也能保证文件被关闭，比手动调用 close() 更安全。",
        ),
        _page(
            130, 6, 30, "字符串分割与拼接",
            "split() 方法按分隔符把字符串拆分成列表，常用于解析 CSV 数据行；"
            'join() 方法把列表中的字符串用指定分隔符拼接成一个字符串，例如 ",".join(words)。',
        ),
        _page(
            131, 7, 7, "函数定义与调用",
            "函数是组织好的、可重复使用的代码块。用 def 关键字定义函数，"
            "例如 def greet(name): 然后通过函数名加括号调用它。"
            "定义一个自己的函数可以让程序结构清晰、避免重复代码。",
        ),
        _page(
            132, 7, 14, "形参与实参",
            "函数定义时括号里的变量是形参（形式参数），调用函数时实际传入的值是实参（实际参数）。"
            "形参是占位符，实参是真正参与运算的数据，这就是参数与实参的区别。",
        ),
        _page(
            133, 7, 20, "默认参数与关键字参数",
            "定义函数时可以给形参设置默认值，例如 def power(x, n=2)，"
            "调用时不传 n 就使用默认参数 2。用关键字参数 power(x=3) 可以不按位置传参。",
        ),
        _page(
            134, 7, 26, "返回值return",
            "return 语句把结果返回给调用者并结束函数执行；没有 return 的函数返回 None。"
            "一个函数可以用元组一次返回多个返回值。",
        ),
        _page(
            135, 7, 33, "变量作用域",
            "函数内部定义的是局部变量，函数外部定义的是全局变量。"
            "在函数内修改全局变量需要 global 声明，作用域决定变量在哪里可见。",
        ),
        _page(
            136, 7, 40, "递归函数",
            "函数直接或间接调用自身称为递归，递归必须有终止条件。"
            "例如阶乘 factorial(n) = n * factorial(n-1)，当 n == 1 时返回 1，否则会无限递归。",
        ),
        _page(
            137, 7, 46, "模块导入import",
            "用 import 语句导入模块，例如 import math 之后调用 math.sqrt()；"
            "from random import randint 只导入指定函数。模块让代码可以复用他人的成果。",
        ),
        _page(
            138, 7, 52, "内置函数",
            "Python 提供大量内置函数：len() 求长度、max() 与 min() 求最值、"
            "sum() 求和、abs() 求绝对值，这些函数无需导入即可直接使用。",
        ),
    ]


@dataclass(frozen=True)
class GoldenCase:
    query: str
    expected_page_ids: frozenset[int]
    category: str


def _case(query: str, pages: set[int], category: str) -> GoldenCase:
    return GoldenCase(query=query, expected_page_ids=frozenset(pages), category=category)


GOLDEN_CASES: list[GoldenCase] = [
    # ---- 概念词 ----
    _case("变量", {104, 135}, "concept"),
    _case("列表", {121, 122, 126}, "concept"),
    _case("字典", {124}, "concept"),
    _case("元组", {123, 134}, "concept"),
    _case("集合", {125}, "concept"),
    _case("循环", {116, 117, 119}, "concept"),
    _case("函数", {131, 132, 133, 134, 138}, "concept"),
    _case("字符串格式化", {110}, "concept"),
    _case("注释", {111}, "concept"),
    _case("缩进", {111}, "concept"),
    _case("条件判断", {115}, "concept"),
    _case("类型转换", {106, 108, 113}, "concept"),
    _case("切片", {122}, "concept"),
    _case("异常处理", {120, 129}, "concept"),
    _case("文件读取", {127, 128, 129}, "concept"),
    _case("递归", {136}, "concept"),
    _case("作用域", {135}, "concept"),
    _case("默认参数", {133}, "concept"),
    _case("返回值", {134}, "concept"),
    _case("进制转换", {113}, "concept"),
    _case("布尔值", {106, 112}, "concept"),
    _case("数据类型", {106}, "concept"),
    # ---- 代码词 ----
    _case("print", {103, 109}, "code"),
    _case("input", {107, 108}, "code"),
    _case("for", {116, 117, 122}, "code"),
    _case("while", {117, 119}, "code"),
    _case("def", {131, 133}, "code"),
    _case("len", {122, 138}, "code"),
    _case("range", {116}, "code"),
    _case("append", {121}, "code"),
    _case("pop", {121}, "code"),
    _case("get", {124}, "code"),
    _case("open", {127, 129}, "code"),
    _case("return", {134}, "code"),
    _case("break", {117, 118, 119}, "code"),
    _case("continue", {118}, "code"),
    _case("import", {137}, "code"),
    _case("sort", {126}, "code"),
    _case("split", {130}, "code"),
    _case("format", {110}, "code"),
    _case("eval", {108}, "code"),
    _case("round", {114}, "code"),
    _case("f-string", {110}, "code"),
    # ---- 易混词 ----
    _case("参数和实参的区别", {132}, "confusable"),
    _case("while和for循环的区别", {117}, "confusable"),
    _case("列表和元组的区别", {123}, "confusable"),
    _case("=和==有什么区别", {104, 112}, "confusable"),
    _case("break和continue的区别", {118}, "confusable"),
    _case("sort和sorted的区别", {126}, "confusable"),
    # ---- 口语化问句 ----
    _case("怎么让程序重复做一件事", {116, 117}, "colloquial"),
    _case("怎么从键盘读取用户输入的内容", {107, 108}, "colloquial"),
    _case("如何把数字转换成字符串", {108}, "colloquial"),
    _case("程序怎么根据条件做出选择", {115}, "colloquial"),
    _case("怎么把数据保存到文件里", {127, 129}, "colloquial"),
    _case("如何统计列表里有多少个元素", {122, 138}, "colloquial"),
    _case("怎么去掉列表里重复的元素", {125}, "colloquial"),
    _case("如何定义一个自己的函数", {131}, "colloquial"),
    _case("用户输入不是数字导致程序出错怎么办", {120}, "colloquial"),
    _case("怎么同时输出文字和变量的值", {109, 110}, "colloquial"),
]
