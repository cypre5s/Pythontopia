# Android `assets/maps` — 小镇地图素材（R-14 单中央小镇）

## 文件清单

| 路径 | 用途 | 来源 | License |
| --- | --- | --- | --- |
| `town_hub.json` | Tiled JSON 地图（50×40, 16px）。**R-14 起为唯一场景** | `frontend/public/assets/maps/town_hub.json`（由 `frontend/scripts/generate-maps.mjs` 程序化生成），与之**字节一致** | 项目源码部分（CC-BY-SA 3.0 包装版本） |
| `tilemap_packed.png` | 12×11 瓦片图集（132 tiles, 16×16）的参考副本 | Kenney Tiny Town 1.1 (`frontend/public/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png`) | CC0 1.0 |
| `LICENSE-kenney-tiny-town.txt` | Kenney 资产 license 副本 | Kenney.nl | CC0 1.0 |

> 注意：游戏运行时加载的瓦片集是 `assets/tilesets/town.png`（见下文），本目录的 `tilemap_packed.png`
> 仅作为来源参考副本保留。

## 当前消费方（R-14）

`:game/WorldRenderScreen` 经 `:game/HubGame` 由 `:feature:hub` 挂载，**只渲染单中央小镇 `town_hub`**：
按 ground → buildings → decoration 分层渲染 + 视锥剔除，碰撞取自 `buildings` 瓦片层
（与可见建筑像素级一致），`objects` 对象层在渲染时被安全跳过（NPC 锚点由 `:game/WorldMapData`
的 `WorldMaps.townHub.npcs` 提供，已对齐 Web 端 `objects` 层）。

- `WorldRenderScreen.show()` 强制载入 `town_hub`；即便历史存档停在已删除的外围图，`WorldMaps.byKey` 也回落到 hub。
- `town_hub` 无边缘出口（单图世界）；旧 5 图世界的外围图（farm/mine/campus/port）JSON 与 `WorldMaps` 定义已整体移除。

## 像素素材（真素材已落地）

真正的 CC0/CC-BY 二进制图集与角色精灵**已落地仓库**，`GameAssets` 在运行时**优先加载真素材**，
仅在缺图/损坏时回退到 `:game/ProceduralArt` 的程序化配色（保证永不崩）：

- 瓦片集：`assets/tilesets/town.png` 与 Kenney Tiny Town `tilemap_packed.png` **像素一致**
  （CC0；`frame = gid - 1`，firstgid=1，12 列 × 11 行 = 132 块，与本目录 JSON 完全对齐）；
  `assets/tilesets/dungeon.png` 为 Kenney Tiny Dungeon 对应图（旧矿洞图素材，单图世界下运行时不再加载）。
- 角色：`assets/sprites/player.png`（LPC walk9）+ `assets/sprites/npc/*.png`；缺图时回退程序化 4 向小人。

程序化生成属纯算法产物（CC0），不引入任何商业素材。

## 与 Web 端的关系

- `town_hub.json` 与 `frontend/public/assets/maps/town_hub.json` **字节一致**（同一份 Tiled JSON），
  避免 Android 与 Web 端在地图布局上漂移。后续如改动地图，应同步两份（或抽到 `data/maps/`）。
- `assets/tilesets/town.png` 与 `frontend/public/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png`
  像素一致（本仓库直接复制该官方文件）。

## 不做的事

- 不嵌入任何商业像素游戏官方素材（参见 `/home/cypress/Pythontopia/AGENTS.md`「禁止事项」）。
- 不在 Android 端运行 Python；评测全部走服务端。
