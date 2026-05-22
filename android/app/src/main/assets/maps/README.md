# Android `assets/maps` — Hub demo 素材

## 文件清单

| 路径 | 用途 | 来源 | License |
| --- | --- | --- | --- |
| `town_hub.json` | Tiled JSON 地图（50×40, 16px），保留给 Android-R13+ 解析 | `frontend/public/assets/maps/town_hub.json`（项目内由 `frontend/scripts/generate-maps.mjs` 程序化生成） | 项目源码部分（CC-BY-SA 3.0 包装版本） |
| `tilemap_packed.png` | 12×11 瓦片图集（132 tiles, 16×16）| Kenney Tiny Town 1.1 (`frontend/public/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png`) | CC0 1.0 |
| `LICENSE-kenney-tiny-town.txt` | Kenney 资产 license 副本 | Kenney.nl | CC0 1.0 |

## 当前消费方

Android-R12 demo 阶段 **不解析** 上述任何文件——`:game/HubScene` 走程序化绘制
（草地 + 路 + 建筑 + 门洞 + NPC 全部以 `ShapeRenderer` 几何填色完成）。

文件提前落地是为：

1. 让 Android-R13+ 接入瓦片渲染时无需再处理资产路径迁移；
2. 让 LICENSE 跟资产同步存在，避免单独的二进制提交破坏 license 审计；
3. Android-R13+ 真正用 `tilemap_packed.png` 时调用：
   ```kotlin
   val pixmap = Pixmap(Gdx.files.internal("maps/tilemap_packed.png"))
   ```

## 与 Web 端的关系

- `town_hub.json` 与 `frontend/public/assets/maps/town_hub.json` 字节一致（同一份 Tiled JSON），
  避免 Android 和 Web 端在地图布局上漂移。后续如改动地图，应同步两份（或抽到 `data/maps/`）。
- `tilemap_packed.png` 与 `frontend/public/assets/pixel/tilesets/kenney-tiny-town/Tilemap/tilemap_packed.png`
  也字节一致。

## 不做的事

- 不嵌入任何商业像素游戏官方素材（参见 `/home/cypress/Pythontopia/AGENTS.md`「禁止事项」）。
- 不在 Android 端运行 Python；评测全部走服务端。
