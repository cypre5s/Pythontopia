# Android `assets/maps` — Hub demo 素材

## 文件清单

| 路径 | 用途 | 来源 | License |
| --- | --- | --- | --- |
| `town_hub.json` | Tiled JSON 地图（50×40, 16px），保留给 Android-R13+ 解析 | `frontend/public/assets/maps/town_hub.json`（项目内由 `frontend/scripts/generate-maps.mjs` 程序化生成） | 项目源码部分（CC-BY-SA 3.0 包装版本） |
| `tilemap_packed.png` | 12×11 瓦片图集（132 tiles, 16×16）| Kenney Tiny Town 1.1 (`frontend/public/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png`) | CC0 1.0 |
| `LICENSE-kenney-tiny-town.txt` | Kenney 资产 license 副本 | Kenney.nl | CC0 1.0 |

## 当前消费方

`:game/WorldRenderScreen` **已解析** 这 5 张 `*.json`（[TiledMapVisual]）并按 ground →
buildings → decoration 分层渲染 + 视锥剔除，玩家走到地图边缘出口自动切图，碰撞取自
buildings 瓦片层。

**瓦片集 / 角色精灵的二进制 PNG（Kenney tiny-town/dungeon、LPC 角色）目前未落地仓库**。
为保证缺图不崩且仍能忠实渲染，`:game/ProceduralArt` + `GameAssets` 在运行时**程序化生成**
等价配色：

- 瓦片集：按 `scripts/generate-maps.mjs` 的 Kenney 帧语义（grass/path/water/roof/wall/door/
  ore…）生成 12×11×16px 调色瓦片集（`frame = gid - 1`，与本目录 JSON 完全对齐）；
- 角色：玩家 + NPC 用 Pixmap 现画的简易 4 向像素小人（NPC 上衣色按精灵文件名稳定派生）。

若日后把真正的 CC0/CC-BY 二进制图集放到 `assets/tilesets/town.png`、`assets/tilesets/dungeon.png`、
`assets/sprites/player_sara2.png`、`assets/sprites/npc/*.png`，`GameAssets` 会自动优先加载真素材，
无需改代码。程序化生成属纯算法产物（CC0），不引入任何商业素材。

## 与 Web 端的关系

- `town_hub.json` 与 `frontend/public/assets/maps/town_hub.json` 字节一致（同一份 Tiled JSON），
  避免 Android 和 Web 端在地图布局上漂移。后续如改动地图，应同步两份（或抽到 `data/maps/`）。
- `tilemap_packed.png` 与 `frontend/public/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png`
  也字节一致。

## 不做的事

- 不嵌入任何商业像素游戏官方素材（参见 `/home/cypress/Pythontopia/AGENTS.md`「禁止事项」）。
- 不在 Android 端运行 Python；评测全部走服务端。
