---
title: Pythontopia tokens.css 扩展提案（调色板与设计令牌 v1.0）
version: v1.0
status: Draft
last_updated: 2026-05-24
owner: 视觉设计师 + 前端工程师
audience: 前端工程师 / 视觉设计师 / Phaser 场景作者 / 教学面板维护者
scope: frontend/src/styles/tokens.css 的所有 CSS 自定义属性
依赖文档:
  - /home/cypress/Pythontopia/docs/stardew/00-visual-system-overview.md
  - /home/cypress/Pythontopia/frontend/src/styles/tokens.css
  - /home/cypress/Pythontopia/AGENTS.md
---

# 0. 文档定位

本文是 Pythontopia 网页端 `frontend/src/styles/tokens.css` 的**变量扩展提案**，与 `00-visual-system-overview.md` 视觉宪法**严格同构**：

- 视觉宪法定义"应该长什么样"（色相、对比度、动画哲学、9-slice 规范）。
- 本文定义"在 CSS 里叫什么名字、什么值、谁能引用、怎么迁移"。

约束范围（**Hard Scope**）：

| 范围内 | 范围外 |
| --- | --- |
| CSS 自定义属性命名/层级/默认值 | Tailwind / UnoCSS 整合（项目未引入） |
| 暗色模式 token 镜像与切换机制 | Phaser BitmapText 字体配置（在 R-100 子任务） |
| 兼容现有变量的别名层 | Android Compose tokens 同步（在 :core:design） |
| 迁移到组件层的 4 阶段路线图 | 后端 / 微服务样式（不涉及） |

**禁止**：在组件层（`*.vue` 或 `*.ts`）硬编码 HEX 颜色、px 间距、ms 时长。所有视觉值必须先在本文档定义 token，再被组件 `var(--pyt-*)` 引用。

---

# 1. 命名规范

## 1.1 命名总规则

| 规则 | 内容 | 反例 |
| --- | --- | --- |
| **前缀** | 全部以 `--pyt-` 开头 | `--color-primary` |
| **分类** | 前缀后紧跟分类英文单词，再用 `-` 分隔语义 | `--pyt-primary-color` |
| **大小写** | 全小写，单词间用 `-` | `--pyt-Color-Primary` |
| **数字阶梯** | 调色板使用 50/100/300/500/700/900；其它使用 0/1/2/3 自然序 | `--pyt-color-primary-light` |
| **语义优先** | 优先用语义名（`success`），避免裸色名（`green`） | `--pyt-color-green` |
| **不带单位** | 数值类 token 在 value 中带单位，名字不带单位 | `--pyt-space-4-px` |

## 1.2 10 个分类与前缀对照表

| 分类 | 前缀 | 用途 | 数量 |
| --- | --- | --- | --- |
| color | `--pyt-color-*` | 调色板（主色/辅色/语义/灰阶） | 19 |
| overlay | `--pyt-overlay-*` | 覆盖层、滤镜、阴影叠加 | 6 |
| font | `--pyt-font-*` | 字体栈 | 5 |
| size | `--pyt-font-size-*` | 字号阶梯 | 5 |
| space | `--pyt-space-*` | 间距阶梯 | 6 |
| radius | `--pyt-radius-*` | 圆角阶梯 | 5 |
| shadow | `--pyt-shadow-*` | 投影 | 4 |
| zindex | `--pyt-z-*` | 层叠顺序 | 5 |
| motion | `--pyt-motion-*` | 过渡时长 | 4 |
| focus | `--pyt-focus-*` | 焦点环 | 2 |

> 调色板 19 + 覆盖层 6 = 调色板总数 **25**（命题要求）。覆盖层归入独立前缀 `--pyt-overlay-*`，与色彩 token 隔离，方便暗色模式覆写。

---

# 2. 调色板（25 个）

## 2.1 主色（金棕系，4 档）

主色承载 Pythontopia"米黄羊皮纸 + 深棕木框"的星露谷气质，4 档覆盖背景层 / hover 层 / 主按钮 / 文字层。

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-color-primary-50` | `#fef7e3` | 页面主背景、面板底色 | 兼容旧 `--pyt-color-bg-primary` | `body { background: var(--pyt-color-primary-50); }` |
| `--pyt-color-primary-100` | `#fdf3d0` | 二级面板、tooltip 底、hover 层 | 兼容旧 `--pyt-color-bg-soft` | 卡片悬停 |
| `--pyt-color-primary-500` | `#c9a86b` | 边框、按钮 default 底、强调 | 兼容旧 `--pyt-color-border` | 按钮 default `background` |
| `--pyt-color-primary-900` | `#4d3613` | 正文文字、像素硬描边、标题 | 兼容旧 `--pyt-color-text-primary` | `h1 { color: var(--pyt-color-primary-900); }` |

## 2.2 辅色（4 个，承载 CTA / 节日 / 链接 / 春樱）

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-color-accent-gold` | `#d4af37` | 主 CTA 内描边、KC 收获徽章、焦点光晕 | 视觉宪法 #04 高亮金 | 提交按钮金线 |
| `--pyt-color-accent-purple` | `#9b59b6` | 节日彩蛋、紫宝石、夜间魔法 | 视觉宪法 #10 特殊紫 | 万圣节横幅 |
| `--pyt-color-accent-blue` | `#5bc0de` | 对话气泡、链接、雨天滤镜 | 视觉宪法 #09 链接蓝 | NPC 对话外框 |
| `--pyt-color-accent-pink` | `#ffb7c5` | 春樱粒子、季节边框（SPRING） | 视觉宪法 #8 季节调色 | 春季 HUD 点缀 |

## 2.3 语义色（5 个，状态通知）

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-color-success` | `#6bbf59` | 成功 toast、能量条、KC 掌握 | 视觉宪法 #07 正向绿 | 提交通过 |
| `--pyt-color-warning` | `#c2871f` | 警告 toast、低能量提示 | 兼容旧 `--pyt-color-warning` | 体力不足 |
| `--pyt-color-danger` | `#d9534f` | 失败 toast、扣血、Bug 矿洞 | 视觉宪法 #08 负向红 | 判题失败 |
| `--pyt-color-info` | `#2a6f97` | 信息提示、链接备注 | 兼容旧 `--pyt-color-info` | 公告板提示 |
| `--pyt-color-neutral` | `#8b6b47` | 次级说明、placeholder、时间戳 | 兼容旧 `--pyt-color-text-muted` | 表格表头 |

## 2.4 灰阶（6 档，从近白到近黑）

灰阶**不是纯灰**，全部带轻微暖色相（H≈30），保证与米黄基调和谐。

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-color-gray-50` | `#fff7e6` | 模态弹窗内层背景、清淡分隔 | 兼容旧 `--pyt-color-bg-elevated` | 模态卡片底 |
| `--pyt-color-gray-100` | `#f3e4be` | 输入框 disabled、表格交替行 | 兼容旧 `--pyt-color-bg-sunken` | disabled input |
| `--pyt-color-gray-300` | `#d8c293` | 分隔线、卡片边、placeholder 文字背景 | 新增 | `hr` 颜色 |
| `--pyt-color-gray-500` | `#a08653` | 中性图标、次级文字、面包屑分隔 | 新增 | 时间戳 |
| `--pyt-color-gray-700` | `#6e4f1d` | 次级标题、tooltip 文字 | 兼容旧 `--pyt-color-text-hint` | h3 |
| `--pyt-color-gray-900` | `#2b1d0a` | 高对比文字（教学长文标题、AAA 强制） | 新增 | 论文标题 |

## 2.5 覆盖层（6 个，专门承载叠加效果）

覆盖层用于**多层混合**：粒子滤镜、模态遮罩、夜间深蓝、庆祝金幕。这些不属于调色板，必须独立命名以便暗色模式整组覆写。

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-overlay-soft` | `rgba(77, 54, 19, 0.12)` | 卡片下沉、轻投影 | 视觉宪法 #11 半透黑 | `box-shadow` |
| `--pyt-overlay-medium` | `rgba(77, 54, 19, 0.24)` | 弹窗下沉、抽屉遮罩 | 新增 | 模态阴影 |
| `--pyt-overlay-strong` | `rgba(0, 0, 0, 0.55)` | 全屏模态遮罩、暂停页 | 新增 | 暂停界面 |
| `--pyt-overlay-warm` | `rgba(212, 175, 55, 0.18)` | 庆祝金幕、收获光晕 | 视觉宪法 §5 庆祝动画 | KC 收获 |
| `--pyt-overlay-night` | `rgba(26, 43, 92, 0.40)` | 夜间时段全屏 multiply | 视觉宪法 §7 NIGHT | Phaser overlay |
| `--pyt-overlay-rain` | `rgba(91, 192, 222, 0.08)` | 雨天蓝色滤镜 | 视觉宪法 §6 RAINY | Phaser overlay |

> 调色板小计：主 4 + 辅 4 + 语义 5 + 灰阶 6 + 覆盖 6 = **25 个**。

---

# 3. 字体（5 个）

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-font-sans` | `'LXGW WenKai', '霞鹜文楷', 'PingFang SC', system-ui, sans-serif` | 教学长文、HUD 正文、按钮 | 现有保留 | `body { font-family: var(--pyt-font-sans); }` |
| `--pyt-font-mono` | `'JetBrains Mono', 'Cascadia Code', Menlo, monospace` | CodeLab 编辑器、调试输出 | 现有保留 | `<pre>` |
| `--pyt-font-pixel` | `'Ark Pixel 12', var(--pyt-font-sans)` | 像素 HUD 文字、徽章、Phaser BitmapText | 现有保留 | KC 标签 |
| `--pyt-font-pixel-mono` | `'Ark Pixel 12 Mono', var(--pyt-font-mono)` | 像素等宽（积分数字、计时器） | 现有保留 | HUD 计时器 |
| `--pyt-font-display` | `'Press Start 2P', var(--pyt-font-pixel)` | 庆祝弹窗大标题、季节切换横幅 | 视觉宪法 §2.1 | "丰收！"大字 |

> `--pyt-font-display` 是 **R-12 新增**，字体本身在 `assets/pixel/fonts/` 待补；fallback 到 `--pyt-font-pixel` 确保未就位也不破图。

---

# 4. 字号（5 个，xs–xl）

像素字体只能整数 px，本阶梯**全部整数**。

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-font-size-xs` | `11px` | 道具叠加数字、迷你地图标签、版权脚标 | 视觉宪法 §2.2 pixel-xs | `<small>` |
| `--pyt-font-size-sm` | `12px` | HUD 主字号、按钮文本、对话框正文 | 视觉宪法 §2.2 pixel-sm | 按钮 |
| `--pyt-font-size-md` | `14px` | 教学正文、菜单项、表格 | 视觉宪法 §2.2 pixel-base | `body` |
| `--pyt-font-size-lg` | `18px` | 模态标题、章节大标题 | 视觉宪法 §2.2 pixel-lg | `h2` |
| `--pyt-font-size-xl` | `24px` | 庆祝弹窗、季节横幅 | 视觉宪法 §2.2 pixel-xl | "Spring 1" |

> 现有 `--pyt-font-size-base / lg / xl / 2xl` 通过别名映射保留兼容（见 §10）。

---

# 5. 间距（6 个，0–24px）

间距阶梯遵循 **4 的倍数**（除 0），方便像素对齐。

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-space-0` | `0` | 重置、移除 padding/margin | 新增 | `.flush { padding: var(--pyt-space-0); }` |
| `--pyt-space-1` | `4px` | 最小内距、像素图标 padding、tag 内距 | 现有保留 | KC 标签 padding |
| `--pyt-space-2` | `8px` | 卡片内距、按钮 padding-y、行间距 | 现有保留 | 按钮 padding |
| `--pyt-space-3` | `12px` | 表单 gap、面板分组间距 | 现有保留 | 表单字段 |
| `--pyt-space-4` | `16px` | 面板外距、模态 padding | 现有保留 | 模态内 padding |
| `--pyt-space-5` | `24px` | 区块大间距、HUD 面板间隔 | 现有保留 | HUD 任务/背包间距 |

> 现有 `--pyt-space-6: 32px` **保留**但不进入新阶梯，标注 deprecated，由 `space-5 × 1.5` 局部使用。

---

# 6. 圆角（5 个，none–full）

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-radius-none` | `0` | 像素 9-slice 框、严格方角 | 新增 | 像素 UI |
| `--pyt-radius-sm` | `2px` | 像素徽章、KC 标签 | 新增 | KC tag |
| `--pyt-radius-md` | `4px` | 按钮、tooltip、卡片 | 视觉宪法 §3.3 | 主按钮 |
| `--pyt-radius-lg` | `6px` | 庆祝弹窗、季节横幅 | 视觉宪法 §3.3（下调 10→6） | 庆祝弹窗 |
| `--pyt-radius-full` | `999px` | 头像、Pill 按钮、能量条端点 | 新增 | 玩家头像 |

> 主流圆角 **4 档**（sm/md/lg/full），none 是边界值。视觉宪法明确"禁止 12px+ 圆角"，所以不设 `--pyt-radius-xl`。

---

# 7. 阴影（4 个）

像素质感**不滥用阴影**，仅在 4 个层级使用。

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-shadow-card` | `0 2px 0 var(--pyt-overlay-soft)` | 卡片下沉（像素硬阴影） | 现有保留语义 | 任务卡片 |
| `--pyt-shadow-popover` | `0 4px 8px var(--pyt-overlay-medium)` | tooltip / 下拉菜单 | 新增 | tooltip |
| `--pyt-shadow-modal` | `0 6px 0 var(--pyt-overlay-medium), 0 12px 24px var(--pyt-overlay-medium)` | 模态弹窗双层阴影 | 现有保留 | 设置面板 |
| `--pyt-shadow-inset` | `inset 0 1px 0 var(--pyt-overlay-soft)` | 按钮按下凹陷、输入框内阴影 | 新增 | input 凹陷 |

> 所有阴影**禁止用蓝色/纯黑**，统一用 `--pyt-overlay-*` 暖褐叠加，保持调性。

---

# 8. z-index（5 个）

层叠秩序按**百位间隔**，留出组件内部微调空间。

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-z-hud` | `100` | HUD（顶部状态栏、迷你地图、背包） | 新增 | HudShell.vue |
| `--pyt-z-popover` | `200` | tooltip、下拉菜单、对话气泡 | 新增 | DialogTree.vue |
| `--pyt-z-modal` | `300` | 模态弹窗、设置面板、庆祝弹窗 | 替代旧 `--pyt-z-modal: 1000` | DialogModal.vue |
| `--pyt-z-toast` | `400` | 右上角通知、成功/失败 toast | 替代旧 `--pyt-z-toast: 1100` | 提交通知 |
| `--pyt-z-debug` | `500` | 开发期调试覆盖层（FPS、坐标） | 新增 | 仅 dev 模式 |

> 旧值 `1000/1100` 与第三方库（如 Vue 默认 Teleport）冲突概率低，但本提案下调到 100 系便于组件层 `1` `10` `99` 微调，避免每次都跳到 `1001`。

---

# 9. 过渡（4 个）+ 焦点环（2 个）

## 9.1 过渡时长

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-motion-instant` | `80ms` | 按下反馈、即时高亮 | 视觉宪法 §5 按下位移 | `transition: transform var(--pyt-motion-instant);` |
| `--pyt-motion-fast` | `120ms` | hover 高亮、tooltip 显示 | 视觉宪法 §5 hover | 按钮 hover |
| `--pyt-motion-base` | `200ms` | 弹窗淡入、抽屉滑出 | 视觉宪法 §5 弹窗淡入 | 模态淡入 |
| `--pyt-motion-slow` | `400ms` | 页面切换 fade、地图边缘转场 | 视觉宪法 §5 页面切换 | WorldScene 切图 |

## 9.2 焦点环

| name | value | 用途 | 引用 | 例子 |
| --- | --- | --- | --- | --- |
| `--pyt-focus-ring` | `2px solid #f1c453` | 默认焦点环（键盘 Tab） | 视觉宪法 §10 焦点环金 | `:focus-visible { outline: var(--pyt-focus-ring); }` |
| `--pyt-focus-ring-strong` | `3px solid #d4af37` | 高对比焦点环（重要 CTA、暗模式） | 新增 | 提交按钮 focus |

> `prefers-reduced-motion: reduce` 时，所有过渡时长 ×0.5，由统一 media query 覆写 `--pyt-motion-*`（见附录 CSS）。

---

# 10. 暗色模式提案

## 10.1 切换机制

支持两种切换：

| 切换方式 | 触发 | 实现 |
| --- | --- | --- |
| **跟随系统** | `prefers-color-scheme: dark` | CSS `@media` 覆写 `:root` 变量 |
| **手动切换** | `<html data-theme="dark">` | CSS 属性选择器覆写 `[data-theme="dark"]` 变量 |

两套必须并存，且**手动切换优先级高于系统**（用户在设置里切了 dark，即便系统 light 也保持 dark）。

## 10.2 暗模式覆写表（仅列差异，其它继承 light）

夜间小镇主题：**米黄 → 深木色 / 深褐 → 米白**，整体 multiply 风味，与游戏 §7 NIGHT overlay 共构。

| token | light | dark | 备注 |
| --- | --- | --- | --- |
| `--pyt-color-primary-50` | `#fef7e3` | `#1f1810` | 主背景反相 |
| `--pyt-color-primary-100` | `#fdf3d0` | `#2b2114` | 二级面板 |
| `--pyt-color-primary-500` | `#c9a86b` | `#8a6c2c` | 边框降饱和 |
| `--pyt-color-primary-900` | `#4d3613` | `#f3e4be` | 文字反色 |
| `--pyt-color-neutral` | `#8b6b47` | `#c4a76d` | 次级文字 |
| `--pyt-color-gray-50` | `#fff7e6` | `#2b2114` | 模态内层 |
| `--pyt-color-gray-100` | `#f3e4be` | `#3a2c1a` | disabled 层 |
| `--pyt-color-gray-700` | `#6e4f1d` | `#d8c293` | 次级标题 |
| `--pyt-color-gray-900` | `#2b1d0a` | `#fff7e6` | 高对比文字 |
| `--pyt-overlay-strong` | `rgba(0,0,0,0.55)` | `rgba(0,0,0,0.75)` | 暗夜更深 |
| `--pyt-focus-ring` | `2px solid #f1c453` | `2px solid #ffd866` | 提亮焦点环 |

> 状态色（success/warning/danger/info）**不变**，保证语义一致；辅色（gold/purple/blue/pink）**不变**，节日感跨主题。

## 10.3 暗色模式禁止事项

- 不允许在组件层写 `[data-theme="dark"] .my-class { color: ... }`，必须通过 token 自动切换。
- 不允许新增 `--pyt-color-*-dark` 形式的变量，dark 是覆写不是新增。
- Phaser 画布**不受**暗色模式影响（光照由 §7 时段 overlay 控制，独立于 HTML 主题）。

---

# 11. 兼容现有组件

现有 5 个文件直接消费旧变量（`--pyt-color-primary` / `--pyt-color-bg-primary` / `--pyt-z-modal` / `--pyt-z-toast` 等），本提案**不删除旧变量**，全部保留为别名。

## 11.1 别名映射表（共 14 个旧变量）

| 旧变量（保留） | 新指向 | 使用方文件数 |
| --- | --- | --- |
| `--pyt-color-bg-primary` | `var(--pyt-color-primary-50)` | 6 |
| `--pyt-color-bg-elevated` | `var(--pyt-color-gray-50)` | 多处 |
| `--pyt-color-bg-soft` | `var(--pyt-color-primary-100)` | 多处 |
| `--pyt-color-bg-sunken` | `var(--pyt-color-gray-100)` | 多处 |
| `--pyt-color-border-strong` | `var(--pyt-color-primary-900)` | 多处 |
| `--pyt-color-border` | `var(--pyt-color-primary-500)` | 多处 |
| `--pyt-color-text-primary` | `var(--pyt-color-primary-900)` | 多处 |
| `--pyt-color-text-muted` | `var(--pyt-color-neutral)` | 多处 |
| `--pyt-color-text-hint` | `var(--pyt-color-gray-700)` | 多处 |
| `--pyt-color-primary` | `var(--pyt-color-gray-700)` | 4+ |
| `--pyt-color-primary-dark` | `var(--pyt-color-primary-900)` | 多处 |
| `--pyt-z-modal` | `var(--pyt-z-modal)`（值由 1000→300） | 2 |
| `--pyt-z-toast` | `var(--pyt-z-toast)`（值由 1100→400） | 2 |
| `--pyt-radius-lg` | `var(--pyt-radius-lg)`（值由 10px→6px） | 多处 |

> 别名以"**新指向，旧名字**"形式保留 1 个版本（R-12 → R-13）。R-13 起组件层完成迁移后，整批别名进入 deprecation 注释，R-14 移除。

## 11.2 不破坏验证

- 任何使用 `--pyt-color-primary` 的组件**继续工作**，颜色无变化（旧 `#6e4f1d` 即新 `--pyt-color-gray-700`）。
- 任何使用 `--pyt-z-modal` 的组件**继续工作**，但层级从 1000 降到 300（仍高于普通内容）。
- `--pyt-radius-lg` 从 10px 降到 6px 是**视觉宪法主动调整**，不视为破坏；如某组件依赖 10px，需在该组件局部覆写。

---

# 12. 迁移路线图（4 阶段）

## 阶段 0：本提案审阅与签字（R-12 当周）

- 视觉设计师 + 前端工程师双签本文档。
- 在 `00-visual-system-overview.md` §13 附录处**反向引用**本文档。
- 输出：本 .md 进入 Draft → Reviewed。

## 阶段 1：tokens.css 落地新变量（R-12 第 2 周）

- 在 `tokens.css` 顶部新增"v1.0 提案变量"区块，包含全部 51 个 token（25 调色 + 5 字体 + 5 字号 + 6 间距 + 5 圆角 + 4 阴影 + 5 z + 4 motion + 2 focus = 61，实际 25+5+5+6+5+4+5+4+2=61）。
- 现有变量**全部保留**，新增为别名（§11.1）。
- 添加暗色模式覆写区块（§10.2）。
- 输出：`tokens.css` 行数从 191 增加到约 380。
- 验证：`npm run dev` 启动，目视检查首页 / 任务页 / 库页无视觉差异。

## 阶段 2：组件层迁移到新变量（R-12 第 3-4 周）

- 5 个已知消费文件按以下顺序逐一迁移：
  1. `HudShell.vue`（HUD 入口，先行验证）
  2. `DialogModal.vue` / `DialogTree.vue`（弹窗类）
  3. `LibraryPage.vue` / `CreditsPage.vue` / `ProblemListPage.vue` / `ExampleListPage.vue`（页面类）
- 迁移时**只换变量名**，不改其它逻辑；每文件单独提交，PR 标题含 `[tokens-v1]`。
- 输出：组件层不再直接消费旧名（旧别名空转）。

## 阶段 3：完成清理（R-13 启动前）

- 标注所有别名为 `/* deprecated v1.0: use --pyt-color-* */`。
- ESLint 自定义规则（`stylelint --custom-syntax` 或 grep CI）禁止新增旧名引用。
- 暗色模式在用户设置面板上线（手动切换 + 系统跟随）。
- 输出：R-13 启动时，所有新写代码强制使用新 token。

## 阶段 4：旧变量移除（R-14）

- 删除所有 `/* deprecated */` 别名。
- 移除 `--pyt-space-6`（32px 不进入阶梯，已用 `space-5 × 1.5` 替代）。
- 输出：`tokens.css` 进入稳定期，仅由本提案 v1.0 + 修订版本驱动。

---

# 13. 验收：vitest tokens.spec.ts 思路

在 `frontend/tests/styles/tokens.spec.ts` 新建测试文件，使用 `jsdom` 环境读取实际 `tokens.css` 应用到 `document.documentElement` 后的 `getComputedStyle`，断言关键 token 不被破坏。

测试矩阵：

| 测试项 | 断言 | 防止 |
| --- | --- | --- |
| 25 个调色板 token 存在 | `getPropertyValue('--pyt-color-primary-50') === '#fef7e3'` | 误删 |
| 5 个字号是整数 px | `parseInt(v) === Number(v.replace('px',''))` | 小数模糊 |
| 6 个间距是 4 倍数（除 0） | `value === 0 || value % 4 === 0` | 不规则间距 |
| 4 个 motion 时长 ≤ 400ms | `parseInt(v) <= 400` | 防"溜冰"动画 |
| 别名 → 新值映射正确 | `getComputedStyle 后 旧 == 新 同值` | 别名失效 |
| 暗色模式覆写生效 | 设置 `data-theme="dark"` 后 `--pyt-color-primary-50` 变 `#1f1810` | 暗模式失败 |
| z-index 5 个递增 | `hud < popover < modal < toast < debug` | 层级错乱 |
| 焦点环包含 `outline` 可用值 | 正则 `/^\d+px solid #[0-9a-f]+$/i` | 格式错 |

测试骨架（思路示意，非最终代码）：

```typescript
import { describe, it, expect, beforeAll } from 'vitest';

describe('pyt design tokens v1.0', () => {
  beforeAll(async () => {
    const css = await import('@/styles/tokens.css?raw');
    const style = document.createElement('style');
    style.textContent = css.default;
    document.head.appendChild(style);
  });
  it('palette & alias', () => {
    expect(getComputedStyle(document.documentElement).getPropertyValue('--pyt-color-bg-primary').trim()).toBe('#fef7e3');
  });
});
```

CI 集成：`npm run test -- tokens.spec.ts` 必须在 pre-commit / pre-push 钩子通过。

---

# 14. 附录：完整 CSS 代码块（仅展示，不写入 tokens.css）

> **本附录仅为提案完整呈现，实际落地由 §12 阶段 1 子任务执行。请勿直接 cp 到 tokens.css。**

```css
/* === Pythontopia tokens.css v1.0 提案（25 调色 + 5 字 + 5 号 + 6 距 + 5 圆 + 4 影 + 5 z + 4 motion + 2 focus） === */

:root {
  /* 主色 4 档 */
  --pyt-color-primary-50:  #fef7e3;
  --pyt-color-primary-100: #fdf3d0;
  --pyt-color-primary-500: #c9a86b;
  --pyt-color-primary-900: #4d3613;

  /* 辅色 4 个 */
  --pyt-color-accent-gold:   #d4af37;
  --pyt-color-accent-purple: #9b59b6;
  --pyt-color-accent-blue:   #5bc0de;
  --pyt-color-accent-pink:   #ffb7c5;

  /* 语义 5 个 */
  --pyt-color-success: #6bbf59;
  --pyt-color-warning: #c2871f;
  --pyt-color-danger:  #d9534f;
  --pyt-color-info:    #2a6f97;
  --pyt-color-neutral: #8b6b47;

  /* 灰阶 6 档 */
  --pyt-color-gray-50:  #fff7e6;
  --pyt-color-gray-100: #f3e4be;
  --pyt-color-gray-300: #d8c293;
  --pyt-color-gray-500: #a08653;
  --pyt-color-gray-700: #6e4f1d;
  --pyt-color-gray-900: #2b1d0a;

  /* 覆盖层 6 个 */
  --pyt-overlay-soft:    rgba(77, 54, 19, 0.12);
  --pyt-overlay-medium:  rgba(77, 54, 19, 0.24);
  --pyt-overlay-strong:  rgba(0, 0, 0, 0.55);
  --pyt-overlay-warm:    rgba(212, 175, 55, 0.18);
  --pyt-overlay-night:   rgba(26, 43, 92, 0.40);
  --pyt-overlay-rain:    rgba(91, 192, 222, 0.08);

  /* 字体 5 个 */
  --pyt-font-sans:        'LXGW WenKai', '霞鹜文楷', 'PingFang SC', system-ui, sans-serif;
  --pyt-font-mono:        'JetBrains Mono', 'Cascadia Code', Menlo, monospace;
  --pyt-font-pixel:       'Ark Pixel 12', var(--pyt-font-sans);
  --pyt-font-pixel-mono:  'Ark Pixel 12 Mono', var(--pyt-font-mono);
  --pyt-font-display:     'Press Start 2P', var(--pyt-font-pixel);

  /* 字号 5 个 */
  --pyt-font-size-xs: 11px;
  --pyt-font-size-sm: 12px;
  --pyt-font-size-md: 14px;
  --pyt-font-size-lg: 18px;
  --pyt-font-size-xl: 24px;

  /* 间距 6 个 */
  --pyt-space-0: 0;
  --pyt-space-1: 4px;
  --pyt-space-2: 8px;
  --pyt-space-3: 12px;
  --pyt-space-4: 16px;
  --pyt-space-5: 24px;

  /* 圆角 5 个 */
  --pyt-radius-none: 0;
  --pyt-radius-sm:   2px;
  --pyt-radius-md:   4px;
  --pyt-radius-lg:   6px;
  --pyt-radius-full: 999px;

  /* 阴影 4 个 */
  --pyt-shadow-card:    0 2px 0 var(--pyt-overlay-soft);
  --pyt-shadow-popover: 0 4px 8px var(--pyt-overlay-medium);
  --pyt-shadow-modal:   0 6px 0 var(--pyt-overlay-medium), 0 12px 24px var(--pyt-overlay-medium);
  --pyt-shadow-inset:   inset 0 1px 0 var(--pyt-overlay-soft);

  /* z-index 5 个 */
  --pyt-z-hud:     100;
  --pyt-z-popover: 200;
  --pyt-z-modal:   300;
  --pyt-z-toast:   400;
  --pyt-z-debug:   500;

  /* 过渡 4 个 */
  --pyt-motion-instant: 80ms;
  --pyt-motion-fast:    120ms;
  --pyt-motion-base:    200ms;
  --pyt-motion-slow:    400ms;

  /* 焦点环 2 个 */
  --pyt-focus-ring:        2px solid #f1c453;
  --pyt-focus-ring-strong: 3px solid #d4af37;

  /* === 兼容别名（R-12 → R-13 过渡，R-14 移除） === */
  --pyt-color-bg-primary:   var(--pyt-color-primary-50);
  --pyt-color-bg-elevated:  var(--pyt-color-gray-50);
  --pyt-color-bg-soft:      var(--pyt-color-primary-100);
  --pyt-color-bg-sunken:    var(--pyt-color-gray-100);
  --pyt-color-border-strong:var(--pyt-color-primary-900);
  --pyt-color-border:       var(--pyt-color-primary-500);
  --pyt-color-text-primary: var(--pyt-color-primary-900);
  --pyt-color-text-muted:   var(--pyt-color-neutral);
  --pyt-color-text-hint:    var(--pyt-color-gray-700);
  --pyt-color-primary:      var(--pyt-color-gray-700);
  --pyt-color-primary-dark: var(--pyt-color-primary-900);
}

/* === 暗色模式：跟随系统 === */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme='light']) {
    --pyt-color-primary-50:  #1f1810;
    --pyt-color-primary-100: #2b2114;
    --pyt-color-primary-500: #8a6c2c;
    --pyt-color-primary-900: #f3e4be;
    --pyt-color-neutral:     #c4a76d;
    --pyt-color-gray-50:     #2b2114;
    --pyt-color-gray-100:    #3a2c1a;
    --pyt-color-gray-700:    #d8c293;
    --pyt-color-gray-900:    #fff7e6;
    --pyt-overlay-strong:    rgba(0, 0, 0, 0.75);
    --pyt-focus-ring:        2px solid #ffd866;
  }
}

/* === 暗色模式：手动切换（优先级最高） === */
:root[data-theme='dark'] {
  --pyt-color-primary-50:  #1f1810;
  --pyt-color-primary-100: #2b2114;
  --pyt-color-primary-500: #8a6c2c;
  --pyt-color-primary-900: #f3e4be;
  --pyt-color-neutral:     #c4a76d;
  --pyt-color-gray-50:     #2b2114;
  --pyt-color-gray-100:    #3a2c1a;
  --pyt-color-gray-700:    #d8c293;
  --pyt-color-gray-900:    #fff7e6;
  --pyt-overlay-strong:    rgba(0, 0, 0, 0.75);
  --pyt-focus-ring:        2px solid #ffd866;
}

/* === 降低动画偏好 === */
@media (prefers-reduced-motion: reduce) {
  :root {
    --pyt-motion-instant: 40ms;
    --pyt-motion-fast:    60ms;
    --pyt-motion-base:    100ms;
    --pyt-motion-slow:    200ms;
  }
}
```

---

> **本文是提案，不是实施单**。任何对 `tokens.css` 的实际修改必须先开 PR 引用本文档章节，并按 §12 阶段顺序推进；跳阶段直接重构（如阶段 1 未完成就改组件）视为破窗。
