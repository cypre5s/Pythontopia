-- Purpose: R-14 主线「练」题库 seed —— 96 道客观题入 mainline_practice_question；
--   新建并 seed OJ 覆盖层 mainline_oj_catalog（39 道编程题统一编号/命名/KC/归属 stage，safe 模式不改 course_problem.id）；
--   回填 mainline_stage.example_problem_ids 与 required_example_count（按 stage 分到的 OJ 题）。
--   客观题为【通识 Python 题】，source_label 标注「通识题（非课件原题）」，不引用课件页码/章节（AGENTS.md 防幻觉约束）。
-- Tables affected:
--   * mainline_practice_question  —— INSERT 96 行（24 stage × 4：CHOICE,CHOICE,JUDGE,FILL）。
--   * mainline_oj_catalog         —— 新建表 + seed 39 行（覆盖 packId=43 全部 39 道 OJ，每题恰好归一个 stage）。
--   * mainline_stage              —— UPDATE 24 行的 example_problem_ids / required_example_count（按 chapter+stage_index 匹配）。
-- Irreversible risk:
--   * drop mainline_oj_catalog 丢失 OJ 编号/命名/归属映射（seed 表，可重跑本迁移恢复）。
--   * 重复执行本 seed 会因 UNIQUE 约束 (chapter,stage_index,ordinal) / 主键冲突而失败（设计为一次性 seed）。
--   * 不改动 course_problem，OJ 判题/题面不受影响。

-- ===========================================================================
-- 1) 客观题题库：96 道（每 stage 4 道，ordinal 1=CHOICE 2=CHOICE 3=JUDGE 4=FILL）
-- ===========================================================================
INSERT INTO mainline_practice_question
  (chapter, stage_index, ordinal, kc_code, type, stem, options_json, answer_json, blank_count, explanation, source_label)
VALUES
  -- 第1章 第1节 计算思维的概念与方法
  (1, 1, 1, 'PY43-KC-004', 'CHOICE', '计算思维（Computational Thinking）这一概念的经典定义是由哪位学者提出的？', '["周以真（Jeannette M. Wing）", "艾伦·图灵", "冯·诺依曼", "高德纳"]'::jsonb, '["周以真（Jeannette M. Wing）"]'::jsonb, 0, '计算思维的经典定义由周以真教授于 2006 年提出，其本质是抽象与自动化。', '通识题（非课件原题）'),
  (1, 1, 2, 'PY43-KC-004', 'CHOICE', '下列对计算思维本质的概括，最准确的是？', '["抽象与自动化", "记忆与背诵", "上网与办公", "复制与粘贴"]'::jsonb, '["抽象与自动化"]'::jsonb, 0, '计算思维的本质可概括为抽象（abstraction）与自动化（automation）。', '通识题（非课件原题）'),
  (1, 1, 3, 'PY43-KC-003', 'JUDGE', '计算思维求解问题的一般流程可概括为：发现问题→抽象建模→算法设计→程序实现→结果评价。', '[]'::jsonb, '["正确"]'::jsonb, 0, '这正是计算思维解决问题的典型操作流程。', '通识题（非课件原题）'),
  (1, 1, 4, 'PY43-KC-004', 'FILL', '计算思维的本质可以概括为抽象和______两个关键词。', '[]'::jsonb, '["自动化"]'::jsonb, 1, '计算思维本质 = 抽象 + 自动化。', '通识题（非课件原题）'),
  -- 第1章 第2节 计算与计算科学
  (1, 2, 1, 'PY43-KC-006', 'CHOICE', '关于“计算”的本质，下列说法最准确的是？', '["计算的本质是基于规则的符号串变换", "计算只能由计算机完成", "计算就是上网查资料", "计算等同于做四则运算"]'::jsonb, '["计算的本质是基于规则的符号串变换"]'::jsonb, 0, '计算的本质是按照确定规则进行的符号串变换或物理状态变换。', '通识题（非课件原题）'),
  (1, 2, 2, 'PY43-KC-007', 'CHOICE', '下列哪一项属于计算科学的基本特征？', '["自动化", "随机化", "主观化", "不可重复"]'::jsonb, '["自动化"]'::jsonb, 0, '计算科学的四大特征为：器械化、形式化、自动化、泛在化。', '通识题（非课件原题）'),
  (1, 2, 3, 'PY43-KC-007', 'JUDGE', '计算思维、理论思维和实验思维并称为三大科学思维。', '[]'::jsonb, '["正确"]'::jsonb, 0, '计算思维与理论思维、实验思维并列为三大科学思维。', '通识题（非课件原题）'),
  (1, 2, 4, 'PY43-KC-007', 'FILL', '计算科学的四大特征包括器械化、形式化、泛在化和______。', '[]'::jsonb, '["自动化"]'::jsonb, 1, '四大特征：器械化、形式化、自动化、泛在化。', '通识题（非课件原题）'),
  -- 第1章 第3节 计算工具与计算机发展
  (1, 3, 1, 'PY43-KC-002', 'CHOICE', '下列哪一项被公认为世界上第一台通用电子数字计算机？', '["ENIAC", "算盘", "差分机", "穿孔制表机"]'::jsonb, '["ENIAC"]'::jsonb, 0, 'ENIAC（1946 年）是世界上第一台通用电子数字计算机；算盘、差分机等属于更早的机械/手动工具。', '通识题（非课件原题）'),
  (1, 3, 2, 'PY43-KC-005', 'CHOICE', '下列哪一项不属于计算机的发展趋势？', '["单机化", "巨型化", "微型化", "网络化"]'::jsonb, '["单机化"]'::jsonb, 0, '计算机发展趋势为巨型化、微型化、网络化、智能化、多媒体化；“单机化”与网络化趋势相反。', '通识题（非课件原题）'),
  (1, 3, 3, 'PY43-KC-001', 'JUDGE', '神威·太湖之光是中国自主研制的超级计算机。', '[]'::jsonb, '["正确"]'::jsonb, 0, '神威·太湖之光是我国研制的超级计算机，体现了中国在计算机领域的成就。', '通识题（非课件原题）'),
  (1, 3, 4, 'PY43-KC-002', 'FILL', '中国古代广泛使用、至今仍在沿用的一种手动计算工具是______。', '[]'::jsonb, '["算盘"]'::jsonb, 1, '算盘是典型的手动式计算工具。', '通识题（非课件原题）'),
  -- 第2章 第1节 Python 与程序语言入门
  (2, 1, 1, 'PY43-KC-009', 'CHOICE', 'Python 语言的创始人是？', '["Guido van Rossum", "James Gosling", "Dennis Ritchie", "Bjarne Stroustrup"]'::jsonb, '["Guido van Rossum"]'::jsonb, 0, 'Python 由 Guido van Rossum 创造；Gosling/Ritchie/Stroustrup 分别与 Java/C/C++ 相关。', '通识题（非课件原题）'),
  (2, 1, 2, 'PY43-KC-015', 'CHOICE', '下列关于程序设计语言的说法，正确的是？', '["Python 是解释型语言", "Python 是编译型语言", "机器语言是高级语言", "汇编语言不需要翻译即可执行"]'::jsonb, '["Python 是解释型语言"]'::jsonb, 0, 'Python 程序由解释器逐行解释执行，属于解释型语言。', '通识题（非课件原题）'),
  (2, 1, 3, 'PY43-KC-008', 'JUDGE', '在 Python 中可以使用 print() 函数把内容输出到屏幕。', '[]'::jsonb, '["正确"]'::jsonb, 0, 'print() 是 Python 的标准输出内置函数。', '通识题（非课件原题）'),
  (2, 1, 4, 'PY43-KC-008', 'FILL', '在 Python 中，用于把信息输出到控制台的内置函数名是______。', '[]'::jsonb, '["print"]'::jsonb, 1, 'print() 用于控制台输出。', '通识题（非课件原题）'),
  -- 第2章 第2节 变量、类型与运算
  (2, 2, 1, 'PY43-KC-011', 'CHOICE', '在 Python 中，表达式 type(3.14) 的结果是？', '["<class ''float''>", "<class ''int''>", "<class ''str''>", "<class ''bool''>"]'::jsonb, '["<class ''float''>"]'::jsonb, 0, '3.14 是带小数点的浮点数，类型为 float。', '通识题（非课件原题）'),
  (2, 2, 2, 'PY43-KC-010', 'CHOICE', '在 Python 中，运算符 // 的作用是？', '["整除（向下取整）", "普通除法", "求幂", "求余数"]'::jsonb, '["整除（向下取整）"]'::jsonb, 0, '// 是整除运算符，结果向下取整；/ 是普通除法，** 求幂，% 求余。', '通识题（非课件原题）'),
  (2, 2, 3, 'PY43-KC-010', 'JUDGE', '在 Python 中，变量名（标识符）可以以数字开头。', '[]'::jsonb, '["错误"]'::jsonb, 0, '标识符只能以字母或下划线开头，不能以数字开头。', '通识题（非课件原题）'),
  (2, 2, 4, 'PY43-KC-010', 'FILL', '在 Python 中，用于求两个整数相除余数的运算符是______。', '[]'::jsonb, '["%"]'::jsonb, 1, '% 是取模（求余）运算符。', '通识题（非课件原题）'),
  -- 第2章 第3节 输入输出与运行原理
  (2, 3, 1, 'PY43-KC-013', 'CHOICE', '在 Python 中，input() 函数的返回值类型是？', '["字符串(str)", "整数(int)", "浮点数(float)", "布尔值(bool)"]'::jsonb, '["字符串(str)"]'::jsonb, 0, 'input() 始终返回字符串，需要时要再做类型转换。', '通识题（非课件原题）'),
  (2, 3, 2, 'PY43-KC-013', 'CHOICE', '要把字符串 "100" 转换为整数 100，应使用下列哪个表达式？', '["int(\"100\")", "str(100)", "float(\"100\")", "input(\"100\")"]'::jsonb, '["int(\"100\")"]'::jsonb, 0, 'int() 将字符串转换为整数；str() 转字符串，float() 转浮点数。', '通识题（非课件原题）'),
  (2, 3, 3, 'PY43-KC-016', 'JUDGE', '程序的执行过程通常可以概括为：输入数据、处理数据、输出数据三个环节。', '[]'::jsonb, '["正确"]'::jsonb, 0, '这是程序运行原理的基本概括。', '通识题（非课件原题）'),
  (2, 3, 4, 'PY43-KC-012', 'FILL', '在 Python 中，从控制台读取用户输入的内置函数名是______。', '[]'::jsonb, '["input"]'::jsonb, 1, 'input() 用于读取控制台输入。', '通识题（非课件原题）'),
  -- 第3章 第1节 进制与二进制运算
  (3, 1, 1, 'PY43-KC-030', 'CHOICE', '十进制数 10 转换为二进制是？', '["1010", "1000", "1100", "1001"]'::jsonb, '["1010"]'::jsonb, 0, '10 = 8 + 2 = 1010(2)。', '通识题（非课件原题）'),
  (3, 1, 2, 'PY43-KC-029', 'CHOICE', '二进制采用的基数（进位基数）是？', '["2", "8", "10", "16"]'::jsonb, '["2"]'::jsonb, 0, '二进制逢二进一，基数为 2。', '通识题（非课件原题）'),
  (3, 1, 3, 'PY43-KC-021', 'JUDGE', '二进制加法运算遵循“逢二进一”的规则。', '[]'::jsonb, '["正确"]'::jsonb, 0, '二进制加法逢二进一，与十进制逢十进一类比。', '通识题（非课件原题）'),
  (3, 1, 4, 'PY43-KC-030', 'FILL', '二进制数 111 转换为十进制数等于______。', '[]'::jsonb, '["7"]'::jsonb, 1, '111(2) = 4 + 2 + 1 = 7。', '通识题（非课件原题）'),
  -- 第3章 第2节 信息编码
  (3, 2, 1, 'PY43-KC-020', 'CHOICE', '在 Python 中，表达式 ord(''A'') 的返回值是？', '["65", "97", "66", "1"]'::jsonb, '["65"]'::jsonb, 0, '大写字母 ''A'' 的 Unicode/ASCII 码位是 65。', '通识题（非课件原题）'),
  (3, 2, 2, 'PY43-KC-020', 'CHOICE', '在 Python 中，要把整数 97 转换为对应的字符，应使用？', '["chr(97)", "ord(97)", "str(97)", "int(97)"]'::jsonb, '["chr(97)"]'::jsonb, 0, 'chr() 由码位得到字符（chr(97) 为 ''a''）；ord() 与之相反。', '通识题（非课件原题）'),
  (3, 2, 3, 'PY43-KC-026', 'JUDGE', 'ASCII 编码主要用于表示西文字符（如英文字母、数字和常见符号）。', '[]'::jsonb, '["正确"]'::jsonb, 0, 'ASCII 是最基础的西文字符编码。', '通识题（非课件原题）'),
  (3, 2, 4, 'PY43-KC-020', 'FILL', '在 Python 中，返回单个字符 Unicode 码位的内置函数名是______。', '[]'::jsonb, '["ord"]'::jsonb, 1, 'ord() 返回字符的 Unicode 码位。', '通识题（非课件原题）'),
  -- 第3章 第3节 数值类型与运算
  (3, 3, 1, 'PY43-KC-019', 'CHOICE', '在 Python 中，表达式 2 ** 3 的结果是？', '["8", "6", "9", "5"]'::jsonb, '["8"]'::jsonb, 0, '** 是求幂运算符，2 的 3 次方等于 8。', '通识题（非课件原题）'),
  (3, 3, 2, 'PY43-KC-025', 'CHOICE', '要提取整数 n 的个位数字，正确的表达式是？', '["n % 10", "n // 10", "n % 100", "n / 10"]'::jsonb, '["n % 10"]'::jsonb, 0, '对 10 取模即得个位数字。', '通识题（非课件原题）'),
  (3, 3, 3, 'PY43-KC-028', 'JUDGE', '在 Python 中，可以使用语句 import random 来导入 random 标准库模块。', '[]'::jsonb, '["正确"]'::jsonb, 0, 'import 模块名 是导入模块的常用方式。', '通识题（非课件原题）'),
  (3, 3, 4, 'PY43-KC-025', 'FILL', '表达式 1234 // 100 % 10 的计算结果是______。', '[]'::jsonb, '["2"]'::jsonb, 1, '1234 // 100 = 12，12 % 10 = 2（提取百位数字）。', '通识题（非课件原题）'),
  -- 第3章 第4节 字符串处理与格式化
  (3, 4, 1, 'PY43-KC-024', 'CHOICE', '已知 s = "Python"，则 s[0] 的值是？', '["''P''", "''n''", "''y''", "''Python''"]'::jsonb, '["''P''"]'::jsonb, 0, '字符串索引从 0 开始，s[0] 是首字符 ''P''。', '通识题（非课件原题）'),
  (3, 4, 2, 'PY43-KC-024', 'CHOICE', '已知 s = "Python"，则切片 s[1:4] 的值是？', '["''yth''", "''Pyt''", "''tho''", "''hon''"]'::jsonb, '["''yth''"]'::jsonb, 0, '切片左闭右开，取下标 1、2、3，即 ''yth''。', '通识题（非课件原题）'),
  (3, 4, 3, 'PY43-KC-022', 'JUDGE', '字符串的 strip() 方法可以去除字符串首尾的空白字符。', '[]'::jsonb, '["正确"]'::jsonb, 0, 'strip() 去除首尾空白（含空格、换行、制表符）。', '通识题（非课件原题）'),
  (3, 4, 4, 'PY43-KC-022', 'FILL', '可以按指定分隔符把字符串切分成列表的字符串方法名是______。', '[]'::jsonb, '["split"]'::jsonb, 1, 'split() 按分隔符切分字符串为列表。', '通识题（非课件原题）'),
  -- 第4章 第1节 顺序与分支结构
  (4, 1, 1, 'PY43-KC-033', 'CHOICE', '在 Python 中，实现多分支选择通常使用的关键字组合是？', '["if-elif-else", "for-in", "while-break", "try-except"]'::jsonb, '["if-elif-else"]'::jsonb, 0, 'if-elif-else 用于多分支条件选择。', '通识题（非课件原题）'),
  (4, 1, 2, 'PY43-KC-035', 'CHOICE', '表达式 (3 > 2) and (1 > 5) 的值是？', '["False", "True", "1", "0"]'::jsonb, '["False"]'::jsonb, 0, 'and 要求两侧都为真；1 > 5 为 False，故整体为 False。', '通识题（非课件原题）'),
  (4, 1, 3, 'PY43-KC-038', 'JUDGE', '顺序结构的程序自上而下依次执行每一条语句。', '[]'::jsonb, '["正确"]'::jsonb, 0, '顺序结构即按书写顺序逐条执行。', '通识题（非课件原题）'),
  (4, 1, 4, 'PY43-KC-033', 'FILL', '在 Python 分支结构中，表示“否则如果”的关键字是______。', '[]'::jsonb, '["elif"]'::jsonb, 1, 'elif 是 else if 的缩写，用于追加分支条件。', '通识题（非课件原题）'),
  -- 第4章 第2节 循环结构
  (4, 2, 1, 'PY43-KC-031', 'CHOICE', '在 Python 中，range(1, 5) 生成的整数序列是？', '["1, 2, 3, 4", "1, 2, 3, 4, 5", "0, 1, 2, 3, 4", "2, 3, 4, 5"]'::jsonb, '["1, 2, 3, 4"]'::jsonb, 0, 'range 区间左闭右开，包含 1 不包含 5。', '通识题（非课件原题）'),
  (4, 2, 2, 'PY43-KC-037', 'CHOICE', '在循环中，用于立即结束（跳出）整个循环的语句是？', '["break", "continue", "pass", "return"]'::jsonb, '["break"]'::jsonb, 0, 'break 跳出整个循环；continue 只结束本次迭代。', '通识题（非课件原题）'),
  (4, 2, 3, 'PY43-KC-037', 'JUDGE', 'continue 语句的作用是跳过本次循环的剩余语句，直接进入下一次循环。', '[]'::jsonb, '["正确"]'::jsonb, 0, 'continue 结束本次迭代并继续下一次。', '通识题（非课件原题）'),
  (4, 2, 4, 'PY43-KC-031', 'FILL', '要重复执行确定次数的遍历循环，常与 range() 配合使用的循环关键字是______。', '[]'::jsonb, '["for"]'::jsonb, 1, 'for 循环常与 range() 配合实现确定次数循环。', '通识题（非课件原题）'),
  -- 第4章 第3节 嵌套结构
  (4, 3, 1, 'PY43-KC-036', 'CHOICE', '执行双重循环：外层 for i in range(3) 内嵌 for j in range(2)，循环体里的 print 语句一共会执行多少次？', '["6 次", "5 次", "3 次", "2 次"]'::jsonb, '["6 次"]'::jsonb, 0, '外层 3 次 × 内层 2 次 = 6 次。', '通识题（非课件原题）'),
  (4, 3, 2, 'PY43-KC-034', 'CHOICE', '关于分支语句嵌套，下列说法正确的是？', '["一个 if 语句的内部可以再包含 if 语句", "if 语句内部不允许再写 if 语句", "嵌套分支最多只能有两层", "嵌套分支可以不用缩进"]'::jsonb, '["一个 if 语句的内部可以再包含 if 语句"]'::jsonb, 0, '分支可以嵌套，内层语句需要相应缩进。', '通识题（非课件原题）'),
  (4, 3, 3, 'PY43-KC-036', 'JUDGE', '在循环嵌套中，外层循环每执行一次，内层循环都要完整执行一遍。', '[]'::jsonb, '["正确"]'::jsonb, 0, '这是循环嵌套的执行规律。', '通识题（非课件原题）'),
  (4, 3, 4, 'PY43-KC-036', 'FILL', '打印九九乘法表通常需要使用______层循环（请填阿拉伯数字）。', '[]'::jsonb, '["2"]'::jsonb, 1, '九九乘法表需要双层（2 层）循环：外层控制行，内层控制列。', '通识题（非课件原题）'),
  -- 第5章 第1节 序列与列表
  (5, 1, 1, 'PY43-KC-045', 'CHOICE', '下列属于 Python 可变（mutable）序列类型的是？', '["列表(list)", "字符串(str)", "元组(tuple)", "整数(int)"]'::jsonb, '["列表(list)"]'::jsonb, 0, '列表可变；字符串和元组是不可变序列，整数不是序列。', '通识题（非课件原题）'),
  (5, 1, 2, 'PY43-KC-040', 'CHOICE', '向列表末尾添加一个元素，应使用的方法是？', '["append()", "remove()", "pop()", "sort()"]'::jsonb, '["append()"]'::jsonb, 0, 'append() 在列表末尾追加元素；remove/pop 删除元素，sort 排序。', '通识题（非课件原题）'),
  (5, 1, 3, 'PY43-KC-044', 'JUDGE', '在 Python 中，len() 函数可以返回列表中元素的个数。', '[]'::jsonb, '["正确"]'::jsonb, 0, 'len() 返回序列长度（元素个数）。', '通识题（非课件原题）'),
  (5, 1, 4, 'PY43-KC-040', 'FILL', '已知 lst = [1, 2, 3]，执行 lst.append(4) 后，列表 lst 的长度是______。', '[]'::jsonb, '["4"]'::jsonb, 1, '追加一个元素后元素个数由 3 变为 4。', '通识题（非课件原题）'),
  -- 第5章 第2节 字典
  (5, 2, 1, 'PY43-KC-043', 'CHOICE', '在 Python 中，创建字典使用的定界符是？', '["花括号 {}", "方括号 []", "圆括号 ()", "尖括号 <>"]'::jsonb, '["花括号 {}"]'::jsonb, 0, '字典用花括号 {} 表示，元素为 键:值 对。', '通识题（非课件原题）'),
  (5, 2, 2, 'PY43-KC-042', 'CHOICE', '已知 d = {"a": 1, "b": 2}，则 d["b"] 的值是？', '["2", "1", "''b''", "报错(KeyError)"]'::jsonb, '["2"]'::jsonb, 0, '通过键 ''b'' 访问，得到对应的值 2。', '通识题（非课件原题）'),
  (5, 2, 3, 'PY43-KC-043', 'JUDGE', '字典中的键（key）必须唯一，不能重复。', '[]'::jsonb, '["正确"]'::jsonb, 0, '字典键唯一，重复赋值会覆盖旧值。', '通识题（非课件原题）'),
  (5, 2, 4, 'PY43-KC-042', 'FILL', '判断键 k 是否存在于字典 d 中，可写成表达式 k ______ d。', '[]'::jsonb, '["in"]'::jsonb, 1, '用成员运算符 in 判断键是否存在于字典中。', '通识题（非课件原题）'),
  -- 第5章 第3节 集合
  (5, 3, 1, 'PY43-KC-047', 'CHOICE', '下列关于 Python 集合(set)特点的描述，正确的是？', '["元素无序且唯一", "元素有序且可重复", "元素有序且唯一", "元素无序且可重复"]'::jsonb, '["元素无序且唯一"]'::jsonb, 0, '集合的两大特性：无序、元素唯一（自动去重）。', '通识题（非课件原题）'),
  (5, 3, 2, 'PY43-KC-047', 'CHOICE', '求集合 a 与 b 的交集，应使用的运算符是？', '["a & b", "a | b", "a - b", "a ^ b"]'::jsonb, '["a & b"]'::jsonb, 0, '& 求交集，| 求并集，- 求差集，^ 求对称差集。', '通识题（非课件原题）'),
  (5, 3, 3, 'PY43-KC-047', 'JUDGE', '把一个含重复元素的列表转换成集合后，重复的元素会被自动去除。', '[]'::jsonb, '["正确"]'::jsonb, 0, '集合元素唯一，可用于去重。', '通识题（非课件原题）'),
  (5, 3, 4, 'PY43-KC-046', 'FILL', '向集合 s 中添加一个元素 x，使用的方法是 s.______(x)。', '[]'::jsonb, '["add"]'::jsonb, 1, 'add() 向集合添加单个元素。', '通识题（非课件原题）'),
  -- 第6章 第1节 文件系统与文件操作
  (6, 1, 1, 'PY43-KC-048', 'CHOICE', '在 Python 中，打开文件使用的内置函数是？', '["open()", "file()", "read()", "load()"]'::jsonb, '["open()"]'::jsonb, 0, 'open() 用于打开文件并返回文件对象。', '通识题（非课件原题）'),
  (6, 1, 2, 'PY43-KC-048', 'CHOICE', '以“只读”方式打开文本文件时，open() 的模式参数应为？', '["\"r\"", "\"w\"", "\"a\"", "\"x\""]'::jsonb, '["\"r\""]'::jsonb, 0, '"r" 只读，"w" 覆盖写，"a" 追加，"x" 排他创建。', '通识题（非课件原题）'),
  (6, 1, 3, 'PY43-KC-048', 'JUDGE', '文件使用完毕后应当调用 close() 关闭，或使用 with 语句自动关闭。', '[]'::jsonb, '["正确"]'::jsonb, 0, '及时关闭文件可释放资源；with 语句能自动关闭。', '通识题（非课件原题）'),
  (6, 1, 4, 'PY43-KC-048', 'FILL', '在 Python 中，打开文件的内置函数名是______。', '[]'::jsonb, '["open"]'::jsonb, 1, 'open() 是打开文件的内置函数。', '通识题（非课件原题）'),
  -- 第6章 第2节 文件读写
  (6, 2, 1, 'PY43-KC-050', 'CHOICE', '以写入模式 "w" 打开一个已存在的文件，会发生什么？', '["清空原有内容后再写入", "在文件末尾追加内容", "报错无法打开", "只能读取不能写入"]'::jsonb, '["清空原有内容后再写入"]'::jsonb, 0, '"w" 模式会清空原文件内容；若要追加应使用 "a"。', '通识题（非课件原题）'),
  (6, 2, 2, 'PY43-KC-052', 'CHOICE', '一次性读取文件全部内容并返回为一个字符串，使用的方法是？', '["read()", "write()", "append()", "close()"]'::jsonb, '["read()"]'::jsonb, 0, 'read() 读取整个文件内容为字符串。', '通识题（非课件原题）'),
  (6, 2, 3, 'PY43-KC-050', 'JUDGE', '以 "a" 模式打开文件写入时，新内容会追加到文件末尾，不会清空原内容。', '[]'::jsonb, '["正确"]'::jsonb, 0, '"a" 为追加模式，保留原有内容。', '通识题（非课件原题）'),
  (6, 2, 4, 'PY43-KC-050', 'FILL', '以“追加”方式打开文件时，open() 的模式参数是______（单个字母）。', '[]'::jsonb, '["a"]'::jsonb, 1, '追加模式用 "a"。', '通识题（非课件原题）'),
  -- 第6章 第3节 文件数据分析
  (6, 3, 1, 'PY43-KC-053', 'CHOICE', '进行词频统计时，通常使用哪种数据结构作为计数器？', '["字典(dict)", "元组(tuple)", "集合(set)", "字符串(str)"]'::jsonb, '["字典(dict)"]'::jsonb, 0, '字典以“词→次数”键值对计数，最为方便。', '通识题（非课件原题）'),
  (6, 3, 2, 'PY43-KC-049', 'CHOICE', '对一行 CSV 文本 "a,b,c" 按逗号切分成列表，应使用？', '["\"a,b,c\".split(\",\")", "\"a,b,c\".strip(\",\")", "\"a,b,c\".join(\",\")", "\"a,b,c\".find(\",\")"]'::jsonb, '["\"a,b,c\".split(\",\")"]'::jsonb, 0, 'split(",") 按逗号分割字符串为列表。', '通识题（非课件原题）'),
  (6, 3, 3, 'PY43-KC-053', 'JUDGE', '字典的 get(key, 0) 方法可以在键不存在时返回默认值 0，常用于计数。', '[]'::jsonb, '["正确"]'::jsonb, 0, 'get(key, 默认值) 避免 KeyError，便于累加计数。', '通识题（非课件原题）'),
  (6, 3, 4, 'PY43-KC-049', 'FILL', '去除字符串首尾空白字符（含换行符）常用的字符串方法名是______。', '[]'::jsonb, '["strip"]'::jsonb, 1, 'strip() 去除首尾空白，常用于清洗按行读取的数据。', '通识题（非课件原题）'),
  -- 第7章 第1节 函数进阶
  (7, 1, 1, 'PY43-KC-059', 'CHOICE', '在函数定义中，用于接收任意数量“位置参数”的语法是？', '["*args", "**kwargs", "args", "&args"]'::jsonb, '["*args"]'::jsonb, 0, '*args 收集任意数量位置参数为元组；**kwargs 收集关键字参数为字典。', '通识题（非课件原题）'),
  (7, 1, 2, 'PY43-KC-054', 'CHOICE', '关于 lambda 函数，下列说法正确的是？', '["它是一种匿名函数", "它必须有函数名", "它可以包含多条语句", "它不能带任何参数"]'::jsonb, '["它是一种匿名函数"]'::jsonb, 0, 'lambda 定义匿名的单表达式函数。', '通识题（非课件原题）'),
  (7, 1, 3, 'PY43-KC-060', 'JUDGE', '在函数内部定义的局部变量，默认情况下在函数外部无法访问。', '[]'::jsonb, '["正确"]'::jsonb, 0, '局部变量作用域限于函数内部。', '通识题（非课件原题）'),
  (7, 1, 4, 'PY43-KC-059', 'FILL', '在函数定义中，用于接收任意数量关键字参数的语法是______（含两个星号）。', '[]'::jsonb, '["**kwargs"]'::jsonb, 1, '**kwargs 收集关键字参数为字典。', '通识题（非课件原题）'),
  -- 第7章 第2节 递归
  (7, 2, 1, 'PY43-KC-061', 'CHOICE', '关于递归函数，下列说法正确的是？', '["递归函数必须有基准情形（终止条件）", "递归函数不需要终止条件", "递归函数不能调用自身", "递归一定比循环运行更快"]'::jsonb, '["递归函数必须有基准情形（终止条件）"]'::jsonb, 0, '递归必须有基准情形，否则会无限递归。', '通识题（非课件原题）'),
  (7, 2, 2, 'PY43-KC-061', 'CHOICE', '5 的阶乘 5! 的值是？', '["120", "25", "60", "100"]'::jsonb, '["120"]'::jsonb, 0, '5! = 5×4×3×2×1 = 120。', '通识题（非课件原题）'),
  (7, 2, 3, 'PY43-KC-061', 'JUDGE', '递归函数如果缺少基准情形（终止条件），可能导致无限递归。', '[]'::jsonb, '["正确"]'::jsonb, 0, '缺少终止条件会无限递归直至栈溢出。', '通识题（非课件原题）'),
  (7, 2, 4, 'PY43-KC-061', 'FILL', '递归的两大要素是递归情形和______情形（即递归终止条件）。', '[]'::jsonb, '["基准"]'::jsonb, 1, '递归两要素：基准情形（终止）与递归情形（规模缩小）。', '通识题（非课件原题）'),
  -- 第7章 第3节 模块与包
  (7, 3, 1, 'PY43-KC-062', 'CHOICE', '在 Python 中，导入整个 math 模块的语句是？', '["import math", "include math", "using math", "require math"]'::jsonb, '["import math"]'::jsonb, 0, 'import 模块名 导入整个模块。', '通识题（非课件原题）'),
  (7, 3, 2, 'PY43-KC-062', 'CHOICE', '只从 math 模块中导入 sqrt 函数，正确的语句是？', '["from math import sqrt", "import sqrt from math", "import math.sqrt only", "using math.sqrt"]'::jsonb, '["from math import sqrt"]'::jsonb, 0, 'from 模块 import 名称 可只导入指定对象。', '通识题（非课件原题）'),
  (7, 3, 3, 'PY43-KC-056', 'JUDGE', '一个包含 __init__.py 文件的目录可以被视为一个 Python 包。', '[]'::jsonb, '["正确"]'::jsonb, 0, '传统包通过 __init__.py 标识目录为包。', '通识题（非课件原题）'),
  (7, 3, 4, 'PY43-KC-062', 'FILL', '在 Python 中，用于导入模块的关键字是______。', '[]'::jsonb, '["import"]'::jsonb, 1, 'import 是导入模块的关键字。', '通识题（非课件原题）'),
  -- 第7章 第4节 库与打包
  (7, 4, 1, 'PY43-KC-057', 'CHOICE', '安装第三方库常用的包管理工具是？', '["pip", "git", "npm", "make"]'::jsonb, '["pip"]'::jsonb, 0, 'pip 是 Python 的官方包管理工具。', '通识题（非课件原题）'),
  (7, 4, 2, 'PY43-KC-058', 'CHOICE', '当一个 Python 文件作为主程序直接运行时，其变量 __name__ 的值是？', '["\"__main__\"", "文件名", "\"module\"", "None"]'::jsonb, '["\"__main__\""]'::jsonb, 0, '直接运行时 __name__ 为 "__main__"，被导入时为模块名。', '通识题（非课件原题）'),
  (7, 4, 3, 'PY43-KC-055', 'JUDGE', 'pyinstaller 可以把 Python 程序打包成独立的可执行文件（如 .exe）。', '[]'::jsonb, '["正确"]'::jsonb, 0, 'pyinstaller 用于将脚本打包为可执行文件。', '通识题（非课件原题）'),
  (7, 4, 4, 'PY43-KC-057', 'FILL', '使用 pip 安装第三方库 requests 的命令是：pip ______ requests。', '[]'::jsonb, '["install"]'::jsonb, 1, 'pip install 库名 用于安装第三方库。', '通识题（非课件原题）'),
  -- 第7章 第5节 面向对象
  (7, 5, 1, 'PY43-KC-064', 'CHOICE', '在 Python 中，定义类使用的关键字是？', '["class", "def", "object", "new"]'::jsonb, '["class"]'::jsonb, 0, 'class 用于定义类；def 定义函数。', '通识题（非课件原题）'),
  (7, 5, 2, 'PY43-KC-065', 'CHOICE', '面向对象编程的三大特征是？', '["封装、继承、多态", "顺序、分支、循环", "输入、处理、输出", "增、删、改、查"]'::jsonb, '["封装、继承、多态"]'::jsonb, 0, 'OOP 三大特征：封装、继承、多态。', '通识题（非课件原题）'),
  (7, 5, 3, 'PY43-KC-064', 'JUDGE', '对象是类的实例。', '[]'::jsonb, '["正确"]'::jsonb, 0, '类是模板，对象是按模板创建的实例。', '通识题（非课件原题）'),
  (7, 5, 4, 'PY43-KC-064', 'FILL', '在 Python 类的实例方法中，表示对象自身的第一个参数通常命名为______。', '[]'::jsonb, '["self"]'::jsonb, 1, '约定俗成用 self 表示实例自身。', '通识题（非课件原题）');

-- ===========================================================================
-- 2) OJ 覆盖层：mainline_oj_catalog（safe 模式，不改 course_problem.id）
-- ===========================================================================
CREATE TABLE mainline_oj_catalog (
  problem_id   BIGINT PRIMARY KEY REFERENCES course_problem(id),
  display_no   VARCHAR(16)  NOT NULL,   -- 稳定编号 OJ-01..OJ-39（按 chapter,stage_index,problem_id 排序）
  display_name VARCHAR(128) NOT NULL,   -- 规范化题名
  kc_code      VARCHAR(32)  NOT NULL,   -- 关联 KC（PY43-KC-NNN）
  chapter      INT          NOT NULL,   -- 归属章
  stage_index  INT          NOT NULL    -- 归属小节（对齐 mainline_stage）
);
CREATE UNIQUE INDEX idx_oj_catalog_display_no ON mainline_oj_catalog (display_no);
CREATE INDEX idx_oj_catalog_stage ON mainline_oj_catalog (chapter, stage_index);

INSERT INTO mainline_oj_catalog
  (problem_id, display_no, display_name, kc_code, chapter, stage_index)
VALUES
  (476, 'OJ-01', '圆面积计算', 'PY43-KC-016', 2, 3),  -- PY43-KC-016 程序运行原理
  (477, 'OJ-02', '个十百千获取', 'PY43-KC-025', 3, 3),  -- PY43-KC-025 整数位数提取
  (478, 'OJ-03', '将单个大写字母加密', 'PY43-KC-022', 3, 4),  -- PY43-KC-022 字符串处理方法
  (479, 'OJ-04', '获取星期字符串', 'PY43-KC-024', 3, 4),  -- PY43-KC-024 字符串索引与切片
  (487, 'OJ-05', '三角形判断', 'PY43-KC-035', 4, 1),  -- PY43-KC-035 多条件判断与逻辑运算
  (488, 'OJ-06', '圆周长和面积计算', 'PY43-KC-038', 4, 1),  -- PY43-KC-038 顺序结构
  (489, 'OJ-07', '平均成绩计算', 'PY43-KC-038', 4, 1),  -- PY43-KC-038 顺序结构
  (490, 'OJ-08', '年历输出', 'PY43-KC-038', 4, 1),  -- PY43-KC-038 顺序结构
  (492, 'OJ-09', '考试成绩判断', 'PY43-KC-033', 4, 1),  -- PY43-KC-033 分支结构
  (480, 'OJ-10', '计算π的近似值', 'PY43-KC-032', 4, 2),  -- PY43-KC-032 while循环与循环分支结合
  (481, 'OJ-11', '自然数偶数之和', 'PY43-KC-031', 4, 2),  -- PY43-KC-031 for循环与遍历
  (483, 'OJ-12', '编写程序，从键盘输入密码', 'PY43-KC-037', 4, 2),  -- PY43-KC-037 循环控制语句
  (484, 'OJ-13', '编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会', 'PY43-KC-037', 4, 2),  -- PY43-KC-037 循环控制语句
  (493, 'OJ-14', '使用for语句计算自然数之和', 'PY43-KC-031', 4, 2),  -- PY43-KC-031 for循环与遍历
  (482, 'OJ-15', '九九乘法表', 'PY43-KC-036', 4, 3),  -- PY43-KC-036 循环嵌套
  (485, 'OJ-16', '三角形图案', 'PY43-KC-036', 4, 3),  -- PY43-KC-036 循环嵌套
  (486, 'OJ-17', 'BMI 指标计算', 'PY43-KC-034', 4, 3),  -- PY43-KC-034 分支语句嵌套
  (491, 'OJ-18', '用户登录', 'PY43-KC-034', 4, 3),  -- PY43-KC-034 分支语句嵌套
  (494, 'OJ-19', '开发一个小型计算器', 'PY43-KC-034', 4, 3),  -- PY43-KC-034 分支语句嵌套
  (495, 'OJ-20', '小型计算器', 'PY43-KC-034', 4, 3),  -- PY43-KC-034 分支语句嵌套
  (496, 'OJ-21', '猜数字游戏', 'PY43-KC-034', 4, 3),  -- PY43-KC-034 分支语句嵌套
  (497, 'OJ-22', '成绩统计 V1（A B F 字典统计）', 'PY43-KC-041', 5, 2),  -- PY43-KC-041 字典在实际问题中的应用
  (498, 'OJ-23', '成绩统计 V2（带格式化输出）', 'PY43-KC-041', 5, 2),  -- PY43-KC-041 字典在实际问题中的应用
  (499, 'OJ-24', '手机通讯录管理', 'PY43-KC-041', 5, 2),  -- PY43-KC-041 字典在实际问题中的应用
  (500, 'OJ-25', '统计需要取快递人员的名单', 'PY43-KC-047', 5, 3),  -- PY43-KC-047 集合类型
  (501, 'OJ-26', '交集', 'PY43-KC-047', 5, 3),  -- PY43-KC-047 集合类型
  (502, 'OJ-27', '差集', 'PY43-KC-047', 5, 3),  -- PY43-KC-047 集合类型
  (503, 'OJ-28', '补集', 'PY43-KC-047', 5, 3),  -- PY43-KC-047 集合类型
  (504, 'OJ-29', '集合类型的复合赋值操作符', 'PY43-KC-047', 5, 3),  -- PY43-KC-047 集合类型
  (505, 'OJ-30', '词频统计问题', 'PY43-KC-053', 6, 3),  -- PY43-KC-053 词频统计
  (506, 'OJ-31', '关键步骤3：对单词的统计值从高到低排序', 'PY43-KC-053', 6, 3),  -- PY43-KC-053 词频统计
  (507, 'OJ-32', '筛选出租车位置数据', 'PY43-KC-049', 6, 3),  -- PY43-KC-049 基于文件的数据分析
  (508, 'OJ-33', '统计学科等级水平', 'PY43-KC-049', 6, 3),  -- PY43-KC-049 基于文件的数据分析
  (509, 'OJ-34', '词频统计代码实现', 'PY43-KC-053', 6, 3),  -- PY43-KC-053 词频统计
  (512, 'OJ-35', '定义一个接收任意数量关键字参数并打印的函数', 'PY43-KC-059', 7, 1),  -- PY43-KC-059 函数参数高级用法
  (513, 'OJ-36', '定义一个接收任意数量数字并求和的函数', 'PY43-KC-059', 7, 1),  -- PY43-KC-059 函数参数高级用法
  (511, 'OJ-37', '计算阶乘', 'PY43-KC-061', 7, 2),  -- PY43-KC-061 函数递归
  (510, 'OJ-38', '类和对象的创建', 'PY43-KC-064', 7, 5),  -- PY43-KC-064 类和对象基础
  (514, 'OJ-39', '面向过程的编程', 'PY43-KC-064', 7, 5);  -- PY43-KC-064 类和对象基础

-- ===========================================================================
-- 3) 回填 mainline_stage：example_problem_ids + required_example_count
--    （按 chapter+stage_index 匹配；过线只需完成 1 道 OJ，有 OJ 则置 1，否则 0）
-- ===========================================================================
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 1 AND stage_index = 1;  -- 计算思维的概念与方法 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 1 AND stage_index = 2;  -- 计算与计算科学 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 1 AND stage_index = 3;  -- 计算工具与计算机发展 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 2 AND stage_index = 1;  -- Python 与程序语言入门 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 2 AND stage_index = 2;  -- 变量、类型与运算 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[476]::bigint[], required_example_count = 1 WHERE chapter = 2 AND stage_index = 3;  -- 输入输出与运行原理 (1 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 3 AND stage_index = 1;  -- 进制与二进制运算 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 3 AND stage_index = 2;  -- 信息编码 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[477]::bigint[], required_example_count = 1 WHERE chapter = 3 AND stage_index = 3;  -- 数值类型与运算 (1 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[478,479]::bigint[], required_example_count = 1 WHERE chapter = 3 AND stage_index = 4;  -- 字符串处理与格式化 (2 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[487,488,489,490,492]::bigint[], required_example_count = 1 WHERE chapter = 4 AND stage_index = 1;  -- 顺序与分支结构 (5 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[480,481,483,484,493]::bigint[], required_example_count = 1 WHERE chapter = 4 AND stage_index = 2;  -- 循环结构 (5 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[482,485,486,491,494,495,496]::bigint[], required_example_count = 1 WHERE chapter = 4 AND stage_index = 3;  -- 嵌套结构 (7 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 5 AND stage_index = 1;  -- 序列与列表 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[497,498,499]::bigint[], required_example_count = 1 WHERE chapter = 5 AND stage_index = 2;  -- 字典 (3 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[500,501,502,503,504]::bigint[], required_example_count = 1 WHERE chapter = 5 AND stage_index = 3;  -- 集合 (5 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 6 AND stage_index = 1;  -- 文件系统与文件操作 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 6 AND stage_index = 2;  -- 文件读写 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[505,506,507,508,509]::bigint[], required_example_count = 1 WHERE chapter = 6 AND stage_index = 3;  -- 文件数据分析 (5 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[512,513]::bigint[], required_example_count = 1 WHERE chapter = 7 AND stage_index = 1;  -- 函数进阶 (2 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[511]::bigint[], required_example_count = 1 WHERE chapter = 7 AND stage_index = 2;  -- 递归 (1 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 7 AND stage_index = 3;  -- 模块与包 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = '{}'::bigint[], required_example_count = 0 WHERE chapter = 7 AND stage_index = 4;  -- 库与打包 (0 OJ)
UPDATE mainline_stage SET example_problem_ids = ARRAY[510,514]::bigint[], required_example_count = 1 WHERE chapter = 7 AND stage_index = 5;  -- 面向对象 (2 OJ)
