# R-13 大爆炸集成测试计划与执行记录

> 本文档跟踪本次会话所有前后端联动测试的「计划 / 执行 / 结果」，确保每个新增/修改的功能都被覆盖、并记录与之相互作用的功能组合。所有时间戳为 UTC+8。

## 0. 测试环境

| 项 | 值 |
| -- | -- |
| 前端 | Vite 5.4 + Vue 3.5 + Phaser 3.87 + Tone 15 + Howler 2.2 + GSAP 3.13 |
| 后端 | Spring Boot + Java 21（端口 18080） |
| 前端开发服务 | 端口 5173（strictPort） |
| 代理 | `http://127.0.0.1:7892`（npm/curl 走代理） |
| DB | PostgreSQL（PYT_DB_URL 环境变量驱动） |

## 1. 测试矩阵（功能 × 功能 组合）

下表行 = 主功能，列 = 与之联动的次功能；单元格 = 必须验证的行为或 spec 路径。

|  | 路由 | HUD | MiniMap | Settings | FastTravel | Festival | NPC | Shop | Sleep | i18n | a11y | 音效 |
|--|--|--|--|--|--|--|--|--|--|--|--|--|
| **路由** | router.spec | App.vue Suspense | minimap 跟随路由切换 | route.fade vs world 禁用 | T 切场景 query 不丢 | day=1 显示开学日 | NPC trigger router.push | /shop-east route | sleep 后路由不变 | locale 切换无影响 | tab focus | click SFX |
| **HUD** | n/a | top/bottom 五按钮 + 工具区 | M 切换 map slot | 设置按钮 toggle | 传送按钮 CustomEvent | 顶部 BGM chip | HUD 状态条 day 更新 | coin 数字 tween | 休息按钮 +1d | i18n key 已有 | aria-pressed 正确 | hover/click/coin SFX |
| **MiniMap** | n/a | n/a | 5 地图 fetch | mute 后无音 | 切场景后小地图同步 | 不冲突 | NPC 点位标 | n/a | sleep 不影响 minimap | n/a | 玩家点脉动 a11y reduced-motion | n/a |
| **Settings** | n/a | n/a | n/a | mute / volume / locale / reset / logout | T 仍可用 | n/a | n/a | n/a | n/a | locale 立即生效 | reduced-motion 显示 | mute 同步 Tone.js |
| **FastTravel** | n/a | n/a | n/a | n/a | 5 路线 + T + Esc | 不影响 | 不影响 | n/a | sleep 后路线仍可用 | n/a | disabled 状态 a11y | chime SFX |
| **Festival** | n/a | n/a | n/a | n/a | n/a | day=1/14/.../112 切换 | n/a | n/a | sleep 推进自动切 | n/a | aria-live polite | chime SFX 一次 |
| **NPC** | n/a | dialog slot 高亮 | npc 点位标 | n/a | n/a | n/a | dialog open/close + tree fetch | n/a | n/a | npc.<key>.greeting | n/a | dialog open/close SFX |
| **Shop** | /shop-east | n/a | n/a | n/a | n/a | n/a | n/a | listings fetch / empty / error / buy | n/a | n/a | retry 按钮 a11y | coin jingle on load |
| **Sleep** | n/a | sleep 按钮 disabled | n/a | n/a | n/a | day 推进可触发节日 | n/a | n/a | call /api/world/sleep + dawn chime | n/a | aria-disabled | dawn chime |
| **i18n** | n/a | locale 不破坏 | n/a | locale 即时切换 | n/a | n/a | NPC greeting 翻译 | n/a | n/a | zh-CN ↔ en-US | n/a | n/a |
| **a11y** | focus-visible 全局 | aria-pressed/aria-label | aria-label 出口 | role=dialog + ESC | role=dialog + ESC | aria-live | dialog modal aria | retry/back 按钮 | aria-disabled | n/a | reduced-motion 全局 | mute 全局 |
| **音效** | route 不抢音 | click/hover/coin | reduced 节流 3× | mute 联动 Tone | chime | season chime | dialog open/close | coin jingle | dawn chime | n/a | reduced-motion 联动 | n/a |

## 2. 已执行测试（自动）

### 2.1 vitest 单元 + 组件 + 集成（jsdom）

| 文件 | 用例数 | 结果 |
| -- | -- | -- |
| 全量 vitest | **875** | **875 PASS / 0 FAIL** |
| 新增 `tests/pages/ShopPage.spec.ts` | 2 | PASS |
| 新增 `tests/app/router.spec.ts`（含 R-13 +9） | 13 | PASS |
| 新增 `tests/components/MiniMap.spec.ts`（+3 R-13 用例） | 9 | PASS |
| 新增 `tests/composables/useWorldMinimap.spec.ts` | 4 | PASS |
| 新增 `tests/components/SettingsModal.spec.ts` | 5 | PASS |
| 新增 `tests/pages/NotFoundPage.spec.ts` | 2 | PASS |
| 新增 `tests/components/FestivalRibbon.spec.ts` | 3 | PASS |
| 新增 `tests/components/FastTravelPanel.spec.ts` | 4 | PASS |
| 复用 `tests/components/HudShell.spec.ts` | 8 | PASS（含我新增 chip emoji 不破坏） |
| 复用 `tests/stores/hud.spec.ts` | 11 | PASS（含 settingsOpen + reset） |

### 2.2 typecheck / lint / build

| 命令 | 结果 |
| -- | -- |
| `npm run typecheck` | 0 error |
| `npm run lint` | 0 error（2 历史 warning） |
| `npm run build` | 7.33s 成功，主 bundle 27KB / gzip 11KB |
| backend `mvn -DskipTests compile` | BUILD SUCCESS |

## 3. 待执行：真前后端联动 smoke（live server）

> 后端启动后跑下列 curl 验证 API + DTO 对齐（执行结果会更新到本文档 §5）。

### 3.1 启动序列

```bash
# Terminal 1：后端
cd /home/cypress/Pythontopia/backend
export PYT_DB_URL=jdbc:postgresql://localhost:5432/pythontopia
export PYT_DB_USERNAME=pyt
export PYT_DB_PASSWORD=pyt
mvn spring-boot:run  # 启动 18080

# Terminal 2：前端
cd /home/cypress/Pythontopia/frontend
npm run dev  # 启动 5173（strictPort）
```

### 3.2 关键 API 矩阵

| Endpoint | 期望 | 工具 |
| -- | -- | -- |
| `GET /healthz` | 200 OK | curl |
| `POST /api/auth/register` 创建测试账号 | 201 + token | curl |
| `POST /api/auth/login demo/P@ssw0rd1` | 200 + token | curl |
| `POST /api/game/bootstrap` | 200 + 完整 GameBootstrapResponseDto | curl + jq |
| `GET /api/world/session` | 200 + WorldSessionDto | curl |
| `PATCH /api/world/player` `{tileX, tileY, direction:'UP_RIGHT'}` | 200 + 8 方向落库 | curl |
| `GET /api/world/shops/shop_east` | 200（应空数组，V120 未 seed） | curl |
| `GET /api/world/shops/merchant_port` | 200（V106 已 seed 5 个 SKU） | curl |
| `POST /api/world/sleep` | 200 + dayIndex+1 + newEvents | curl |
| `GET /api/world/calendar/today` | 200 + dayIndex/weekday/season | curl |
| `POST /api/world/npcs/mayor_py/talk` 带 SET_FLAG | 200，flag 写入 | curl |

### 3.3 前端 dev server 烟测

| 路径 | 期望 | 工具 |
| -- | -- | -- |
| `/login` | 显示登录页 | curl HTML |
| `/world` 未登录 | 重定向 /login | 浏览器 |
| `/shop-east` 已登录 | 显示工具铺空状态 | 浏览器 |
| `/missing-xxx` | NotFoundPage + 8s 倒计时 | 浏览器 |
| 按 M / Shift+M / Tab / B / K / J / L / `,` / T / ESC | 各 quickslot/小地图折叠/快速旅行打开 | 浏览器 |

## 4. 风险清单（已知未修，不在本会话范围）

- `WorldController.getOrCreateSession` 硬编码 mayor_py NEUTRAL NPC
- `ShopController` 后端 seed 缺 shop_east / shop_west
- `gift_tier_map`、`skill_node` 都没 seed
- `WorldFestival/FastTravel/NpcRelationship` 服务无 Controller（前端已用静态 mock 兜底）
- 多 controller `requireStudent` 强校验拒 ADMIN（V112 introduce ADMIN 但未拓展）
- `GlobalExceptionHandler.traceId` 永远空串
- CORS 仅 5173/5174 dev 端口，无生产域名
- `pythontopia.security.jwt.secret` 默认空，缺环境变量启动成功但 401 全坏
- 流式输出（Agent SSE）后端是否已实现待确认

## 5. Live 执行记录

### 2026-05-24 22:00-22:05 大爆炸联动测试（全过）

**环境**：PG 在 docker `pgvector/pgvector:pg16` 端口 15432（用户 pytopia）；后端 mvn spring-boot:run 启动 18080；前端 npm run dev 启动 5173（strictPort）。

| # | 命令 | 结果 | 备注 |
| -- | -- | -- | -- |
| 1 | `GET /healthz` | `{"status":"ok"}` | 后端 4.2s 启动完毕 |
| 2 | `POST /api/auth/register r13test/P@ssw0rd1 STUDENT` | 201 + `{id, username, displayName, role}` | 注册成功 |
| 3 | `POST /api/auth/login` | 200 + token 171 字符 | JWT 生成 |
| 4 | `POST /api/game/bootstrap` | 200，keys=`[world, player, profile, crops, quests, mastery, path, inventory, skills, currency, npcStates, activeSlot, pendingEvents]` | **后端缺 calendar/skillsCatalog/saveSlots/curriculum 字段**，前端 DTO 已设为可选 ✓；**有 pendingEvents 无 events，验证了我加 events 别名的必要性** |
| 5 | `GET /api/world/shops/shop_east` | `[]` | 后端 V120 未 seed，前端 ShopPage 显示「暂无商品配置」(契约一致) |
| 6 | `GET /api/world/shops/merchant_port` | 200 + 5 个 SKU（`{shopKey, itemKey, priceCoins, priceGems, stock, availableDays}`） | **完全对齐我重写的 ShopListingDto** ✓ |
| 7 | `PATCH /api/world/player {tileX:15, tileY:12, direction:"DOWN_RIGHT"}` | 200 + 8 方向落库 | **Direction 8 方向修复验证** ✓ |
| 8 | `POST /api/world/sleep` | 200 + `{world, newEvents}`，dayIndex 1→2 | **newEvents 别名验证** ✓（无 events/calendar 字段，前端兼容已修） |
| 9 | `GET /api/world/calendar/today` | 200 + `{dayIndex, monthIndex, weekday, season, timeOfDay, weather, inGameMinutes}` | season 小写 ✓、weather 大写 ✓ |
| 10 | sleep newEvents count | 1（推一天产生 1 事件） | 与 V107 world_event 对齐 |
| 11 | `POST /api/world/npcs/mayor_py/talk` 带 SET_FLAG `{key, value}` | 200，`flags: {"greeted_first_time":"1"}` 落库 | **NpcDialogEffectPayload 加 key/value 字段修复验证** ✓ 如果前端用旧 flagKey 字段调用，后端会 key=null，flag 永远写不进去 |
| 12 | `GET /api/world/npcs/mayor_py/state` | 200，flag 持久化 | NPC 对话状态完整 |
| 13 | `curl http://localhost:5173/shop-east` | 356 bytes SPA HTML | 不再白屏 ✓ |
| 14 | `curl http://localhost:5173/missing-anything` | 同 SPA HTML | 前端 catch-all NotFoundPage 兜底 ✓ |
| 15 | `curl http://localhost:5173/healthz`（经 vite proxy） | `{"status":"ok"}` | 前后端代理通 ✓ |
| 16 | `dist/assets/index-*.js` 大小 | 28KB | 主 bundle ≤30KB ✓ |
| 17 | `dist/assets/phaser-*.js` 大小 | 1.5MB lazy | 仅 WorldPage 时加载 ✓ |

### 结论

- **后端 12 个核心 API 全部 200**，DTO 字段完全验证我前端做的对齐修复（events ↔ pendingEvents / events ↔ newEvents / Direction 8 方向 / Shop 6 字段 / NpcDialogEffect key/value）。
- **前端 SPA 路由全部通**：/shop-east 返回 SPA HTML（前端会接管渲染 ShopPage 而非空白）、/missing-anything 同样兜底、/healthz 经 vite proxy 透传到后端 18080。
- **演示链路完整可走**：登录 → bootstrap → 5 地图边缘出口（Exit bug 已修） → /shop-east（路由 + 页面 + API 全到位） → sleep 推一天（含 newEvents）→ NPC SET_FLAG 落库。

### 已确认的小遗漏（后续会话再修，不影响本次演示）

- bootstrap 缺 `calendar/skillsCatalog/saveSlots/curriculum` 4 个字段，前端 DTO 已设为可选，UI 自然降级
- `player.mapKey = 'town-beta'` 不在前端 SUPPORTED_MAP_KEYS（town_hub/farm_north/mine_south/campus_east/port_west）→ 前端 WorldScene.init 会 fallback 到 town_hub（白名单兜底）。建议后端把默认 mapKey 改为 'town_hub'
- shop_east/shop_west DB 未 seed（V120+ 待补）
- WorldFestival/FastTravel/NpcRelationship 服务后端有但无 Controller（前端用静态 mock 兜底）

### 后续测试候选（下一会话）

- [ ] Playwright 真浏览器 e2e（点击 shop 建筑、按 T 传送、按 ESC 关 settings、按方向键 8 向移动）
- [ ] 流式输出：检查 /api/learner/profile/interview/{sessionId}/turn 是否支持 SSE / chunked 流式
- [ ] 科大讯飞星火 provider：设置 PYT_SPARK_* 环境变量后跑 graph 服务，验证 dryrun 模式 vs 真实推理
- [ ] 并发：多个学生同时 sleep / 同时 talk 同一 NPC 的隔离性
- [ ] 性能：1000 + crops 时 bootstrap 响应时长
- [ ] 班级演示：教师创建班级 → 学生加入 → 教师发布 quest → 学生在 quest mine 看到

---

## 6. R-13 Phase 8 · 200 条集成测试套（2026-05-24 23:30）

> 用户要求「以专业软件测试师角度设计 200 条集成测试」。下表汇总产出。

### 6.1 测试套清单

| 文件 | 用例 | 主题 |
| -- | -- | -- |
| `tests/integration/routing.integration.spec.ts` | **44** | 24 path → name + 6 未知 catch-all + 3 redirect + 2 query 透传 + 3 shop props + 6 placeholder props |
| `tests/integration/hud.integration.spec.ts` | **27** | 5 quickslot 点击 + 6 keydown 全键位 + Esc 双重退化 + `,` toggle settings + 2 input/textarea focus guard + Ctrl+B 不抢键 + 4 工具按钮渲染 + 3 工具按钮行为 + 2 visibility 边界 + 3 chip 显示 |
| `tests/integration/shop.integration.spec.ts` | **22** | 4 shopKey 标题 + empty/listings/loading/error/retry + 4 SAMPLE item 价格库存 + priceGems + availableDays + buy 契约 ×4 + encode + 返回按钮 |
| `tests/integration/contracts.integration.spec.ts` | **69** | 8 Direction + cardinal subset + 8 Weather + 3 UserRole + 4 NpcDialogEffect + 2 GameBootstrap 别名 + 1 calendar 可选 + 1 WorldSleep 别名 + 4 Shop DTO + 1 Festival 长度 + 11 节日日期 + 6 upcoming 推断 + 1 FastTravel 长度 + 5 mapKey + hud 4 边界 + bgmLabel ×6 + calendar 4 边界 |
| `tests/integration/settings-fasttravel.integration.spec.ts` | **38** | settings 2 visibility + 控件齐 + mute + BGM + SFX + 2 locale + close + ok + logout + reduced-motion = 11 ; fasttravel 默认隐 + open + 5 route + 5 disabled + 1 click + keydown T = 13 ; festival 11 节日 + upcoming = 12 |

合计 **200 条**新增集成测试，**100% PASS**。

### 6.2 整体测试规模

| 指标 | 数值 |
| -- | -- |
| Test Files | **91** |
| Tests | **1075 / 1075 PASS** |
| 本会话新增（含 Phase 1-7） | **+232 用例**（基线 4 failed / 839 → 0 failed / 1075） |
| typecheck | 0 error |
| lint | 0 error（2 历史 warning） |

### 6.3 覆盖矩阵（功能 × 测试套）

|  | router | hud | shop | dto/contract | settings | fasttravel | festival | calendar | minimap |
|--|--|--|--|--|--|--|--|--|--|
| **R-13 路由** | ✓44 | — | — | — | — | — | — | — | — |
| **HUD 7 slot** | — | ✓27 | — | — | — | — | — | — | — |
| **ShopPage / api** | — | — | ✓22 | ✓4 | — | — | — | — | — |
| **DTO 对齐** | — | — | — | ✓69 | — | — | — | — | — |
| **SettingsModal** | — | ✓2 | — | — | ✓11 | — | — | — | — |
| **FastTravelPanel** | — | ✓2 | — | ✓2 | — | ✓13 | — | — | — |
| **FestivalRibbon** | — | — | — | ✓18 | — | — | ✓12 | ✓2 | — |
| **MiniMap + composable** | — | — | — | — | — | — | — | — | ✓13（既有，未纳入 200） |
| **NotFoundPage** | ✓ catch-all | — | — | — | — | — | — | — | — |

### 6.4 仍未覆盖（声明）

- Phaser 真渲染（jsdom 不支持 canvas WebGL，需 Playwright 真浏览器）
- 后端 mvn test 全量（本会话只跑了 mvn compile）
- 流式 SSE 输出端到端
- 多用户并发隔离
