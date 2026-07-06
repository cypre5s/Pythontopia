# Pythontopia 设计语言（Design Language）

> 单一事实源：所有视觉令牌定义在 `frontend/src/styles/tokens.css`（`--pyt-*`）。
> 本文是这套令牌背后的「为什么」与「怎么用」，新页面 / 组件按此对齐，**不要再散落硬编码 hex / px**。

## 0. 设计理念

- **教育优先，游戏承载**：像素皮肤为学习服务，绝不喧宾夺主；装饰必须有教育或导航意义。
- **星露谷式暖色像素**：木纹 + 羊皮纸（wood / parchment）暖色基调，紧致像素边框，克制投影。
- **克制**：信息密度服从可读性；能用一处视觉（图标 / 颜色 / 状态）表达的，就不堆文字。

## 1. 色彩 Color

全部取 `--pyt-color-*`，按语义用，禁止页面内自造 hex。

| 组 | 令牌 | 用途 |
| --- | --- | --- |
| 背景 | `bg-primary` `bg-elevated` `bg-soft` `bg-sunken` | 页面底 / 卡片 / 柔和块 / 凹陷区，由浅到深 |
| 边框 | `border-strong` `border` `border-soft` | 主描边（深木）/ 常规（浅木）/ 分隔（半透） |
| 文本 | `text-primary` `text-muted` `text-hint` `text-on-dark` | 正文 / 次要 / 提示 / 深底反白 |
| 状态 | `success(-bg)` `warning(-bg)` `danger(-bg)` `info(-bg)` | 成对：前景描边色 + 浅底 |
| 主色 | `primary` `primary-dark` | 主按钮 / 标题强调 |
| 强调 | `gold` `gold-bright` `accent` `shadow` | 金色高亮 / 紫色点缀 / 像素位移投影色 |
| 终端 | `terminal-bg` `terminal-text` `terminal-stdout` `terminal-stderr-*` | 代码输出 / Pyodide stdout/stderr 专用「深底亮字」 |

- **深色块例外**：仅代码 / 终端 / Phaser 画布舞台用深底（`#161126` 塔、`terminal-*` 代码），其余一律暖色亮底。

## 2. 字体 Typography（分层是硬规则）

| 场景 | 令牌 | 说明 |
| --- | --- | --- |
| 游戏 UI / 徽章 / 按钮 / 像素标签 | `--pyt-font-pixel`（Zpix → Ark Pixel 12） | 配 `.pyt-pixel-text`（`image-rendering: pixelated` + 关字体平滑） |
| 学习正文（讲义 / 答疑 / 大段中文） | `--pyt-font-read`（PingFang/雅黑 系无衬线） | 长文用无衬线，**不要**用像素字撑长文本 |
| 代码 / 样例 | `--pyt-font-mono` / `--pyt-font-pixel-mono` | 等宽 |

字号取 `--pyt-font-size-{xs..2xl}`（11/12/14/16/20/24）。

## 3. 间距与圆角 Spacing & Radius

- 间距：`--pyt-space-1..6` = 4 / 8 / 12 / 16 / 24 / 32，**只用这 6 档**。
- 圆角：`--pyt-radius-{none,sm,md,lg,full}` = 0 / 2 / 4 / 6 / 999。像素紧致，**弃用 8px+ 软圆角**。

## 4. 投影与层级 Shadow & Z-index

- 像素位移投影：卡片 `box-shadow: <x> <y> 0 rgba(74,54,29,.2~.35)`（硬边、无模糊），仅浮层 / 强调块用；`--pyt-shadow-card` / `--pyt-shadow-modal` 为标准档。
- 层级只走 `--pyt-z-{base,hud,popover,overlay,modal,toast,debug}`（1/100/200/300/1000/1100/1200），**禁止 9000/9999 魔法数字**。

## 5. 动效 Motion

- 时长走 `--pyt-motion-{fast,base,slow,slowest}`（80/120/200/400ms）。
- 一律尊重 `prefers-reduced-motion: reduce`（关动画 / 缩短时长 / 即时滚动）。

## 6. 可达性 A11y

- 焦点环 `--pyt-focus-ring`（金色 2px），全局 `:focus-visible` 生效、`:focus` 不留框。
- 学习内容必须 `user-select: text` 可选中复制。
- 交互元素用真实 `<button>`/`<a>`，键盘可达；图标按钮带 `aria-label`。

## 7. 组件范式 Component Patterns

- **像素卡 / 面板**：`2px solid border-strong` + 像素位移投影 + `radius-md/lg`；重点卡可加左上/右下金色 8–10px 装饰小方块。
- **按钮**：主按钮金底深字 + 2px 描边 + 2px 位移投影；次按钮透明 / 柔底；禁用 `opacity:.55`。
- **状态片 chip**：`status-bg` 浅底 + 同名前景描边 + `radius-sm`，小字（11–12px）。
- **代码 / 终端块**：`terminal-*` 深底亮字 + `font-mono`。
- **画布（Phaser）**：单 `Phaser.Game` 单例；`image-rendering: pixelated`；深色舞台底是允许的深色例外。
- **自适应窗口高度**：应用型页面锁视口（`height: calc(100dvh - 头部)`）、分区各自内部滚动，避免整页长滚；文档型窄屏可回归自然滚动。

## 8. 文案 Voice & Copy（克制优先）

- **减少不必要的文字说明**：界面以视觉（图标 / 颜色 / 状态 / 布局）先行，文字只补「视觉表达不了」的部分。
- 动作导向：按钮 / 提示用动词短语（「开始远征」「弃局」），不写整段背景解释。
- 不堆内部术语：面向学生的界面不暴露「5 重审查 / golden 兜底 / 选择器」等实现词；说明放文档（指南）里。
- 一处一义：同一信息不在标题、副标题、提示里重复三遍。

## 9. 反模式（禁止）

- 散装硬编码 hex / px（绕过令牌）；9000/9999 z-index；8px+ 软圆角；像素字排长正文；
- 用大段文字替代清晰的视觉状态；面向学生暴露后端实现术语；忽略 `prefers-reduced-motion`。
