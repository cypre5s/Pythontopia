# UI / HUD 素材来源

> Pythontopia R-100 · 素材抓取子代理 #4 (UI / HUD)
> 抓取日期：2026-05-21
> 代理：HTTP_PROXY=http://127.0.0.1:7892
> License 政策：**严格 CC0 / CC-BY 4.0**（跳过 CC-BY-SA / GPL / 未确认）

本目录收纳像素 UI 与 HUD 元素，覆盖 9-slice 对话框 / 按钮 4 态 / 进度条 / 物品栏 / 卡片背景 / 角标 / 评分星 / 键盘提示 / 光标。

**本轮 R-100 全部素材均为 CC0 1.0 Universal**（无需署名，致谢推荐）。

---

## 0. License 合规摘要

| 包 | 路径 | 协议 | 商用 | 衍生 | 署名 |
|---|---|---|---|---|---|
| Kenney UI Pack | `assets/inbox/ui/kenney_ui-pack/` | CC0 1.0 | ✅ | ✅ | 推荐 |
| Kenney Input Prompts | `assets/inbox/ui/kenney_input-prompts/` | CC0 1.0 | ✅ | ✅ | 推荐 |
| Kenney Cursor Pixel Pack | `assets/inbox/ui/kenney_cursor-pixel-pack/` | CC0 1.0 | ✅ | ✅ | 推荐 |
| Kenney UI Pack: Pixel Adventure | `assets/inbox/pixel_ui/kenney_ui-pack-pixel-adventure/` | CC0 1.0 | ✅ | ✅ | 推荐 |
| Kenney UI Pack: RPG Expansion | `assets/inbox/pixel_ui/kenney_ui-pack-rpg-expansion/` | CC0 1.0 | ✅ | ✅ | 推荐 |

> **本轮决策**：先前抓取的 `oga_golden-ui/`（CC-BY-SA 3.0）与 `oga_rpg-gui-construction-kit/`（CC-BY 3.0）已**移除**，因不符合 R-100 子代理 #4 "只接受 CC0 / CC-BY 4.0" 的硬约束。`.work/assets_dl/ui/` 中对应缓存也一并清理。

---

## 1. kenney_ui-pack (CC0 · Kenney, 本轮下载)

- 主页：<https://kenney.nl/assets/ui-pack>
- 下载 URL：`https://kenney.nl/media/pages/assets/ui-pack/f651646eab-1718203990/kenney_ui-pack.zip`
- 缓存：`.work/assets_dl/ui/kenney_ui-pack.zip` (1.2 MB)
- 许可：Creative Commons Zero (CC0 1.0)。可商用、可修改、可再分发、无需署名（致谢 `Kenney` / `www.kenney.nl` 是礼貌但非强制）
- 版本：2.0 (2024-06-12)
- 文件数：1315（870 PNG + Vector + 字体 + 文档）
- 占用：5.5 MB
- 内容：
  - 6 配色（Blue / Green / Grey / Red / Yellow + Extra）× Default/Double 像素密度
  - 控件：button（rectangle / round / square × border/flat/gloss/gradient/line × normal/depth 两态）
  - checkbox / radio / icon / slider / arrow / star / divider
- 用途：
  - 按钮多态合成（用 `_depth_flat.png` 作 normal，`_flat.png` 作 pressed）
  - 评分星 ★（实心 / 空心 / 深度空心 3 态）
  - 4 方向箭头（basic + decorative）
  - 通用 checkmark / cross / 控件
- **缺**：完整 9-slice 对话框 → 由 RPG Expansion 的 `panel_*` 补齐

## 2. kenney_input-prompts (CC0 · Kenney, 本轮下载)

- 主页：<https://kenney.nl/assets/input-prompts>
- 下载 URL：`https://kenney.nl/media/pages/assets/input-prompts/8de120163f-1777890371/kenney_input-prompts_1.5.zip`（"pixel-16"变体）
- 缓存：`.work/assets_dl/ui/kenney_input-prompts-pixel-16.zip` (5.2 MB)
- 许可：CC0 1.0
- 版本：1.5 (2026-05-04)
- 文件数：4644（3052 PNG + XML + Vector + 字体 + 预览）
- 占用：21 MB
- 内容：按设备分类的按键提示
  - **Keyboard & Mouse** 220 个键 × Default/Double：全键盘字母数字 + Space/Enter/Esc + 方向键 + Shift/Ctrl/Alt + Tab + 鼠标按键
  - **PlayStation Series / Xbox Series / Nintendo Switch / Switch 2 / Wii / WiiU / Gamecube / Steam Controller / Steam Deck / Meta Quest / Playdate / Generic / Touch / Valve Index / Flairs**
  - 每类含 `Default` / `Double` / `Fonts` / `Vector` 子目录与 `*_sheet_default/double.png` + `.xml` sprite atlas
- 用途：教学引导（"按 Space 跳跃 / 按 Enter 确认 / 按 ↑↓←→ 移动"）、HUD 操作提示、新手村教程

## 3. kenney_cursor-pixel-pack (CC0 · Kenney, 本轮下载)

- 主页：<https://kenney.nl/assets/cursor-pixel-pack>
- 下载 URL：`https://kenney.nl/media/pages/assets/cursor-pixel-pack/092f2b012b-1720601332/kenney_cursor-pixel-pack.zip`
- 缓存：`.work/assets_dl/ui/kenney_cursor-pixel-pack.zip` (100 KB)
- 许可：CC0 1.0
- 版本：1.0 (2024-07-10)
- 文件数：227（223 PNG + atlas + 文档）
- 占用：956 KB
- 内容：220 个 16×16 像素光标 tile
  - 指针（默认箭头 / 等待 / 选择）
  - 手势（点击 / 抓取 / 食指）
  - 沙漏 / 加载圆圈
  - 文本光标 (I-beam)
  - 攀爬 / 战斗 / 农具（锄头 / 斧子 / 剑 / 铲子）
  - 含 `Tilemap/tilemap.png` 与 `tilemap_packed.png` atlas
- 用途：Phaser 自定义 cursor 替换、农具切换、教学/任务上下文提示

## 4. kenney_ui-pack-pixel-adventure (CC0 · Kenney, 复用自 pixel_ui/)

- 主页：<https://kenney.nl/assets/ui-pack-pixel-adventure>
- 许可：CC0 1.0
- 版本：2.0 (2024-09-04)
- 文件数：519（514 PNG + 4 个 atlas + 文档）
- 占用：2.2 MB
- 位置：`assets/inbox/pixel_ui/kenney_ui-pack-pixel-adventure/`（**已存在，本轮未重新下载**）
- 内容：纯像素 UI tile（16×16 / 32×32）×（Thick / Thin outline）
  - **Small tiles 16×16 · Thick outline**：161 个独立 tile + tilemap.png + tilemap_packed.png
  - **Small tiles 16×16 · Thin outline**：161 个独立 tile + 同上 atlas
  - **Large tiles 32×32 · Thick outline**：91 个独立 tile + 同上 atlas
  - **Large tiles 32×32 · Thin outline**：91 个独立 tile + 同上 atlas
  - tile 覆盖：button（normal / pressed）、9-slice 边框、checkbox、heart、coin、key、arrow、X / O、滚动条、对话气泡、键盘字母键、数字键、Tag、星星
- 用途：Pythontopia 主像素 UI 风格（16×16 与小镇主体匹配）

## 5. kenney_ui-pack-rpg-expansion (CC0 · Kenney, 复用自 pixel_ui/)

- 主页：<https://kenney.nl/assets/ui-pack-rpg-expansion>
- 许可：CC0 1.0
- 版本：1.0 (2014-04-20)
- 文件数：94（90 PNG + 1 spritesheet + Vector + 文档）
- 占用：612 KB
- 位置：`assets/inbox/pixel_ui/kenney_ui-pack-rpg-expansion/`（**已存在，本轮未重新下载**）
- 内容：复古 RPG 风（beige / blue / brown / grey）
  - `panel_<color>.png` 100×100 9-slice 卡片背景（4 色 + beigeLight）
  - `panelInset_<color>.png` 内嵌面板（4 色 + beigeLight）
  - `buttonLong_<color>.png` + `_pressed.png` 横条按钮 2 态（4 色）
  - `buttonSquare_<color>.png` + `_pressed.png` 方形按钮 2 态（4 色）
  - `buttonRound_<color>.png` 圆形按钮（4 色）
  - `bar<Color>_horizontal/vertical_Left/Mid/Right.png` 9-slice 进度条（Blue / Green / Red / Yellow + Back）
  - `iconCheck/Cross/Circle_<color>.png` 角标（4 色）
  - `cursorGauntlet/Hand/Sword_<color>.png` 装饰光标
  - `arrow<Color>_left/right.png` 翻页箭头（4 色）
- 用途：任务面板 / 物品栏底板 / 进度条 / 角标 / 翻页箭头的主用源

---

## 备选未下载（已评估）

| 源 | License | 状态 | 原因 |
|---|---|---|---|
| OGA "Golden UI" (Buch) | CC-BY-SA 3.0 | ❌ 不下载 | SA 传染，违反本轮硬约束 |
| OGA "RPG GUI Construction Kit v1.0" (Lamoot) | CC-BY 3.0 | ❌ 不下载 | 非 4.0，违反本轮硬约束 |
| OGA "9-piece UI button set" | 未确认 | ❌ 不下载 | 页面无附件链接（疑似下线/已合并） |
| Kenney Game Icons | CC0 | ⚪ 可后续补 | 非像素风，与 Pythontopia 16×16 像素风不匹配 |
| Kenney Scribble Platformer | CC0 | ⚪ 可后续补 | 非像素风 |
| Kenney Onscreen Controls | CC0 | ⚪ 可后续补 | 仅触屏按钮，桌面网页无需 |

> 若未来需要 hover / disabled 多态像素按钮原图（而非运行时合成），可以考察：
> - Kenney "Pixel Platformer GUI"（CC0，未抓取）
> - itch.io 公开 CC0 UI kit（需逐个核验 license）

---

## 总计

- **本轮下载新包**：3 套（kenney_ui-pack / input-prompts / cursor-pixel-pack），全 CC0
- **复用 pixel_ui/ 已有**：2 套（pixel-adventure / rpg-expansion），全 CC0
- **下载缓存**：`.work/assets_dl/ui/` 3 个 zip，共 6.2 MB
- **入库新增**（`assets/inbox/ui/`）：6186 文件，28 MB
- **复用既有**（`assets/inbox/pixel_ui/`）：613 文件，2.8 MB
- **5 套合并覆盖**：6799 文件，30.8 MB（PNG 实占 ~5249）
- **License**：5/5 CC0 1.0 — 零署名强制、零传染、零商用障碍
