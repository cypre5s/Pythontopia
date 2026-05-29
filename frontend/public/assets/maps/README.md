# Pythontopia 地图（W2-MAPS 子代理输出）

Pythontopia M2 阶段的 5 张分块大地图，全部以 16×16 源 tile 渲染，Phaser 端按 2× 放大到 32×32 实际像素。

## 概览

| key | 中文名 | 尺寸（列×行） | tileset | BGM | 出口 | spawn |
| --- | --- | --- | --- | --- | --- | --- |
| town_hub | Pythontopia 小镇 | 50×40 | kenney_tiny_town | bgm_town_day | farm_north, mine_south, campus_east, port_west | (25, 20) |
| farm_north | 北农场 · 棉麻试验田 | 40×40 | kenney_tiny_town | bgm_town_day | town_hub | (20, 37) |
| mine_south | Bug 矿洞 · 织丝走廊 | 30×30 | kenney_tiny_dungeon | bgm_bug_mine | town_hub | (15, 2) |
| campus_east | 校园 | 40×30 | kenney_tiny_town | bgm_quest | town_hub | (2, 15) |
| port_west | 西港 · 棉布商埠 | 35×25 | kenney_tiny_town | bgm_town_eve | town_hub | (33, 12) |

> 出口列说明：当前地图边缘门洞会切换到列出的目标地图；M2-ROUTER 子代理读取 `exits` 解析跳转。

## 每张地图的细节

### town_hub.json（50×40，中央小镇）

- 4 边都有出口：north→farm_north / south→mine_south / east→campus_east / west→port_west
- 建筑（6 栋）：
  - `building_mayor_hall`（4×5）→ `/profile-room`
  - `building_shop_west`（4×4）→ `/shop-west`
  - `building_shop_east`（4×4）→ `/shop-east`
  - `building_house_a / b / c`（4×4 三栋住宅）
- 中央喷泉：(24, 19) 起 2×2 粉色花坛
- NPC：`mayor_py`, `librarian_doc`, `coach_q`, `sage_review`, `gardener`, `chef`

### farm_north.json（40×40，知识农场）

- 出口：south→town_hub
- 7 个知识田（5×5 一块）：basics / variable / data_types / control_flow / loop / function / class
- 建筑：`building_barn`（谷仓 5×4）、`building_chicken_coop`（鸡舍 4×3）
- 装饰：水井 (32, 25)、田间小路、围栏
- NPC：`farmer_kc`

### mine_south.json（30×30，Bug 矿洞）

- 出口：north→town_hub
- 使用 **kenney_tiny_dungeon** tileset（其它地图均使用 kenney_tiny_town）
- 两个矿洞入口（2×2 黑色 void）：东侧 (8, 12)、西侧 (20, 12)，分别打开 `/bug-mine?side=east|west`
- 外围一圈深色墙壁，矿石与宝箱散布在地板上
- NPC：`foreman_bug`

### campus_east.json（40×30，校园）

- 出口：west→town_hub
- 北行 3 栋教学楼：图书馆（→ `/library`）、教室（题库，→ `/problems`）、复盘大厅（→ `/review-hall`）
- 南行 1 栋工坊（→ `/workshop`）
- 中央东西向砂石步道
- NPC：`librarian_doc`, `coach_q`, `sage_review`

### port_west.json（35×25，码头港口）

- 出口：east→town_hub
- 南侧 5 行海水（rows 20–24），中间 12 格沙地码头
- 灯塔（2×4，西北角）+ 商船（4×2，水边）
- 班级广场（22, 6 起 6×6 围栏空地）
- NPC：`merchant`, `class_president`

## 文件结构

```
frontend/public/assets/maps/
├── index.json        # 5 张地图的 metadata 索引（M2-ROUTER 入口）
├── README.md         # 本文件
├── town_hub.json     # 50×40
├── farm_north.json   # 40×40
├── mine_south.json   # 30×30（dungeon tileset）
├── campus_east.json  # 40×30
└── port_west.json    # 35×25
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
- mine_south 暂未铺设矿洞内部副本——副本场景由独立 Phaser scene 处理，本 JSON 仅负责矿洞外景与入口判定。
- 字段 `spawn` 在 index.json 与 map.objects 内重复出现：runtime 默认读 index.json，地图内的 `spawn` 对象作为权威覆盖。
