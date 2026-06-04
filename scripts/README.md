# scripts/ — 构建与资产流水线

仓库根的构建脚本说明。运行前确保在仓库根目录。

## 地图 / 数据

| 脚本 | 作用 |
| --- | --- |
| `generate-maps.mjs` | 程序化生成 5 张 Tiled 地图 JSON 到 `frontend/public/assets/maps/`。 |
| `sync-assets.mjs` | 把 `assets/inbox/`（已审计离线镜像）增量同步到 `frontend/public/assets/pixel/`，并自动生成 `frontend/public/assets/pixel/LICENSE.md` 的 License 索引。`noSync` 条目只登记、不复制（直接产物已在 public）。 |
| `crop-portrait-heads.py` | 从 NPC 立绘裁切统一头像。 |
| `r12-smoke-test.sh` | R-12 冒烟脚本。 |

## R-12 动作模组：像素角色 / 建筑合成（本批新增）

这三件产物是「构建产物」：源素材已审计、合成脚本可复现，二进制直接落在
`frontend/public/assets/pixel/sprites/` 供运行时使用（`sync-assets.mjs` 里以 `noSync` 登记 License）。

| 脚本 | 作用 | 输出 | 依赖 |
| --- | --- | --- | --- |
| `build-ulpc-characters.py` | 用**官方 Universal-LPC 素材库** + sheet_definitions（z 序/路径/动画）+ 官方 expanded 布局，合成「全动作」主角 + 8 个变体 NPC（含 run/jump/idle/sit + 头部/眼睛/逐图层着色）。 | `frontend/public/assets/pixel/sprites/ulpc/*.png`（13×42） | 官方仓库克隆到 `/home/cypress/ulpc-gen`（构建期依赖，未入库，约 835M）。署名见 `assets/inbox/characters_extra/ulpc_generated/CREDITS-ulpc-used.txt`。 |
| `build-cottages.py` | 从 LPC 茅草屋 kit 的 `preview.png`（作者已正确装配）裁切+草色抠透，得到透视一致的整屋。 | `frontend/public/assets/pixel/sprites/cottages/*.png` | `assets/inbox/buildings_npc/lpc_thatched-roof-cottage/`（已入库）。 |

### 重新生成角色

```bash
# 1) 克隆官方 ULPC 素材库（构建期依赖，仅本地）
git clone --depth 1 https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator.git /home/cypress/ulpc-gen
# 2) 合成角色 + 建筑
python3 scripts/build-ulpc-characters.py
python3 scripts/build-cottages.py
# 3) 重生 License 索引
node scripts/sync-assets.mjs
```

> 运行时手持工具叠加层（浇水壶/锄头：`sprites/lpc-tools/`）与斧头超尺寸叠加层
> （`sprites/weapons/`）来自 LPC Hand Tools / ULPC 武器，源 zip 在
> `assets/inbox/characters_extra/lpc_hand_tools/`，已在 LICENSE-ART.md 登记。
> axe(砍树/挖矿)用 192px oversize attack_slash 与 slash 同步；pickaxe/fishing 因 ULPC
> 缺对应身体+衣物动画帧暂不可用。
