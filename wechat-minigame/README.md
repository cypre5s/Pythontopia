# Pythontopia · 微信原生小游戏（像素小镇渲染端）

这是 Pythontopia 的**微信原生小游戏（微信小游戏 / 小游戏 canvas runtime）**端，用来渲染像素小镇 `town_hub`。

> 注意区分：
> - 这是**小游戏（小游戏，`compileType: game`）**，跑在小游戏的裸 canvas 运行时上，**没有 DOM**，全局画布来自 `wx.createCanvas()`、系统信息来自 `wx.getSystemInfoSync()`。
> - 它**不是小程序（mini-program）**，也**不是任何游戏引擎 IDE 导出的工程**。整套渲染是 TypeScript 手写的原生 `canvas.getContext('2d')`，零运行时依赖。
> - 学习陪伴（画像 / 课件问答 / 资源等）是**另一个独立的小程序**；本工程只负责「像素小镇」这一块的渲染与走动，是那个小程序的**配套地图端**。

## 技术形态

- TypeScript（strict）编译为 CommonJS JS：`src/**/*.ts` → `js/`。
- 纯 `canvas2d` 渲染，无第三方运行时依赖。
- `wx` / canvas / image / 文件系统 / 触摸 / 网络等原生运行时全部隔离在 `src/runtime/wx.ts` 适配层，游戏逻辑层无 `any`、无裸全局访问。

## 目录结构

```text
wechat-minigame/
├── game.js                 # 小游戏入口：require('./js/main.js')
├── game.json               # { "deviceOrientation": "portrait" }
├── project.config.json     # appid=touristappid（发布前替换）, compileType=game
├── package.json            # devDeps: typescript, minigame-api-typings
├── tsconfig.json           # 编译 src/**/*.ts -> js/（CommonJS, strict）
├── assets/
│   ├── maps/town_hub.json          # Tiled 地图（50x40，16px tile），从 frontend 复制
│   └── tilesets/tilemap_packed.png # Kenney Tiny Town tileset（192x176，12 列 132 帧）
└── src/
    ├── main.ts             # 入口逻辑：new Game().start()
    ├── api.ts              # 可选后端 bootstrap（受保护、非阻塞）
    ├── runtime/wx.ts       # 原生运行时适配层（唯一与 wx 全局交互的边界）
    ├── types/tiled.ts      # Tiled JSON DTO 类型
    └── core/
        ├── assets.ts       # 读取地图 JSON + 加载 tileset 图片
        ├── TileMap.ts      # 解析 Tiled → 渲染层 / 碰撞网格 / 可交互对象
        ├── Renderer.ts     # 瓦片层 + NPC + 玩家 + HUD 渲染
        ├── Camera.ts       # 跟随玩家 + 边界夹紧
        ├── Player.ts       # 按格移动 + 缓动插值
        ├── Input.ts        # 方向键 + 滑动摇杆
        └── layout.ts       # HUD 几何（方向键命中区）
```

## 构建

需要 Node.js（建议 18+）。

```bash
cd wechat-minigame
npm install          # 安装 typescript + minigame-api-typings
npm run build        # tsc 编译 src -> js
# 或开发期：
npm run watch        # 监听编译
npm run typecheck    # 仅类型检查（tsc --noEmit）
```

> 编译产物在 `js/`，默认被 `.gitignore` 忽略。**首次在微信开发者工具打开前，请先 `npm run build` 生成 `js/`**（`game.js` 会 `require('./js/main.js')`）。

## 在微信开发者工具中打开（小游戏）

1. 打开**微信开发者工具**，选择「**小游戏**」类型新建/导入项目（不要选小程序）。
2. 项目目录选择本文件夹 `wechat-minigame/`。
3. AppID：
   - 调试可直接点「**使用测试号**」；
   - 或在 `project.config.json` 里把 `"appid": "touristappid"` **替换成你自己的小游戏 AppID** 后再导入。
4. 工具会按 `project.config.json` 的 `"compileType": "game"` 以小游戏方式编译运行。

### 调用后端时（可选）

本工程可选地请求本地后端 `http://localhost:18080/api/game/bootstrap`。开发者工具默认会校验合法域名，本地调试需关闭：

- **微信开发者工具 → 右上角「详情」→「本地设置」→ 勾选「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」**。

（`project.config.json` 里已设 `setting.urlCheck=false`，但以工具内「本地设置」开关为准。）

## 玩法 / 控制

- **移动**：左下角十字**方向键**，或在屏幕任意处**滑动**（虚拟摇杆，按住持续移动）。玩家按瓦片网格移动，格间做缓动插值。
- **交互提示**：走到建筑 / NPC / 地标**相邻格**时，屏幕上方出现信息横幅（建筑显示中文名 + route，NPC 显示名字 + key）。NPC 头顶有「!」提示气泡。

## 渲染要点

- **瓦片地图**：读取 Tiled JSON 的 `ground` / `buildings` / `decoration` 三个 tilelayer，按 `gid → 帧号`（`gid - firstgid`，并剥离 flip 高位）映射到 tileset 的 12 列网格，按镜头视口裁剪后逐格 `drawImage`。
- **玩家**：方块小人（阴影 + 身体 + 头 + 朝向眼睛），按格移动并平滑插值，走动时上下轻微 bob。
- **镜头**：跟随玩家中心，按世界边界夹紧；世界比视口小则居中。
- **交互**：解析 object 层的 `building` / `landmark` / `low_obstacle` / `npc`，构建碰撞网格与可交互列表；每帧按「与玩家八邻接且玩家不在其footprint内」选出最近对象显示横幅。
- **像素清晰**：`imageSmoothingEnabled = false`，并按 `devicePixelRatio` 提升画布分辨率。
- **出生点**：地图缺省出生点 `tile(25,20)`；若被占用则就近螺旋搜索可通行格。后端 bootstrap 成功且落点可通行时覆盖为后端位置（失败则保持地图出生点，全程非阻塞）。

## 后端联动（可选、非阻塞）

`src/api.ts` 用 `wx.request` GET `http://localhost:18080/api/game/bootstrap`，若 `wx.getStorageSync('pyt_token')` 存在则带 `Authorization: Bearer <token>`。返回 `player.tileX/tileY` 时把玩家放到该格；任何失败（无网络、超时、非 2xx）都安静回退到地图出生点，**不阻塞**游戏启动与运行。

## 资源授权

`assets/tilesets/tilemap_packed.png` 为 Kenney「Tiny Town」像素素材（CC0），随包附带 `assets/tilesets/License.txt`。地图 `town_hub.json` 由 Pythontopia 前端程序化生成并复制而来。
