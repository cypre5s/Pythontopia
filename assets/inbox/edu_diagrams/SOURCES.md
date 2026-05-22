# 来源与许可

| 文件 | 类型 | 作者 | License | 来源 |
| --- | --- | --- | --- | --- |
| `01_list_memory.mmd` .. `10_decorator_order.mmd` | Mermaid 源码 | Pythontopia 项目（2026-05-21 原创） | CC0 1.0 | 本仓库 |
| `MANIFEST.md` | 文档 | Pythontopia | CC0 1.0 | 本仓库 |
| `SOURCES.md` | 文档 | Pythontopia | CC0 1.0 | 本仓库 |

## 设计原则

- 教学严谨：每张图覆盖一个 Python 概念的核心动态过程（执行顺序 / 内存指向 / 状态机），而非静态结构。
- 防幻觉：与课件 KC 对齐；找不到 KC 的延伸概念标记"附加补充"。
- 像素小镇风格：图表节点用浅色 fill + 深色 stroke，与小镇 UI 配色 token 一致。
- 可读性：每张图都自带"边注"（warning / note），暗示常见误用。

## 未直接采用 Wikimedia Commons 素材的原因

Wikimedia 上 Python 相关 SVG 大多是 GPL / GFDL，不符合本仓库严格 CC0 / CC-BY 4.0 / OFL-1.1 / MIT 的硬约束。Mermaid 源码自绘是更安全的方案，也便于后续 ResourceDesignerAgent 根据学生画像动态改写。
