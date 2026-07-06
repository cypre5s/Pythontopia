# Pythontopia 地图（R-14 世界收缩）

R-14 方案 A：仅生成 **town_hub**（中央小镇）。外围图与 Bug 矿洞风景图（mine_south）均已移除；Bug 矿洞功能仍在，经 town_hub building → /bug-mine 进入。

## 概览

| key | 中文名 | 尺寸（列×行） | tileset | BGM | 出口 | spawn |
| --- | --- | --- | --- | --- | --- | --- |
| town_hub | Pythontopia 小镇 | 50×40 | kenney_tiny_town | bgm_town_day |  | (25, 20) |

## town_hub.json（50×40）

- 出口：无（R-14 单中央 hub，外围图已移除；Bug 矿洞经 building → /bug-mine 路由进入）
- 学习建筑：图书馆 / 工坊 / 训练场 / 竞技场 / 复盘大厅 / Bug 矿洞入口等
- 7 核心 NPC 贴近建筑入口排布：镇长 Py / 馆长 Doc / 工坊师傅 / 教务 Q / 矿头 Bug / 复盘师 Sage / 班长（见 `scripts/generate-maps.mjs` 内 `makeNpc` 列表）

## 文件结构

```
frontend/public/assets/maps/
├── index.json
├── README.md
└── town_hub.json
```

## 字段约定

### tileset 引用

- 每张地图只用 1 个 tileset，`firstgid = 1`
- `tilesets[].image` 路径相对 maps/ 目录：
  - kenney_tiny_town → `../pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png`
  - kenney_tiny_dungeon → `../pixel/tilesets/kenney-tiny-dungeon/Tilemap/tilemap_packed.png`

### layer 顺序

| id | name | type | 说明 |
| --- | --- | --- | --- |
| 1 | ground | tilelayer | 草地 / 沙地 / 水面（不参与碰撞） |
| 2 | buildings | tilelayer | 房屋外墙 + 矿洞入口（runtime 视为碰撞，玩家不可穿过） |
| 3 | decoration | tilelayer | 树木、花坛、围栏、矿石（一般不参与碰撞，纯视觉） |
| 4 | objects | objectgroup | exit / building / npc / spawn / landmark 等元数据 |

### objects 对象类型

- `exit`：边缘门洞，`properties.target` 指向下一张地图 key
- `building`：建筑点击区，`properties.route` 指向 Vue 路由
- `npc`：NPC 出生点，`properties.key` / `label` / `sprite`
- `spawn`：玩家默认出生点（每张地图唯一）
- `low_obstacle`：低障碍物，正常行走视为阻挡，玩家可面朝它按 Space 跳过 1 格
- `landmark` / `field` / `plaza`：纯视觉锚点

## 编辑流程

### 方式 1：在 Tiled 编辑器里改

1. 安装 [Tiled Map Editor](https://www.mapeditor.org/)（1.10+）
2. 用 Tiled 打开任意 `*.json`
3. 编辑后导出回原 JSON 即可

### 方式 2：改脚本重新生成（推荐）

地图布局都写在 `scripts/generate-maps.mjs` 顶部的常量 / 函数里：

```bash
cd /home/cypress/Pythontopia
node scripts/generate-maps.mjs
```

执行后会重新覆盖：

- `frontend/public/assets/maps/*.json`
- `frontend/public/assets/maps/index.json`
- 本 README.md 不会自动重写（只在脚本本体改动时更新一次）

## 当前已知 TODO

- decoration 层目前只散布 bush / flower / fence 三类瓷砖；后续 W2-MAPS 二期会引入更精细的 Tiled object（NPC 走路路径、商铺货架、矿洞机关等）。
- 各地图碰撞规则现在由 buildings 层 + 边界 implicit 阻挡推断；M2-ROUTER 子代理可能需要额外的 `collision` 数据层来标记不可穿越的装饰（例如树木）。
- 字段 `spawn` 在 index.json 与 map.objects 内重复出现：runtime 默认读 index.json，地图内的 `spawn` 对象作为权威覆盖。
