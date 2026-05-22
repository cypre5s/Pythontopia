# 教学示意图清单（Pythontopia 原创 / CC0）

10 个 Mermaid 源文件，供资源工坊 MindMapAgent 与 ResourceDesignerAgent 引用。所有内容由 Pythontopia 团队原创，按 CC0 1.0 发布。

| 文件 | 用途 | 关联 KC code | 章节 |
| --- | --- | --- | --- |
| `01_list_memory.mmd` | list 内存模型（引用 / 浅拷贝 / 深拷贝） | PY43-KC-040 列表基本操作 | 组合数据类型 |
| `02_dict_hash.mmd` | dict 哈希查找流程 | PY43-KC-042 字典基本操作 | 组合数据类型 |
| `03_for_loop_flow.mmd` | for 循环执行流程（含 else 子句） | PY43-KC-031 for循环与遍历 | 程序控制 |
| `04_while_flow.mmd` | while 循环流程（含死循环风险） | PY43-KC-032 while循环与循环分支结合 | 程序控制 |
| `05_function_callstack.mmd` | 函数调用栈（传参 / return / 局部销毁） | PY43-KC-012 基础函数 + PY43-KC-060 函数变量的作用域 | 函数 |
| `06_recursion_tree.mmd` | 递归调用树（factorial(4) 为例） | PY43-KC-061 函数递归 | 函数 |
| `07_try_except_flow.mmd` | try / except / else / finally 流程 | 附加补充（归类 PY43-KC-014 程序的运行方式） | 程序运行 |
| `08_class_inheritance.mmd` | 类继承 / 多态 UML | PY43-KC-064 类和对象基础 + PY43-KC-065 面向对象编程的特征 | 类与对象 |
| `09_generator_yield.mmd` | generator / yield 状态机 | 附加补充（归类 PY43-KC-044 序列基本操作） | 序列高级 |
| `10_decorator_order.mmd` | 装饰器调用顺序（log + cache 嵌套） | 附加补充（归类 PY43-KC-059 函数参数高级用法） | 函数高级 |

## 使用方式

前端 `WorkshopPanel` 思维导图卡片可以直接 fetch `.mmd` 内容传给 mermaid.js 渲染。后端 `ResourceDesignerAgent` 可以把 `.mmd` 作为基础模板，按学生画像调整节点文案 / 复杂度。

## 与课程包关系

7 个文件直接对齐课程包 65 KC 中的 7 个 KC；3 个文件（异常处理 / generator / decorator）属于课程未直接讲到的"延伸概念"，标记为"附加补充教学示意图"，仅在学生画像勾选"想要进阶"或在 Bug 矿洞引导时使用。

## 协议

- 全部文件许可：**CC0 1.0**（公共领域贡献）
- 作者：Pythontopia 项目（2026-05-21 创建）
- 不依赖任何第三方素材，可商用 / 可二次修改 / 可分发
