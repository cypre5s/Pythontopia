# UI / HUD 元素映射表

> Pythontopia 像素小镇 UI 元素 → 源文件映射
> 抓取日期：2026-05-21 · 子代理 #4 (UI / HUD)
> 严格 License 约束：**CC0 1.0 only** — 5/5 包通过

本表把 Pythontopia 需要的 UI 元素（对话框、按钮 4 态、进度条、物品栏、卡片背景、角标、键盘提示、光标）映射到具体源文件 + 9-slice 切片坐标。所有路径都基于仓库根 `assets/inbox/`。

> **路径约定**：
> - `ui/<pack>/...` = 本轮 R-100 新下载至 `assets/inbox/ui/`
> - `pixel_ui/<pack>/...` = 已存在于 `assets/inbox/pixel_ui/`，本轮**复用不重复下载**

---

## 一、9-slice 对话框 / 面板

| 用途 | 推荐源 | 文件 | 尺寸 | 9-slice (top, right, bottom, left) | 备注 |
|---|---|---|---|---|---|
| **主对话框（NPC 对话）** | Pixel Adventure（复用） | `pixel_ui/kenney_ui-pack-pixel-adventure/Tilesheets/Small tiles/Thick outline/tilemap_packed.png` 中的 9-slice 边框 tile（行 1 列 1~3、行 2 列 1~3、行 3 列 1~3） | 16×16 / tile | 16, 16, 16, 16（按 tile 拼） | 极简像素风，配 Thick outline 醒目 |
| **任务窗口 / 角色面板** | RPG Expansion（复用） | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panel_beige.png` | 100×100 | 24, 24, 24, 24 | 复古羊皮纸感，4 色变体（beige / blue / brown / grey + beigeLight） |
| **任务窗口（其他色）** | RPG Expansion（复用） | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panel_blue.png` / `panel_brown.png` / `panel_grey.png` / `panel_beigeLight.png` | 100×100 | 24, 24, 24, 24 | 班级公告 / 错误警告 / 中性面板 / 高亮版 |
| **物品栏底板 / 内嵌面板** | RPG Expansion（复用） | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panelInset_beige.png` | 100×100 | 16, 16, 16, 16 | 凹陷内嵌效果，搭配 panel_* 做嵌套；4 色 + beigeLight |
| **大尺寸卡片（成就 / 任务奖励）** | Pixel Adventure（复用） | `pixel_ui/kenney_ui-pack-pixel-adventure/Tilesheets/Large tiles/Thick outline/tilemap_packed.png` 中 9 块拼接 | 32×32 / tile | 32, 32, 32, 32 | 适合 1080p 屏放大显示 |
| **HUD / 任务列表小气泡** | Pixel Adventure（复用） | `pixel_ui/kenney_ui-pack-pixel-adventure/Tilesheets/Small tiles/Thin outline/tilemap_packed.png` | 16×16 / tile | 16, 16, 16, 16 | Thin outline，信息密度更高，不抢戏 |

> **建议**：网页端默认主对话用 `Pixel Adventure/Small tiles/Thick outline`，与 16×16 像素小镇风格统一；任务面板用 `RPG Expansion/panel_beige` 凸显"卷轴感"。
>
> **9-slice 切片建议**：
> - Pixel Adventure 单 tile 是 16×16，9-slice 直接以 tile 为单位
> - RPG Expansion `panel_*` 是单张 100×100 PNG，按 24px 切边 + 中段拉伸
> - Phaser 中用 `add.nineslice(x, y, key, frame, width, height, leftWidth, rightWidth, topHeight, bottomHeight)`

---

## 二、按钮 4 态（normal / hover / pressed / disabled）

### A. 主用：RPG Expansion（复用，含 normal + pressed 2 态）

| 颜色 | normal | pressed | hover 合成 | disabled 合成 |
|---|---|---|---|---|
| Beige | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/buttonLong_beige.png` | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/buttonLong_beige_pressed.png` | normal + CSS `filter: brightness(1.1)` 或 Phaser `tint(0xFFEECC)` | normal + CSS `filter: grayscale(1) opacity(0.5)` |
| Blue | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/buttonLong_blue.png` | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/buttonLong_blue_pressed.png` | 同上 | 同上 |
| Brown | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/buttonLong_brown.png` | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/buttonLong_brown_pressed.png` | 同上 | 同上 |
| Grey | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/buttonLong_grey.png` | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/buttonLong_grey_pressed.png` | 同上 | 同上 |

> 尺寸 190×49，9-slice (top=16, right=16, bottom=16, left=16)
> 方形按钮：`buttonSquare_<color>.png` + `_pressed.png` 45×49（4 色）
> 圆形按钮：`buttonRound_<color>.png`（4 色，仅 1 态，可作头像/快捷键圆按）

### B. 备用小尺寸按钮：UI Pack（新下载，6 配色 × 4 风格 × 2 深度）

| 用途 | 文件（Blue 示例，其余 5 色镜像） | 4 态生成策略 |
|---|---|---|
| 矩形扁平按钮 normal | `ui/kenney_ui-pack/PNG/Blue/Default/button_rectangle_depth_flat.png` | 自带"凸起"深度阴影 |
| 矩形扁平按钮 pressed | `ui/kenney_ui-pack/PNG/Blue/Default/button_rectangle_flat.png` | 平面无阴影，对应"按下"视觉沉降 |
| 矩形按钮其他风格 | `button_rectangle_{border,gloss,gradient,line,depth_border,depth_gloss,depth_gradient,depth_line}.png` | 8 风格组合可定制不同模块 |
| 圆形按钮 normal | `ui/kenney_ui-pack/PNG/Blue/Default/button_round_depth_flat.png` | depth_* 系列均含按下抬起感 |
| 圆形按钮 pressed | `ui/kenney_ui-pack/PNG/Blue/Default/button_round_flat.png` | — |
| 方形按钮 normal | `ui/kenney_ui-pack/PNG/Blue/Default/button_square_depth_flat.png` | 配快捷栏 / 设置面板 |
| 方形按钮 pressed | `ui/kenney_ui-pack/PNG/Blue/Default/button_square_flat.png` | — |

> **6 色覆盖**：Blue / Green / Grey / Red / Yellow / Extra；每色含 Default + Double（高分辨率）
> **4 态合成**：normal = `_depth_flat.png`；pressed = `_flat.png`；hover = normal + tween 轻微缩放/亮度；disabled = normal + `filter: grayscale(0.7) opacity(0.5)`

### C. 像素小按钮：Pixel Adventure（复用，16×16）

- `pixel_ui/kenney_ui-pack-pixel-adventure/Tilesheets/Small tiles/Thick outline/tilemap.png` 中按钮 tile（参考 Tilesheet.txt 索引）
- 单帧仅 normal；pressed 用 Phaser tween `y+1px` 模拟按下

---

## 三、进度条 / 血条 / 经验条

| 类型 | 背景 9-slice | 填充 9-slice | 尺寸 | 9-slice 建议 |
|---|---|---|---|---|
| **水平 - Blue** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/barBack_horizontalLeft/Mid/Right.png` | `barBlue_horizontalLeft/Mid/Right.png` | 左右端 6×26 | (0, 6, 0, 6) |
| **水平 - Green** | 同上 Back | `barGreen_horizontalLeft/Mid/Right.png` | 同上 | 同上 |
| **水平 - Red** | 同上 Back | `barRed_horizontalLeft/Mid/Right.png` | 同上 | 同上 |
| **水平 - Yellow** | 同上 Back | `barYellow_horizontalLeft/Mid/Right.png` | 同上 | 同上 |
| **垂直 - 4 色** | `barBack_verticalTop/Mid/Bottom.png` | `bar<Color>_verticalTop/Mid/Bottom.png` | 上下端 26×6 | (6, 0, 6, 0) |
| **滑块条（UI Pack）** | `ui/kenney_ui-pack/PNG/Blue/Default/slide_horizontal_grey.png` | `slide_horizontal_color.png` + `slide_horizontal_color_section.png` | 任意可拉伸 | 由 hangle 旋钮 + section 拼接 |
| **滑块旋钮** | `ui/kenney_ui-pack/PNG/Blue/Default/slide_hangle.png` | — | 旋钮 | 6 配色镜像 |
| **像素小血条** | `pixel_ui/kenney_ui-pack-pixel-adventure/Tilesheets/Small tiles/Thick outline/tilemap_packed.png` 中血条 tile | 同图集对应填充 tile | 16×16 / tile 拼 | 适合人物头顶 HP |

**Pythontopia 映射**：

| 业务含义 | 使用素材 | 颜色规则 |
|---|---|---|
| 学习进度条（章节掌握度） | `barGreen_horizontal*.png` | Green = 已掌握 KC% |
| 复习剩余时间 | `barYellow_horizontal*.png` | Yellow = 紧迫感 |
| 疲劳/专注度 | `barRed_horizontal*.png` | Red = 警示 |
| 经验值（每完成 1 个练习） | `barBlue_horizontal*.png` | Blue = 累计 EXP |
| 作物成熟度 | `ui/kenney_ui-pack/PNG/Green/Default/slide_horizontal_color_section.png` 拼绿黄渐变 | 由 section 数表示阶段 |

---

## 四、物品栏 / 背包格子

| 用途 | 文件 | 尺寸 | 备注 |
|---|---|---|---|
| **背包单格底板（常态）** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panelInset_beigeLight.png` | 100×100 → 缩 64×64 | 9-slice (16,16,16,16)；做 grid 时直接缩放 |
| **背包单格（选中态）** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panelInset_blue.png` | 同上 | 蓝色高亮 |
| **背包单格（深色）** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panelInset_brown.png` | 同上 | 深棕，区分不同分类 |
| **背包单格（默认棕褐）** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panelInset_beige.png` | 同上 | 主色 |
| **像素小格子（HUD 快捷栏）** | `pixel_ui/kenney_ui-pack-pixel-adventure/Tilesheets/Small tiles/Thick outline/tilemap_packed.png` 中物品格 tile | 16×16 | 适合屏幕底部 1-9 快捷栏 |
| **大尺寸格子（仓库 / 升级）** | `pixel_ui/kenney_ui-pack-pixel-adventure/Tilesheets/Large tiles/Thick outline/tilemap_packed.png` 中物品格 tile | 32×32 | 含选中 / 锁定 / 装备态 |

**Pythontopia 映射**：

| 业务含义 | 使用素材 |
|---|---|
| 种子/作物背包 | `panelInset_beige.png` 5×4 grid |
| 代码片段收藏 | `panelInset_brown.png` + 选中切到 `panelInset_blue.png` |
| 任务道具栏 | Pixel Adventure 16×16 小格子（紧凑） |
| 班级任务奖励格 | `panelInset_beigeLight.png` + 蓝边描边表示"班级专属" |

---

## 五、卡片背景 / 公告板

| 用途 | 文件 | 9-slice | 备注 |
|---|---|---|---|
| **任务卡片（默认）** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panel_beige.png` | 24,24,24,24 | 主用 |
| **NPC 对话卡（亮色）** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panel_beigeLight.png` | 24,24,24,24 | 亮度更高，对话气泡 |
| **教师公告 / 班级任务** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panel_blue.png` | 24,24,24,24 | 蓝色权威感 |
| **错误/警告卡** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panel_brown.png` | 24,24,24,24 | 深棕沉稳 |
| **中性卡（无色彩偏向）** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/panel_grey.png` | 24,24,24,24 | 调试 / 设置 |
| **像素小卡（密集列表）** | `pixel_ui/kenney_ui-pack-pixel-adventure/Tilesheets/Small tiles/Thin outline/tilemap_packed.png` | 16×16 / tile | 任务列表项 / 物品 tooltip |

> **缺**：羊皮纸 / 木质纹理大背景（先前的 OGA RPG GUI Kit 因 CC-BY 3.0 协议被本轮移除）。
> **替代方案**：
> - 短期：`panel_beige.png` 缩放 + Phaser 平铺即可逼近羊皮纸感
> - 长期：另抓 CC0 纸质纹理（Kenney "Pattern Pack" CC0 含 paper 纹理）

---

## 六、角标 / Tag / 评分星 / 旗帜

| 元素 | 推荐源 | 文件 | 尺寸 | 备注 |
|---|---|---|---|---|
| **✓ 正确角标（4 色）** | RPG Expansion（复用） | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/iconCheck_beige.png` | ~38×38 | 4 色：beige / blue / bronze / grey |
| **✗ 错误角标（4 色）** | RPG Expansion（复用） | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/iconCross_<color>.png` | ~38×38 | 4 色同上 |
| **○ 圆形角标（4 色）** | RPG Expansion（复用） | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/iconCircle_<color>.png` | ~38×38 | 通用占位 |
| **像素 checkmark / cross / circle / square** | UI Pack（新） | `ui/kenney_ui-pack/PNG/Blue/Default/icon_checkmark.png` / `icon_cross.png` / `icon_circle.png` / `icon_square.png` | 小尺寸 | 6 配色；outline 版本可叠加文字 |
| **icon outline 版** | UI Pack（新） | `ui/kenney_ui-pack/PNG/<color>/Default/icon_outline_checkmark.png` 等 | 小尺寸 | 镂空风格，配深色背景 |
| **评分星 ★（实心 / 空心 / 深度空心）** | UI Pack（新） | `ui/kenney_ui-pack/PNG/Yellow/Default/star.png` + `star_outline.png` + `star_outline_depth.png` | 6 配色 | 三态适合 3 星评级 |
| **像素星星** | Pixel Adventure（复用） | `pixel_ui/kenney_ui-pack-pixel-adventure/Tilesheets/Small tiles/Thick outline/tilemap_packed.png` 中星星 tile | 16×16 | 空 / 半 / 满 3 态 |
| **Tag / 标签胶囊** | Pixel Adventure（复用） | Pixel Adventure 拼接 tile | 16×16 拼接 | 配色用 RPG Expansion panel 颜色 |
| **旗帜 / 任务标记** | Pixel Adventure（复用） | Pixel Adventure 中旗帜 tile | 16×16 / 32×32 | Thick outline 标记地图 NPC |

> **Pythontopia 评分映射**：练习提交结果 → 1~3 星
> - 0 颗实心 = 全错（`star_outline.png` × 3）
> - 1 颗实心 = 通过但思路欠优（`star.png` × 1 + `star_outline.png` × 2）
> - 3 颗实心 = 完美解（`star.png` × 3）
> - 颜色：Yellow（默认）/ Green（最优解）/ Red（侥幸通过）

---

## 七、键盘 / 鼠标 / 手柄提示（教学引导）

| 用途 | 文件 | 备注 |
|---|---|---|
| **"按 Space 跳跃"** | `ui/kenney_input-prompts/Keyboard & Mouse/Default/keyboard_space.png` + `keyboard_space_icon.png` | space 有 3 种风格（文字 / 图标 / outline） |
| **"按 Enter 确认"** | `ui/kenney_input-prompts/Keyboard & Mouse/Default/keyboard_enter.png` + `_outline.png` | outline 版可叠加文字 |
| **"按 Esc 退出"** | `ui/kenney_input-prompts/Keyboard & Mouse/Default/keyboard_escape.png` | — |
| **方向键 ↑↓←→ 引导** | `ui/kenney_input-prompts/Keyboard & Mouse/Default/keyboard_arrow_{up,down,left,right}.png` | 4 方向都有 outline 版 |
| **WASD 移动** | `ui/kenney_input-prompts/Keyboard & Mouse/Default/keyboard_{w,a,s,d}.png` | 单字母键 |
| **Shift / Ctrl / Alt / Tab** | `ui/kenney_input-prompts/Keyboard & Mouse/Default/keyboard_{shift,ctrl,alt,tab}.png` + `_outline.png` + `_icon.png` | 修饰键 3 风格 |
| **Backspace** | `ui/kenney_input-prompts/Keyboard & Mouse/Default/keyboard_backspace.png` + `_icon.png` + `_outline.png` | — |
| **数字键 0-9 / 字母 A-Z** | `ui/kenney_input-prompts/Keyboard & Mouse/Default/keyboard_{0-9,a-z}.png` | 全键盘覆盖 |
| **鼠标左键** | `ui/kenney_input-prompts/Keyboard & Mouse/Default/mouse_left.png` + `_outline.png` | 配 UI 教程 |
| **鼠标右键** | `ui/kenney_input-prompts/Keyboard & Mouse/Default/mouse_right.png` | — |
| **鼠标滚轮 / 拖动** | `mouse_scroll.png` / `mouse_scroll_up/down.png` / `mouse_move.png` / `mouse_horizontal/vertical.png` | 多场景 |
| **完整 atlas** | `ui/kenney_input-prompts/Keyboard & Mouse/keyboard-&-mouse_sheet_default.png` + `.xml` | Phaser/Pixi 一次性加载 |
| **手柄按键（备）** | `ui/kenney_input-prompts/PlayStation Series/` + `Xbox Series/` + `Nintendo Switch/` | 12 设备完整覆盖，Pythontopia 主端无需 |

> **Pythontopia 教学场景示例**：
> - 新手村开场："按 `keyboard_w` `keyboard_a` `keyboard_s` `keyboard_d` 或 `keyboard_arrow_*` 移动"
> - 与 NPC 对话提示："按 `keyboard_space` 或 `mouse_left` 交互"
> - 课件翻页："按 `keyboard_arrow_left` / `keyboard_arrow_right` 翻页 / 按 `keyboard_escape` 退出阅读"
> - 提交代码："按 `keyboard_enter` 提交 / `keyboard_shift` + `keyboard_enter` 换行"

---

## 八、光标 / 鼠标指针

| 用途 | 文件 | 备注 |
|---|---|---|
| **默认指针** | `ui/kenney_cursor-pixel-pack/Tiles/tile_0000.png` | 16×16，标准箭头 |
| **手势点击 / 抓取** | `ui/kenney_cursor-pixel-pack/Tiles/tile_0010~tile_0015.png` | 多种手势 |
| **农具光标** | `ui/kenney_cursor-pixel-pack/Tiles/tile_0050~tile_0080.png` | 锄头 / 斧子 / 剑 / 铲子（"知识田"农耕用） |
| **沙漏 / 等待** | `ui/kenney_cursor-pixel-pack/Tiles/tile_0100~tile_0105.png` | 加载态、Agent 思考中 |
| **文本光标 (I-beam)** | `ui/kenney_cursor-pixel-pack/Tiles/` 中 I-beam tile | 代码编辑场景 |
| **RPG 装饰光标** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/cursorGauntlet_<color>.png` | 3 色高分辨率（40~50px） |
| **手 / 剑装饰光标** | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/cursorHand_<color>.png` / `cursorSword_<color>.png` | RPG 风装饰 |
| **完整 atlas** | `ui/kenney_cursor-pixel-pack/Tilemap/tilemap.png` + `tilemap_packed.png` | Phaser preload 用 |

> **Pythontopia 光标切换策略**：
> - 默认浏览：`tile_0000.png`（指针）
> - 在 NPC / 可交互对象上悬停：手势 `tile_0010.png`
> - 在知识田耕作：锄头 `tile_0050`+
> - 在代码编辑器内：I-beam
> - 等待 Agent 响应：沙漏 `tile_0100`

---

## 九、箭头 / 翻页

| 用途 | 文件 | 配色 |
|---|---|---|
| 翻页箭头（4 色） | `pixel_ui/kenney_ui-pack-rpg-expansion/PNG/arrowBeige_left/right.png` 等 | Beige / Blue / Brown / Silver |
| 4 方向箭头（基础） | `ui/kenney_ui-pack/PNG/Blue/Default/arrow_basic_n/s/e/w.png` | 6 配色 + small 变体 |
| 装饰箭头（带羽毛） | `ui/kenney_ui-pack/PNG/Blue/Default/arrow_decorative_n/s/e/w.png` | 6 配色 + small 变体 |

---

## 十、跨包元素覆盖度自检

| Pythontopia 需求元素 | 主用源 | 备用源 | 覆盖度 |
|---|---|---|---|
| 9-slice 对话框 | Pixel Adventure tilemap_packed.png | RPG Expansion panel_* | ✅ 完整 |
| 按钮 normal / pressed | RPG Expansion buttonLong_* | UI Pack button_*_depth_flat | ✅ 2 态完整 |
| 按钮 hover / disabled | 运行时滤镜（brightness / grayscale + opacity） | — | ⚠️ 需前端 CSS/Shader 合成 |
| 进度条水平 | RPG Expansion bar*_horizontal* | UI Pack slide_horizontal_* | ✅ 4 色 × 9-slice |
| 进度条垂直 | RPG Expansion bar*_vertical* | UI Pack slide_vertical_* | ✅ |
| 物品栏格子 | RPG Expansion panelInset_* | Pixel Adventure tile | ✅ 大小两版 |
| 卡片背景 | RPG Expansion panel_* | Pixel Adventure tile | ✅ 4 色 + beigeLight |
| 角标 ✓/✗/○ | RPG Expansion iconCheck/Cross/Circle | UI Pack icon_checkmark/cross/circle/square | ✅ 双套 |
| 评分星 | UI Pack star.png（3 态） | Pixel Adventure 星星 tile | ✅ |
| Tag / 标签 | Pixel Adventure 拼接 | RPG Expansion panel_*（缩小） | ✅ |
| 旗帜 | Pixel Adventure 旗帜 tile | — | ✅ |
| 键盘提示 | Input Prompts Keyboard & Mouse | — | ✅ 220 键 |
| 光标 | Cursor Pixel Pack | RPG Expansion cursor* | ✅ 220 像素光标 |

---

## 已知缺漏

1. **按钮 hover / disabled 直接像素素材** — 现有 CC0 包仅给 normal + pressed，需前端用 Phaser tween 或 CSS `filter: brightness(1.1)` / `grayscale(1) opacity(0.5)` 合成。
2. **像素风对话框 NPC 头像框** — Pixel Adventure 含通用 9-slice 对话框，但没有头像位预留；建议自切（在 9-slice 左侧留 32×32 空槽）。
3. **加载/转场动画** — 仅 Cursor Pixel Pack 含沙漏 tile；如需带帧动画 spinner 应另抽取 CC0 资源。
4. **Pythontopia 专属 icon**（Python 火焰 / 终端 / 代码片段）— 需另设计或从 Kenney `game-icons`（CC0，非像素）二次像素化。
5. **羊皮纸 / 木质纹理大背景** — 先前的 OGA RPG GUI Kit 因 CC-BY 3.0 在本轮被移除。短期方案：`panel_beige.png` 缩放铺底；长期方案：抓 CC0 paper/wood 纹理（如 Kenney "Pattern Pack" CC0）。
6. **金色稀有度边框** — 先前的 OGA Golden UI 因 CC-BY-SA 3.0 在本轮被移除。短期方案：用 Phaser shader 给 `panelInset_*` 加金色光环；长期方案：另寻 CC0 稀有度边框。

---

## 复用 pixel_ui/ 清单

> 任务要求 "检查 pixel_ui/ 已有内容，能复用的标注，不重复下载"

| pixel_ui/ 包 | 文件数 (PNG) | 占用 | 本轮复用情况 | MANIFEST 引用次数 |
|---|---|---|---|---|
| `pixel_ui/kenney_ui-pack-pixel-adventure/` | 514 PNG / 519 总 | 2.2 MB | ✅ 全部复用，作主像素 UI 风格源 | 11 处（9-slice 对话框、卡片、物品格、星星、Tag、旗帜、HUD 气泡、像素小血条、像素小按钮、Thin outline 列表项、密集卡） |
| `pixel_ui/kenney_ui-pack-rpg-expansion/` | 90 PNG / 94 总 | 612 KB | ✅ 全部复用，作主面板/按钮/进度条/角标/装饰光标源 | 35+ 处（panel × 5 色 × 2 风格 = 10、panelInset × 5 = 5、buttonLong × 4 × 2 态 = 8、bar × 4 色 × 6 段 + Back × 6 = 30、icon × 3 × 4 色 = 12、cursor × 3 × 4 色 = 12、arrow × 4 色 × 2 = 8）|

> **结论**：`pixel_ui/` 既有 2 套包**100% 被复用**，无需重新下载；本轮新下载的 3 套（ui-pack / input-prompts / cursor-pixel-pack）补齐了 pixel_ui/ 不覆盖的：6 色按钮变体、220 键键盘提示、220 像素光标、★ 三态评分星、icon_outline 镂空角标、slider 旋钮、4 方向 basic+decorative 箭头。

---

## License 合规自检

| 项 | 状态 | 备注 |
|---|---|---|
| 5/5 包均为 **CC0 1.0** | ✅ | License.txt / license.txt 已逐个核对 |
| 0 包含 CC-BY-SA / CC-BY 3.0 / GPL / 未确认 | ✅ | 先前 `oga_golden-ui/`（CC-BY-SA 3.0）与 `oga_rpg-gui-construction-kit/`（CC-BY 3.0）已移除 |
| 严格符合 R-100 子代理 #4 "只接受 CC0 / CC-BY 4.0" 硬约束 | ✅ | — |
| 商用使用障碍 | 0 | CC0 无任何限制 |
| 衍生作品署名传染 | 0 | CC0 不传染 |
| 强制署名义务 | 0 | CC0 推荐致谢但非强制 |
| 移除内容是否影响 UI 元素覆盖 | 否 | 主要影响：羊皮纸纹理（替代方案见"已知缺漏 #5"）、金色稀有度边框（替代方案见 #6） |

---

## 文件统计

| 子目录 | 文件数 (含所有类型) | PNG 数 | 占用 | 许可 |
|---|---|---|---|---|
| `ui/kenney_ui-pack/` | 1315 | 870 | 5.5 MB | CC0 1.0 |
| `ui/kenney_input-prompts/` | 4644 | 3052 | 21 MB | CC0 1.0 |
| `ui/kenney_cursor-pixel-pack/` | 227 | 223 | 956 KB | CC0 1.0 |
| **inbox/ui 小计（本轮新增）** | **6186** | **4145** | **28 MB** | **CC0** |
| `pixel_ui/kenney_ui-pack-pixel-adventure/` | 519 | 514 | 2.2 MB | CC0 1.0 |
| `pixel_ui/kenney_ui-pack-rpg-expansion/` | 94 | 90 | 612 KB | CC0 1.0 |
| **inbox/pixel_ui 小计（复用）** | **613** | **604** | **2.8 MB** | **CC0** |
| **合并总计（5 套 CC0）** | **6799** | **4749** | **30.8 MB** | **100% CC0** |

> 下载缓存：`.work/assets_dl/ui/` 3 个 zip，6.2 MB（kenney_ui-pack.zip / kenney_input-prompts-pixel-16.zip / kenney_cursor-pixel-pack.zip）
