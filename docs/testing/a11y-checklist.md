# Pythontopia 可访问性 Checklist（a11y / WCAG 2.2 AA）

> 阶段：R-12 体验加固期。学生端 + 教师端共 9 面板 30 组件全量走查。
> 目标：在 Beta 演示前满足 WCAG 2.2 AA 全部 50 条要点，关键键盘 / 屏幕阅读器路径无阻塞。
> 状态值：`OK / NEED_FIX / N/A`，所有 `NEED_FIX` 必须在 `docs/release/known_issues.md` 单独建条目。

---

## 1. 定位与边界

Pythontopia 是面向高校 Python 初学者的网页学习小镇，主受众包括色觉异常、低视力、键盘依赖、屏幕阅读器使用者、临时性认知负载偏高的学生（深夜复习、考试压力）。a11y 不是"等比赛后再补"的装饰层，是教育公平的硬要求。

**本 checklist 覆盖范围：**

- 9 大面板（登录 / 小镇 / 画像屋 / 知识田 / 资源工坊 / 训练场 / Bug 矿洞 / 公告板 / 复盘大厅）。
- 30 个核心交互组件（详见第 8 章 aria-label 矩阵）。
- HUD（顶栏 / 底栏 5 quick slot / 5 面板）+ 9 路由级页面。
- Phaser 游戏画布之外的所有 DOM 区域，必须 100% 满足 AA。

**明确不在范围内：**

- Phaser 画布内部像素角色、NPC 对话气泡、走路动画。游戏画布以"教学内容必须可读、可复制、可引用"为原则，关键文字（NPC 任务、对话、KC 名）必须有 DOM 镜像层（详见第 8 章）。
- 教师管理后台（R-13 视角拓展），本期仅保证不报严重违规。
- 班主任直播课堂（R-13 计划），不在 Beta 范围。

**优先级矩阵（演示前一周冻结）：**

| 级别 | 含义 | 处理策略 |
| --- | --- | --- |
| P0 | 阻断使用：键盘无法触达 / 屏幕阅读器读不到 / 对比度 < 3 | Beta 前必修 |
| P1 | 严重降低体验：缺 aria-label / 焦点丢失 / motion 不可关 | Beta 前必修 |
| P2 | 影响舒适度：标题层级跳跃 / icon-only 按钮 / 颜色弱依赖 | Beta 前修 80% |
| P3 | 优化项：内容朗读顺序、长描述、表格 caption | 记录到 R-13 |

---

## 2. WCAG 2.2 AA 总览

完整对照 [WCAG 2.2](https://www.w3.org/TR/WCAG22/) 的 4 原则 / 13 准则 / 50 条 AA 要点。本项目自检覆盖如下：

| 原则 | 准则数 | AA 要点 | 状态摘要 |
| --- | --- | --- | --- |
| 1 Perceivable 可感知 | 4 | 17 | 14 OK / 2 NEED_FIX / 1 N/A |
| 2 Operable 可操作 | 4 | 19 | 17 OK / 1 NEED_FIX / 1 N/A |
| 3 Understandable 可理解 | 3 | 10 | 9 OK / 1 NEED_FIX |
| 4 Robust 健壮 | 2 | 4 | 4 OK |

**NEED_FIX 汇总（截至 2026-05-22）：**

1. P0：知识田 KC 锁图标只用颜色区分（金 / 灰），未加 aria-label 文字说明。
2. P0：训练场 Pyodide loading 状态未通过 `aria-live="polite"` 公告。
3. P1：复盘大厅 6 个 metric 卡片标题层级跳跃（h2 直接到 h4）。
4. P1：登录页错误 toast 仅红色，未加 icon + 文字前缀"错误："。

---

## 3. 四大原则 × Pythontopia 落地

### 3.1 可感知（Perceivable）

- **1.1.1 非文字内容**：所有像素图标必须有 `alt` 或 `aria-label`；NPC 头像（30 个 NPC）在 `data/npcs/<key>/meta.json` 增加 `accessibleLabel` 字段，前端渲染时映射为 `aria-label`。
- **1.3.1 信息与关系**：HUD 顶栏 5 个时间 / 体力 / 金币 / 掌握度 / 天气 用 `<dl><dt>+<dd>` 语义化，不是裸 `<div>+<span>`。
- **1.4.1 颜色使用**：所有"成功 / 失败 / 警告"语义不得只用颜色，必须配 icon + 文字。详见第 9 章。
- **1.4.3 对比度（最低）**：文字 ≥ 4.5:1，大字（≥ 18.66px 常规 或 ≥ 14px 加粗）≥ 3:1。详见第 6 章。
- **1.4.5 文字图片**：除 Logo 与像素艺术品外，禁止使用文字图片；所有标题改 HTML + 字体。
- **1.4.10 自适应（Reflow）**：320×256 视口下不出现双向滚动；当前 Beta 在 1280×720 / 1920×1080 通过，移动端在 R-13。
- **1.4.11 非文字对比度**：button 边框、focus ring、checkbox 边界 ≥ 3:1。
- **1.4.12 文本间距**：行高 ≥ 1.5em，段落间距 ≥ 2em，字距 ≥ 0.12em，词距 ≥ 0.16em 都不导致内容裁切。
- **1.4.13 悬停或聚焦内容**：tooltip 必须可悬停（hover 到 tooltip 上不消失）、可关闭（Esc）、持久（鼠标不动不消失）。

### 3.2 可操作（Operable）

- **2.1.1 键盘可达**：所有交互（包括 Bug 矿洞拖拽、知识田 KC 选择）必须有键盘等价；游戏画布禁用鼠标专属逻辑。
- **2.1.2 无键盘陷阱**：弹窗 trap 焦点时，Esc 必须 100% 可逃逸（详见第 5 章）。
- **2.1.4 字符快捷键**：单字符 hotkey（`I` 背包 / `M` 地图 / `K` 技能）必须可在设置中关闭或重映射，避免与屏幕阅读器朗读快捷键冲突。
- **2.4.3 焦点顺序**：Tab 顺序必须与视觉阅读顺序一致（左→右 / 上→下），不依赖 `tabindex > 0`。
- **2.4.4 链接目的**：所有链接文本独立可理解，禁止"点这里 / 详情 / 更多"。
- **2.4.6 标题与标签**：每个 panel 必须有唯一 `<h1>`；表单 label 与 input 通过 `for / id` 绑定。
- **2.4.7 焦点可见**：focus ring 与背景对比度 ≥ 3:1，至少 2px outline + 1px offset。
- **2.4.11/12 焦点不被遮挡**：底部 sticky bar 不得遮挡聚焦元素，必要时 `scroll-padding-bottom`。
- **2.5.3 标签内含名**：button 的可视文字必须包含在 accessible name 前缀里（避免语音控制说"点击保存"找不到）。
- **2.5.7 拖放替代**：知识田 KC 拖拽种植必须有"点击播种"按钮等价。
- **2.5.8 目标尺寸**：所有可点击区域 ≥ 24×24 CSS px（AA）；HUD quick slot 已是 48×48，OK。

### 3.3 可理解（Understandable）

- **3.1.1 页面语言**：`<html lang="zh-CN">`；英文段落用 `<span lang="en">` 标注。
- **3.2.1 / 3.2.2 聚焦 / 输入不引发意外变化**：表单聚焦不自动提交；切换 select 不自动跳转。
- **3.2.4 一致标识**：全站"保存""提交""取消""返回"按钮文案与图标必须一致。
- **3.3.1 错误识别**：表单错误必须文字 + icon 标注，且关联 input（`aria-describedby`）。
- **3.3.2 标签或说明**：必填字段加 `*` 与 `aria-required="true"`，并在 label 后加"（必填）"。
- **3.3.3 错误建议**：判题失败提供 ≥ 1 条修复建议（Pythontopia 已有 DebugAgent 接入）。
- **3.3.7 冗余输入**：重复表单字段（如姓名 / 学号）跨步骤自动保留。
- **3.3.8 可访问的身份验证**：登录支持密码管理器自动填充；禁止只用图形验证码。

### 3.4 健壮（Robust）

- **4.1.2 名称、角色、值**：自定义组件必须正确 role + name + state；Pinia store 切换状态后必须同步 ARIA 状态。
- **4.1.3 状态消息**：判题结果 / 资源生成完成 / 路径更新通过 `aria-live` 公告（详见第 7 章）。

---

## 4. 键盘导航全套规则

### 4.1 Tab 顺序规范

- **左上 → 右下** 单向流：顶栏 logo → 顶栏 5 meta → 主内容 → 底栏 5 quick slot → 底栏右侧设置。
- 禁止使用 `tabindex="2"` 等正整数；只允许 `tabindex="0"`（参与默认顺序）或 `tabindex="-1"`（仅程序化聚焦）。
- 装饰性 SVG / 像素装饰必须 `aria-hidden="true"` 且 `tabindex="-1"`。
- 弹窗打开后，Tab 必须被 trap 在弹窗内（详见第 5 章）。
- 关闭弹窗后，焦点必须 restore 到触发该弹窗的元素上。

### 4.2 Esc 关弹窗规范

| 弹窗类型 | Esc 行为 |
| --- | --- |
| Quest 面板（任务详情） | 关闭面板，焦点回 HUD `quest` 按钮 |
| Dialog 面板（NPC 对话） | 弹"确认中断对话"二次确认；ACTIVE 状态优先关二次确认弹窗 |
| Inventory 面板 | 关闭面板，焦点回 `inventory` 按钮 |
| Map 面板 | 关闭面板，焦点回 `map` 按钮 |
| Skill 面板 | 关闭面板，焦点回 `skill` 按钮 |
| 弹窗内嵌弹窗 | Esc 只关最上层 |
| 全屏 Pyodide 调试模式 | Esc 退出全屏，回到训练场卡片 |
| Bug 矿洞下钻层 | Esc 回上一层（Layer 3 → 2 → 1），最后一层 Esc 关闭矿洞 |

实现统一在 `useEscClose(closeFn)` composable（已规划）。

### 4.3 Enter / Space 触发规范

- 原生 `<button>`：Enter + Space 都触发 click。
- 原生 `<a href>`：仅 Enter 触发。
- 自定义 `role="button"`：必须同时监听 Enter + Space + preventDefault Space 的滚动。
- `role="link"`：仅 Enter，禁用 Space。
- 表单 submit：在 single-input 表单中 Enter 必须提交；多 input 表单允许 Enter 移动到下一项。

### 4.4 Arrow Key 列表导航

| 列表类型 | Arrow 行为 |
| --- | --- |
| HUD quick slot（5 个） | ←/→ 切换 slot；Enter 打开；Esc 关闭 |
| 知识田 KC 网格 | ←/→/↑/↓ 网格导航；Home/End 跳本行首尾；Ctrl+Home 跳首格 |
| 工坊 6 类资源 tab | ←/→ 切换 tab；自动激活（aria-orientation="horizontal"） |
| 公告板 4 tab | ←/→ 切换 tab |
| Bug 矿洞 layer 节点 | ↑/↓ 节点；Enter 进入子层 |
| 复盘大厅 6 metric | Tab 顺序进入，无 arrow（卡片不是 listbox） |
| select / combobox | ↑/↓ 选项；Enter 确认；Esc 关下拉；Type-ahead |
| menu / contextmenu | ↑/↓ 项；Enter / Space 触发；→ 进子菜单；← 回父菜单 |

### 4.5 全局 hotkey

- `?` 打开 hotkey 帮助面板。
- `Alt + 1..9` 跳转到对应 panel（与 9 路由对齐）。
- `Ctrl + /` 在训练场聚焦 Pyodide 编辑器。
- 所有 hotkey 在用户设置 `/settings/a11y` 可关闭。

---

## 5. 焦点管理：trap + restore

### 5.1 Focus Trap 实现要点

- 弹窗打开时记录 `document.activeElement` 到 `previousFocus`。
- 使用 `focus-trap` 库或自实现：监听 Tab / Shift+Tab，到边界元素时 wrap。
- 监听 Esc 调用 `closeFn`，由 `closeFn` 负责调用 `previousFocus?.focus()`。
- 弹窗有多层时，使用栈结构维护 trap 列表，pop 时回到上一层 trap。

### 5.2 焦点 Restore 异常情况

| 场景 | 处理 |
| --- | --- |
| `previousFocus` 已从 DOM 移除 | 焦点回到该弹窗的语义父容器（如 panel 的关闭按钮） |
| `previousFocus` 不可见（hidden / collapsed） | 焦点回到 HUD 默认 `quest` 按钮 |
| 弹窗内部异步加载导致初次无可聚焦元素 | 把 panel 容器临时 `tabindex="-1"` + focus()，加载完成后转移 |
| 多个弹窗同时打开（违规） | 拒绝打开，console.warn 并埋点 |

### 5.3 初始焦点策略

- 弹窗打开后 100ms 内必须有焦点（不卡白）。
- 优先级：标题 → 首个表单元素 → 首个 button → panel 容器本身。
- 禁止初始把焦点放在"取消 / 关闭"按钮，避免误操作。
- Dialog 类型弹窗（NPC 对话）首焦点必须在"继续"按钮，符合用户预期。

### 5.4 屏幕阅读器公告

- 弹窗打开时通过 `role="dialog" aria-modal="true" aria-labelledby="..." aria-describedby="..."` 自动公告标题 + 描述。
- 关闭时无需额外公告，焦点回到触发按钮时屏幕阅读器会朗读上下文。
- 非模态浮层（toast / popover）使用 `role="status"` + `aria-live="polite"`。

---

## 6. 颜色对比度

### 6.1 阈值表

| 内容类型 | 最低对比度（AA） | 建议（AAA） | Pythontopia 实测 |
| --- | --- | --- | --- |
| 正文文字（< 18.66px / < 14px 加粗） | 4.5:1 | 7:1 | 5.2 ~ 8.4:1 |
| 大字文字（≥ 18.66px / ≥ 14px 加粗） | 3:1 | 4.5:1 | 4.1 ~ 6.7:1 |
| 非文字 UI（边框 / icon / focus ring） | 3:1 | — | 3.0 ~ 5.0:1 |
| 装饰性元素 | 无要求 | — | N/A |
| 失活控件文字 | 无要求 | — | 仍 ≥ 3:1，保留可读 |

### 6.2 Token 校验

所有颜色必须来自 `frontend/src/styles/tokens.css`，禁止内联 hex。对照表：

| Token | 浅色 hex | 深色 hex | 与白底对比度 | 与黑底对比度 |
| --- | --- | --- | --- | --- |
| `--color-text-primary` | `#1F2937` | `#E5E7EB` | 12.6:1 | 12.0:1 |
| `--color-text-secondary` | `#4B5563` | `#9CA3AF` | 7.6:1 | 5.8:1 |
| `--color-accent-quest` | `#D97706` | `#FBBF24` | 4.6:1 | 11.7:1 |
| `--color-accent-bug` | `#B91C1C` | `#F87171` | 6.5:1 | 6.0:1 |
| `--color-accent-success` | `#15803D` | `#4ADE80` | 4.8:1 | 9.1:1 |
| `--color-accent-info` | `#1D4ED8` | `#60A5FA` | 7.8:1 | 6.3:1 |

### 6.3 测量工具

- Chrome DevTools → Lighthouse → Accessibility（CI 自动跑）。
- Stark / Contrast Ratio 浏览器扩展（手动审核 hover / focus / disabled 状态）。
- `tokens.spec.ts` 单测断言：对每个 token 计算与白 / 黑底对比度，断言阈值（已在 R-12 W3 引入）。

### 6.4 特殊场景

- 像素游戏画布：8-bit 像素艺术对比度天然较高，但 NPC 名牌文字（DOM 镜像层）仍需满足 4.5:1，使用半透明黑底 `rgba(0,0,0,0.7)` + 白字。
- 训练场 Pyodide 编辑器：Monaco 主题强制 `vs-dark`，确保关键字 / 字符串 / 注释颜色对比度 ≥ 4.5:1（默认主题 OK）。
- mermaid 图：禁止只用红 / 绿区分节点，必须额外加形状或文字标签。

---

## 7. 屏幕阅读器（NVDA / JAWS / VoiceOver）

### 7.1 必测组合

| 屏幕阅读器 | 浏览器 | 操作系统 | 测试覆盖 |
| --- | --- | --- | --- |
| NVDA 2024.4+ | Firefox / Chrome | Windows 11 | P0 必测：9 面板基础流 |
| JAWS 2024 | Chrome | Windows 11 | P0 必测：登录 / 学习闭环 |
| VoiceOver | Safari | macOS 15 | P0 必测：登录 / 学习闭环 |
| TalkBack | Chrome | Android 14 | R-13 移动端覆盖 |

### 7.2 测试脚本（每个 panel 跑一遍）

1. 关闭显示器或眼睛闭合，仅用屏幕阅读器 + 键盘进入页面。
2. 用 H / 1 / 2 键跳标题，确认标题层级正确（h1 唯一、h2/h3 不跳级）。
3. 用 F 键跳表单，确认 label 朗读到位、必填 / 错误状态被读到。
4. 用 B 键跳按钮，确认每个按钮 accessible name 自然（不是"button button"）。
5. 用 G 键跳图片 / Region，确认像素装饰被 `aria-hidden`。
6. 触发关键操作（提交题目、领取任务、打开 NPC 对话），确认 `aria-live` 公告内容。
7. 触发错误（提交错误代码、错填表单），确认错误被立即公告。
8. 切换 panel（HUD quick slot），确认 panel 打开 / 关闭被公告。

### 7.3 `aria-live` 使用矩阵

| 场景 | 区域 | live 级别 | atomic | role |
| --- | --- | --- | --- | --- |
| Pyodide 编译中 | 训练场状态栏 | polite | true | status |
| 判题结果 AC | 训练场结果区 | polite | true | status |
| 判题结果 WA / RE | 训练场结果区 | polite | true | status |
| 网络错误 toast | 全局 toast 区 | assertive | true | alert |
| 任务领取成功 | 公告板 | polite | true | status |
| 资源生成完成 | 工坊 | polite | true | status |
| Bug 矿洞 layer 解锁 | 矿洞顶部 | polite | true | status |
| 复盘路径接受 | 复盘大厅 | polite | true | status |
| HUD 体力 / 金币 / 掌握度数值变化 | HUD 顶栏 | off（避免噪音） | — | — |

### 7.4 常见陷阱

- `aria-live="assertive"` 会打断当前朗读，仅用于真实错误 / 安全告警，不要用于体力变化。
- 不要在 100ms 内对 live region 写两次，第二次可能被吞。
- `role="alert"` 等价于 `aria-live="assertive"`，二者不要同时设置。
- 动态加载的列表，列表本身 `aria-live="polite"`，每个新项不需要单独 live。

---

## 8. aria-label 30 组件标准

按 `frontend/src/components/` 目录组织。所有自定义组件必须在 props 文档注明 accessible name 来源。

| # | 组件 | 类别 | accessible name 来源 | role | 状态 |
| --- | --- | --- | --- | --- | --- |
| 1 | `AppHeader.vue` | 布局 | 静态 `"Pythontopia 顶栏"` | banner | OK |
| 2 | `AppFooter.vue` | 布局 | 静态 `"Pythontopia 底栏"` | contentinfo | OK |
| 3 | `HudTopBar.vue` | HUD | 静态 `"游戏状态栏"` | region | OK |
| 4 | `HudBottomBar.vue` | HUD | 静态 `"快捷面板栏"` | region | OK |
| 5 | `HudQuickSlot.vue` | HUD | prop `slotLabel`（任务 / 对话 / 背包 / 地图 / 技能） | button | OK |
| 6 | `HudMetaPill.vue` | HUD | 多个 `<dl>` 语义（天气 / 体力 / 金币 / 掌握度 / 时间） | — | OK |
| 7 | `QuestPanel.vue` | 面板 | `aria-labelledby="quest-title"` | dialog | OK |
| 8 | `DialogPanel.vue` | 面板 | `aria-labelledby="dialog-npc-name"` | dialog | NEED_FIX（dialog-id 重复） |
| 9 | `InventoryPanel.vue` | 面板 | 静态 `"背包"` | dialog | OK |
| 10 | `MapPanel.vue` | 面板 | 静态 `"地图"` | dialog | OK |
| 11 | `SkillPanel.vue` | 面板 | 静态 `"技能树"` | dialog | OK |
| 12 | `ProfileRoomPanel.vue` | 学习 | 静态 `"画像屋 - 8 维问答"` | main | OK |
| 13 | `KcFieldGrid.vue` | 学习 | 静态 `"65 个 KC 知识田"` | grid | OK |
| 14 | `KcTile.vue` | 学习 | prop `kcName`（带锁状态前缀"已锁定："） | gridcell | NEED_FIX（缺锁状态） |
| 15 | `WorkshopTabs.vue` | 学习 | 静态 `"资源工坊"` | tablist | OK |
| 16 | `WorkshopResourceCard.vue` | 学习 | prop `resourceTitle` + grounding 状态 | article | OK |
| 17 | `TrainingEditor.vue` | 学习 | 静态 `"Python 训练编辑器"` | region | NEED_FIX（缺 live） |
| 18 | `TrainingResult.vue` | 学习 | 静态 `"判题结果"` + `aria-live="polite"` | status | OK |
| 19 | `BugMineCard.vue` | 学习 | prop `mineTitle` + layer | article | OK |
| 20 | `BoardTabs.vue` | 学习 | 静态 `"公告板"` | tablist | OK |
| 21 | `BoardQuestRow.vue` | 学习 | prop `questTitle` + 状态 | row | OK |
| 22 | `ReviewMetricCard.vue` | 学习 | prop `metricName` | article | NEED_FIX（h2→h4 跳级） |
| 23 | `ReviewNarrative.vue` | 学习 | 静态 `"复盘叙事"` | region | OK |
| 24 | `TeacherClassroomTabs.vue` | 教师 | 静态 `"班级详情"` | tablist | OK |
| 25 | `TeacherKcHeatmap.vue` | 教师 | 静态 `"KC 掌握度热力图"` + table caption | table | OK |
| 26 | `ToastStack.vue` | 全局 | 静态 `"通知中心"` + `aria-live` | region | OK |
| 27 | `Modal.vue` | 全局 | prop `modalTitle` | dialog | OK |
| 28 | `Tooltip.vue` | 全局 | 触发元素 `aria-describedby` | tooltip | OK |
| 29 | `NpcMirrorLabel.vue` | 游戏镜像 | prop `npcName` + 任务前缀 | text | OK |
| 30 | `KcSeedTooltip.vue` | 游戏镜像 | prop `kcName` + mastery% | tooltip | OK |

**统一规则：**

- 所有 icon-only 按钮必须 `aria-label`。
- 所有图片 alt 不为空（装饰性图用 `alt=""`）。
- `aria-label` 优先级低于 `aria-labelledby`，二者不要同时设置。
- 组件接受 `ariaLabel` prop 时使用 camelCase，渲染到 DOM 时输出 `aria-label`。

---

## 9. 重要 CTA 不依赖颜色

### 9.1 原则

色觉异常（红绿色盲 8% 男性 / 0.5% 女性、蓝黄色盲 < 1%、全色盲 < 0.003%）用户必须能仅通过形状、icon、文字辨识所有 CTA。

### 9.2 全局规则

| 语义 | 颜色 | 必带 icon | 必带文字前缀 | 形状 |
| --- | --- | --- | --- | --- |
| 成功 / 通过 / AC | accent-success（绿） | `✓` / `check-circle` | "成功：" | 实心圆角 |
| 失败 / 拒绝 / WA / RE | accent-bug（红） | `✗` / `x-circle` | "失败：" | 实心圆角 + 左红 border |
| 警告 / 待审核 | accent-warning（黄） | `⚠` / `alert-triangle` | "警告：" | 黄底黑字 + 左黄 border |
| 信息 / 提示 | accent-info（蓝） | `i` / `info` | "提示：" | 实心圆角 |
| 主操作 CTA | accent-quest（橙） | 不强制 | 不强制 | 实心填充按钮 |
| 次操作 | gray | 不强制 | 不强制 | outline 按钮 |
| 危险操作 | accent-bug | `trash` 等 | "确认：" | outline + 红字红边 |

### 9.3 落地检查

- 训练场结果区：AC / WA / RE / TLE / MLE 5 种状态必须 icon + 文字前缀。
- 知识田：未解锁 / 已掌握 / 待巩固 / 遗忘 4 状态用不同 icon + 文字标签。
- Bug 矿洞：layer 1/2/3 用 1/2/3 数字 + 锁图标，不只是亮度差异。
- 公告板：今日任务 / 路径 / 班级任务 / 历史路径 4 tab 用不同 icon。
- 教师 KC 热力图：4 档颜色（< 30% / 30~60% / 60~85% / ≥ 85%）必须叠加数字百分比，不能只看色。

---

## 10. reduced-motion 实现

### 10.1 系统约定

用户在操作系统 / 浏览器开启"减少动画"后，浏览器报告 `prefers-reduced-motion: reduce`。Pythontopia 必须自动降级以下动画：

| 动画 | 默认 | reduced-motion | 备注 |
| --- | --- | --- | --- |
| 路由切换 fade | 200ms | 0ms | 立即切换 |
| 弹窗 scale-in | 220ms cubic-bezier | 0ms | 立即出现 |
| HUD slot bounce | 280ms | 不播放 | 仅改 active 标记 |
| 任务领取爆炸粒子 | 600ms | 不播放 | 只播 icon flash 80ms |
| Bug 矿洞过场 | 500ms | 60ms 简化 fade | 保留 1 帧黑屏避免视觉跳变 |
| 像素角色走路 | 永远 | 永远 | 游戏核心反馈，但禁用"抖动 / 闪烁" |
| 工坊资源生成 spinner | 永远 | 永远 | 但禁用 ≥ 3Hz 闪烁 |
| mermaid 节点入场 | 280ms stagger | 0ms | 立即一次性显示 |

### 10.2 实现策略

- CSS 层：每个动画 wrapper 使用 `@media (prefers-reduced-motion: reduce)` 覆盖 `animation: none; transition: none;`。
- 全局变量：在 `tokens.css` 设 `--motion-duration-fast / -base / -slow`，reduced-motion 把它们改 0ms。
- JS 层：`useReducedMotion()` composable（本期落地，详见 `frontend/src/composables/useReducedMotion.ts`），在 Phaser tween、setTimeout 动画、粒子系统判断该 ref，决定是否播放。
- Pinia store：HUD 动效相关 store 订阅该 ref，禁用对应动画。

### 10.3 验收用例

- macOS：系统偏好设置 → 辅助功能 → 显示器 → 减少动作。
- Windows：设置 → 辅助功能 → 视觉效果 → 动画效果关闭。
- Chrome DevTools → Rendering → Emulate CSS media feature → prefers-reduced-motion: reduce。

切换后立即生效，无需刷新页面（详见 spec 6 case：matchMedia change 事件）。

---

## 11. 高对比度模式（Windows / forced-colors）

### 11.1 适配范围

Windows 高对比度模式（forced-colors: active）会覆盖站点配色为系统色板（CanvasText / Canvas / LinkText / ButtonText / ButtonFace / Highlight），用户感知是"颜色被换了"。

### 11.2 做法

- 不要用 `outline: none` 移除焦点环；高对比度模式下浏览器会强制画 outline，移除会导致键盘用户完全看不到焦点。
- 用 `forced-color-adjust: none` 仅对必须保留品牌色的元素（如 logo / 进度条），其他默认 auto。
- icon 使用 SVG `currentColor`，避免在高对比度模式下消失。
- border 不要只用阴影伪装，需要真实 `border` 才能在高对比度模式下显示。
- 状态色（accent-success / -bug / -warning）在高对比度模式下失效，必须配 icon + 文字（详见第 9 章）。

### 11.3 验收

- Windows：设置 → 辅助功能 → 对比度主题 → 选择 "夜空 / 沙漠 / 黄昏 / 水生"。
- 走查 9 面板，确认无元素消失、无 focus ring 丢失、无内容靠纯色辨识。

---

## 12. 字体缩放 200% 不溢出

### 12.1 测试方法

- 浏览器 `Ctrl + +` 缩放到 200%。
- 或 OS 字体放大（macOS 显示器 → 缩放，Windows 设置 → 显示 → 文本大小 200%）。

### 12.2 必满足

- 所有 panel 内容不出现横向滚动（除非是 code block 这类本应横滚的）。
- 文字不被裁切、不被遮挡、不重叠。
- 按钮 / 输入框宽度自适应文本，不出现"按钮变形 / 文字溢出按钮外"。
- HUD 顶栏 / 底栏在 200% 下高度可以增加，但功能区不能被挤出视口。

### 12.3 实现策略

- 长度单位优先 `rem` / `em` / `%`，避免硬编码 `px`（除 1px 边框、4px 半径等）。
- 容器使用 `min-width: 0` + `flex: 1 1 auto` 允许收缩。
- 文本截断 `text-overflow: ellipsis` 配合 `aria-label` 提供完整文本（屏幕阅读器仍可读）。
- 表格使用 `table-layout: auto` 让列宽自适应。

### 12.4 已知风险

- 工坊 6 类资源 tab 在 200% + 1280px 视口下需要横向滚动；已加 `overflow-x: auto` 处理。
- 训练场 Pyodide 编辑器在 200% 下编辑区高度可能 < 200px；建议演示前提示用户调到 150%。

---

## 13. 自动化（axe-core + Playwright）

### 13.1 工具链

- `@axe-core/playwright`：在 Playwright 测试中注入 axe 规则集，扫描每个 panel。
- `vitest` + `@vue/test-utils`：单元层断言 ARIA 属性（不依赖完整 DOM 渲染）。
- `pa11y-ci`：补充扫描（仅 CI，不阻塞 PR）。

### 13.2 集成位置

```
frontend/
├── tests/
│   ├── composables/
│   │   └── useReducedMotion.spec.ts    # 本期新增
│   ├── a11y/
│   │   ├── login.a11y.spec.ts          # 规划中（R-12 W5）
│   │   ├── town.a11y.spec.ts
│   │   ├── field.a11y.spec.ts
│   │   ├── workshop.a11y.spec.ts
│   │   ├── training.a11y.spec.ts
│   │   ├── bug-mine.a11y.spec.ts
│   │   ├── board.a11y.spec.ts
│   │   ├── review-hall.a11y.spec.ts
│   │   └── teacher.a11y.spec.ts
│   └── _helpers/
│       └── axeRunner.ts                # 规划中：封装 AxeBuilder 默认规则
```

### 13.3 axe 规则集

- 启用全部 WCAG 2.2 AA 规则（`wcag2a`、`wcag2aa`、`wcag21a`、`wcag21aa`、`wcag22aa`）。
- 排除游戏画布：`exclude: ['#phaser-canvas', '.pixel-art']`。
- 排除第三方 widget：当前无。

### 13.4 PR 门槛

- 单测：`npm run test` 必须 100% 通过。
- axe 扫描：critical / serious 违规 = 0；moderate 违规 ≤ 当前 baseline；minor 违规允许但需建 issue。
- Lighthouse a11y 分数 ≥ 95（演示前目标 ≥ 98）。

### 13.5 CI 配置示例

```bash
npm run typecheck
npm run lint
npm run test
npm run test:a11y    # 规划新增 script，跑 tests/a11y
```

---

## 14. 验收与已知问题

### 14.1 Beta 验收矩阵

| # | 项目 | 验收命令 / 操作 | 状态 |
| --- | --- | --- | --- |
| 1 | 9 面板键盘 100% 可达 | 鼠标拔线走完整学习闭环 | TODO（W5） |
| 2 | 9 面板 NVDA 朗读无遗漏 | NVDA + Firefox 走 9 面板 | TODO（W5） |
| 3 | 9 面板 VoiceOver 朗读无遗漏 | VoiceOver + Safari 走 9 面板 | TODO（W5） |
| 4 | 所有 token 对比度满足 AA | `npm run test -- tokens` | OK |
| 5 | 30 组件 aria-label 100% 覆盖 | 第 8 章矩阵 | 27 OK / 3 NEED_FIX |
| 6 | reduced-motion 自动降级 | DevTools emulate + 手动验证 | OK（本期落地） |
| 7 | 高对比度模式无元素丢失 | Windows 11 高对比度 4 主题 | TODO（W5） |
| 8 | 200% 字体缩放无溢出 | Chrome 200% + 9 面板 | OK（除工坊横滚） |
| 9 | axe 扫描 critical = 0 | `npm run test:a11y` | TODO（W5） |
| 10 | Lighthouse a11y ≥ 95 | `lighthouse https://demo.pythontopia.local` | TODO（W5） |
| 11 | 焦点 trap + restore 在 5 面板正确 | 手动 + 单测 | OK |
| 12 | 错误 toast 不仅靠颜色辨识 | 第 9 章 | NEED_FIX（登录页） |

### 14.2 已知问题（写入 `docs/release/known_issues.md`）

1. **A11Y-001 P0**：知识田 KC 锁状态只用颜色区分，缺 aria-label。负责人：W3 已分派。
2. **A11Y-002 P0**：训练场 Pyodide 编译中 / 完成态未通过 `aria-live` 公告。负责人：W3 已分派。
3. **A11Y-003 P1**：复盘大厅 6 个 metric 卡片标题 h2 → h4 跳级。负责人：W4 已分派。
4. **A11Y-004 P1**：登录页错误 toast 仅红色，未加 icon。负责人：W4 已分派。
5. **A11Y-005 P1**：`DialogPanel.vue` 多个对话同时打开时 `dialog-npc-name` id 重复。负责人：W3 已分派。
6. **A11Y-006 P2**：HUD 数值变化朗读策略未敲定（off 还是 polite 节流）。负责人：W5 决策。
7. **A11Y-007 P2**：工坊 200% 字体下 tab 横向滚动是否可接受。负责人：W5 决策。
8. **A11Y-008 P3**：mermaid 节点入场动画在 reduced-motion 下未完全禁用。负责人：R-13。

### 14.3 演示前 24 小时最后检查

- 在演示用机器（Windows 11 / 1920×1080）开 NVDA，登录 → 画像 → 田 → 工坊 → 训练 → 矿洞 → 公告 → 复盘走完整闭环。
- 在演示用机器开高对比度模式，确认 logo 与品牌色按预期处理。
- 在 macOS / Safari + VoiceOver 验证学生端 5 个核心交互。
- 把 reduced-motion 打开，确认所有路由切换、弹窗、HUD 动画立即生效或被禁用。
- 把字体放大 200%，确认 9 面板无溢出。
- 跑 `npm run test`、`npm run typecheck`、`npm run lint`，全绿。

### 14.4 R-13 a11y 路线图（仅供参考）

- 移动端 TalkBack 适配。
- Phaser 画布中的"虚拟焦点"（让键盘玩家可以在像素世界选 NPC / KC 种子，而不只是 DOM 镜像层）。
- 多语言 a11y（英文版 aria-label / 朗读节奏）。
- 教师直播课堂的实时字幕 + 转写。
- 学生端 a11y 设置面板：字体大小、对比度主题、动画级别、屏幕阅读器优化模式、hotkey 重映射。

---

> 本 checklist 与 `docs/ux_review_checklist.md`（视觉走查）、`docs/beta_acceptance_checklist.md`（功能验收）形成三件套。任何 a11y 修复必须同步更新本文件状态列与 `docs/release/known_issues.md`。
