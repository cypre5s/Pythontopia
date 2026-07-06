# B站视频卡片 + Marvis 式智能体工作室增强 — 设计与计划

- 状态：草案（待用户过目后开工）
- 日期：2026-06-04（R-14）
- 作者：AI Agent
- 范围：①「视频生成」改为抓取 B 站任务相关视频用卡片展示；②把多智能体资源生成可视化做成"真·按 agent 的实时虚拟工作室"（借鉴腾讯 Marvis）

---

## 0. 背景与现状（探查结论）

### 0.1「视频生成」现状（要被替换/补充的慢链路）
STORYBOARD（动画脚本）这条线：
- Python `StoryboardAgent`（`services/pythontopia-graph/.../agents/storyboard_agent.py`）产出 3–5 分镜脚本（6 类资源之一）。
- 后端 `StoryboardCompositionService`（`POST /api/media/storyboard/video`）= 真正"合成视频"：**讯飞文生图 + TTS + ffmpeg 拼 mp4**，每分镜一次，依赖服务端 ffmpeg，没装则降级。**这是"要等待"的根源。**
- 前端 `StoryboardView.vue` 的"🎬 合成讲解视频"按钮调它；平时走"实时剧场"（图+TTS）。

### 0.2 已有"真视频"体系（B 站方案的现成模板/落点）
- `CourseVideoController`/`CourseVideoService` 读 `video_manifest.json`（`crawl_chaoxing_videos.py` 从超星爬）按章节给 mp4；前端 `api/courseVideo.ts` + `pages/LecturePage.vue`（"看"页）渲染。
- **空洞**：内置课程无任务点视频，manifest 为空，`LecturePage` 现显示"本章暂无在线视频"。← B 站卡片填这个洞。

### 0.3 Marvis 式可视化「已有约 80%」
- 前端 `components/workshop/AgentOfficeScene.vue`（像素「智能体事务所」）：4 个 agent NPC + 走位主管 + 右侧进度/步骤/日志栏 + 交付前证据卡，**已由 SSE 驱动**。宿主 `WorkshopChat.vue` 用 `useSseStream('/api/workshop/resources/stream')`。同类还有 `TutorCrew.vue`、`GuideOverlay.vue`（注释明确"借鉴腾讯 Marvis"）。
- 后端 Spring **MVC servlet**，SSE 成熟：`SseEmitter` + `common/sse/GraphSseConsumer`(JDK HttpClient) + `SseRelayExecutor`。`POST /api/workshop/resources/stream` 已**实时转发** Python 的 `trace` 事件，仅拦截 `result` 落库。
- Python `ResourceGraph.run_streaming`（`graph/resource_graph.py`，**非 LangGraph**，手写 async）已发 `trace`/`result`/`error`；SSE 包装在 `routes/streaming.py`。

**真正差距（要补的 20%）**
1. Python 每节点**只发"完成"**（`run_streaming` 在每步后 `emit("trace", trace[-1])`），无 `start`/相位，无开场"工位清单"。
2. 前端 `AgentOfficeScene.vue` 把流水线**塌缩为固定 4 工位**，`stageOf()` 用 `/agent/i` 正则把所有 agent 节点丢进 `generate`，**不显示真实具体 agent**（如 StoryboardAgent）。

**诚实约束**：资源生成每次只跑 **1 个设计师 agent（按 `request.type` 选）+ evidence + grounding + hallucination + safety**，并非 11 个 agent 全跑。工作室必须忠实反映**真实节点**，设计师工位**动态显示真实 agent 名**，不摆假 agent（遵 AGENTS.md「不造假」）。

### 0.4 决策落定（来自用户）
- 取数库：`bilibili-api-python`（Python、GPLv3+，已接受）→ 取数层**必须**在 Python 服务，后端代理。
- agent 流：**实时流式（SSE）**。
- 落点：**两者都做**（LecturePage + 工坊 STORYBOARD）。
- 流程：先出本文档（含详细验收标准 + 实时进度日志），过目后开工；可适当并发 subagent。

---

## 1. 目标
1. **B 站视频卡片**：按"用户任务/章节/KC"关键词搜 B 站视频，以卡片渲染；替换工坊 STORYBOARD 的默认"合成视频"（生成保留为可选），并填补 LecturePage 空洞。
2. **真·按 agent 的实时虚拟工作室**：把资源生成流水线的**真实节点**做成 Marvis 式实时面板（工位 + 相位 + 日志）。

非目标：不删除现有合成视频能力；不引入 LangGraph 重写；不改 Tutor/Orchestrator 的 SSE 契约；不下载转存 B 站视频。

---

## 2. 方案 A：B 站视频卡片

### A1. Python 取数（pythontopia-graph，FastAPI）
- 新增依赖：`bilibili-api-python`（`pyproject.toml`）。**GPL 隔离声明**：在该服务 README/依赖说明标注；同步更新 `LICENSE-ART.md`。
- 新增路由（建议 `routes/video.py`）：`GET /agents/video/bilibili/search?keyword=...&limit=8`
  - 调 `bilibili_api.search.search_by_type(keyword, search_type=VIDEO, ...)`（库自带 WBI 签名 + 反爬）。
  - 结构化返回（fail-fast，不静默降级成空聊天）：`{ items: [{ bvid, title, author, cover, duration_sec, play, danmaku, pubdate, url }] }`。
  - 清洗：过滤付费课（cheese）、广告；`cover` 统一 `https:`；`url=https://www.bilibili.com/video/{bvid}`。
  - 失败：抛结构化错误（`VIDEO_SEARCH_FAILED`），后端转 `BUSINESS`。
- 注册到 `main.py`。

### A2. 后端代理（Spring MVC）
- `BilibiliVideoDto`（record）：`bvid,title,author,coverUrl,durationSec,play,danmaku,pubdate,url`（`@JsonProperty` 对齐 snake_case）。
- `BilibiliVideoService`：通过共享 `graphRestTemplate` 调 `properties.graphUrl()+"/agents/video/bilibili/search"`；失败 `BusinessException(BAD_GATEWAY,"VIDEO_SEARCH_UNAVAILABLE")`。
- `BilibiliVideoController`：`GET /api/videos/bilibili?keyword=...&limit=...`（`@Validated`，可选 `@CurrentUser`）。**保持 `authenticated()`**（axios 带 JWT），无需改 `SecurityConfig`。
- 复用 `GraphServiceProperties.graphUrl`（无新配置）。

### A3. 前端
- `api/bilibiliVideo.ts`：`BilibiliVideoDto` + `searchBilibili(keyword, limit)`（axios，失败降级 `[]`）。
- `components/video/BilibiliVideoCards.vue`：卡片网格。
  - 封面 `<img referrerpolicy="no-referrer" loading="lazy">`（规避 hdslb 防盗链）。
  - 标题、UP 主、时长、播放量；右上角"外部资源·B站"角标。
  - 点击：默认新标签打开 `url`；可选内嵌 `//player.bilibili.com/player.html?bvid=...&autoplay=0`（iframe）。
  - 关键词来源由父组件传入。
- 落点①「看」页 `LecturePage.vue`：
  - 空状态（"本章暂无在线视频"）下方渲染 `BilibiliVideoCards`，关键词=`${chapterName} Python ${kcDisplayName(首个KC)}`。
  - 侧栏新增"B站精选讲解"区块（与"AI 图解"并列）。
  - 观看/点击计入 `reportMediaConsumed('video')`（沿用现有多模态学习闭环）。
- 落点②工坊 STORYBOARD：`StoryboardView.vue` / `ResourceDetailPanel.vue` 顶部新增"B站精选讲解"卡片，关键词=`Python ${resource.kcCodes.map(kcDisplayName)}`；"🎬 合成讲解视频"保留为次级可选项（文案改"（较慢）合成讲解视频"）。

### A4. 合规
- 卡片明确"外部资源·B站"，**不**进课件 grounding，不当课件页引用。
- 仅展示封面缩略图 + 跳转/官方 iframe；不下载视频；GPL 记入 `LICENSE-ART.md`。

---

## 3. 方案 B：真·按 agent 的实时虚拟工作室（增强现有）

### B1. Python（`graph/resource_graph.py` 的 `run_streaming`）
- **开场 plan 事件**：`run_streaming` 开头按 `request.type` 算出确定性工位清单并 `emit("plan", {...})`：
  - `evidence_fetcher` → 节点
  - 设计师节点（`_select_agent` 的 `AGENT_NAME`，附中文 label）
  - `grounding_critic`、`hallucination_detector`、`safety_critic`
  - 载荷：`{ type, nodes: [{ node, agent_label, kind }] }`（零 LLM 成本）。
- **每节点发 start + finish + phase**：新增 `_step_streaming(emit, trace, name, awaitable)` 与 `_record_sync_streaming(emit, ...)`：
  - await 前 `emit("trace", {node, phase:"start", status:"running", elapsed_ms:0})`；
  - await 后 `emit("trace", {node, phase:"finish", status, elapsed_ms, note})`。
  - `GraphTraceStep`（落库记录）**不变**；仅"线上事件"加 `phase` 超集字段，保持落库 schema 兼容。
- `result`/`error` 终态不变；同步审查间可加极小 `asyncio.sleep`（动画节奏，避免同 tick 闪过）。
- **隔离性**：仅改资源流；Tutor/Orchestrator/Profile 的 SSE 不动。

### B2. 后端
- `GraphSseConsumer` 逐事件透传（仅特判 `result`），新 `plan` 事件 + `phase` 字段**自动透传**，预期**零改动**；补 1 个透传单测兜底。

### B3. 前端
- `composables/useSseStream.ts`：加性扩展——`SseTraceEvent` 增可选 `phase?: 'start'|'finish'`；新增可选 `plan` 事件解析（`plan = ref<...>`）。不破坏 Tutor/Orchestrator（它们不发这俩，走默认分支）。
- `components/workshop/AgentOfficeScene.vue`：
  - 用 `plan` 事件动态生成工位（替代写死 4 工位 + `stageOf` 正则）；设计师工位显示**真实 agent label**。
  - 按 `phase` 驱动相位：`start`→该工位"工作中"，`finish`→"完成✓/重试↻/失败✗"，主管走位到当前工位。
  - 无 `plan` 事件时回退旧 4 工位逻辑（向后兼容/容错）。
  - 工位→sprite 映射复用 `BootScene.ts` 既有 NPC（npc_librarian/workshop/professor/trainer/coach/tutor_ai/sage/guard 等）；新增 sprite 才需登记 license（本次尽量复用，不新增素材）。

---

## 4. 验收标准（详细、可测）

### 方案 A — B 站卡片
- [ ] A-1 Python：`GET /agents/video/bilibili/search?keyword=for循环 Python` 返回 ≥1 条，结构含 `bvid/title/author/cover/duration_sec/play/url`；关键词无结果时返回 `items:[]` 且 HTTP 200。
- [ ] A-2 Python：网络/风控失败时返回结构化错误（非 500 堆栈、非空聊天）；`python -m pytest -q` 新增用例通过（mock 搜索）。
- [ ] A-3 后端：`GET /api/videos/bilibili?keyword=...` 鉴权后返回 `BilibiliVideoDto[]`；graph 不可用时返回标准 `ApiError(VIDEO_SEARCH_UNAVAILABLE)`；`mvn test` 新增控制器测试通过。
- [ ] A-4 后端：未携带 JWT → 401（与现有受保护端点一致）。
- [ ] A-5 前端：`LecturePage` 空状态显示 B 站卡片；封面正常加载（`referrerpolicy=no-referrer`，无 403 防盗链）；卡片有"外部资源·B站"角标。
- [ ] A-6 前端：点击卡片新标签打开正确 `https://www.bilibili.com/video/{bvid}`；（若启用 iframe）内嵌播放器可播。
- [ ] A-7 前端：工坊 STORYBOARD 顶部出现 B 站卡片；"合成讲解视频"仍可用（不回归）。
- [ ] A-8 前端：搜索失败/空时卡片区降级为占位文案，不报错、不白屏；`npm run typecheck`、`npm run lint`、相关 `npm run test` 通过。
- [ ] A-9 合规：`LICENSE-ART.md` 记录 `bilibili-api-python`(GPLv3) 与 B 站外链使用声明。

### 方案 B — 虚拟工作室
- [ ] B-1 Python：`/agents/resource/produce/stream` 首事件为 `plan`，`nodes` 含 evidence/designer/grounding/hallucination/safety 且 designer label 与 `request.type` 一致。
- [ ] B-2 Python：每节点先 `trace{phase:start}` 后 `trace{phase:finish}`；`finish` 的 `status∈{ok,retry,fail}` 与原 `GraphTraceStep` 一致；落库 `graph_trace` schema 不变。
- [ ] B-3 Python：STORYBOARD 跑通时 designer 节点名为 `StoryboardAgent.attempt0`；重试触发时出现 `attempt1`；`pytest -q` 通过。
- [ ] B-4 后端：新 `plan` 事件与含 `phase` 的 `trace` 事件原样透传到浏览器；`result` 仍落库；`mvn test` 通过。
- [ ] B-5 前端：`AgentOfficeScene` 按 `plan` 渲染真实工位数（STORYBOARD 应为 5 工位），设计师工位显示真实 agent 名（非"内容生成"泛称）。
- [ ] B-6 前端：相位动画正确——start 点亮"工作中"，finish 标 ✓/↻/✗，主管走位；日志栏逐条出现"节点·状态·耗时"。
- [ ] B-7 前端：无 `plan`（旧后端）时回退旧逻辑不崩；Tutor/Orchestrator 可视化无回归；`typecheck/lint/test` 通过。
- [ ] B-8 端到端：在工坊生成一个 STORYBOARD，工作室实时演完 5 工位 → 证据卡 → 交付，全程无白屏/无未捕获错误。

---

## 5. 风险与缓解
- **R1 GPL 传染**：`bilibili-api-python` GPLv3+ → 隔离在 pythontopia-graph，登记 license（用户已接受）。
- **R2 B 站反爬/风控**：库自带 WBI；仍可能限频 → 后端加短超时 + 失败降级卡片占位；可选 `secrets/bilibili.cookie`（gitignore）提升稳定性，参照 `crawl_chaoxing_videos.py` 的 cookie 读法。
- **R3 封面防盗链**：`referrerpolicy="no-referrer"`；必要时再加后端缩略图代理（需 permitAll 路径，参照 `/api/media/assets/**`）。
- **R4 SSE 双事件兼容**：仅改资源流；前端 `phase`/`plan` 加性扩展 + 旧逻辑回退，确保其他 SSE 不回归。
- **R5 演示链路**：B 站需公网；`dev.sh` 起 graph 服务后联通。无网时卡片降级，不阻塞主流程。

---

## 6. 实施顺序（建议）
1. A1 Python 搜索路由（+测试）→ 2. A2 后端代理（+测试）→ 3. A3 前端卡片 + LecturePage 落点 → 4. A 工坊落点。
5. B1 Python plan/phase（+测试）→ 6. B3 前端 useSseStream + AgentOfficeScene 增强（+测试）→ 7. B 端到端联调。
（A、B 相对独立，可并行；每步按 AGENTS.md「验证与交付」自测。）

---

## 7. 进度日志（实时更新）

> 格式：`[时间] 状态 — 做了什么 / 下一步`

- `[2026-06-04 21:xx] 探查完成` — 并发 3 个只读 subagent 摸清前端/Python/后端三端现状：确认 Marvis 式工作室已存在约 80%、SSE 全链路已具备、「视频生成」慢链路定位到 StoryboardCompositionService。
- `[2026-06-04 21:xx] 文档完成` — 产出本设计/计划文档（含详细验收标准）。
- `[2026-06-04 21:xx] 开工·并行` — 用户批准「A、B 并行」。派 2 个实现子代理各做一条纵切（文件互不重叠）：Track A=B站卡片（Python 路由+后端代理+前端卡片+2 落点），Track B=虚拟工作室（Python plan/phase+前端 useSseStream/AgentOfficeScene）。子代理只跑定向测试；全量 typecheck/lint/test + 集成验证由父代理统一收口。
- `[2026-06-04 22:00] 两 Track 实现完成` — Track A 定向测试：Python 5、后端 5、前端 9 全过；Track B 定向测试：Python 4、前端 2 全过。文件互不重叠，Tutor/Orchestrator SSE 未动。
- `[2026-06-04 22:06] 集成验证·全绿` — 父代理统一收口：① Python 全量 **273 passed**；② 后端 编译 OK + BilibiliVideoControllerTest **5 passed**；③ 前端 **typecheck ✓ / lint ✓ / test 1426 passed**。集成期仅 1 处类型错误（`AgentOfficeScene.designerSprite` 在 `noUncheckedIndexedAccess` 下索引返回 `string|undefined`），已加字面量兜底修复并复跑通过。
- `[2026-06-04 22:08] 代码层完成` — 详见下方「验证结果」。未验证（需联网/起栈）：真实 B 站检索联通、浏览器内封面防盗链、SSE 工作室端到端实跑。
- `[2026-06-04 22:14] 补强·VIDEO 意图` — 用户要求 B站视频像文生图一样「既可意图触发，也可主动调用」。主动调用（卡片）已在 LecturePage/工坊。新增**意图路由**那条路：Python `IntentRouterAgent` 加 `VIDEO` 意图（schema + 关键词兜底 + 提示词，VIDEO 非资源不进生成管线）；前端 `intent.ts`(IntentKind+isResourceIntent)、`useSlashCommand`(新增 `/视频` 命令 + 「看讲解」分组)、`WorkshopChat`(新增 `video` 气泡，按 `Python+KC名+重点` 检索关键词内联渲染 `BilibiliVideoCards`)；后端 `intent` 是 String 透传，仅更新 Javadoc。**全量复测全绿**：前端 typecheck/lint/**1427** 测、Python **276** 测、后端编译 + IntentControllerTest + BilibiliVideoControllerTest。**下一步：起 dev.sh 全栈 + 联网做一次端到端 + 视觉确认。**

### 8.1 VIDEO 意图（双路）验收
| 项 | 结果 |
|---|---|
| 意图触发（百晓工坊聊天）：「我想看 for 循环的视频」→ VIDEO → 卡片 | ✅ Python 意图测试（LLM + 关键词兜底）通过；前端 `video` 气泡渲染卡片 |
| 主动调用：`/视频` 斜杠命令 / LecturePage 空状态+侧栏 / 工坊 STORYBOARD | ✅ 斜杠命令测试通过；卡片组件测试通过 |
| 非生成隔离：VIDEO 不进工坊生成/不落库；6 类资源仍走 STORYBOARD 等 | ✅ `isResourceIntent` 排除 VIDEO；`RESOURCE_INTENTS` 仍为 6 |
| 关键词路由不串：「视频脚本/动画分镜」仍判 STORYBOARD | ✅ 关键词顺序：STORYBOARD 在 VIDEO 之前，测试覆盖 |

- `[2026-06-04 22:14] 补强·D（VIDEO 接入学习总管）` — orchestrator「看」阶段文案 + 提示词显式推荐 B站精选讲解视频（保持 watch=StoryboardAgent/panel=lecture，测试不破）；GuideOverlay 新增「想看视频讲解」建议。复测：Python **276**、前端 typecheck/lint/test 全绿。
- `[2026-06-04 22:18] 星露谷风格` — 审 tokens（已统一像素体系）；给 B站卡片补硬位移投影（2px2px0 + hover/active 压感 + reduced-motion），与 travel-bar/lecture-frame 一致。
- `[2026-06-04 22:22] A 起栈 + API 级 e2e（live）` — 离线镜像缓存起全栈：backend/graph/rag/vite 全 200，graph 走 dryrun。对真实运行栈做 API 端到端：
  - 注册 201 + 登录 JWT（后端+PG 通）。
  - 后端 `/api/videos/bilibili`(JWT) → 502 `VIDEO_SEARCH_UNAVAILABLE`（代理+错误链路对；无公网降级）；无 JWT → 401（A-4 实证）。
  - 后端 `/api/intent/route` → `intent:"VIDEO"`（VIDEO 意图 Java→Python 端到端）。
  - graph `/agents/video/bilibili/search` → 502 `No module named 'bilibili_api'`（路由+lazy import+结构化错误对；缺包因无网）。
  - graph orchestrator/turn → 看步标题「B站视频：Python for循环基础」（D 实证）。
  - graph + **后端** SSE `/agents/resource/produce/stream` & `/api/workshop/resources/stream` → `event:plan`(5 工位+真实 agent_label) + 每节点 `phase:start→finish`，**经后端原样透传**（Feature B 端到端）。
- `[2026-06-04 22:22] B 浏览器截图受限` — 浏览器子代理因账户计费被拦无法启动；公网不通，真实 B 站检索 + 浏览器可视化截图未取。已用「API 级 live e2e + 读码/ tokens 风格审计」替代。开发栈仍在运行（localhost:5173，停止：`./dev.sh --down`）。
- `[2026-06-04 22:30] ✅ 确保 B 站可联通` — 代理 7892 可达 B 站（`api.bilibili.com/nav` 200；直连 000）。
  - 代码：`routes/video.py` 新增 `_configure_bilibili_proxy()`——读 `PYT_BILI_PROXY`（回退 `HTTPS_PROXY`）调 `request_settings.set_proxy()`，幂等、版本容错（可提交的真实改进，不是临时 hack）。
  - 运行时：宿主装 `bilibili-api-python 17.4.1`，用代理 env 跑宿主版 graph 顶替容器版（容器内 `127.0.0.1` 触达不到宿主代理 7892）。后端 `PYT_GRAPH_URL=127.0.0.1:18091` 无需改。
  - **验真**：graph 直连真实搜索出 3 条真视频；**后端带 JWT `/api/videos/bilibili` → 200，3 张真实卡片**（bvid/清洗标题/UP/时长秒/播放量/**https 封面 hdslb**）。`test_video_route.py` 仍 5 passed。
  - 残留：dockerized graph 镜像未含该包且容器触达不到宿主代理——容器化需 `rebuild` 镜像 + 让容器经 `host.docker.internal`/网关访问代理；当前运行栈走宿主 graph，B 站实卡可用。
- `[2026-06-04 22:50] ✅ 浏览器深度使用（学生视角，真实点击+截图）` — 直连 cursor-ide-browser MCP（子代理被计费拦，直连可用），锁定标签模拟学生：
  - 世界/小镇：星露谷像素镇 + 20+ NPC（镇长 Py / AI 导师 / 工坊师傅 / 矿洞 Bug / 竞技场…）加载正常。
  - **学习总管（D）**：输入"我想系统学 for 循环"→ 看→学→练→测 4 步计划，**看步标题=「看视频：B站精选讲解 + 微课」**，多智能体协同链 ✓，已随学更新画像 learning_goal；新增「想看视频讲解」建议气泡可见。
  - **看课页 B站精选（真实）**：/lecture/1 侧栏"本章 B站精选"出 6 张真卡（真实封面 + 角标 + 时长 + UP + 播放量），`no-referrer` 封面无 403；主播放器放课程视频。
  - **工坊 VIDEO 意图（真实）**：聊天"我想看 for 循环的视频讲解"→「▶ B站视频」气泡 + 8 张高度相关真卡（第15课 Python For循环 / 7分钟学会Python循环 / 3小时入门 ▶623.2万 …）。
  - **智能体工作室（Feature B）**：生成讲义触发「智能体事务所」——5 个真实 agent 工位全 ✓（馆员/讲义设计师/审查官/校对官/安全员）+ 执行日志带耗时 + 交付证据确认卡（课本第 4/5/12 页 + 安全通过）。证据卡为交付前硬确认模态（合理设计）。
  - 题库：packId=43 · 39 题（章/KC/难度筛选）；图书馆：7 章/561 页/65 KC + 课件 RAG 检索。
  - 风格：全程星露谷牛皮纸像素风一致（含新增 B站卡片/视频气泡/工作室）。
  - 未单独点击深用：训练场判题、闯关塔 boss、矿洞、复盘、画像（均为既有功能，非本次改动）。

## 8. 验证结果（父代理收口）

| 验收项 | 结果 |
|---|---|
| A-1/A-2 Python 搜索路由（mock）+ 结构化错误 | ✅ pytest 通过（**真实联网未测**：无公网） |
| A-3 后端代理 + graph 不可用错误 | ✅ 控制器测试通过（日志见 VIDEO_SEARCH_UNAVAILABLE 正确返回） |
| A-4 未带 JWT → 401 | ⚠️ 由 `SecurityConfig.anyRequest().authenticated()`（未改）保证；WebMvcTest 关了 filter 未单测，**待 e2e** |
| A-5~A-8 前端卡片/落点/降级 | ✅ typecheck/lint/test 通过 + 卡片组件 9 测；**封面防盗链/点击跳转/iframe 实跑待浏览器** |
| A-9 LICENSE 登记 | ✅ `LICENSE-ART.md` 已加 bilibili-api-python(GPL) + 外链声明 |
| B-1~B-3 Python plan/phase/designer | ✅ 流式测试 4 测通过（首事件 plan、每节点 start→finish、STORYBOARD designer 名正确） |
| B-4 后端原样透传 | ✅ 逐事件透传（仅特判 result），新 plan/phase 自动通过；**端到端待实跑** |
| B-5~B-8 前端工作室动态工位/相位/端到端 | ✅ typecheck/lint/test 通过 + useSseStream 测；**工作室实时动画端到端待浏览器** |

**未验证 / 剩余风险**：①真实 B 站检索需公网 + `pip install bilibili-api-python`（本机无网未装，靠 lazy import + mock 测试保绿）；②SSE 工作室端到端、封面防盗链需起全栈 + 浏览器确认；③A-4 的 401 走配置保证，未单测。**不影响代码合入，但比赛演示前需联网 + dev.sh 起栈做一次端到端。**
