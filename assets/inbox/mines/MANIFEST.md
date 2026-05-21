# Pythontopia / Bug 矿洞 (Mines) 素材清单

> 目录：`assets/inbox/mines/`
> 关联文档：`SOURCES.md`（来源与 license）
> 目标场景：Pythontopia 调试副本「Bug 矿洞」——学生提交代码失败 / 抛异常时进入，击败 bug 怪物 = 修复对应错误。

## 0. 套件总览

| 套件 | License | 风格 | 主要用途 |
|------|---------|------|----------|
| `kenney_tiny-dungeon/` | CC0 | 暖色木 + 灰石 dungeon | 主玩法 tile / 5 种 bug 怪物 / 玩家备选 |
| `kenney_roguelike-caves-dungeons/` | CC0 | 灰色洞穴 + 棕矿洞 | 第 2-3 层矿洞地形 / 矿石 / 蘑菇病害 |
| `oga_buch_dungeon/` | CC0 | 深色阴森地牢 | 矿洞主地图 + 火把（橙色火焰） |
| `oga_calciumtrice_dungeon/` | CC-BY 3.0 | 写实灰色地牢 | 火焰特效 / 大型 bug 怪物 / 角色细化 |

---

## 1. 矿洞地图 (Map / Tileset)

「Bug 矿洞」分 3 层，深度递增，地形主题不同：

### 1.1 Layer 1 — 入口矿区（黄铜色石头，温暖）

- **主 tileset**：`kenney_tiny-dungeon/Tilemap/tilemap_packed.png`（12×11 tile，无 padding）
- **可拆分**：`kenney_tiny-dungeon/Tiles/tile_0XXX.png`（132 个独立 16×16 tile）
- **核心 tile（按 row, col 顺序，0-based）**：
  - 地面石板：`tile_0040.png`（灰石）、`tile_0048.png` / `tile_0049.png` / `tile_0050.png`（橙土）
  - 墙体：`tile_0012.png` ~ `tile_0023.png`（row 1 各角度石墙）
  - 楼梯：`tile_0024.png`、`tile_0025.png`（上 / 下）
  - 入口红门（bug 房间标记）：`tile_0029.png`
  - 木门 / 开启 / 关闭：`tile_0020.png` ~ `tile_0023.png`、`tile_0031.png` ~ `tile_0035.png`

### 1.2 Layer 2 — 深矿巷道（灰色洞穴 + 矿石点）

- **主 tileset**：`kenney_roguelike-caves-dungeons/Spritesheet/roguelikeDungeon_transparent.png`
- **风格**：偏写实洞穴，自带矿石与水域，适配矿坑叙事
- **可用素材区**：
  - 灰色洞穴墙体（左上区）
  - 棕色矿洞墙体（右上 / 右下区）
  - 水池 / 毒池（中下区，可作陷阱）
  - 小型矿石 / 骨头 / 蘑菇（中上区，可作 KC 掉落物或病害）
  - 灰色 / 蓝色房间地砖（左下区）

### 1.3 Layer 3 — 终局深渊（深色石板 + 火把）

- **主 tileset**：`oga_buch_dungeon/dungeon_tiles_0.png`
- **带网格版本（便于切片）**：`oga_buch_dungeon/tilesheet_1.png`
- **预览参考（完整 dungeon 布局示例）**：`oga_buch_dungeon/dungeon_pre.png`
- **风格**：深灰石板 + 阴影，氛围最强（适合 boss 关卡）
- **关键区**：
  - 大型石板房间（左上）
  - 凹陷地面（楼梯井）
  - 拱门入口
  - 木门 + 铁栅栏门
  - 火把（橙色火焰，详见 §2）

### 1.4 备用 / 高品质替代

- **完整套件**：`oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png`（含天花板）
- **简化套件**（推荐用于 Phaser 切片）：`oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice_simple.png`
- **独立天花板**：`oga_calciumtrice_dungeon/ceilings.png`
- **使用说明**：`oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.zip` 内的 `dungeon tileset calciumtrice instructions.txt`
  - 关键提醒：阴影应作为 multiply 图层应用于角色 / 物体下方；椅子 / 容器 / 火把 / 角色等垂直偏移 4-6 px。

---

## 2. 火把 / 灯笼 / 走廊光效 (Torches / Light)

| 类型 | 文件 | 尺寸 | 说明 |
|------|------|------|------|
| 主火把（首选）| `oga_buch_dungeon/dungeon_tiles_0.png` 中部小型橙色火把 | 16×16 | 深色场景反差强，氛围最佳；从 tilesheet 切片，坐标约 (col 11, row 6) |
| 火焰精灵（动效用）| `oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png` 中部「FIRE 16×16」标签区 | 16×16 | 多帧火焰，适配 Phaser AnimatedSprite |
| 火把（带架）| `oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png` 中部「TORCHES 16×16」标签区 | 16×16 | 墙上挂式 |
| 蜡烛 / 烛台 | `oga_buch_dungeon/dungeon_tiles_0.png` 中部小烛 | 16×16 | 弱光源，可作 KC 进度提示 |
| Kenney 灯笼备选 | `kenney_tiny-dungeon/Tiles/tile_0072.png` ~ `tile_0083.png` | 16×16 | 风格与 Kenney 整体一致 |
| 走廊光效 | Phaser radial gradient + 火焰 sprite 组合 | shader | 建议用 Phaser Light2D pipeline 渲染，火把为光源点 |

> 实现建议：火把使用 2-4 帧动画，光源半径 48-64 px，配合 `oga_buch_dungeon` 的深色背景能强化矿洞阴森感。

---

## 3. 矿石 (Ore / Crystals)

Bug 矿洞核心比喻：**每种 bug = 一种矿石**。学生提交失败时，相应矿石脉露出；正确修复后，矿石变为「掌握度宝石」。

| 矿石类型 | 对应 bug | 来源文件（候选）|
|----------|----------|------------------|
| 紫水晶（NameError）| 未定义名 | `kenney_roguelike-caves-dungeons/Spritesheet/roguelikeDungeon_transparent.png` 中部小水晶 / Kenney `kenney_tilesets/kenney_tiny-dungeon/Tiles/tile_0095.png` 紫色魔法石 |
| 绿翡翠（TypeError）| 类型不匹配 | `kenney_roguelike-caves-dungeons` 绿色矿点 / `oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png` 「POTIONS」绿瓶 |
| 红宝石（IndexError）| 索引越界 | `kenney_roguelike-caves-dungeons` 红色矿点 / Kenney `tile_0127.png` 红药水 |
| 金锭（KeyError）| 钥匙缺失 | `kenney_roguelike-caves-dungeons` 金色矿点 / `oga_calciumtrice_dungeon` 「LOOT 16×16」金币堆 |
| 蓝晶石（ValueError）| 值不合法 | `kenney_roguelike-caves-dungeons` 蓝色矿点 / Kenney `tile_0125.png` 蓝瓶 |

> 这些是初版候选；R-101 阶段可由 ResourceDesignerAgent 进一步从 spritesheet 切片并统一风格。

---

## 4. Bug 怪物 5 种映射 (Core Bug Monsters)

每种怪物 = 一类 Python 错误。怪物从矿石脉里出现，击败方式 = 修复对应类型的 bug 题目。Pythontopia 学生完成相应 KC 练习后，怪物变为可收集的「Bug 标本」。

| Bug 类型 | 中文 | 怪物形象 | 主 sprite | 备用 sprite | 叙事 |
|---------|------|----------|----------|------------|------|
| **NameError** | 变量未定义 | 紫色幽灵 / 无名亡灵 | `kenney_tiny-dungeon/Tiles/tile_0121.png` | `kenney_tiny-dungeon/Tiles/tile_0117.png`（小灰幽灵）| 你引用了一个不存在的「名字」，被无主灵魂质问。修复方式：先 `=` 定义再使用。|
| **TypeError** | 类型不匹配 | 绿色史莱姆 / 变形黏液 | `kenney_tiny-dungeon/Tiles/tile_0108.png` | `oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png` 「MONSTERS 16×16」绿黏液 | 你把 `int` 当 `str` 用，史莱姆形态不定。修复方式：`int(...)` / `str(...)` 显式转换。|
| **IndexError** | 索引越界 | 红色小恶魔 / 越界跳脱 | `kenney_tiny-dungeon/Tiles/tile_0110.png` | `oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png` 「MONSTERS 16×32」恶魔形象 | 索引超出列表边界，小恶魔从洞穴边缘跳出。修复方式：检查 `len()` 与索引范围。|
| **KeyError** | 字典键缺失 | 棕色老鼠 / 偷钥匙小贼 | `kenney_tiny-dungeon/Tiles/tile_0109.png` | `kenney_tiny-dungeon/Tiles/tile_0123.png`（棕脸）| 字典里没有这个 key，老鼠四处偷你的钥匙。修复方式：`dict.get(k, default)` 或先 `if k in d`。|
| **ValueError** | 值不合法 | 红色蝙蝠 / 病害飞虫 | `kenney_tiny-dungeon/Tiles/tile_0111.png` | `oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png` 「MONSTERS」红色生物 | 传入的值不在合法范围（如 `int("abc")`），蝙蝠携带病害扑来。修复方式：先 `try/except` 或校验。|

### 4.1 备用 / 第二批 bug 怪物（Beta 之后扩展）

| Bug 类型 | 怪物形象 | 主 sprite |
|---------|----------|----------|
| **AttributeError** | 棕色蜘蛛 / 织错网 | `kenney_tiny-dungeon/Tiles/tile_0120.png` |
| **ZeroDivisionError** | 棕色兽人 / 巨型敌人 | `kenney_tiny-dungeon/Tiles/tile_0123.png` |
| **ImportError** | 红法师 / 模块咒师 | `kenney_tiny-dungeon/Tiles/tile_0122.png` |
| **SyntaxError**（关卡 boss）| 大型骷髅 | `oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png` 「MONSTERS 16×32」骷髅 |
| **IndentationError**（关卡 boss）| 巨型兽人 | `oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png` 「MONSTERS 16×32」兽人 |

### 4.2 Bug 标本背包图标（收集后展示）

学生击败 bug 后获得标本，背包格显示小尺寸图标，建议尺寸 16×16，直接复用上表 sprite。R-103 阶段需要 PracticeAgent 在 KC 关联表里写入：

```
kc_id -> bug_type -> sprite_path
```

---

## 5. 玩家 / 矿工角色（备用 Player Sprite）

「Bug 矿洞」是 Pythontopia 的副本场景，玩家形象通常沿用 R-100 任务 #1 的 character 套件。如果矿洞内需要特殊矿工 / 法师装扮：

| 角色 | 来源 | 备注 |
|------|------|------|
| 法师（紫袍）| `kenney_tiny-dungeon/Tiles/tile_0084.png` | 默认调试者形象 |
| 战士（红袍）| `kenney_tiny-dungeon/Tiles/tile_0085.png` | 备选 |
| 探险者（绿）| `kenney_tiny-dungeon/Tiles/tile_0086.png` ~ `tile_0088.png` | 备选 |
| 公主 / 学者 | `kenney_tiny-dungeon/Tiles/tile_0089.png` ~ `tile_0091.png` | 备选 |
| Buch 法师 | `oga_buch_dungeon/dungeon_tiles_0.png` 中部小法师 | 风格与矿洞最贴合 |
| Calciumtrice 角色 | `oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png` 「CHARACTERS 16×32」整排 | 多色多职业 |

---

## 6. 装饰 / 道具

| 类型 | 文件 | 用途 |
|------|------|------|
| 宝箱（关卡奖励）| `kenney_tiny-dungeon/Tiles/tile_0089.png` 区附近 / `oga_calciumtrice_dungeon` 「CONTAINERS」 | KC 完成奖励 |
| 钥匙 | `kenney_tiny-dungeon/Tiles/tile_0096.png` 区 | KeyError 修复证明 |
| 卷轴（提示）| `oga_calciumtrice_dungeon/dungeon_tileset_calciumtrice.png` 「SCROLLS」 | 显示错误提示 / hint |
| 药水（血量）| `kenney_tiny-dungeon/Tiles/tile_0125.png` ~ `tile_0127.png` | 调试次数回复 |
| 蘑菇 / 病害 | `kenney_roguelike-caves-dungeons` 绿色蘑菇区 | Bug 蔓延视觉 |
| 骨头 / 警示 | `kenney_roguelike-caves-dungeons` 骨头 / 头骨 | 失败提交标记 |

---

## 7. 统计

- **套件数**：4
- **License 分布**：3 CC0 + 1 CC-BY 3.0
- **总文件数（含 symlink 目标）**：
  - `mines/oga_buch_dungeon/`：4 png
  - `mines/oga_calciumtrice_dungeon/`：6 png + 1 zip
  - `mines/kenney_tiny-dungeon/`（→ 已存在 Kenney 资产目录）：132 单 tile + 2 拼合图 + 元数据
  - `mines/kenney_roguelike-caves-dungeons/`（→ 已存在 Kenney 资产目录）：2 拼合图 + 元数据
- **磁盘占用（mines/ 直接占用）**：~388 KB（不含 symlink 目标）
- **磁盘占用（mines/ 全引用资产合计）**：~1.3 MB（含 Kenney 真实数据）
- **bug 怪物 sprite 已确认 5 + 备用 5**：见 §4

## 8. R-101 后续 TODO（不在本任务范围）

- ResourceDesignerAgent 把 4 套 spritesheet 切成统一 16×16 Pythontopia 命名空间 tile（`mine.floor.gray.01`、`mine.bug.name_error` 等）
- 用 Phaser 3 Tiled JSON 描述 3 层矿洞布局
- 火焰 / 火把 sprite 接 Phaser AnimatedSprite，2-4 帧 / 100 ms
- KC 表新增 `bug_type` 字段，关联到本表中的 sprite
- 班级公告板「Bug 矿洞副本」入口
