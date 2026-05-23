---
title: Pythontopia 天气粒子与滤镜规范（星露谷风但不是星露谷）
version: v1.0
status: Draft
owner: 视觉设计师 + Phaser 程序员
last_updated: 2026-05-24
audience: 前端工程师 / Phaser 场景作者 / 美术 / 教学策划 / 画像分析师 / QA
scope: 全端 Web 游戏画布 4 种天气（SUNNY / CLOUDY / RAINY / SNOWY）的粒子层、调色滤镜、切换动画、性能预算、教育耦合
依赖文档:
  - /home/cypress/Pythontopia/docs/stardew/00-visual-system-overview.md
  - /home/cypress/Pythontopia/docs/stardew/05-tilemap-decoration-spec.md
  - /home/cypress/Pythontopia/docs/stardew/06-bgm-cuelist.md
  - /home/cypress/Pythontopia/docs/gameplay/world-clock-calendar.md
  - /home/cypress/Pythontopia/docs/gameplay/r12-deepen/03-energy-fatigue-curve.md
  - /home/cypress/Pythontopia/frontend/src/game/core/WorldClock.ts
  - /home/cypress/Pythontopia/frontend/src/game/adapters/weather.ts
  - /home/cypress/Pythontopia/LICENSE-ART.md
---

# 0. 文档定位

天气粒子在 Pythontopia 中**不是装饰**，而是**教育与画像信号的视觉载体**：

> 玩家看到屏幕在下雨，就该立刻明白"今天浇水任务做不了 / 在家读书更划算 / 能量回复打折"——天气视觉是教学行为的**第一时间提示**。

`WorldClock.weather` 是后端 `WorldService` 推送、`WorldClock` listener 派发、Phaser 场景渲染的三段链路（见 `frontend/src/game/core/WorldClock.ts` §41-45 `weather-changed` 事件）。本文专门规定**渲染段**：4 种天气如何用粒子 + 滤镜 + 切换动画呈现，使其满足以下硬约束（违反一条即拒绝合并）：

1. **教育语义优先**：每种天气必须对应至少一个**教学钩子 + 一个画像维度信号**（详 §10）。
2. **性能可控**：单天气粒子上限 200，移动端自动 ×0.5，FPS < 50 自动降级 -20%（详 §5）。
3. **资产 license 干净**：仅 CC0 / CC-BY 3.0/4.0 / OFL-1.1，**严禁 CC-BY-SA 粒子贴图**（与 §06 BGM 同立场，传染对教学版分发不友好）。
4. **可访问性兜底**：`prefers-reduced-motion` 用户可一键禁用粒子；雪花闪烁频率 ≤ 3 Hz（WCAG 2.3.1 不闪烁）。
5. **矿洞内层无天气**：`mine_south` 的非地表层（B1F / B2F / ...）**不应用任何天气**，由地下生态自管（详 §7.2）。

视觉调色与字体规范沿用 `00-visual-system-overview.md` §1 12 色基本盘与 §6 天气滤镜总表；本文是其中第 6 节的**完整落地规范**。

---

# 1. 4 种天气视觉规范总表

`WorldClock.Weather` 枚举（`frontend/src/game/core/WorldClock.ts:25`）为 `'SUNNY' | 'CLOUDY' | 'RAINY' | 'SNOWY'` 共 4 种；现有 `weather.ts` adapter 额外支持 `stormy`（暴雨）作为 `rainy` 的强化变体（`fx_rain_drop` 共用贴图，`frequency` 12 vs 28），本文按 4 种主天气规范，`stormy` 在 §4.4 单独说明。

| 天气 | 粒子层 | 调色滤镜 | 屏幕级附加 | 教学语义 | 画像信号 |
| --- | --- | --- | --- | --- | --- |
| **SUNNY** | **无粒子** | 阳光柔和黄 `#FFE9A8` 8% `screen` 叠加 | 无 | "最适合出门 / 浇水 / 高密度学习"的基准日 | `learning_preference.outdoor_active +0.02` |
| **CLOUDY** | 低密度漂浮云 **10 个**（持续平移 sprite，非粒子发射器） | 饱和度 **-10%**、亮度 **-3%** | 无 | "适合室内阅读 / 与 NPC 聊天"的过渡日 | `cognitive_style.observation +0.01` |
| **RAINY** | 雨滴 **80/s**（白色 1×2px 斜下 30°） | 蓝色 `#5BC0DE` 8% `multiply` + 亮度 **-5%** | 地面溅水 **12/s**、对话气泡内 SFX 提示"雨声" | "学习 / 读书 / 写代码"的深度专注日；不能浇水 | `learning_preference.context_aware +0.03`（详 §10.3） |
| **SNOWY** | 雪花 **40/s**（白色 2×2px 缓慢下落 + 微飘移） | 亮度 **+5%**、饱和度 **-15%** | 地面积雪叠加（地块顶 2px 白条带，与季节 LUT 协同） | "节奏放慢、慢思考"的反思日；移动衰减 ×0.9 | `emotional_persistence.calm +0.02` |

> **调色实现**：所有滤镜优先用 **Phaser RenderTexture + ColorMatrix** 或 `cameras.main.setTint()`（`weather.ts:128`），**严禁** CSS `filter: brightness/saturate` 全屏触发桌面端 GPU 合成层抖动（已在 R-11 性能巡检中被列为 P1 反面案例）。

> **与 `weather.ts` 现状的差异**：现有 adapter 的 `WEATHER_TINT` 表（`weather.ts:19-24`）使用 `rainy: 0x90b0d0 / snowy: 0xd0e0f0 / cloudy: 0xc0c8d0`，本文在 §3 给出**校准后**的 16 进制 + alpha 双值版本，W5 落地时一并替换。

---

# 2. 粒子参数总表（Phaser 3.87 ParticleEmitter）

每个粒子发射器的 **8 个核心参数**（位置 / 寿命 / 速度 / 角度 / 缩放 / 透明度 / 混合模式 / 颜色）必须严格按本表配置，未在表中列明的参数禁止自由发挥。

| 参数 | RAINY 主雨 | RAINY 溅水 | SNOWY 雪花 | CLOUDY 云（sprite+tween） | SUNNY 阳光 overlay |
| --- | --- | --- | --- | --- | --- |
| 贴图 key | `fx_rain_drop` | `fx_rain_splash` | `fx_snowflake` | `fx_cloud_drift` | — |
| `x` | `{ 0, mapWidth }` | `{ 0, mapWidth }` | `{ 0, mapWidth }` | `random*mapWidth` 初始 | 全屏 rect |
| `y` | `-10` | `{ 0, mapHeight }` | `-10` | `40 + i*(H-80)/10` | 全屏 rect |
| `lifespan`（ms） | 1200（移动 1000） | 300 | 4000（移动 3500） | 持续 sprite，无 lifespan | 持续 overlay |
| `speedY`（px/s） | `{ 400, 600 }` | 0 | `{ 60, 100 }` | 0 | — |
| `speedX`（px/s） | `{ -20, 0 }`（斜下 30°） | 0 | `{ -30, 30 }` | tween 横向 60–110 s 循环 | — |
| `scale` | `0.5 → 0.3` | `0.2 → 0.6` | `0.6 → 0.6`（不缩） | `{ 0.8, 1.4 }` 随机 | — |
| `alpha` | `0.8 → 0.4` | `0.6 → 0` | `0.9 → 0.7` | `0.5` 固定 | `0.08` |
| `frequency`（ms） | 28 + `quantity:3`（≈ 80/s） | 83（≈ 12/s） | 50 + `quantity:2`（≈ 40/s） | — | — |
| `rotate` | — | — | `{ 0, 360 }` | — | — |
| `blendMode` | NORMAL | ADD | NORMAL | NORMAL | SCREEN |
| `tint` | `0xE0F0FF` | `0xC0E0FF` | `0xFFFFFF` | `0xFFFFFF` | `0xFFE9A8` |
| `depth` | 80 | 5 | 80 | 78 | 90 |
| 桌面目标 | 80/s | 12/s | 40/s | **10 个 sprite** | 1 全屏矩形 |
| 移动目标 | 40/s | 6/s | 20/s | **6 个 sprite** | 1 全屏矩形 |

> **关键约束**：(1) RAINY 的 `quantity:3` 复合发射避免 `frequency < 10` 抖动；(2) 雪花全程 `scale` 不变是固体不应缩放；(3) `WEATHER_DEPTH=80 / CLOUD_DEPTH=78` 与 `weather.ts:26-28` 现有常量对齐。

> **CLOUDY 为何用 sprite + tween 而不是粒子**：云是低频大尺寸对象（10 个 sprite vs 80 个雨粒子），ParticleEmitter 对象池反而是负担；tween 单向平移 60+ s，CPU < 1%。

> **SUNNY 为何不加蝴蝶 / 蒲公英**：这些是季节装饰范畴（详 `05-tilemap-decoration-spec.md` §3），不应混入天气层；晴 = 干净 / 雨 = 满屏粒子，对比鲜明才能给玩家"今日不能浇水"的瞬时教学提示。

---

# 3. Phaser 配置代码示意（仅声明，本文不实现）

```ts
import type Phaser from 'phaser';
import type { Weather } from '@/game/core/WorldClock';

interface WeatherRenderTier {
  rainQuantity: number;
  snowQuantity: number;
  splashFrequency: number;
}

const TIER_DESKTOP: WeatherRenderTier = { rainQuantity: 3, snowQuantity: 2, splashFrequency: 83 };
const TIER_MOBILE: WeatherRenderTier  = { rainQuantity: 1, snowQuantity: 1, splashFrequency: 166 };
const TIER_DEGRADED: WeatherRenderTier = { rainQuantity: 2, snowQuantity: 1, splashFrequency: 110 };

export function configureRainEmitter(
  scene: Phaser.Scene,
  mapWidth: number,
  tier: WeatherRenderTier,
): Phaser.GameObjects.Particles.ParticleEmitter {
  return scene.add.particles(0, 0, 'fx_rain_drop', {
    x: { min: 0, max: Math.max(mapWidth, 16) },
    y: -10,
    lifespan: 1200,
    speedY: { min: 400, max: 600 },
    speedX: { min: -20, max: 0 },
    scale: { start: 0.5, end: 0.3 },
    alpha: { start: 0.8, end: 0.4 },
    frequency: 28,
    quantity: tier.rainQuantity,
    blendMode: Phaser.BlendModes.NORMAL,
    tint: 0xE0F0FF,
  }).setDepth(80);
}

export function configureSnowEmitter(
  scene: Phaser.Scene,
  mapWidth: number,
  tier: WeatherRenderTier,
): Phaser.GameObjects.Particles.ParticleEmitter {
  return scene.add.particles(0, 0, 'fx_snowflake', {
    x: { min: 0, max: Math.max(mapWidth, 16) },
    y: -10,
    lifespan: 4000,
    speedY: { min: 60, max: 100 },
    speedX: { min: -30, max: 30 },
    scale: { start: 0.6, end: 0.6 },
    alpha: { start: 0.9, end: 0.7 },
    rotate: { min: 0, max: 360 },
    frequency: 50,
    quantity: tier.snowQuantity,
    blendMode: Phaser.BlendModes.NORMAL,
    tint: 0xFFFFFF,
  }).setDepth(80);
}
```

> 实际接入位置：`frontend/src/game/adapters/weather.ts` 替换现有 `applyWeather` 函数体；本文仅给出**参数集**，落地代码由 R-12 W5 子任务执行。

---

# 4. 粒子资源 PNG

## 4.1 目录与文件

资源路径：**`frontend/public/assets/pixel/weather/`**（与 §00 §11.1 已规划目录一致）

> **现状**：现有粒子贴图已存放于 `frontend/public/assets/pixel/effects/weather/`（`rain_drop.png` / `snowflake.png` / `cloud.png`），BootScene 也已加载（`BootScene.ts:128-134`）。本文规定 R-12 W5 起**逐步迁移**到 `pixel/weather/`，保留软链接避免破坏现有引用。

| 文件名 | 源像素尺寸 | 用途 | License | 来源 |
| --- | --- | --- | --- | --- |
| `particle_rain.png` | **4×4 px** | RAINY 雨滴主粒子 | CC0 | Kenney Particle Pack（去除 IP 元素） |
| `particle_rain_splash.png` | **4×4 px** | RAINY 地面溅水 | CC0 | 自制（白色圆环 1px） |
| `particle_snow.png` | **8×8 px** | SNOWY 雪花主粒子 | CC0 | Kenney Particle Pack |
| `particle_cloud.png` | **32×16 px** | CLOUDY 云朵 sprite | CC-BY 3.0 | OpenGameArt Cloud Pack（作者署名） |

> **为什么是 4×4 / 8×8**：(1) 像素游戏粒子贴图越小越锐利，> 8×8 会让玩家看到"模糊圆斑"破坏像素质感；(2) GPU 上传纹理时小尺寸纹理带宽友好，10000 粒子同屏才 < 5 MB 显存。

## 4.2 命名约定

| 类型 | 模式 | 例子 |
| --- | --- | --- |
| 天气粒子 | `particle_<weather>[_<variant>].png` | `particle_rain.png` / `particle_rain_splash.png` |
| 天气滤镜 LUT（如未来需要） | `lut_weather_<weather>.png` | `lut_weather_rainy.png` |

强制：snake_case、英文、PNG 透明、不允许 WebP / SVG。

## 4.3 License 同步

新增任意粒子资源必须同步更新：
1. `/home/cypress/Pythontopia/LICENSE-ART.md` 顶层总表 §「天气粒子盘点」
2. `/home/cypress/Pythontopia/frontend/public/assets/pixel/LICENSE.md` 运行时清单
3. 若是 CC-BY 类（如云朵），必须在 `frontend/public/credits/` 加入作者署名

## 4.4 stormy 复用规则

`weather.ts:13` 已声明 `WeatherKind = 'sunny' | 'rainy' | 'snowy' | 'cloudy' | 'stormy'`，`stormy` 是 `rainy` 的强化变体：
- 复用 `particle_rain.png`，**不另出贴图**
- `frequency` 从 `28` 收紧到 `12`（约 250/s 雨滴，**例外突破 200 上限**仅限 boss 战节日场景，单场不超过 30 s）
- 滤镜从 `#5BC0DE 8%` 加深到 `#6080A0 15% multiply`
- 额外叠加 `fx_lightning_flash` 全屏闪烁（R-13 闪电特效，本文不展开）

---

# 5. 性能预算与降级策略

## 5.1 硬上限

| 项 | 桌面 | 移动 | 备注 |
| --- | ---: | ---: | --- |
| 单天气最大粒子并发 | **200** | **100** | 移动端 ×0.5（基线） |
| RAINY 目标粒子 / 秒 | 80 | 40 | `frequency=28 ms × quantity=3` |
| RAINY 溅水 / 秒 | 12 | 6 | 第二发射器 |
| SNOWY 目标粒子 / 秒 | 40 | 20 | `frequency=50 ms × quantity=2` |
| CLOUDY 云 sprite 数 | 10 | 6 | 移动端少 4 个 |
| FPS 监控阈值 | **< 50** 持续 3 s | **< 35** 持续 3 s | 触发降级 |
| 降级幅度 | -20% `quantity` | 同 | 一次性减档，不抖动 |
| 单次切换 GC 开销 | ≤ 50 ms | ≤ 80 ms | Chrome DevTools 测量 |

## 5.2 设备判定

```ts
const isMobile = /Android|iPhone|iPad/i.test(navigator.userAgent)
  || window.matchMedia('(max-width: 767px)').matches;
const tier = isMobile ? TIER_MOBILE : TIER_DESKTOP;
```

> 不用更复杂的 GPU 性能探测（如 WebGL renderer 字符串黑名单），简单 UA + viewport 双判定已足够分辨"PC 教室投影 vs 学生手机"；过度优化反而引入维护负担。

## 5.3 运行时 FPS 降级

```ts
// 伪代码，落地见 frontend/src/game/core/PerformanceMonitor.ts（待 W5 创建）
let lowFpsSince = 0;
scene.events.on(Phaser.Scenes.Events.POST_UPDATE, (time, delta) => {
  const fps = scene.game.loop.actualFps;
  const fpsThreshold = isMobile ? 35 : 50;
  if (fps < fpsThreshold) {
    lowFpsSince += delta;
    if (lowFpsSince > 3000 && currentTier !== TIER_DEGRADED) {
      currentTier = TIER_DEGRADED;
      reconfigureWeather(currentTier);
      console.warn('[weather] FPS degraded, switching to TIER_DEGRADED');
    }
  } else {
    lowFpsSince = 0;
  }
});
```

> 降级**只触发一次**，不来回切换；FPS 恢复后保持降级档直到 `weather-changed` 事件重置。这是为了避免"FPS 50.1 → 49.9 反复抖动"造成视觉颤抖。

## 5.4 内存占用预算

| 项 | 桌面预算 | 移动预算 |
| --- | ---: | ---: |
| 粒子贴图 GPU 显存 | < 1 MB | < 0.5 MB |
| ParticleEmitter 对象池 | < 200 × 80 B = 16 KB | < 100 × 80 B = 8 KB |
| Cloud sprite 实例 | 10 × 8 KB = 80 KB | 6 × 8 KB = 48 KB |
| 总天气层峰值显存 | **< 2 MB** | **< 1 MB** |

---

# 6. 天气切换动画

## 6.1 切换矩阵

| 触发 | 旧天气处理 | 新天气处理 | 过渡时长 |
| --- | --- | --- | --- |
| `weather-changed` 任意切换 | 粒子 emitter 停发射 + fade-out 1.0 s | fade-in 0.8 s 后开始发射 | **1.0 s 串行** |
| 滤镜 overlay 切换 | Camera fade to neutral 1.0 s | Camera fade to new tint 1.0 s | **总 2.0 s**（与粒子并行） |
| 同天气重复触发 | 忽略（不重置粒子） | — | 0 |
| 进入矿洞内层（§7.2） | 立即销毁所有粒子 + 清 tint | 不重启 | **即时**（地图切换 fade 内完成） |
| 出矿洞回地表 | — | 按当前 `WorldClock.weather` 重新 `applyWeather` | 跟随地图切换 fade |

## 6.2 实现细节

```ts
function transitionWeather(scene: Phaser.Scene, fromHandle: WeatherHandle | null, toKind: Weather): WeatherHandle {
  if (fromHandle) {
    fadeOutParticles(fromHandle, 1000);
    setTimeout(() => fromHandle.destroy(), 1000);
  }
  scene.cameras.main.fadeIn(800, 0, 0, 0, undefined, undefined);
  return applyWeather(scene, toKind.toLowerCase() as WeatherKind, mapWidth, mapHeight);
}
```

> 关键约束：**旧粒子必须 fade-out 完才能 destroy**，否则雨滴突然消失会让玩家"出戏"。Camera fade 用 Phaser 内置 `cameras.main.fade(1000, r, g, b)`，不要 CSS 全屏黑遮罩（与 §06 BGM crossfade 同理，性能优先 GPU 方案）。

## 6.3 与 BGM 切换协同

`docs/stardew/06-bgm-cuelist.md` §4.2 已规定**天气变化触发 BGM 音量 -20% × 8 s + 叠加 SFX**（不切歌），与本文 6.1 的视觉 2.0 s 过渡**并行**：
- 0.0 s：玩家走到雨开始的边界
- 0.0–1.0 s：旧粒子淡出 + 旧 tint 淡出 + BGM 音量开始 ramp down
- 1.0–2.0 s：新粒子淡入 + 新 tint 淡入 + `sfx_rain_loop` 淡入
- 2.0 s 后：稳态运行 + BGM 已降到 -20%（继续到 8 s 完成）

视觉先稳定，听觉 ramp 长一点更自然（玩家眼睛对 1 s 内变化敏感，耳朵对 8 s 内变化敏感）。

---

# 7. 5 地图差异

## 7.1 地表 4 地图差异

`town_hub` / `farm_north` / `campus_east` 三张地图**完全按本文 §1-3 规范**应用 4 种天气；`port_west` 在 RAINY/SNOWY 下需要额外的**港口浪花特效**：

| 地图 | 天气 | 额外特效 | 实现 |
| --- | --- | --- | --- |
| `port_west` | RAINY | **海面浪花** 8/s（白色 4×4 + ADD 模式） | 第三 emitter，发射在 `y > mapHeight * 0.7` 的海面区域 |
| `port_west` | SNOWY | 浪花减弱到 4/s + tint `0xC0E0FF` 冷色 | 同 emitter，参数微调 |
| `port_west` | CLOUDY | 云朵数量 +4（共 14 个），海上云感更强 | sprite 数 override |
| `port_west` | SUNNY | 海面反光：水面区域 `0xFFE9A8` 12% screen 叠加 | 矩形 overlay，仅覆盖 `y > 0.7H` |

> 港口的天气差异是**地理合理性**驱动，不是装饰冗余：海面会有浪花、雾、反光，这是玩家"哦原来不同地图同一天气表现不同"的常识投射。

## 7.2 矿洞内层无天气

`mine_south` 走到地面层（`floor: 0`）应用 §1 规范；走到 **B1F / B2F / ... 地下层**（`floor > 0`）：

| 项 | 行为 |
| --- | --- |
| 粒子 | **全部销毁**，不发射任何天气粒子 |
| Camera tint | **清除**，恢复中性 `0xFFFFFF` |
| BGM | 切到 `bgm_mine_south_<season>`（详 §06 §2），不受 weather 影响 |
| 玩家影响 | **取消 RAINY 能量惩罚 / SNOWY 移动衰减**（地下不淋雨） |
| 教学耦合 | 矿洞内层有独立的 Bug 矿洞照明系统（红黄警告闪烁），见 `05-tilemap-decoration-spec.md` §3 mine 行 |

> **判定时机**：`SceneSwitcher` 切换 floor 时（`frontend/src/game/core/SceneSwitcher.ts`）触发 `floor-changed` 事件，`WorldScene` 监听后调 `currentWeatherHandle?.destroy()` 即可。出矿洞 `floor=0` 时再 `applyWeather(currentWeather)`。

---

# 8. NPC 行为耦合

天气直接影响 20 个 NPC 的 schedule 与 dialog（NPC 元数据见 `data/npcs/<key>/meta.json`）。**3 个标杆耦合**：

## 8.1 三个标杆 NPC 覆盖表

| NPC | 默认 schedule | 雨天覆盖 | 雪天覆盖 | 晴天加成 |
| --- | --- | --- | --- | --- |
| **farmer_kc**（田老 / `farm_north`） | 06:00 田头巡视 | **强制宅家** `farm_north (16, 6)` 屋内；走近触发"雨天田老在家"对话：「下雨了，今天的庄稼自己喝水。你也别在外面跑，找本书读」→ `learning_preference.context_aware +0.03` | 田头巡视但速度 ×0.6 + 头顶 "❄" emote | 默认 |
| **chef**（厨师 / `town_hub`） | 07:00 小餐馆 | 默认 5 道 KC 主题菜（+5% × 10 min KC buff） | **新增"暖冬热汤"** -10 Pythoco → 全 KC **+15% × 20 min** + `emotional_persistence.calm +0.02` | 默认 |
| **gardener**（园丁 / `town_hub`） | 06:00 花园 | **回小屋** `town_hub (6, 8)`；走近触发"雨天补种"对话，可买种子 | 花园扫雪 4 帧动画 | **晴天浇花**（4 帧水壶 + 花朵摇摆）；走近触发"今日浇水任务"，协助浇 3 朵花 → 5 Pythoco + `seeds` ×2 |

> 雪天热汤与晴天浇花是**显式教学奖励**：把天气与 NPC 互动绑定，鼓励学生在不利天气（雨）找 NPC 而非硬肝任务，与 §9 能量惩罚形成"惩罚 + 替代奖励"的闭环。

## 8.2 其余 17 NPC 微调

- 户外 NPC（postman / class_president / merchant）：雨天速度 ×0.8 + 头顶 "☔" 持续 2 s
- 室内 NPC（librarian_doc / sage_review / nurse / mayor_py）：不受天气影响
- 学生 NPC（6 个 student_*）：雨天 7-9 个集中到 `librarian_doc` 图书馆，形成"学生雨天扎堆读书"的小镇感

> 实现位置：`frontend/src/game/core/NpcFramework.ts` 的 `resolveScheduleSlot(npcKey, snapshot)` 方法，调用前检查 `weatherOverride[npcKey]?.[weather]` 命中即返回覆盖坐标 / 行为。

---

# 9. 玩家影响

天气直接驱动 4 个玩家系统（能量 / 移动 / 工具 / 学习速度）的乘数表，统一在 `frontend/src/game/core/energyTuning.config.ts`（W5 新建）配置：

| 项 | SUNNY | CLOUDY | RAINY | SNOWY | 实现位置 |
| --- | --- | --- | --- | --- | --- |
| 能量自动回复速率 | **+3%** | 100%（基准） | **-10%** | **+5%**（冷天精神） | `energyTuning.config.ts` 读取 weather 乘 multiplier |
| 移动速度 | 100% | 100% | 100% | **×0.9**（雪地阻力） | `PlayerStateMachine.ts` 当 weather=SNOWY 时 velocityScale |
| 跑步能量消耗 | -5/30 s（基准） | -5/30 s | **-6/30 s**（+20%） | -5/30 s | `energyTuning` 雨天乘 1.2 |
| 浇水动作（`watering`） | 允许（最佳） | 允许 | **禁用**（toast「下雨天不需要浇水」） | 允许（雪水融化） | `InteractableSystem.ts` 工具调用前 |
| 户外作物 KC 实践 | 允许 | 允许 | **改室内副本**（同 KC、不同场景） | 允许 | `quest-engine` resolveScene |
| KC 学习速度 | **+2%** | 基准 | 基准（雨天靠教育耦合奖励，见 §10） | 难题 +50% XP（见 §10.2） | mastery 计算钩子 |
| BGM 影响 | 100% 音量 | 90% 音量 | 80% + `sfx_rain_loop` 叠加 | 90% + `sfx_wind_loop` 叠加 | 详 §11 与 §06 §4.2 |

> SUNNY 是"最舒适日"，给轻微 buff；RAINY 浇水禁用是最强的玩法约束（玩家会立刻感知"今天没法做农场任务"），SNOWY 移动衰减是次强约束（雪地走路慢半拍）。CLOUDY 完全中性，作为过渡缓冲。

---

# 10. 教育耦合

## 10.1 雨天 / 雪天教学奖励对照

雨天叙事：**外面不能干活，进屋读书最划算**；雪天叙事：**节奏放慢，做错题复盘最划算**。

| 行为 | 雨天加成 | 雪天加成 | 非天气日基准 |
| --- | --- | --- | --- |
| `librarian_doc` 图书馆读完 1 篇课件 | +20 XP + `learning_preference.context_aware +0.03` | +20 XP | +20 XP |
| 完成 1 道 KC 练习题 | +1 mastery + `cognitive_style.focused_reading +0.02` | +1 mastery | +1 mastery |
| 与 NPC 对话 ≥ 30 s | +5 friendship + `social_engagement.passive +0.01` | +5 friendship | +5 friendship |
| `sage_review` 错题复盘 | +20 XP | **+30 XP** + `emotional_persistence.calm +0.02` + `metacognition.review_habit +0.03` | +20 XP |
| 完成 1 道难题（mastery_level ≥ 3） | 标准 XP | **额外 +50% XP**（慢思考奖励） | 标准 XP |
| 在 chef 处购买热汤 | — | 全 KC +15% × 20 min + calm +0.02 | — |
| 雨中跑步出门 | 能量 -6/30 s + **不奖励** | — | -5/30 s |

## 10.2 画像维度信号写入

每次天气相关行为触发，必须写一条 `world_event`：

```ts
{
  event_type: 'WEATHER_BEHAVIOR',
  payload: {
    weather: 'RAINY',
    behavior: 'read_book_indoor',
    profileDelta: { 'learning_preference.context_aware': 0.03 },
    evidence: { resource_id: 'doc_kc_040_list_intro' }
  }
}
```

后端 `PassiveProfileService` 监听后聚合到 `learner_profile.dimensions` 对应 key（详 `backend/src/main/java/com/pythontopia/learner/profile/service/PassiveProfileService.java`）。

## 10.3 教师视角观测

教师 dashboard `/classroom/{id}/weather-insight`（R-13 实现）查看全班「雨天读书时长 / 雪天复盘次数 / 晴天户外活动比例」3 个指标，识别"天气适应型学生"vs"天气无感学生"。这是 R-13 班长视角拓展的前置能力。

---

# 11. BGM 联动

详见 `docs/stardew/06-bgm-cuelist.md` §4.2 与 §10.1：天气变化**不切歌**，只通过音量 -20% × 8 s + SFX 叠加 + 滤镜调节，与本文 §6.3 的视觉过渡协同。本节仅列对照表，避免文档分裂：

| 天气 | BGM 主曲行为 | 叠加 SFX | EQ 调节 |
| --- | --- | --- | --- |
| SUNNY | 100% 音量 | 无 | 时段 EQ profile（详 §06 §4.3） |
| CLOUDY | 90% 音量（轻微压低） | 无 | 时段 EQ profile |
| RAINY | 80% 音量 + 8 s ramp down | `sfx_rain_loop.ogg` (0.4 音量) | lowpass cutoff 略低 1 kHz |
| SNOWY | 90% 音量 | `sfx_wind_loop.ogg` (0.3 音量) | lowpass cutoff 不变，highpass 略高 20 Hz（冷感） |

---

# 12. 可访问性

| 项 | 要求 | 验证方式 |
| --- | --- | --- |
| **粒子可关** | settings `audio/video → 减少动画效果` 一键禁用所有天气粒子（保留滤镜） | 单元测试 |
| **`prefers-reduced-motion`** | 用户启用系统级 reduced-motion → 自动禁用所有粒子 + 切换 fade 时长 ×0.5 | CSS 媒体查询 + JS 检测 |
| **雪花不闪烁 > 3 Hz** | WCAG 2.3.1 不闪烁要求 ≤ 3 Hz；雪花本身不闪烁（持续旋转 ≠ 闪烁），地面反光叠加禁止 > 3 Hz 周期变化 | 主观验收 + 频率分析 |
| **不依赖颜色判定天气** | RAINY 必须有粒子（不只是蓝 tint）；色盲玩家通过粒子认出雨 | 灰度截图自检 |
| **HUD 天气图标** | 屏幕右上角必须有 24×24 天气图标（☀ / ☁ / ☂ / ❄），与画面粒子双重指示 | 截图自检 |
| **教师强制简化** | 教师可在班级后台「视觉减负模式」一键关全班粒子（教学投影性能保护） | R-13 实现 |
| **toast 替代** | 关粒子后，雨/雪天进入时弹 toast「今日 天气：雨」3 s | 单元测试 |

> 核心原则与 §06 §10 一致：**玩家关闭所有粒子后，游戏可玩性、教学完整性、信息可达性必须不打折**。粒子是"锦上添花"，不是"不可或缺"。

---

# 13. 验收与生效

## 13.1 单元测试（vitest）

新增文件：`frontend/tests/game/adapters/weatherParticles.spec.ts`

| 用例 | 操作 | 预期 |
| --- | --- | --- |
| WP-01 | `applyWeather(scene, 'sunny')` | 不创建 ParticleEmitter，clearTint 调用，可选 screen overlay |
| WP-02 | `applyWeather(scene, 'cloudy')` | 10 个 cloud sprite（桌面）/ 6 个（移动），tween 注册 |
| WP-03 | `applyWeather(scene, 'rainy')` | 2 个 emitter（主 + 溅水），key 为 `fx_rain_drop` / `fx_rain_splash` |
| WP-04 | `applyWeather(scene, 'snowy')` | 1 个 emitter，key 为 `fx_snowflake`，含 `rotate:{0,360}` |
| WP-05 | `WeatherHandle.destroy()` 调 2 次 | idempotent 不抛错 |
| WP-06 | 切换 RAINY → SNOWY | 旧 emitter `destroy` 调用，新 emitter 创建，过渡 1 s |
| WP-07 | 移动端 tier | `frequency` 与 `quantity` 按 `TIER_MOBILE` 配置 |
| WP-08 | FPS 降级触发 | `currentTier` 切到 `TIER_DEGRADED`，emitter 参数重配 |
| WP-09 | `prefers-reduced-motion` 启用 | 不创建任何 emitter，只创建滤镜 + toast |
| WP-10 | 矿洞内层（floor > 0） | 调用被忽略，emitter 不创建 |
| WP-11 | port_west RAINY | 3 个 emitter（主 + 溅水 + 海浪） |

## 13.2 端到端截图对比（Playwright）

新增 spec：`frontend/tests-e2e/weather-particles.spec.ts`

| 用例 | 操作 | 验证 |
| --- | --- | --- |
| E2E-WP-01..04 | 4 种天气逐一应用 + 等待 2 s | 截图对比 `baseline/<weather>.png`，差异 < 2% |
| E2E-WP-05 | RAINY → 走到 farmer_kc 田头 | NPC 在屋内，触发"雨天田老在家"对话 |
| E2E-WP-06 | RAINY → 尝试浇水 | toast「下雨天不需要浇水」可见，动作未执行 |
| E2E-WP-07 | 全程 reduced-motion 模式 | 30 s 内 4 次切换，无粒子创建（canvas 像素对比无飘落） |
| E2E-WP-08 | port_west RAINY | 海面浪花粒子可见（y > 0.7H 区域有白色亮点） |

## 13.3 性能与 License 验收

| 指标 / 检查项 | 阈值 / 验收 | 方法 |
| --- | --- | --- |
| 桌面 RAINY 满载 FPS | ≥ 55 | DevTools Performance 60 s |
| 移动 RAINY 满载 FPS | ≥ 40 | DevTools Mobile Emulation |
| RAINY → SNOWY 切换 GC | ≤ 50 ms | DevTools Memory timeline |
| 粒子贴图总显存 | ≤ 2 MB | DevTools Memory |
| FPS < 50 自动降级 | ≤ 3 s 触发 | 模拟低性能场景 |
| 4 粒子贴图在 `LICENSE-ART.md` 登记 | 作者 / 来源 / 协议齐备 | `rg` 自检 |
| `pixel/LICENSE.md` 与顶层一致 | diff 无差异 | diff 自检 |
| 云朵 CC-BY 3.0 作者署名 | about 页面可见 | 浏览器访问 |
| 无 CC-BY-SA 粒子贴图 | `rg -i 'CC-BY-SA' .../weather/LICENSE.md` 返回空 | grep 自检 |

完成 §13.1–13.3 全部用例后，本文档由 Draft 升级为 Active（v1.0 → v1.0-released）。

---

# 14. 附录：与现有 `weather.ts` 的迁移差异

| 项 | 现状（R-11 `weather.ts`） | 本文规范（R-12 W5） | 迁移类型 |
| --- | --- | --- | --- |
| 雨粒子 `frequency` | `28`（≈ 36/s） | `28` + `quantity:3`（≈ 80/s） | **加密**，测试预期更新 |
| 雪粒子 `frequency` | `50`（≈ 20/s） | `50` + `quantity:2`（≈ 40/s） | **加密** |
| 云 sprite 数 | `CLOUD_COUNT = 4` | `10`（桌面）/ `6`（移动） | **加密**，tier 化 |
| 溅水 / 海港浪花 | **无** | 新增第二 / 第三 emitter | **新增** |
| `WEATHER_TINT` 16 进制 | `0x90b0d0 / 0xd0e0f0 / 0xc0c8d0` | 按 §1 校准（美术定稿） | **微调** |
| 性能降级 / 矿洞内层判定 | **无** | FPS < 50 自动 -20%；地下 floor 销毁 | **新增** |
| 资源路径 | `assets/pixel/effects/weather/` | 迁移到 `assets/pixel/weather/`（保留软链接） | **路径迁移** |

> **W5 落地拆分**：(1) `quantity` / `tier` 参数化（向下兼容），(2) 溅水 + 海港 + 矿洞内层判定，(3) FPS 降级 + reduced-motion。每步独立 PR + 单元测试覆盖。

---

> **本文是宪法，不是说明书**。任何新天气需求（如 R-13 闪电 / 沙尘暴 / 雾、节日烟花粒子）必须先回到本文 §1 总表与 §5 性能预算找语义与槽位，找不到再补充新提案。**严禁在组件层硬编码 `scene.add.particles(...)` 绕过 `applyWeather`**，必须经 `weather.ts` adapter 统一管理，否则破坏 §6 切换动画与 §12 可访问性兜底。
