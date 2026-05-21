# Pythontopia 教学示意图清单（edu_diagrams）

本目录由 R-100 素材爬取子代理 #9 生成，用于 Pythontopia 资源工坊中"思维导图"和"讲义"模块的可视化插图。

- 全部源文件采用 [Mermaid](https://mermaid.js.org/) 语法（`.mmd`），可在前端用 `mermaid.js` 直接渲染为 SVG。
- 全部为 **原创**，统一遵循 **CC0 / Public Domain** 协议，可直接商用，无需署名。
- 课程包 `packId=43`《Python 语言基础》共 65 个 KC；其中 **异常处理 / generator / 装饰器** 三类在课程中作为通识扩展呈现，未直接对应单独 KC，因此映射到最接近的相关 KC，并在 mermaid 注释中明确标注"通识扩展"。

## KC ↔ Diagram 映射表

| # | 文件 | 主题 | 主关联 KC | 关联 KC（补充） | 教学场景 |
|---|------|------|-----------|-----------------|----------|
| 01 | `01_list_memory.mmd` | list 内存模型（变量→列表对象→元素对象的三层引用） | PY43-KC-040（列表基本操作） | PY43-KC-044（序列基本操作） | 思维导图：数据结构 / 讲义：列表底层 |
| 02 | `02_dict_hash.mmd` | dict 哈希表寻址（hash → 取模 → 桶 → 冲突 → 扩容） | PY43-KC-042（字典基本操作） | PY43-KC-043（字典类型） | 思维导图：字典原理 / 讲义：哈希冲突 |
| 03 | `03_for_loop.mmd` | for 循环执行流程（iterator 协议 + break/continue/else） | PY43-KC-031（for 循环与遍历） | PY43-KC-037（循环控制语句） | 思维导图：流程自动化 / 讲义：循环结构 |
| 04 | `04_while_loop.mmd` | while 循环执行流程（条件判断 + 循环体 + else 子句） | PY43-KC-032（while 循环与循环分支结合） | PY43-KC-037（循环控制语句） | 思维导图：流程自动化 / 讲义：循环结构 |
| 05 | `05_call_stack.mmd` | 函数调用栈（栈帧、局部变量、压栈出栈过程） | PY43-KC-060（函数变量的作用域） | PY43-KC-012（基础函数） | 思维导图：函数 / 讲义：作用域 |
| 06 | `06_recursion_tree.mmd` | 函数递归调用树（以 `fib(5)` 为例） | PY43-KC-061（函数递归） | PY43-KC-012（基础函数） | 思维导图：归纳与抽象 / 讲义：递归 |
| 07 | `07_try_except.mmd` | 异常处理流程（try/except/else/finally + 异常传播） | PY43-KC-048（Python 文件操作）⚠️ 通识扩展 | PY43-KC-061（函数递归边界保护） | 讲义：健壮性编程（通识补充） |
| 08 | `08_class_inheritance.mmd` | 类继承 UML（封装 / 继承 / 多态 / MRO） | PY43-KC-065（面向对象编程的特征） | PY43-KC-064（类和对象基础） | 思维导图：面向对象 / 讲义：继承多态 |
| 09 | `09_generator_yield.mmd` | generator 函数的暂停-恢复语义（yield / next / StopIteration） | PY43-KC-031（for 循环与遍历）⚠️ 通识扩展 | PY43-KC-012（基础函数） | 讲义：迭代器进阶（通识补充） |
| 10 | `10_decorator_order.mmd` | 装饰器堆叠顺序（包装顺序 vs 调用顺序的"洋葱模型"） | PY43-KC-012（基础函数）⚠️ 通识扩展 | PY43-KC-060（函数变量的作用域） | 讲义：函数式进阶（通识补充） |

> ⚠️ 通识扩展：表示该概念未在 `packId=43` 课程包的 KC 列表中独立出现，但在初学者讲义中常见。生成内容时由 `GroundingCriticAgent` 标注为"通识知识 / 非课件原文"。

## 文件规范

- 编码：UTF-8 无 BOM
- 行尾：LF
- 首行：`%% Pythontopia CC0 - 用途：…`
- 次几行：`%% 关联 KC：…` 以及（必要时）示例代码注释
- 节点文本：中文优先，关键英文术语保留（如 `iterator`、`MRO`、`StopIteration`）

## 渲染示例

前端 `Vue 3 + mermaid` 渲染：

```ts
import mermaid from "mermaid";
const src = await fetch("/assets/edu_diagrams/03_for_loop.mmd").then(r => r.text());
const { svg } = await mermaid.render("for-loop", src);
container.innerHTML = svg;
```

服务端批量转 SVG / PNG（可选）：

```bash
npx -y @mermaid-js/mermaid-cli -i 03_for_loop.mmd -o out/03_for_loop.svg
```

## 后续来源补充

- Wikimedia Commons / OpenClipart 检索结果见同目录 `EXTERNAL_SOURCES.md`。
- 若后续引入第三方素材，必须同步更新协议清单 `/home/cypress/Pythontopia/assets/LICENSES.md`（如尚不存在则由素材入库子代理统一建立）。

## 校验清单（落库前）

- [ ] 所有 `.mmd` 能被 `mermaid-cli` 渲染通过
- [ ] 每张图至少被一个 KC 引用
- [ ] 资源工坊生成时附带 `evidence_kc_codes`（指向上表）
- [ ] 通识扩展图打 `is_general_knowledge=true` 标记
