# 第三方补充素材清单（external/）

> 本目录下所有第三方素材均经过协议核验，仅纳入 **CC0 / Public Domain** 来源。原始下载脚本与日期见各子目录 `LICENSE.md`。

## 来源 1：Wikimedia Commons —— 用户 Pluke 的 CPT- 系列 CC0 教学图

- 协议：CC0 1.0 Universal Public Domain Dedication
- 作者：Pluke（https://commons.wikimedia.org/wiki/User:Pluke）
- 子目录：`external/wikimedia_pluke_cc0/`
- 共计 13 个 SVG，可商用、可修改、不强制署名

### 与 mermaid 源图的对应（叠加使用）

| Pythontopia 自制 mermaid 图 | 可补充使用的 Pluke CC0 SVG | 用途 |
|---|---|---|
| `01_list_memory.mmd` | `programming_array.svg`, `programming_variable.svg` | 讲义页眉做"变量 → 内存位置"的形象化插图 |
| `02_dict_hash.mmd` | `hashing_concept.svg` | 引入哈希函数概念（密码哈希场景），再用 mermaid 进入字典桶细节 |
| `03_for_loop.mmd` | `for_loop_simple.svg`, `for_loop_complex.svg` | 思维导图旁边放对照图，强化"流程框-菱形判断" |
| `05_call_stack.mmd` | `stack_frame.svg`, `stack_real_life.svg`, `global_local_scope.svg` | 配套作用域与栈帧讲义 |
| `06_recursion_tree.mmd` | `recursion_factorial.svg` | 与 fib(5) 树形图互补，提供阶乘代码视角 |
| `08_class_inheritance.mmd` | `oop_objects_classes.svg`, `oop_objects_attmeth.svg`, `oop_inheritance.svg`, `oop_inheritance_bikes.svg` | UML 类图（mermaid）+ 真实对象/继承示意图（外部） |

> `04_while_loop.mmd` / `07_try_except.mmd` / `09_generator_yield.mmd` / `10_decorator_order.mmd` 暂未找到合适的 CC0 公共领域补充素材，全部依赖自制 mermaid 即可。

## 来源 2：Python 官方教程（docs.python.org/3/tutorial/）

- 已核查 `https://docs.python.org/3/tutorial/` 全部章节，**官方教程几乎不包含可下载的示意图（PNG/SVG）**，主要以代码块和文字描述为主。
- 故本子代理未引入官方文档插图，转而：
  1. 全部自制 10 张 mermaid 图（见 `MANIFEST.md`）
  2. 引入上面的 13 张 Wikimedia CC0 SVG 作为补充

## 来源 3：OpenClipart

- 检索关键词：`python flowchart`、`for loop diagram`、`class diagram`
- 结果：未找到主题契合且明确 CC0 标注的 Python 教学专用图（OpenClipart 中现有 Python 素材多为 Monty-Python 卡通蛇形象，与教学无关）
- 暂不引入。如后续需要更多素材，可继续在 [Pluke 用户的 Commons 贡献页](https://commons.wikimedia.org/wiki/Special:ListFiles/Pluke) 中挑选。

## 校验清单

- [x] 全部第三方文件协议 = CC0 或 Public Domain
- [x] 全部文件已下载成功并通过 SVG 头校验
- [x] 每个文件作者、原始 URL、下载日期均记录在 `LICENSE.md`
- [ ] 资源入库时同步写入 `assets/LICENSES.md`（由素材入库子代理统一处理）
