# ULPC 全动作角色（方案A：官方生成器素材合成）

> R-12 动作模组：主角与世界 NPC 改用**官方 Universal-LPC-Spritesheet-Character-Generator**
> 的整套素材库合成「全动作」角色表（含 run / jump / idle / sit / emote + 6 基础动作）。

## 来源

- 官方仓库：<https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator>
- 在线版：<https://sanderfrenken.github.io/Universal-LPC-Spritesheet-Character-Generator/>
- 合成脚本：`scripts/build-ulpc-characters.py`（用官方 `spritesheets/` 素材 + `sheet_definitions/`
  的 zPos/路径/动画清单 + 官方 `sources/state/constants.ts` 的 `ANIMATION_OFFSETS` 布局忠实合成；
  **不含任何手绘像素**，全部来自官方 LPC 素材库）。

## 产物

`frontend/public/assets/pixel/sprites/ulpc/*.png`（13 列 × 42 行，64px/帧，Expanded 布局）：

- `player_sara2`（主角，女性体 + 长发 + 长袖 + 紧腿裤 + 鞋）
- `ulpc_villager_f / ulpc_villager_m / ulpc_scholar / ulpc_worker / ulpc_lady / ulpc_youth / ulpc_elder / ulpc_guard`（8 个变体 NPC）

## License

LPC 素材为 **CC-BY-SA 3.0 / OGA-BY 3.0 / CC-BY 3.0+ / CC0 / GPL** 分文件混合（见
`CREDITS-ulpc-used.txt` 本项目实际使用部件的逐项署名，`LICENSE-ulpc.txt` 为生成器仓库 LICENSE）。
本项目接受 CC-BY-SA 3.0 传染：包装作品同时以 CC-BY-SA 3.0+ 公开发布，代码层保持 MIT。

> 复现：`git clone` 官方仓库到 `/home/cypress/ulpc-gen`，再 `python3 scripts/build-ulpc-characters.py`。
