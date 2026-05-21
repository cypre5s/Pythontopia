# UI / HUD 素材来源

> Pythontopia R-100 · 素材抓取子代理 #4 (UI / HUD)
> 抓取日期：2026-05-21
> 代理：HTTP_PROXY=http://127.0.0.1:7892

本目录收纳像素 UI 与 HUD 元素，覆盖 9-slice 对话框 / 按钮 4 态 / 进度条 / 物品栏 / 卡片背景 / 角标 / 评分星 / 键盘提示 / 光标。

所有素材均为 **CC0** 或 **CC-BY 3.0 / CC-BY-SA 3.0**，可在比赛与教学项目中商用。

---

## 1. kenney_ui-pack (CC0 · Kenney)

- 主页：<https://kenney.nl/assets/ui-pack>
- 下载：`https://kenney.nl/media/pages/assets/ui-pack/f651646eab-1718203990/kenney_ui-pack.zip`
- 许可：Creative Commons Zero (CC0)。可商用，无需署名（建议致谢 `Kenney` / `www.kenney.nl`）。
- 版本：2.0 (2024-06-12)
- 文件数：870 PNG
- 占用：5.5 MB
- 内容：6 配色（Blue / Green / Grey / Red / Yellow + Extra）× Default/Double 像素密度 × button (rectangle / round / square × border/flat/gloss/gradient/line × normal/depth)、checkbox、radio、icon、slide（horizontal / vertical × color / grey × section / section_wide）、arrow、star、divider。
- **缺**：完整 9-slice 对话框（这套主要是 button + control，不含 panel）。

## 2. kenney_ui-pack-rpg-expansion (CC0 · Kenney)

- 主页：<https://kenney.nl/assets/ui-pack-rpg-expansion>
- 许可：CC0
- 文件数：91 PNG（含 sprite sheet xml）
- 占用：612 KB
- 内容：复古 RPG 风（beige / blue / brown / grey）：
  - `panel_<color>.png` 100×100 9-slice 卡片背景
  - `panelInset_<color>.png` 内嵌面板（物品栏底色）
  - `buttonLong_<color>.png` + `_pressed.png` 横条按钮 2 态
  - `buttonSquare_<color>.png` + `_pressed.png` 方形按钮 2 态
  - `buttonRound_<color>.png` 圆形按钮
  - `bar<Color>_horizontal/vertical_Left/Mid/Right.png` 9-slice 血条 / 经验条（Blue / Green / Red / Yellow + Back）
  - `iconCheck/Cross/Circle_<color>.png` 角标
  - `cursorGauntlet/Hand/Sword_<color>.png` 光标
  - `arrow<Color>_left/right.png` 翻页箭头
- **复用来源**：已存在于 `assets/inbox/pixel_ui/kenney_ui-pack-rpg-expansion/`，本次为 inbox/ui 完整镜像。

## 3. kenney_ui-pack-pixel-adventure (CC0 · Kenney)

- 主页：<https://kenney.nl/assets/ui-pack-pixel-adventure>
- 许可：CC0
- 版本：2.0 (2024-09-04)
- 文件数：514 PNG（4 个变体 × Tiles + Tilesheets）
- 占用：2.2 MB
- 内容：纯像素 UI tile（16×16 / 32×32）×（Thick / Thin outline）：
  - **Small tiles 16×16 · Thick outline**：161 个独立 tile
  - **Small tiles 16×16 · Thin outline**：161 个独立 tile
  - **Large tiles 32×32 · Thick outline**：91 个独立 tile
  - **Large tiles 32×32 · Thin outline**：91 个独立 tile
  - 每变体附 `tilemap.png`（间隔 1px）与 `tilemap_packed.png`（紧密合成）atlas
  - tile 类型覆盖：button (normal / pressed)、9-slice 边框、checkbox、heart、coin、key、arrow、X / O、滚动条、对话气泡、键盘字母键、数字键
- **复用来源**：已存在于 `assets/inbox/pixel_ui/kenney_ui-pack-pixel-adventure/`，本次为 inbox/ui 完整镜像。

## 4. kenney_input-prompts (CC0 · Kenney)

- 主页：<https://kenney.nl/assets/input-prompts>
- 下载：`https://kenney.nl/media/pages/assets/input-prompts/8de120163f-1777890371/kenney_input-prompts_1.5.zip`
- 许可：CC0
- 版本：1.5 (2026-05-04)
- 文件数：3086（PNG + XML + Vector）
- 占用：21 MB
- 内容：按设备分类的按钮提示：
  - **Keyboard & Mouse** 220 个 × Default/Double：全键盘字母数字 + 鼠标按钮
  - **PlayStation Series / Xbox Series / Nintendo Switch / Switch 2 / Wii / WiiU / Gamecube / Steam Controller / Steam Deck / Meta Quest / Playdate / Generic / Touch / Flairs**
  - 每类含 `Default` / `Double` / `Fonts` / `Vector` 子目录与 `_sheet_default/double.png` + `.xml` sprite atlas
- 用途：教学引导（"按 Space 跳跃 / 按 Enter 确认"）、HUD 操作提示。

## 5. kenney_cursor-pixel-pack (CC0 · Kenney)

- 主页：<https://kenney.nl/assets/cursor-pixel-pack>
- 下载：`https://kenney.nl/media/pages/assets/cursor-pixel-pack/092f2b012b-1720601332/kenney_cursor-pixel-pack.zip`
- 许可：CC0
- 版本：1.0 (2024-07-10)
- 文件数：223
- 占用：956 KB
- 内容：220 个 16×16 像素光标 tile（指针 / 手势 / 沙漏 / 文本 / 攀爬 / 战斗等），含 `Tilemap` atlas。
- 用途：Phaser 自定义 cursor 替换、对话/任务上下文提示。

## 6. oga_rpg-gui-construction-kit (CC-BY 3.0 · Lamoot)

- 主页：<https://opengameart.org/content/rpg-gui-construction-kit-v10>
- 下载：`https://opengameart.org/sites/default/files/rpg_gui_v1.zip`
- 许可：**CC-BY 3.0** — 商用需署名 `Lamoot`
- 版本：v1.0 (2010-09-30)
- 文件数：5 PNG + 1 XCF + 1 ReadMe
- 占用：4.0 MB
- 内容：
  - `RPG_GUI_v1.png` 1024×666 完整 GUI 拼图集（窗口 9-slice、按钮、滚动条、卡片、ornament）
  - `RPG_GUI_v1_source.xcf` 2.9 MB GIMP 源文件（可二次开发）
  - `paper background.png` 羊皮纸纹理（任务面板背景）
  - `wood background.png` 木质纹理（界面外框）
  - `gradients.png` 渐变样板
- 用途：任务面板 / 角色面板 / 物品栏卡片底（与像素风混搭时需要缩放 → 适合作为参考美术，比赛用建议先转 16×16 像素化）。
- **署名要求**：在 `docs/CREDITS.md` 注明 `RPG GUI Construction Kit v1.0 by Lamoot (CC-BY 3.0)`

## 7. oga_golden-ui (CC-BY-SA 3.0 · Buch)

- 主页：<https://opengameart.org/content/golden-ui-bigger-than-ever-edition>
- 下载：
  - `https://opengameart.org/sites/default/files/ui_big_pieces_0.png` (864×568, 50 KB)
  - `https://opengameart.org/sites/default/files/ui_big_preview.png` (55 KB)
  - `https://opengameart.org/sites/default/files/ui_big_preview_part.png` (6 KB)
- 许可：**CC-BY-SA 3.0** — 商用需署名 + 衍生作品同许可
- 作者：Buch (2013-10-19)
- 文件数：3 PNG
- 占用：120 KB
- 内容：金色像素风窗口、9-slice 卡片、进度条、按钮、光标、minimap 框、inventory slot、HUD 元素。
- 用途：高级 RPG 风 HUD（成就卡 / 任务奖励弹窗 / 等级提升通知）。
- ⚠️ **SA 限制**：使用此素材的派生界面必须以同许可（CC-BY-SA 3.0）发布。仅作为参考美术或非衍生引用时可考虑；不建议作为核心 UI 资产，**未参与本次 MANIFEST 主映射**。

---

## 备选未下载（已评估）

| 源 | 状态 | 原因 |
|---|---|---|
| OGA 9-piece UI button set | ❌ 未下载 | 页面无附件链接（疑似下线/已合并） |
| Kenney Game Icons | ⚪ 已找到 URL 未下载 | 非像素，与 Pythontopia 像素风不符 |
| Kenney Scribble Platformer | ⚪ 已找到 URL 未下载 | 非像素 |
| Kenney Onscreen Controls | ❌ 无链接 | 仅触屏按钮，不适合教学网页 |

---

## 总计

- **下载 5 套新素材**：Kenney UI Pack / Input Prompts / Cursor Pixel Pack / OGA RPG GUI / OGA Golden UI
- **复用 2 套已有**：Kenney UI Pack RPG Expansion / Pixel Adventure（pixel_ui → inbox/ui 镜像）
- **总文件**：6808 个（PNG / XML / XCF / 文档）
- **总占用**：35 MB
- **许可**：5 套 CC0（最自由）+ 1 套 CC-BY 3.0（需署名）+ 1 套 CC-BY-SA 3.0（仅参考）
