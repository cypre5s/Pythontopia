# Pythontopia 性能基线与监控规范

> 版本：v1.0 · 起草日期：2026-05-24 · 适用版本：R-12 起 · 状态：Draft（W4 完成后转 Active）
> 目标读者：性能工程师、前端 owner、Phaser/玩法工程师、后端 API owner、Android 客户端 owner、QA、班级教师视角策划
> 上游约束：`AGENTS.md`（"交付说明必须包含性能指标"）、`frontend/perf-budget.json`（机器可读预算）、`frontend/src/services/perfMonitor.ts`（运行时埋点）

## 1. 文档定位

本规范是 Pythontopia 的**性能单一真相（Single Source of Performance Truth）**：

- 任何"页面太慢""场景切换卡""API 超时"的争论，先回到本文 §2 总览表对齐"快"的定义；
- 任何新增页面 / 场景 / API / Agent / 资产之前，必须先在 §13 验收 checklist 里勾选自己将影响的预算项；
- 任何 R-12 Beta 演示前 24h，QA 必须按 §12 不达标阻断策略复跑一次自测脚本；
- 任何 R-13 演示前，须再次校准本文所有阈值与实测基线（§14 待办）。

显式 **不** 覆盖的范围：

- Pyodide 沙箱内学生代码的资源预算 → 见 `docs/gameplay/r12-deepen/08-pyodide-budget.md`；
- 班级教师 dashboard 大数据可视化的渲染性能 → 见 R-13 计划；
- LLM Agent 推理时延 → 见 `services/pythontopia-graph/docs/`（R-13 起草）。

## 2. 总览：5 类指标 × 26 个具体目标

| 类 | 指标 | 目标 | 阻断阈值 | 测量工具 | 责任人 |
| --- | --- | --- | --- | --- | --- |
| 前端 Web Vitals | LCP | ≤ 2.5 s | > 4.0 s | Lighthouse / `perfMonitor.recordWebVital` | 前端 owner |
| 前端 Web Vitals | FCP | ≤ 1.8 s | > 3.0 s | Lighthouse / PerformanceObserver | 前端 owner |
| 前端 Web Vitals | TTI | ≤ 3.8 s | > 7.3 s | Lighthouse | 前端 owner |
| 前端 Web Vitals | INP | ≤ 200 ms | > 500 ms | Chrome DevTools / PerformanceObserver | 前端 owner |
| 前端 Web Vitals | CLS | ≤ 0.1 | > 0.25 | Lighthouse | 前端 owner |
| 前端 Web Vitals | TTFB | ≤ 800 ms | > 1.8 s | Network panel | 前端 owner + 后端 owner |
| Phaser 场景 | Boot → town_hub | ≤ 800 ms | > 1.5 s | `perfMonitor.start/end('boot_to_town_hub')` | 玩法工程师 |
| Phaser 场景 | Scene transition | ≤ 400 ms（fade 含） | > 800 ms | `perfMonitor` + WorldScene 埋点 | 玩法工程师 |
| Phaser 场景 | Tilemap load | ≤ 600 ms | > 1.2 s | `perfMonitor` + BootScene preload | 玩法工程师 |
| Phaser 场景 | FPS（小镇行走） | ≥ 50（目标 60） | < 30 持续 5 s | `Phaser.Game.loop.actualFps` 上报 | 玩法工程师 |
| Phaser 场景 | Frame time | ≤ 20 ms p95 | > 33 ms p95 | Chrome Performance | 玩法工程师 |
| 后端 API | P50 | ≤ 100 ms | > 200 ms | Micrometer + Prometheus | 后端 owner |
| 后端 API | P95 | ≤ 300 ms | > 600 ms | Micrometer + Prometheus | 后端 owner |
| 后端 API | P99 | ≤ 800 ms | > 1.5 s | Micrometer + Prometheus | 后端 owner |
| 后端 API | 错误率 | ≤ 0.5 % | > 2 % | Sentry / Prometheus | 后端 owner |
| 数据库 | SQL p95 | ≤ 50 ms | > 200 ms | `pg_stat_statements` | 后端 owner |
| 数据库 | N+1 检出 | 0 例 | ≥ 1 例 | `spring.jpa.show_sql` + grep | 后端 owner |
| 资源体积 | main bundle (gzip) | ≤ 180 KB | > 200 KB | size-limit | 前端 owner |
| 资源体积 | vendor chunk (gzip) | ≤ 240 KB | > 280 KB | size-limit | 前端 owner |
| 资源体积 | phaser chunk (gzip) | ≤ 520 KB | > 600 KB | size-limit | 玩法工程师 |
| 资源体积 | pixel assets | ≤ 2 MB | > 2.5 MB | size-limit / du | 美术 + 玩法工程师 |
| 资源体积 | css bundle (gzip) | ≤ 50 KB | > 75 KB | size-limit | 前端 owner |
| 资源体积 | fonts | ≤ 300 KB | > 500 KB | size-limit / 子集化校验 | 前端 owner |
| Pyodide | 冷启动加载 | ≤ 6 s（4G） | > 10 s | `perfMonitor.start('pyodide_cold_boot')` | 前端 owner |
| Pyodide | 单次执行 | ≤ 1 s（典型作业） | > 5 s（CPU 上限） | `pyodideBudget.cpuTimeoutMs` | 前端 owner |
| Android | 启动冷时长 | ≤ 2 s | > 4 s | Android Studio Profiler | Android owner |
| Android | LibGDX FPS | ≥ 50 | < 30 持续 5 s | Profiler frame time | Android owner |

数字解释：

- 阻断阈值 ≈ 目标 × 2，这是经验取值，意为"已经从'有点慢'恶化到'不能演示'"。
- P95 取代均值是因为 P50 + 均值会掩盖少数极慢请求，而教学场景是"一个学生卡死整个体验"。
- FPS ≥ 50 而非 60，是因为像素游戏在 60 → 50 之间肉眼几乎无感，但低于 30 立刻像 PPT。

## 3. 前端 Web Vitals：LCP / FCP / TTI / INP / CLS

### 3.1 LCP（Largest Contentful Paint）

- **目标**：≤ 2.5 s（4G 模拟器，CPU 4× slowdown）。
- **测量方式**：
  - 自动化：CI 跑 Lighthouse mobile preset，断言 `lhr.audits['largest-contentful-paint'].numericValue <= 2500`。
  - 真实用户：`PerformanceObserver` 监听 `largest-contentful-paint` 入口，转发 `perfMonitor.recordWebVital('LCP', value)`。
- **典型最大元素**：登录页 → 像素 logo `pixel/logo.png`；首页 → `<canvas>` 首帧（BootScene 占位灰色背景）。
- **常见恶化原因**：
  - vendor chunk 没拆 → 阻塞 LCP；
  - 中文像素字体未 preload → 字体 swap 触发 LCP 重算；
  - LCP 图片未走 `<img loading="eager" fetchpriority="high">`。

### 3.2 FCP（First Contentful Paint）

- **目标**：≤ 1.8 s。
- **测量方式**：同 LCP，但订阅 `paint` 入口的 `first-contentful-paint`。
- **关注点**：登录页骨架（pixel 背景 + 输入框）必须在 FCP 内出现，避免空白闪烁。

### 3.3 TTI（Time to Interactive）

- **目标**：≤ 3.8 s。
- **测量方式**：Lighthouse audit `interactive`，无运行时近似指标，依赖 CI。
- **特别约束**：Pyodide 冷启动在 TTI 之后启动（按需 lazy load），不能阻塞 TTI。

### 3.4 INP（Interaction to Next Paint）

- **目标**：≤ 200 ms。
- **测量方式**：`PerformanceObserver` 监听 `event` 入口（`durationThreshold: 16`），按 P95 聚合。
- **高风险点**：
  - DialogTree 选项点击 → 后端 effects 请求 → UI 更新；目前同步更新友好度，需要确保 200 ms 内反馈。
  - 背包格点击 → 物品详情弹窗；动画需 `<200ms`。
  - Tilemap 切换时锁输入避免误响应。

### 3.5 CLS（Cumulative Layout Shift）

- **目标**：≤ 0.1。
- **测量方式**：同 LCP，订阅 `layout-shift` 入口。
- **典型违规**：
  - HUD chip 数字位数变化（10 → 100）撑宽 → 给数字栏 `min-width`；
  - 字体 swap 时高度跳变 → `font-display: optional` + size-adjust。

## 4. Phaser 场景切换时长

### 4.1 度量点

WorldScene 切换流程定义为：

1. 玩家走到 exit 触发 `SceneSwitcher.startTransition()`；
2. 主场景 fade out 240 ms；
3. 目标 Scene 加载（Tilemap + sprite + NPC schedule）；
4. 目标 Scene fade in 240 ms；
5. 触发 `cameras.main.fadeIn` 完成事件。

埋点：

```
perfMonitor.start('scene_transition:town_hub→farm_north');
// ...startTransition + load + fadeIn 完成回调
perfMonitor.end('scene_transition:town_hub→farm_north');
```

p95 必须 ≤ 400 ms，包含两段 fade（共 480 ms 预算 → 实际感知 < 800 ms）。

### 4.2 BootScene 首次加载

`boot_to_town_hub` 包含资产 preload + 字体 ready + Pinia 状态 hydration。目标 800 ms：

- preload：地图 JSON + tileset + Sara sheet + 4 BGM（streaming，不计入 800 ms） ≤ 500 ms；
- 字体 ready：通过 `document.fonts.ready` 等待 ≤ 100 ms；
- Pinia 水合 + WorldScene init ≤ 200 ms。

### 4.3 Tilemap 加载

单图 Tilemap 加载 ≤ 600 ms（含解析 JSON + create layers + collider 重建），由 `WorldScene.preload` + `create` 测量。port_west（35×25）最小，town_hub（50×40）最大。

### 4.4 内存清理 checklist

Scene 切换时必须执行（写入 `SceneSwitcher.cleanup()`）：

- [ ] 当前 Scene 所有 tween `removeAll()`；
- [ ] 当前 Scene 所有 TimerEvent `remove()`；
- [ ] 当前 Scene 所有 Group `destroy(true, true)`；
- [ ] 当前 Scene 所有 input listener `removeAllListeners()`；
- [ ] WeakMap NPC 状态保留（跨场景）；
- [ ] BGM 走 useAudio 全局栈，不在 Scene 内管理；
- [ ] Tilemap 切换前 `map.destroy()` 释放图块缓存。

未执行清理时跨场景 100 次会爆 GC，FPS 从 60 跌到 < 30。

### 4.5 分块预加载策略

R-13 计划：玩家走到地图边缘 2 tile 内时，提前 `load.tilemapTiledJSON(nextMap)`，使 fade 期间只剩 collider 重建（≤ 100 ms），不再受网络抖动影响。

## 5. FPS 监控

### 5.1 采样方式

`Phaser.Game.loop.actualFps` 每秒读一次，写入 ring buffer（最近 30 s 30 个样本），由 `perfMonitor.record('fps', value)` 上报。同时记录 `frameDelta` p95。

### 5.2 阈值与告警

- **目标**：FPS ≥ 50（5 s 滚动均值）。
- **警告**：连续 3 s FPS < 40 时控制台 warn。
- **阻断**：连续 5 s FPS < 30 时弹"性能降级"教育对话框，并把 `world.renderMode` 切到 'reduced'（关闭天气粒子 + 阴影）。

### 5.3 常见 FPS 杀手

- **Tween 过多**：同屏 > 50 个 tween 时 FPS 会跌；用 `Phaser.Time.TimerEvent` 替代连续 tween。
- **shadow ellipse 重绘**：阴影通过每帧 `clear + draw` 实现会爆 CPU；改 sprite mask。
- **未压缩 PNG**：每张 Tilemap 切片要求 pngquant -Q 80 处理。

## 6. API P95 / P99

### 6.1 测量栈

后端使用 Spring Boot Actuator + Micrometer + Prometheus（默认 `/actuator/prometheus` 暴露）。指标名：

- `http_server_requests_seconds_count`
- `http_server_requests_seconds_sum`
- `http_server_requests_seconds_max`
- 通过 `histogram` bucket 计算 P95 / P99。

### 6.2 重点端点

| 端点 | 频率 | P95 目标 | 说明 |
| --- | --- | --- | --- |
| `POST /api/auth/login` | 每天人均 1 次 | 300 ms | 含 BCrypt，已有缓存 |
| `GET /api/world/bootstrap` | 每天人均 5+ 次 | 400 ms | 一次返回 12 张表 JSON，需评估缓存 |
| `POST /api/submissions` | 每天人均 20 次 | 500 ms | 含判题进程派发，可异步化 |
| `POST /api/quests/{id}/complete` | 每天人均 5 次 | 300 ms | 含 evidence 强校验 + KC 更新 |
| `GET /api/learner/profile` | 每天人均 5 次 | 200 ms | 读多写少，可强缓存 |

### 6.3 高频违例处理

P95 连续 1 小时超阈值时，Prometheus 触发 Alertmanager → Slack；oncall 须在 30 min 内响应。

### 6.4 缓存策略表

| 端点 | 缓存层 | TTL | 失效触发 |
| --- | --- | --- | --- |
| `GET /api/course-packs/43` | HTTP `Cache-Control: max-age=86400, immutable` | 1 天 | 课程包版本号变更 |
| `GET /api/course-packs/43/chapters/{id}/pages` | HTTP `Cache-Control: max-age=3600` | 1 小时 | 课件修订（极少） |
| `GET /api/learner/profile` | 内存（Spring `@Cacheable`） | 5 分钟 | 任何 LearningEvent 触发 evict |
| `GET /api/world/bootstrap` | 内存 + ETag | 1 分钟 | sleep 推进或商人刷新 |
| `POST /api/submissions` | 不缓存 | - | - |

### 6.5 请求合并

避免 "登录后 N 次串行请求" 反模式：`/api/world/bootstrap` 一次返回 12 张表（player + crops + quests + mastery + profile + path + inventory + skills + currency + npc_states + calendar + active_save_slot），前端按 slice 分发到对应 Pinia store。

## 7. 数据库 SQL 与 N+1 检测

### 7.1 慢查询

- 启用 `pg_stat_statements`，每天上报 top 20 慢查询；
- SQL p95 ≤ 50 ms；P99 ≤ 200 ms；
- 任何 sequential scan on > 10k rows 都需在 commit message 标注理由。

### 7.2 N+1 检测

- 开发环境：`spring.jpa.show_sql=true` + 单测断言"同一 endpoint 内 SELECT 次数 ≤ N"，N 由测试 setup 显式声明；
- CI：跑 `mvn verify` 时启用 `hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS=100`；
- 生产：QueryCount Spring Filter（待 R-13 接入），>5 SELECT/req 上报 Sentry breadcrumb。

### 7.3 已知 N+1 风险

- `GET /api/classroom/{id}/quests` 关联学生提交 → 必须 `fetch join`；
- `GET /api/learner/profile` 关联 8 维 dimensions → 已 JSONB 单列，安全；
- `GET /api/world/bootstrap` 关联 inventory + skill + npc_states → 需视图 V100 一次性返回。

## 8. 资源体积预算

机器可读形式见 `frontend/perf-budget.json`；本节给出取值理由与边界。

### 8.1 JS

- `main bundle ≤ 180 KB gzip`：Vue 3 app + 路由 + Pinia + 自有业务；
- `vendor chunk ≤ 240 KB gzip`：Vue 运行时 + vue-router + pinia + axios；
- `phaser chunk ≤ 520 KB gzip`：Phaser 3.87 完整运行时（Arcade Physics + Tilemap + Animation）。

拆分策略（Vite manualChunks）：

```ts
manualChunks: {
  vendor: ['vue', 'vue-router', 'pinia', 'axios'],
  phaser: ['phaser'],
}
```

### 8.2 CSS

- `css bundle ≤ 50 KB gzip`：全局 tokens + Vue scoped + 像素 HUD 样式；
- 严禁组件内私自 import 整套 UI 库（如 Element Plus），任何超过 5 KB 的 CSS 增量必须 PR 评估。

### 8.3 图片

- `pixel assets ≤ 2 MB`：5 张 Tiled JSON + LPC Sara sheet + Kenney tileset + NPC portraits；
- 单文件 PNG 上限 100 KB；超出需要走 tileset 切分；
- 严禁直接引入 LPC 全 dump（200+ MB），仅子集化的 sara_walk.png 入仓。

### 8.4 字体

- `fonts ≤ 300 KB`：Ark Pixel 中文像素字体 + 拉丁子集合计；
- 中文字体必须 unicode-range 子集化为常用 3500 字 + 教学高频 500 字 = 约 4000 字。

## 9. Pyodide 启动时长

### 9.1 冷启动

- **目标**：≤ 6 s（4G 模拟器，下载 + 初始化）；
- **策略**：
  - 走 `pyodide.js` CDN（jsDelivr），缓存命中后 ≤ 1.5 s；
  - 第一次进 `/workshop` 路由时才 `import('pyodide')`，不进 main bundle；
  - 加载期间显示像素加载条 + "正在准备 Python 环境..." 教育文案。

### 9.2 单次执行

- 见 `docs/gameplay/r12-deepen/08-pyodide-budget.md`：CPU 5 s / 内存 64 MB / 60 秒 20 次 / 自然日 200 次；
- 典型作业（输出 hello world / 列表排序）应在 1 s 内完成。

## 10. Android 客户端 FPS / 启动

> Android 端 R-12 仅冒烟 demo，正式落地见 R-13。

- **冷启动**：Application init → MainActivity onCreate → 进入主界面 ≤ 2 s（中端机型）；
- **LibGDX FPS**：≥ 50；通过 `Gdx.graphics.getFramesPerSecond()` 每秒上报；
- **内存**：Application 总驻留 ≤ 200 MB（基线 + 1 场景）；
- **APK 体积**：debug APK ≤ 80 MB，release ≤ 50 MB（含 LibGDX natives）。

## 11. 监控工具栈

| 维度 | 工具 | 触发方式 | 报警目的地 |
| --- | --- | --- | --- |
| Web Vitals（合成） | Lighthouse CI（GitHub Action） | 每次 PR | PR comment + fail check |
| Web Vitals（真实用户） | `PerformanceObserver` + `perfMonitor.recordWebVital` | 用户访问时 | 浏览器控制台 warn，后续接 R-13 后端汇总 |
| FPS / Phaser 场景 | `perfMonitor.start/end` + `actualFps` 采样 | 运行时 | 控制台 warn → R-13 后端 |
| API P95 / P99 | Spring Actuator + Micrometer + Prometheus | 1s scrape | Grafana dashboard + Alertmanager → Slack |
| 数据库慢查询 | `pg_stat_statements` + 每日定时 cron | 每天 | Slack 报告 |
| 资源体积 | size-limit | CI | PR comment + fail check |
| 错误率 | Sentry SDK | 运行时 | Slack + 邮件 |
| Android | Android Studio Profiler + Sentry Android | 本地 + 真机 | Sentry |

工具选型理由：

- 选 `web-vitals` 库的 API 风格但不直接依赖该库（perfMonitor 不引入第三方）；
- 选 Sentry 而非自建 APM：Beta 阶段量小，Sentry free tier 足够；
- 选 Lighthouse CI 而非 SpeedCurve：免费 + 集成简单。

## 12. 不达标阻断策略

### 12.1 PR 级别阻断

CI 任一项失败立即 fail PR：

- `npm run typecheck` 0 error；
- `npm run lint` 0 error；
- `npm run test` 100 % pass；
- `npx size-limit` 所有 budget pass；
- Lighthouse CI score ≥ 0.85 / LCP ≤ 2500 / CLS ≤ 0.1（mobile preset）。

### 12.2 演示前阻断

Beta 演示 24 h 前 QA 必须跑 `scripts/r12-smoke-test.sh`（已存在）+ 本节附加的 `scripts/perf-baseline-check.sh`（R-13 落地）：

- 真机 4G 模拟器跑 5 张地图 fade 切换，全部 ≤ 800 ms；
- 真机点 NPC 对话 50 次，INP P95 ≤ 200 ms；
- 后端 30 min 持续压测，API P95 ≤ 300 ms。

任何一项失败 → 立即开 P0，不允许演示带病。

### 12.3 生产事故响应

P95 > 阻断阈值持续 1 h（生产环境）：

- L1：oncall 收 Slack alert，30 min 内响应；
- L2：oncall 评估能否回滚，2 h 内决策；
- L3：若涉及数据库，启动只读模式 + 教师视角降级。

## 13. 验收 Checklist

每次涉及性能预算的 PR 必须在描述里勾选：

- [ ] 已阅读 `docs/performance-baseline.md` §2 总览
- [ ] 已运行 `npm run typecheck && npm run lint && npm run test` 全过
- [ ] 已运行 `npm run build`，新增/修改的入口未超 `frontend/perf-budget.json` 任何一项
- [ ] 如新增 API：已加 Micrometer counter / timer 标签
- [ ] 如新增场景：已加 `perfMonitor.start/end` 埋点
- [ ] 如新增图片：单文件 ≤ 100 KB 且 pngquant 压缩
- [ ] 如新增字体：unicode-range 子集化
- [ ] 如新增第三方依赖：vendor / phaser chunk 增量已评估
- [ ] 如修改阻断阈值：已同步更新 `perf-budget.json` 与本文档 §2

Beta 演示前额外勾选：

- [ ] `scripts/r12-smoke-test.sh` 7/7 通过
- [ ] Lighthouse mobile preset LCP ≤ 2500 / CLS ≤ 0.1
- [ ] 真机 5 张地图 fade ≤ 800 ms
- [ ] 后端 30 min 压测 API P95 ≤ 300 ms
- [ ] Pyodide 冷启动 ≤ 6 s

## 14. R-13 待办

本文为 R-12 W4 基线版，R-13 计划补完：

1. `scripts/perf-baseline-check.sh`：把本文 §12.2 演示前阻断步骤脚本化，CI 与本地通用；
2. `analyticsBridge.ts`：把 `perfMonitor` 数据通过 navigator.sendBeacon 上报到后端 `/api/analytics/perf`；
3. 后端 `analytics_perf` 表 + `AnalyticsService`：聚合上报数据，给班级教师视角"性能热力图"看哪个学生卡；
4. Lighthouse CI GitHub Action 接入：目前只本地跑，PR 评论缺；
5. `web-vitals` 库可选接入：当 R-13 决定不再"零依赖" perfMonitor 时，3 行接入；
6. Android 真机 Profiler 接入：与 LibGDX FPS 上报对齐；
7. Pyodide 冷启动 P95 全量真实数据回填（目前是 4G 模拟器估计）；
8. 长任务 (`longtask` PerformanceObserver) 接入：>50 ms 的任务上报 perfMonitor；
9. 资源加载错误率：`resource` PerformanceObserver 监听 4xx/5xx 加载；
10. 与教师视角 KC 热力图联动：找出"卡点 = KC = 难点"三件套，反哺教学。

---

附录 A：阈值速查（按类别）

```
Web Vitals     LCP≤2.5s  FCP≤1.8s  TTI≤3.8s  INP≤200ms  CLS≤0.1  TTFB≤800ms
Phaser         boot≤800ms  scene≤400ms  tilemap≤600ms  FPS≥50  frame≤20ms p95
API            P50≤100ms  P95≤300ms  P99≤800ms  错误率≤0.5%
DB             SQL p95≤50ms  N+1=0
Bundle         main≤180KB  vendor≤240KB  phaser≤520KB  pixel≤2MB  css≤50KB  fonts≤300KB
Pyodide        冷启动≤6s  单次≤1s（CPU 上限 5s）
Android        启动≤2s  FPS≥50  内存≤200MB  APK debug≤80MB / release≤50MB
```

附录 B：与既有规范的交叉引用

- `AGENTS.md`：交付说明必须包含性能指标 → 本文 §13 提供 checklist；
- `docs/gameplay/r12-deepen/08-pyodide-budget.md`：Pyodide 沙箱预算 → 本文 §9 仅引用，不重复；
- `docs/release/r12/r12_release_notes.md` Wave-4：W4 完成项 → 本文是 W4 `perf_baseline` todo 的产出；
- `docs/beta_demo_checklist.md`：演示 checklist → 本文 §13 第二段对齐。

附录 C：经典 case study

**Case 1：登录后 5 s 白屏（R-11 已修复）**
- 现象：登录跳转 `/world` 后 5 s 才出现像素地图，期间纯白屏。
- 根因：`main.ts` 同步 `import 'phaser'`，phaser chunk（487 KB gzip）阻塞 LCP。
- 修复：改为路由级 `defineAsyncComponent(() => import('@/pages/WorldPage.vue'))`，phaser 推迟到进入 `/world` 才加载，登录页 LCP 从 5.2 s 降到 1.8 s。
- 教训：任何 > 200 KB gzip 的依赖默认走路由级 lazy。

**Case 2：farm_north 地图卡顿（R-12 W3 已修复）**
- 现象：从 town_hub 走到 farm_north 后 FPS 从 60 跌到 22，玩家走两步停一下。
- 根因：farm_north 40×40 tilemap 每帧重绘 + 16 个 tween 动画作物 + 阴影 ellipse 全屏 draw。
- 修复：tilemap 改用 `setCullPadding(2)` 视野裁剪 + 作物动画改 spritesheet anim（一次性 GPU 加速）+ 阴影改 sprite mask。FPS 回到 58。
- 教训：60 → 30 的 FPS 跌通常是"看似无害的连续 tween"。

**Case 3：API `/api/world/bootstrap` P95 800 ms（R-12 W4 修复中）**
- 现象：登录后 bootstrap 慢，玩家等 1 s 才能动。
- 根因：12 张表分 12 次 SQL，Hibernate lazy load 走出去再回来。
- 修复：写 V100 视图 `vw_game_bootstrap` 单 query JOIN 12 张表 + Spring `@Cacheable("bootstrap")` 缓存 1 min。
- 当前：P95 380 ms，目标 P95 ≤ 400 ms 已达成；继续优化到 200 ms 是 R-13 工作。

附录 D：版本变更日志

| 版本 | 日期 | 作者 | 变更摘要 |
| --- | --- | --- | --- |
| v1.0 | 2026-05-24 | Pythontopia 性能工程师（W4） | 首版：14 章节 + 5 类 × 26 指标 + 7 工具栈 + 阻断策略 + 23 验收项 + 10 R-13 待办 + 3 case study |
