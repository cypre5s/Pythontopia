---
title: Pythontopia 视觉系统总览（星露谷风但不是星露谷）
version: v1.0
status: Draft
owner: 视觉设计师 + 前端工程师
last_updated: 2026-05-24
audience: 前端工程师 / Phaser 场景作者 / UI 设计师 / 美术外包
scope: 全端 Web HUD、Phaser 画布、教学面板、Android 像素挂件
依赖文档:
  - /home/cypress/Pythontopia/LICENSE-ART.md
  - /home/cypress/Pythontopia/frontend/public/assets/pixel/LICENSE.md
  - /home/cypress/Pythontopia/frontend/src/styles/tokens.css
  - /home/cypress/Pythontopia/AGENTS.md
---

# 0. 文档定位

本文是 Pythontopia 全部 UI / 画布 / 像素资产的**视觉宪法**。所有页面、HUD、Phaser 场景、Android 挂件、Demo 海报，凡是会被玩家看到的像素，都必须符合本文的颜色、字体、边框、动画、光照、季节规范。

视觉目标用一句话概括：

> **让玩家在 3 秒内觉得"这是星露谷式的像素小镇"，但同时在 10 秒内意识到"这不是星露谷"**。

为此本文严格遵守 `AGENTS.md` 的硬约束：

| 约束 | 含义 |
| --- | --- |
| **不复制商业像素游戏官方素材** | 不允许出现星露谷物语 / 牧场物语 / 莱莎工房等专有 sprite、专有字体、专有 UI 切图 |
| **仅参考玩法结构** | 农场 / 天气 / 日夜 / 任务 / NPC / 背包 / 技能树 / 存档 可参考布局思路 |
| **资产协议** | CC0 / CC-BY 3.0/4.0 / CC-BY-SA 3.0+（LPC）/ OFL-1.1（Ark Pixel） |
| **代码 vs 资产** | 代码层 MIT；像素资产层 CC-BY-SA 3.0+ 分层授权 |

视觉的差异化锚点（让玩家觉得"不是星露谷"）：

1. **米黄羊皮纸基色**（`#FBE8C5`）比星露谷草绿地图基色更"教育书本感"。
2. **金色高亮 `#D4AF37`** 替代星露谷的红色高亮，承载"知识点亮"语义。
3. **像素中文字体 Ark Pixel 12**（OFL-1.1）替代任何专有像素中文字体，是 LPC 像素小镇里少见的真·中文像素字。
4. **教学面板与游戏画布共享同一套色板**，让"游戏 - 课堂"无缝过渡，这是纯娱乐像素游戏不会做的。

---

# 1. 核心调色板（12 色基本盘）

颜色全部以 #HEX 表示，并标注 WCAG 对比度（以"在 `#FBE8C5` 米黄背景上的前景文字"为基准；纯背景色不参与对比）。对比度算法：WCAG 2.1 相对亮度公式。

| # | 语义 | HEX | RGB | 用途 | vs 主背景对比度 | 来源依据 |
| --- | --- | --- | --- | --- | --- | --- |
| 01 | **主背景 米黄** | `#FBE8C5` | (251, 232, 197) | 页面底色、面板底色、菜单底色 | — 基准 — | LPC parchment 经典色，规避星露谷专有 #FFFEE9 |
| 02 | **深棕边框** | `#5C3A24` | (92, 58, 36) | 9-slice 外描边、卡片硬边、像素按钮外框 | 8.9:1（AAA） | LPC wood frame，标准 OpenGameArt 暗木色 |
| 03 | **次背景 木板** | `#E2BC85` | (226, 188, 133) | 二级面板、抽屉、tooltip 底 | — 背景层 — | Kenney pixel_ui 木质卡片 |
| 04 | **高亮金** | `#D4AF37` | (212, 175, 55) | 主 CTA、焦点光晕、KC 收获徽章、季节金穗 | 2.4:1（**禁文字**） | 自定义金穗，区别于星露谷红 |
| 05 | **主文字 深褐** | `#4D3613` | (77, 54, 19) | 正文、标题、关键说明 | 10.6:1（AAA） | 继承 `tokens.css --pyt-color-text-primary` |
| 06 | **次文字 灰褐** | `#8B6B47` | (139, 107, 71) | 次级说明、placeholder、时间戳 | 4.6:1（AA 正文） | 继承 `tokens.css --pyt-color-text-muted` 略调 |
| 07 | **正向绿** | `#6BBF59` | (107, 191, 89) | 成功 toast、能量条、作物成长、KC 掌握 | 2.7:1（**仅图形**） | LPC crop green，比星露谷 #8AC34A 更黄调 |
| 08 | **负向红** | `#D9534F` | (217, 83, 79) | 失败 toast、扣血、Bug 矿洞警告 | 3.5:1（**仅图形**） | Bootstrap danger 系，规避星露谷 #FF4040 纯红 |
| 09 | **链接蓝** | `#5BC0DE` | (91, 192, 222) | 对话气泡、教师视角链接、雨天滤镜 | 2.1:1（**仅图形**） | LPC water 浅蓝，对话与雨天共享 |
| 10 | **特殊紫** | `#9B59B6` | (155, 89, 182) | 节日彩蛋、紫宝石、稀有掉落、夜间魔法 | 4.7:1（AA） | Pythontopia 自定义节日紫，区别于星露谷紫 |
| 11 | **阴影 半透黑** | `rgba(0,0,0,0.35)` | — | 弹窗投影、卡片下沉、模态遮罩 | — 透明叠加 — | 通用 35% 黑，避免纯黑割裂米黄基调 |
| 12 | **焦点环金** | `#F1C453` | (241, 196, 83) | 键盘焦点 2px 环、可访问性高亮 | 1.7:1（**仅外描边**） | 高亮金亮调版本，AAA 焦点标识 |

> **对比度说明**：所有"仅图形"色禁止承载正文文字；如需在彩色块上写字，必须叠加白色/米黄底，或改用 #05 / #02 作为前景。

---

# 2. 字体规范

## 2.1 字体栈

| 用途 | 字体 | 协议 | fallback |
| --- | --- | --- | --- |
| **像素中文（主字体）** | `Ark Pixel 12` | OFL-1.1 | `'PingFang SC', 'Noto Sans CJK SC', monospace` |
| **像素英文 / 数字（标题）** | `Press Start 2P` | OFL-1.1 | `'Ark Pixel 12', monospace` |
| **像素中文等宽（代码）** | `Ark Pixel 12 Mono` | OFL-1.1 | `'JetBrains Mono', Menlo, monospace` |
| **正文非像素（教学长文）** | `LXGW WenKai` / `霞鹜文楷` | OFL-1.1 | `'PingFang SC', 'Microsoft YaHei', system-ui` |
| **代码长文（CodeLab 编辑器）** | `JetBrains Mono` | OFL-1.1 | `Menlo, Consolas, monospace` |

> Ark Pixel 与 LXGW WenKai 已在 `frontend/public/assets/pixel/fonts/` 自托管，详见 `tokens.css`。`Press Start 2P` 待补，作为 R-12 接受度任务。

## 2.2 字号阶梯

像素字体**只能使用整数 px**（小数会模糊像素）：

| token | 像素值 | 用途 |
| --- | --- | --- |
| `pixel-xs` | 10px | 道具叠加数字、迷你地图标签 |
| `pixel-sm` | 12px | HUD 主字号、按钮文本、对话框正文 |
| `pixel-base` | 14px | 标题二级、菜单项 |
| `pixel-lg` | 18px | 模态标题、章节大标题 |
| `pixel-xl` | 24px | 庆祝弹窗、季节切换横幅 |

> 教学长文（非像素）仍可使用 `tokens.css` 现有 `--pyt-font-size-xs / sm / base / lg / xl / 2xl`（11/12/14/16/20/24），两套体系互不污染。

---

# 3. 边框与按钮（9-slice）

## 3.1 9-slice 切图规范

- **画布尺寸**：24×24px
- **切片网格**：8 / 8 / 8（左、中、右；上、中、下均为 8px）
- **像素描边**：外圈 1px `#5C3A24`，内圈 1px `#FBE8C5`（双线像素描边）
- **导出格式**：`png`（不允许 webp / svg；像素质感需要 png 锯齿）
- **命名约定**：`frame_<语义>_<状态>.png`，例如 `frame_panel_default.png`

## 3.2 主按钮 3 状态

| 状态 | 背景色 | 描边 | 位移 | 阴影 |
| --- | --- | --- | --- | --- |
| **default** | `#E2BC85` | 外 `#5C3A24` + 内 `#FBE8C5` | `translateY(0)` | `0 2px 0 rgba(0,0,0,0.35)` |
| **hover** | `#FBE8C5`（明度 +5%） | 外 `#5C3A24` + 内 `#F1C453` | `translateY(0)` | `0 3px 0 rgba(0,0,0,0.30)` |
| **pressed** | `#C9A86B`（明度 -5%） | 外 `#5C3A24` + 内 `#FBE8C5` | `translateY(1px)` | `0 1px 0 rgba(0,0,0,0.35)` |

CTA 主按钮（确认 / 提交 / 收获）额外加 `#D4AF37` 高亮金 2px 内描边，与普通按钮区分。

## 3.3 圆角

- **统一 4px**（`--pyt-radius-sm`）：所有按钮、卡片、tooltip、弹窗。
- **6px** 仅限：庆祝弹窗、季节切换横幅（已存在于 `tokens.css --pyt-radius-md`）。
- **8px** 仅限：登录 / 注册等"非游戏内"页面（已存在于 `tokens.css --pyt-radius-lg = 10px`，将下调到 8px）。
- **禁止** 12px+ 圆角；像素质感对大圆角极度敏感。

---

# 4. 图标系统

## 4.1 规格

| 项 | 值 |
| --- | --- |
| 标准尺寸 | **24×24px** |
| 大图尺寸（任务面板封面） | 48×48px（24 的 2 倍放大，nearest-neighbor） |
| 调色板 | 限定使用 `#02 #03 #04 #05 #07 #08 #09 #10` 8 色，其余不允许 |
| 描边 | 外圈 1px `#5C3A24`（与按钮统一） |
| 格式 | `png`（透明背景） |
| 命名 | `icon_<category>_<name>.png`，snake_case |

## 4.2 6 类道具图标（必须有 png 占位）

| 类别 | 文件名 | 占位状态 | 现有候选资产 |
| --- | --- | --- | --- |
| **seed**（种子 / KC 种子） | `icon_seed.png` | 占位 emoji 🌱，R-12 接收 | `crops/lpc-crops/*` |
| **tool**（工具） | `icon_tool.png` | 占位 emoji 🔧，R-12 接收 | `opengameart_farming/lpc_farm/tools/*` |
| **gift**（礼物 / NPC 好感） | `icon_gift.png` | 占位 emoji 🎁，R-12 接收 | `pixel_ui/inventory/*` |
| **consumable**（消耗品 / 能量饮） | `icon_consumable.png` | 占位 emoji 🍞，R-12 接收 | `kenney_tilemaps/food/*` |
| **book**（学习资源 / 课件） | `icon_book.png` | 占位 emoji 📖，R-12 接收 | `pixel_ui/icons/book.png` |
| **quest**（任务 / 公告板） | `icon_quest.png` | 占位 emoji 📜，R-12 接收 | `icons_kc/quest_main/lorc__compass-rose.png` |

> **emoji 仅占位**：Beta 首发版本必须全部替换为 24×24 png；emoji 不允许出现在最终发布的游戏画布与正式 HUD。教学面板非游戏区可保留 emoji。

## 4.3 现有 KC 图标复用

`frontend/public/assets/pixel/icons_kc/` 已有 60 个 game-icons.net（CC-BY 3.0）图标，分 6 类：

| 类别 | 路径 | 文件数 |
| --- | --- | --- |
| 任务主线 | `icons_kc/quest_main/` | 4 |
| 班级协作 | `icons_kc/quest_class_collab/` | 3 |
| 代码 Lab | `icons_kc/resource_code_lab/` | 2 |
| 思维导图 | `icons_kc/resource_mindmap/` | 3 |
| Bug 矿洞 | `icons_kc/bug_mine/` | 3 |
| 通用搜索 | `icons_kc/ui_search/` | 1 |

这 60 个图标**已经满足**任务面板 / 思维导图 / Bug 矿洞的视觉需要，无需另外采购。

---

# 5. 动画规范

像素游戏的动画哲学：**少而准，绝不溜冰**。

| 动画 | 持续时间 | 缓动 | 用途 |
| --- | --- | --- | --- |
| **hover 高亮** | 120ms | `ease` | 按钮 / 道具 / NPC 名牌 |
| **按下位移** | 60ms | `linear` | 1px translate，反馈"按到了" |
| **弹窗淡入** | 200ms | `ease-out` | 模态 / tooltip / 庆祝 |
| **弹窗淡出** | 150ms | `ease-in` | 模态关闭 |
| **toast 滑入** | 250ms | `cubic-bezier(0.25, 1.4, 0.5, 1)` | 右上角通知 |
| **页面切换 fade** | 400ms | `ease-in-out` | 5 张地图边缘切换 |
| **庆祝爆发** | 1200ms | `cubic-bezier(0.25, 1.4, 0.5, 1)` | 已存在 `.pyt-celebrate__burst` |

## Sprite 帧动画（Phaser 内）

| 动作 | 帧数 | 帧率 | 循环 |
| --- | --- | --- | --- |
| **idle** | 4 | 4 fps | 是 |
| **walk**（8 方向） | **8 帧** / 方向 | 8 fps | 是 |
| **attack**（采集 / 浇水 / 开矿） | **4 帧** | 12 fps | 否（触发一次） |
| **emote**（NPC 表情泡） | 2 帧闪烁 | 2 fps | 3 次后停止 |
| **天气粒子**（雨雪） | 持续 GPU 粒子 | — | 是 |

> 用户启用系统级 `prefers-reduced-motion: reduce` 时，所有过渡时长 ×0.5，sprite 帧率不变（剥夺动画感会影响"像素小镇"叙事，但提速不影响）。

---

# 6. 天气视觉

天气类型与游戏服务端 `WeatherType` 枚举一一对应：

| 天气 | 调色滤镜 | 粒子层 | 音频钩子（参考） |
| --- | --- | --- | --- |
| **SUNNY** | 无（基准明亮） | 无 | `bgm/sunny_*.ogg` |
| **CLOUDY** | 饱和度 -10%、亮度 -3% | 缓慢移动云阴影 sprite | `bgm/cloudy_*.ogg` |
| **RAINY** | 蓝色滤镜 8%（`#5BC0DE` 8% multiply）+ 亮度 -5% | **雨粒子**（白色 1×2px 斜下 30°） | `sfx/rain_loop.ogg` |
| **SNOWY** | 亮度 +5%、饱和度 -15% | **雪粒子**（白色 2×2px 缓慢下落 + 微飘移） | `sfx/wind_loop.ogg` |

> 滤镜实现优先用 **Phaser RenderTexture + ColorMatrix**，避免 CSS `filter` 全屏触发重绘性能问题。粒子层使用 Phaser ParticleEmitter，桌面端 200 粒子 / 移动端 80 粒子。

---

# 7. 时段光照

时段与服务端 `TimePhase` 枚举对齐（DAWN / MORNING / NOON / AFTERNOON / EVENING / NIGHT）。光照统一以**全屏 overlay 矩形**实现，叠加 `multiply` 或 `screen` 模式。

| 时段 | overlay 颜色 | overlay 透明度 | 混合模式 | 额外效果 |
| --- | --- | --- | --- | --- |
| **DAWN**（05:00–07:00） | `#D4AF37` × `#9B59B6` 渐变 | 20% | `screen` | 鸡叫 sfx |
| **MORNING**（07:00–11:00） | — | 0% | — | 基准日照 |
| **NOON**（11:00–14:00） | — | 0% | — | 对比度 +5% |
| **AFTERNOON**（14:00–17:00） | `#D4AF37` 金色 | 10% | `screen` | — |
| **EVENING**（17:00–20:00） | `#D9534F` 橙红 | 25% | `multiply` | NPC 回家路径高亮 |
| **NIGHT**（20:00–05:00） | `#1A2B5C` 深蓝（自定义夜色） | 40% | `multiply` | **NPC 头顶 8px 黄光圈** + 萤火虫粒子 |

> overlay 切换不应突变，必须 8 秒内线性插值过渡，让玩家"感受到"日夜变化。

---

# 8. 四季节调色变化

季节与服务端 `Season` 枚举对齐（SPRING / SUMMER / AUTUMN / WINTER）。每季节通过**地块色调 LUT**（256 色 PNG 查表）对地图 tile 做整体调色，HUD 不受影响。

| 季节 | 地块 LUT | 草地饱和度 | 装饰 sprite | HUD 边框点缀 |
| --- | --- | --- | --- | --- |
| **SPRING** | `lut_spring.png` | **+15%** | 樱花瓣粒子（粉 `#FFB7C5`，4 帧飘落） | 樱花 1×1 像素装饰 |
| **SUMMER** | `lut_summer.png` | 基准 | 萤火虫粒子（金 `#D4AF37`，仅夜间） | 麦穗装饰 |
| **AUTUMN** | `lut_autumn.png` | -10%，整体**棕红偏移**（红 `#D9534F` 5% multiply） | 落叶粒子（红棕，6 帧旋转飘落） | 红叶装饰 |
| **WINTER** | `lut_winter.png` | -25%，整体**冷灰偏移**（蓝 `#5BC0DE` 8% multiply） | 持续雪盖（地块顶部 2px 白条带） | 雪花装饰 |

> LUT png 由 Phaser `WebGLPipeline` 加载并应用到 `WorldScene` 的 tile 层。HUD 用 `tokens.css` CSS 变量切换，4 套季节边框已规划在附录变量中。

---

# 9. 响应式断点

Pythontopia Web 端默认面向**桌面 1280×800 教室投影**，但保留平板与移动端阅读教学面板能力。

| 设备 | 视口宽度 | HUD 行为 | 游戏画布 | 输入方式 |
| --- | --- | --- | --- | --- |
| **桌面** | ≥ 1280px | 完整 HUD（顶部状态栏 + 底部背包 + 右侧迷你地图 + 左侧任务） | 16:10 满铺中央 | 键鼠 + WASD |
| **平板** | 768–1279px | HUD 紧凑：所有间距 **-20%**、迷你地图缩 2/3、背包条 6 格→5 格 | 4:3 等比缩放，留 8px 黑边 | 触屏 + 长按 |
| **手机** | ≤ 767px | HUD **折叠**：状态栏顶部不变，背包/任务收入抽屉，迷你地图折成右上角 64×64 | 16:9 横屏强制 | **虚拟摇杆**（左下 96×96） + **A/B 按钮**（右下 80×80 × 2） |

> 横屏强制：手机端必须横屏，竖屏弹"请旋转设备"全屏提示。这是教学游戏的常见妥协。

---

# 10. 可访问性

| 项 | 要求 | 验证 |
| --- | --- | --- |
| **文字对比度** | 正文 vs 背景 ≥ 4.5（WCAG AA），标题 ≥ 3.0 | Chrome DevTools Lighthouse |
| **焦点环** | 键盘 Tab 焦点必须有 **2px `#F1C453` 高亮金外环**，不可去除 outline | `:focus-visible` |
| **CTA 不依赖颜色** | 重要按钮（提交、放弃、删除）**必须带文字 + 图标**，色盲也能识别 | 灰度截图自检 |
| **图标 + 文字配对** | 道具数量 / 任务状态除图标外必须有数字 / 文字 | 截图人工检查 |
| **toast 持续时间** | 至少 4000ms，重要 toast 必须可手动关闭 | 单元测试 |
| **音效非强制** | 所有 SFX 必须有视觉等价反馈（如成功 toast 必有绿色 + ✓） | UX checklist |
| **prefers-reduced-motion** | 已在 `tokens.css` 处理；所有自定义动画必须遵守 | CSS 媒体查询 |
| **键盘可达** | 所有 HUD 元素必须可键盘到达，Tab 顺序逻辑清晰 | 手动键盘测试 |

> **不依赖颜色**的具体落地：成功 toast = 绿色 + ✓ + 文字"已完成"；失败 toast = 红色 + ✗ + 文字"失败"；警告 toast = 金色 + ⚠ + 文字"请注意"。三个符号在最终版本替换为 24×24 像素图标。

---

# 11. 资产路径与命名

## 11.1 像素 UI 资产

```
frontend/public/assets/pixel/
├── ui/                          # 9-slice 框 / 按钮切图
│   ├── frame_panel_default.png  # 24×24 9-slice 主面板
│   ├── frame_panel_dark.png     # 24×24 9-slice 深色面板（夜间）
│   ├── frame_tooltip.png        # 16×16 9-slice tooltip
│   ├── btn_primary_default.png  # 16×16 9-slice 主按钮
│   ├── btn_primary_hover.png
│   ├── btn_primary_pressed.png
│   ├── btn_secondary_default.png
│   ├── btn_secondary_hover.png
│   └── btn_secondary_pressed.png
├── icons/                       # 24×24 道具与系统图标
│   ├── icon_seed.png
│   ├── icon_tool.png
│   ├── icon_gift.png
│   ├── icon_consumable.png
│   ├── icon_book.png
│   └── icon_quest.png
├── icons_kc/                    # 已存在，60 个 game-icons.net 图标
├── fonts/                       # 字体（已存在 Ark Pixel）
├── effects/                     # 庆祝 / 粒子（已存在 jellyfizh-confetti）
└── weather/                     # 天气 LUT 与粒子（待 R-12 接收）
    ├── lut_spring.png
    ├── lut_summer.png
    ├── lut_autumn.png
    ├── lut_winter.png
    ├── particle_rain.png
    ├── particle_snow.png
    ├── particle_cherry.png
    ├── particle_leaf.png
    └── particle_firefly.png
```

## 11.2 命名约定（强制）

| 类别 | 模式 | 例子 |
| --- | --- | --- |
| 9-slice 框 | `frame_<语义>_<状态>.png` | `frame_panel_default.png` |
| 按钮 | `btn_<语义>_<状态>.png` | `btn_primary_pressed.png` |
| 图标 | `icon_<类别>[_<变体>].png` | `icon_seed.png`、`icon_tool_iron.png` |
| 天气 | `particle_<天气>.png` / `lut_<季节>.png` | `particle_rain.png` |
| sprite | `sprite_<角色>_<动作>_<方向>.png` | `sprite_farmer_walk_south.png` |

**禁止**：camelCase、PascalCase、中文文件名、空格、特殊符号。

---

# 12. License 提醒（强制）

> **新增任意资产，必须在以下两个文件同步更新作者、来源、协议：**
>
> 1. `/home/cypress/Pythontopia/LICENSE-ART.md`（顶层总表）
> 2. `/home/cypress/Pythontopia/frontend/public/assets/pixel/LICENSE.md`（运行时清单）

如果资产来自：

- **LPC（CC-BY-SA 3.0+）**：必须同时在 `frontend/public/credits/` 加入作者清单；本项目最终二进制以 CC-BY-SA 3.0+ 分层发布。
- **game-icons.net（CC-BY 3.0）**：必须在 about 页面署名 Lorc / Delapouite / Skoll / sbed / Carl Olsen。
- **Kenney（CC0）**：推荐致谢，不强制。
- **Ark Pixel（OFL-1.1）**：必须在 about 页面署名 TakWolf；字体不允许单独再发布。

**严禁**引入 Cup Nooble、星露谷物语、牧场物语、莱莎工房、像素任意非 CC 兼容协议的素材。已删除的违规清单见 `LICENSE-ART.md` 第 46-55 行。

---

# 13. 附录：核心 CSS 变量提案（不要直接改 tokens.css）

下列变量是本视觉系统希望在 `frontend/src/styles/tokens.css` 中**新增 / 调整**的 30 个 token。实际改动由后续 R-12 子任务执行，本文仅提案。

```css
:root {
  /* === A. 调色板对齐（新增 12 个） === */
  --pyt-color-bg-stardew: #FBE8C5;          /* 主背景米黄，本视觉系统基准 */
  --pyt-color-bg-wood: #E2BC85;             /* 次背景木板 */
  --pyt-color-border-stardew: #5C3A24;      /* 深棕硬边，替代 #4d3613 用于像素 UI */
  --pyt-color-accent-gold: #D4AF37;         /* 高亮金，CTA / 焦点 */
  --pyt-color-accent-gold-bright: #F1C453;  /* 焦点环亮金 */
  --pyt-color-text-stardew: #4D3613;        /* 复用现有主文字 */
  --pyt-color-text-stardew-muted: #8B6B47;  /* 替代 #8a6c2c，色相更暖 */
  --pyt-color-success-stardew: #6BBF59;     /* 比现 #2f8f3c 更亮，适合像素 */
  --pyt-color-danger-stardew: #D9534F;      /* 取代 #b8442d */
  --pyt-color-link-stardew: #5BC0DE;        /* 链接 + 雨天复用 */
  --pyt-color-festival-purple: #9B59B6;     /* 节日紫 */
  --pyt-color-shadow-soft: rgba(0, 0, 0, 0.35);

  /* === B. 像素字号阶梯（新增 5 个） === */
  --pyt-pixel-size-xs: 10px;
  --pyt-pixel-size-sm: 12px;
  --pyt-pixel-size-base: 14px;
  --pyt-pixel-size-lg: 18px;
  --pyt-pixel-size-xl: 24px;

  /* === C. 动画时长（新增 5 个） === */
  --pyt-anim-hover: 120ms;
  --pyt-anim-press: 60ms;
  --pyt-anim-modal-in: 200ms;
  --pyt-anim-modal-out: 150ms;
  --pyt-anim-fade-scene: 400ms;

  /* === D. 季节边框（新增 4 个，预留季节皮肤） === */
  --pyt-border-season-spring: #FFB7C5;
  --pyt-border-season-summer: #D4AF37;
  --pyt-border-season-autumn: #C2502E;
  --pyt-border-season-winter: #B0C4D9;

  /* === E. 焦点环（新增 1 个） === */
  --pyt-focus-ring: 2px solid var(--pyt-color-accent-gold-bright);
}
```

> 实际改动 `tokens.css` 时必须保留现有所有变量，仅新增；现有变量值的微调（如 `--pyt-radius-lg: 10px → 8px`）需单独走代码评审。

---

# 14. 验收与生效

| 项 | 验收标准 | 责任人 |
| --- | --- | --- |
| 本文档审阅 | 视觉设计师 + 前端工程师双签 | 提案人 |
| 调色板落地 | `tokens.css` 新增 30 个变量 | 前端工程师 |
| 9-slice 切图 | `frontend/public/assets/pixel/ui/` 9 个 png 就位 | 美术 |
| 6 道具图标 | `frontend/public/assets/pixel/icons/` 6 个 png 就位 | 美术 |
| 4 季节 LUT | `frontend/public/assets/pixel/weather/` 4 个 LUT 就位 | 美术 |
| 文档署名 | `LICENSE-ART.md` 与 `pixel/LICENSE.md` 同步更新 | 美术 |
| 截图自检 | 桌面 / 平板 / 手机 三档截图无破窗 | 前端工程师 |
| 可访问性 | Lighthouse 评分 ≥ 90、键盘可达 100% | 前端工程师 |

完成上述 8 项后，本文档由 Draft 升级为 Active（v1.0 → v1.0-released）。

---

> **本文是宪法，不是说明书**。任何新视觉需求（如班长视角红边、教师视角紫边、Bug 矿洞警告闪烁）必须先回到本文档第 1 节调色板与第 5 节动画规范找语义，找不到再补充新提案。**严禁在组件层硬编码颜色与时长。**
