# UI / HUD 元素映射表

> Pythontopia 像素小镇 UI 元素 → 源文件映射
> 抓取日期：2026-05-21 · 子代理 #4 (UI / HUD)

本表把 Pythontopia 需要的 UI 元素（对话框、按钮 4 态、进度条、物品栏、卡片背景、角标、键盘提示、光标）映射到具体源文件 + 9-slice 切片坐标。所有路径相对 `assets/inbox/ui/`。

---

## 一、9-slice 对话框 / 面板

| 用途 | 推荐源 | 文件 | 尺寸 | 9-slice (top, right, bottom, left) | 备注 |
|---|---|---|---|---|---|
| **主对话框（角色对话）** | Pixel Adventure | `kenney_ui-pack-pixel-adventure/Tiles/Small tiles/Thick outline/tile_0011.png` 等 9 个 tile 组合 | 16×16 / tile | 通过 tile_0010~tile_0012、tile_0021~tile_0023、tile_0032~tile_0034 9 块拼成（16 / 16 / 16 / 16） | 极简像素风，配 Thick outline 醒目 |
| **任务窗口 / 角色面板** | RPG Expansion | `kenney_ui-pack-rpg-expansion/PNG/panel_beige.png` | 100×100 | 24, 24, 24, 24 | 复古羊皮纸感，4 色变体（beige / blue / brown / grey + Light） |
| **物品栏底板 / 内嵌面板** | RPG Expansion | `kenney_ui-pack-rpg-expansion/PNG/panelInset_beige.png` | 100×100 | 16, 16, 16, 16 | 凹陷内嵌效果，搭配 panel_* 做嵌套 |
| **大尺寸卡片（成就 / 任务奖励）** | Pixel Adventure | `kenney_ui-pack-pixel-adventure/Tiles/Large tiles/Thick outline/tile_0010.png` ~ `tile_0034.png` | 32×32 / tile | 通过 9 块 32×32 拼成（32 / 32 / 32 / 32） | 适合 1080p 屏放大显示 |
| **HUD / 任务列表小气泡** | Pixel Adventure | `kenney_ui-pack-pixel-adventure/Tiles/Small tiles/Thin outline/tile_0011.png` | 16×16 / tile | 同上但 Thin outline | 信息密度更高，不抢戏 |
| **羊皮纸长文本背景** | RPG GUI Kit | `oga_rpg-gui-construction-kit/paper background.png` | 大图集 | 自切（建议 32, 32, 32, 32） | 用作 PDF 课件 / 长任务说明背景 |
| **木质外框** | RPG GUI Kit | `oga_rpg-gui-construction-kit/wood background.png` | 大图集 | 自切（建议 24, 24, 24, 24） | 用作小镇公告板 / 摊位招牌 |

> **建议**：网页端默认主对话用 `kenney_ui-pack-pixel-adventure/Small tiles/Thick outline`，与 16×16 像素小镇风格统一；任务面板用 `RPG Expansion/panel_beige` 凸显"卷轴感"。

---

## 二、按钮 4 态（normal / hover / pressed / disabled）

### A. 推荐主用：RPG Expansion（无 disabled 态，需手动叠加半透明灰）

| 颜色 | normal | pressed | hover | disabled |
|---|---|---|---|---|
| Beige | `kenney_ui-pack-rpg-expansion/PNG/buttonLong_beige.png` | `kenney_ui-pack-rpg-expansion/PNG/buttonLong_beige_pressed.png` | `buttonLong_beige.png` + 1.1× 亮度 滤镜 | `buttonLong_beige.png` + 50% 透明 + 灰度 |
| Blue | `buttonLong_blue.png` | `buttonLong_blue_pressed.png` | 同上 | 同上 |
| Brown | `buttonLong_brown.png` | `buttonLong_brown_pressed.png` | 同上 | 同上 |
| Grey | `buttonLong_grey.png` | `buttonLong_grey_pressed.png` | 同上 | 同上 |

> 尺寸 190×49，9-slice (top=16, right=16, bottom=16, left=16)。
> 方形按钮：`buttonSquare_<color>.png` + `_pressed.png` 45×49。

### B. 备选小尺寸按钮：UI Pack（含完整 6 配色，无 pressed 直接态）

| 用途 | 文件 | 备注 |
|---|---|---|
| 圆角矩形扁平按钮 | `kenney_ui-pack/PNG/Blue/Default/button_rectangle_flat.png` + `_depth_flat.png` | 配 6 色：Blue / Green / Grey / Red / Yellow + Extra |
| 圆形按钮 | `kenney_ui-pack/PNG/Blue/Default/button_round_flat.png` | 4 风格：border / flat / gloss / gradient / line |
| 方形按钮 | `kenney_ui-pack/PNG/Blue/Default/button_square_flat.png` | depth 版自带按下感 |

> 用 `_depth_flat.png` 作 normal，`_flat.png` 作 pressed（depth 是抬起阴影；按下时切到平面 → 视觉沉降）。

### C. 像素小按钮：Pixel Adventure（16×16，1 帧仅 normal）

- `kenney_ui-pack-pixel-adventure/Tiles/Small tiles/Thick outline/tile_0006.png` ~ `tile_0009.png` 等按钮 tile
- pressed 态需后端美术补，或运行时 y+1px 偏移模拟。

---

## 三、进度条 / 血条 / 经验条

| 类型 | 背景 9-slice | 填充 9-slice | 尺寸 | 备注 |
|---|---|---|---|---|
| **水平 - Blue / Green / Red / Yellow** | `kenney_ui-pack-rpg-expansion/PNG/barBack_horizontalLeft/Mid/Right.png` | `bar<Color>_horizontalLeft/Mid/Right.png` | 左右端 6×26，中段可伸 | left/right 各 6px，9-slice (top=0, right=6, bottom=0, left=6) |
| **垂直** | `barBack_verticalTop/Mid/Bottom.png` | `bar<Color>_verticalTop/Mid/Bottom.png` | 上下端 26×6 | 同上转 90° |
| **滑块条（UI Pack）** | `kenney_ui-pack/PNG/Blue/Default/slide_horizontal_grey.png` | `slide_horizontal_color.png` + `slide_horizontal_color_section.png` | 可拉伸 | 6 配色 × 2 密度 |
| **像素小血条** | `kenney_ui-pack-pixel-adventure/Tiles/Small tiles/Thick outline/tile_0078~tile_0080.png` | 同图集对应填充 tile | 16×16 / tile 拼 | 适合人物头顶 HP |

**Pythontopia 映射**：
- **学习进度条（章节掌握度）** → Green 水平 9-slice
- **复习剩余时间** → Yellow 水平
- **疲劳/专注度** → Red 水平
- **作物成熟度** → 自定义颜色（用 `slide_horizontal_color_section.png` 拼绿黄渐变）

---

## 四、物品栏 / 背包格子

| 用途 | 文件 | 尺寸 | 备注 |
|---|---|---|---|
| **背包单格底板（常态）** | `kenney_ui-pack-rpg-expansion/PNG/panelInset_beigeLight.png` | 100×100 → 缩 64×64 | 9-slice 16,16,16,16；做 grid 时直接缩放 |
| **背包单格（选中态）** | `kenney_ui-pack-rpg-expansion/PNG/panelInset_blue.png` | 同上 | 蓝色高亮 |
| **像素小格子（HUD 快捷栏）** | `kenney_ui-pack-pixel-adventure/Tiles/Small tiles/Thick outline/tile_0050.png` | 16×16 | 配 tile_0051（高亮选中）|
| **大尺寸格子（仓库 / 升级）** | `kenney_ui-pack-pixel-adventure/Tiles/Large tiles/Thick outline/tile_0050~tile_0053.png` | 32×32 | 含 1 选中 + 1 锁定 + 1 装备态 |
| **金色 inventory slot 框** | `oga_golden-ui/oga_golden-ui_pieces.png` | 864×568 atlas 切 | 高级用途（如装备稀有度边框），CC-BY-SA 限制注意 |

**Pythontopia 映射**：
- **种子/作物背包** → `panelInset_beige` 5×4 grid
- **代码片段收藏** → `panelInset_brown` + 选中切到 `panelInset_blue`
- **任务道具栏** → Pixel Adventure tile_0050 系列（16×16 紧凑）

---

## 五、卡片背景 / 公告板

| 用途 | 文件 | 9-slice | 备注 |
|---|---|---|---|
| **任务卡片** | `kenney_ui-pack-rpg-expansion/PNG/panel_beige.png` | 24,24,24,24 | 主用 |
| **NPC 对话卡** | `kenney_ui-pack-rpg-expansion/PNG/panel_beigeLight.png` | 24,24,24,24 | 亮度更高，对话气泡 |
| **教师公告 / 班级任务** | `kenney_ui-pack-rpg-expansion/PNG/panel_blue.png` | 24,24,24,24 | 蓝色权威感 |
| **错误/警告卡** | `kenney_ui-pack-rpg-expansion/PNG/panel_brown.png` | 24,24,24,24 | 深棕沉稳 |
| **大尺寸羊皮纸卡** | `oga_rpg-gui-construction-kit/paper background.png` | 自切 32,32,32,32 | 长文本任务说明 |

---

## 六、角标 / Tag / 评分星 / 旗帜

| 元素 | 推荐源 | 文件 | 尺寸 | 备注 |
|---|---|---|---|---|
| **✓ 正确角标** | RPG Expansion | `kenney_ui-pack-rpg-expansion/PNG/iconCheck_beige.png` | ~38×38 | 4 色：beige / blue / bronze / grey |
| **✗ 错误角标** | RPG Expansion | `iconCross_<color>.png` | ~38×38 | 同上 4 色 |
| **○ 圆形角标** | RPG Expansion | `iconCircle_<color>.png` | ~38×38 | 通用占位 |
| **像素 checkmark / cross** | UI Pack | `kenney_ui-pack/PNG/Blue/Default/icon_checkmark.png` / `icon_cross.png` | 小尺寸 | 6 配色 |
| **评分星 ★** | UI Pack | `kenney_ui-pack/PNG/Yellow/Default/star.png` + `star_outline.png` + `star_outline_depth.png` | 6 配色 | 三态（实心 / 空心 / 深度空心）适合 3 星评级 |
| **像素星星** | Pixel Adventure | `kenney_ui-pack-pixel-adventure/Tiles/Small tiles/Thick outline/tile_0119~tile_0121.png` | 16×16 | 3 态：空 / 半 / 满 |
| **Tag / 标签胶囊** | Pixel Adventure | `tile_0102~tile_0104.png`（小 tile 拼接） | 16×16 拼接 | 配色用 RPG Expansion 中的 panel 颜色 |
| **旗帜 / 任务标记** | Pixel Adventure | `tile_0140` ~ 含旗杆/旗面 tile | 16×16 / 32×32 | 用 Thick outline 标记地图 NPC |

---

## 七、键盘 / 鼠标提示（教学引导）

| 用途 | 文件 | 备注 |
|---|---|---|
| **"按 Space 跳跃"** | `kenney_input-prompts/Keyboard & Mouse/Default/keyboard_space.png` | 220 个键全覆盖 |
| **"按 Enter 确认"** | `keyboard_enter.png` + `keyboard_enter_outline.png` | outline 版可叠加文字 |
| **方向键引导** | `keyboard_arrow_up/down/left/right.png` 或 `keyboard_w/a/s/d.png` | 教学新手必备 |
| **鼠标点击** | `keyboard_mouse_left/right/middle.png` | 配 UI 教程 |
| **手柄按键** | `kenney_input-prompts/PlayStation Series/Default/*.png` | PS / Xbox / Switch 完整 |
| **完整 atlas** | `kenney_input-prompts/Keyboard & Mouse/keyboard-&-mouse_sheet_default.png` + `.xml` | Phaser/Pixi 一次性加载 |

---

## 八、光标 / 鼠标指针

| 用途 | 文件 | 备注 |
|---|---|---|
| **默认指针** | `kenney_cursor-pixel-pack/Tiles/tile_0000.png` | 16×16 |
| **手势点击** | `kenney_cursor-pixel-pack/Tiles/tile_0010~tile_0015.png` | 多种手势 |
| **战斗 / 农具** | `kenney_cursor-pixel-pack/Tiles/tile_0050~tile_0080.png` | 锄头/斧子/剑/铲子 |
| **沙漏 / 等待** | `kenney_cursor-pixel-pack/Tiles/tile_0100~tile_0105.png` | 加载态 |
| **RPG 装饰光标** | `kenney_ui-pack-rpg-expansion/PNG/cursorGauntlet/Hand/Sword_<color>.png` | 高分辨率（40~50px） |
| **完整 atlas** | `kenney_cursor-pixel-pack/Tilemap/` 下的 tilemap | Phaser preload 用 |

---

## 九、箭头 / 翻页

| 用途 | 文件 |
|---|---|
| 像素翻页箭头 | `kenney_ui-pack-rpg-expansion/PNG/arrow<Color>_left/right.png`（4 色） |
| 4 方向箭头 | `kenney_ui-pack/PNG/Blue/Default/arrow_basic_n/s/e/w.png` + `_decorative_*.png` |
| 装饰箭头 | `arrow_decorative_*.png`（带羽毛/箭尾） |

---

## 十、跨包元素覆盖度自检

| Pythontopia 需求元素 | 主用源 | 备用源 | 覆盖度 |
|---|---|---|---|
| 9-slice 对话框 | Pixel Adventure tile_0010~tile_0034 | RPG Expansion panel_* | ✅ 完整 |
| 按钮 normal / pressed | RPG Expansion buttonLong_* | UI Pack button_*_depth_flat | ✅ 2 态完整，hover/disabled 运行时合成 |
| 按钮 hover / disabled | 运行时滤镜（亮度 / 灰度） | ─ | ⚠️ 需前端 CSS/Shader 合成 |
| 进度条水平 | RPG Expansion bar*_horizontal* | UI Pack slide_horizontal_* | ✅ 4 色 × 9-slice |
| 进度条垂直 | RPG Expansion bar*_vertical* | UI Pack slide_vertical_* | ✅ |
| 物品栏格子 | RPG Expansion panelInset_* | Pixel Adventure tile_0050 | ✅ 大小两版 |
| 卡片背景 | RPG Expansion panel_* | RPG GUI Kit paper background | ✅ 4 色 + 羊皮纸 |
| 角标 ✓/✗/○ | RPG Expansion iconCheck/Cross/Circle | UI Pack icon_checkmark/cross | ✅ |
| 评分星 | UI Pack star.png（3 态） | Pixel Adventure tile_0119~0121 | ✅ |
| Tag / 标签 | Pixel Adventure 拼接 | RPG Expansion panel_*（缩小） | ✅ |
| 旗帜 | Pixel Adventure tile_0140+ | ─ | ✅ |
| 键盘提示 | Input Prompts Keyboard & Mouse | ─ | ✅ 220 键 |
| 光标 | Cursor Pixel Pack | RPG Expansion cursor* | ✅ 220 像素光标 |

---

## 已知缺漏

1. **按钮 hover / disabled 直接素材** — 现有包仅给 normal + pressed，需前端用 Phaser tween 或 CSS `filter: brightness(1.1)` / `grayscale(1) opacity(0.5)` 合成。
2. **像素风对话框 NPC 头像框** — Pixel Adventure 含通用对话框，但没有头像位预留；建议自切（在 9-slice 左侧留 32×32 空槽）。
3. **加载/转场动画** — 仅 Cursor Pixel Pack 含沙漏 tile；如需带帧动画 spinner 应另抽取（参考 Kenney `loading-icons` 或 itch.io 像素 spinner）。
4. **Pythontopia 专属 icon**（Python 火焰 / 终端 / 代码片段）— 需另设计或从 `kenney_game-icons`（非像素）二次像素化。
5. **Golden UI 9-slice 切片信息** — 现下载的是单 PNG atlas，需开发者用 Aseprite 切片标注后才能直接用。

---

## 文件统计

| 子目录 | 文件数 | 占用 | 许可 |
|---|---|---|---|
| kenney_ui-pack/ | 870 | 5.5 MB | CC0 |
| kenney_ui-pack-rpg-expansion/ | 91 | 612 KB | CC0 |
| kenney_ui-pack-pixel-adventure/ | 514 | 2.2 MB | CC0 |
| kenney_input-prompts/ | 3086 | 21 MB | CC0 |
| kenney_cursor-pixel-pack/ | 223 | 956 KB | CC0 |
| oga_rpg-gui-construction-kit/ | 5 | 4.0 MB | CC-BY 3.0 |
| oga_golden-ui/ | 3 | 120 KB | CC-BY-SA 3.0 |
| **合计** | **6808** | **35 MB** | 5 × CC0 + 1 × CC-BY + 1 × CC-BY-SA |
