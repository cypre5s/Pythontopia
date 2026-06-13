# 并发构建主契约（2026-06-13）

本文件是 14 个并发 subagent 的**唯一权威契约**。每个 agent 启动后**先读本文件**，
只在自己「文件归属」域内写代码，引用跨域内容一律按本文件冻结的 schema/接口，
**不得**修改非自己归属的文件。集成（跨 agent 接线）由编排者在 Phase 2 统一完成。

## 0. 全员红线（违反即返工）

- 不伪造课件页码/章节/题目。学生上传内容只进**带标签的 context 通道**（如 `【你上传的《main.py》】…`），
  绝不混进 packId=43 课件 evidence / 引用。
- Pyodide 预览 ≠ 正式判题；掌握度唯一权威仍是服务端 submission 判题。
- 前端 TypeScript strict，业务层禁 `any`；Phaser/第三方动态对象隔离在 adapter 层。
- 后端 Java 21：类 PascalCase、方法变量 camelCase、常量 UPPER_SNAKE_CASE；表/字段 snake_case；
  新 API 必须有 DTO + 校验 + 统一错误 + 测试。
- Python：snake_case 函数/变量、PascalCase 类；Agent 故障 fail fast，不静默降级成自由聊天。
- 不复制任何商业像素游戏官方素材。
- 只做完成目标必须触达的文件，不顺手重构/格式化无关代码。

## 1. 迁移号分配（创建前确认仓库内尚不存在同号）

- `V162__workshop_upload.sql` → **B-backend-parse** 独占
- `V163__workshop_upload_chunk.sql` → **B-backend-vector** 独占
- `V164+` 暂不分配（C 剧场 MVP 不需要持久化，纯前端轨迹回放）

## 2. 文件归属矩阵（单工作树，互不重叠）

| Agent | 独占可写文件域 | 只读依赖 | 禁止触碰 |
|---|---|---|---|
| A-harness-budget | `services/pythontopia-graph/pythontopia_graph/tools/harness.py` + 其测试 | observability 接口(§5.6) | graph/*, pyproject |
| A-otel-foundation | `services/pythontopia-graph/pythontopia_graph/observability/**` + `services/pythontopia-graph/pyproject.toml` + 其测试 | — | tools/harness.py |
| A-resource-oscillation | `services/pythontopia-graph/pythontopia_graph/graph/resource_graph.py` + 其测试 | — | harness.py, orchestrator_graph.py |
| A-orchestrator-replan | `services/pythontopia-graph/pythontopia_graph/graph/orchestrator_graph.py`, `.../agents/dispatch.py` + 其测试 | — | resource_graph.py, harness.py |
| B-backend-parse | `backend/src/main/java/com/pythontopia/workshop/upload/**`（**根包**：controller/service/entity/repo/parser），`backend/.../db/migration/V162__workshop_upload.sql`，`backend/pom.xml` + 测试 | §3 DTO | workshop/upload/retrieval/** |
| B-backend-vector | `backend/src/main/java/com/pythontopia/workshop/upload/retrieval/**`（独立子包 + 独立 `UploadRetrievalController`），`V163__workshop_upload_chunk.sql` + 测试 | §3 表列, RagEmbeddingClient | pom.xml, upload 根包 |
| B-frontend | `frontend/src/components/workshop/ChatComposer.vue`, `WorkshopChat.vue`, 新 `frontend/src/api/upload.ts`, 新 `frontend/src/components/workshop/AttachmentPills.vue` + 测试 | §3 API | codeTheater/**, package.json* |
| C-trace-engine | `frontend/src/services/codeTheater/trace/**`（轨迹生成） + 测试/fixtures | `game/adapters/pyodide.ts`(只读), `types.ts`(§5) | game/scenes/**, mappers/** |
| C-stage-render | `frontend/src/game/scenes/CodeTheaterScene.ts`, `frontend/src/game/codeTheater/**`（渲染原语） + 测试 | `types.ts`(§5), `phaserGame.ts`(只读) | services/codeTheater/** |
| C-mappers | `frontend/src/services/codeTheater/mappers/**`（纯函数：事件→渲染指令） + 测试 | `types.ts`(§5) | trace/**, game/** |
| C-debug-theater | `frontend/src/components/bugmine/CodeTheaterReplay.vue` + `frontend/src/services/codeTheater/debug/**` + 测试 | `types.ts`(§5), DebugAgent API | bug-mine 现有核心逻辑 |
| C-quest-sample | `frontend/src/data/codeTheater/**`（样板任务 + 世界条件检查器纯逻辑） + 测试 | `types.ts`(§5) | scenes/** |
| C-curriculum | `frontend/src/data/codeTheater/animatableSubset.ts`, `docs/plans/2026-06-13-code-theater-curriculum.md` | KC 前置图(V125) | 上述 data 其他文件 |
| docs | `docs/plans/2026-06-13-file-parsing-design.md`, `docs/plans/2026-06-13-code-theater-blueprint.md` | 全仓只读 | 任何代码文件 |

\* `frontend/package.json`：仅 **B-frontend** 在确需新依赖时可改；其余 agent 一律不改。
`frontend/src/services/codeTheater/types.ts` 由**编排者预先创建**（见 §5），所有 C agent **只读引用、不重建**。

## 3. 文件解析（B）契约

### 3.1 表（B-backend-parse 建 V162；B-backend-vector 建 V163）
见仓库 §1 迁移；列名冻结：
- `workshop_upload(id, user_id, filename, ext, byte_size, extracted_text, char_count, line_count, status, error, created_at)`，status ∈ `PENDING|READY|FAILED`。
- `workshop_upload_chunk(id, upload_id, user_id, chunk_index, text, embedding vector(512), model, created_at)`，ivfflat 余弦索引。

### 3.2 REST（前缀 `/api`，JWT，`requireStudent` 同 ResourceWorkshopController）
- `POST /api/workshop/uploads`（multipart, 字段名 `file`）→ `UploadDto`（B-backend-parse）
- `GET /api/workshop/uploads` → `UploadDto[]`（B-backend-parse）
- `GET /api/workshop/uploads/{id}` → `UploadDetailDto`（含 `extractedText`）（B-backend-parse）
- `DELETE /api/workshop/uploads/{id}`（B-backend-parse）
- `POST /api/workshop/uploads/retrieve` body `{query:string, uploadIds?:number[], topK?:number}` → `{hits:[{uploadId, filename, chunkIndex, text, score}]}`（**B-backend-vector**, `UploadRetrievalController`）

### 3.3 DTO（camelCase JSON）
```
UploadDto       { id, filename, ext, byteSize, charCount, lineCount, status, error? }
UploadDetailDto { ...UploadDto, extractedText }
```

### 3.4 解析与限制（B-backend-parse）
- 允许扩展名：`.py .txt .md .csv .pdf .docx`（白名单，其余 415）。≤5 MB，否则 413。
- 文本类（py/txt/md/csv）UTF-8 直读；`.pdf` 用 Apache PDFBox；`.docx` 用 Apache POI（pom.xml 新增这两依赖，取最新稳定版，不要臆造版本号）。
- 解析失败 → status=FAILED + error，不抛 500。
- 向量化异步、fail-soft（同 `RagEmbeddingClient` 范式）：embed 不可用时 chunk 仍入库、embedding 留空，检索回退字面匹配。

### 3.5 前端接线（B-frontend，**不需要改后端 tutor 端点**）
- A 通路：提交带附件时，前端 `GET /uploads/{id}` 取 `extractedText`，截断到 ~6000 字（超出加「（已截断）」），以 `【你上传的《{filename}》】\n{text}` 形式注入**现有** `context` 字段（`WorkshopChat.runAsk` 的 `context`），再走现有 `/api/tutor/ask`。
- B 通路（scoped）：仅当本会话有上传 / 学生 `@` 引用某上传时，调 `/uploads/retrieve` 取 hits，同样以带标签文本注入 `context`。默认不每问都检索。
- `ComposerSubmitPayload` 增加 `attachments: { id:number; filename:string; ext:string; charCount:number }[]`（默认空数组，向后兼容）。
- 上传 UX 仿 Claude Code 网页版：📎 按钮 + 拖拽覆盖层 + 粘贴；每个文件一枚可删除 pill（类型图标/文件名/大小、上传中 spinner→就绪显示行数/字数、失败显示错误+重试）；多文件；发送后 pill 渲染在用户气泡上方（`ChatMessage` 增加 `attachments` 字段）。

## 4. （保留段落，无额外内容）

## 5. 代码剧场（C）共享 TS 契约

> 编排者已创建 `frontend/src/services/codeTheater/types.ts`，内容即下方权威类型。
> C-trace-engine 产出 `Trace`；C-mappers 把 `TraceEvent` 纯函数映射为 `RenderCommand[]`；
> C-stage-render 消费 `RenderCommand[]` 在 Phaser 演出。**全部 import 自该 types.ts，禁止各自重定义。**

### 5.1 事件类型 `TraceEvent.kind`
`line | assign | loop_iter | branch | call | return | list_op | dict_op | output | input | exception`

### 5.2 轨迹 `Trace`
```ts
interface Trace {
  version: 1;
  source: string;        // 学生原始代码
  events: TraceEvent[];
  stdout: string;
  truncated: boolean;    // 触发步数预算被截断
  stepCount: number;     // = events.length
}
```
每个 `TraceEvent` 必含 `step:number`（自增）与 `line:number`（1 基）。各 kind 额外字段见 types.ts。

### 5.3 渲染指令 `RenderCommand`
```ts
interface RenderCommand {
  step: number;          // 对齐 TraceEvent.step
  action: RenderAction;  // 见 types.ts 联合
  payload: Record<string, string | number | boolean>;
  durationMs: number;
}
```

### 5.4 映射器签名（C-mappers）
```ts
type TraceMapper = (event: TraceEvent, ctx: MapContext) => RenderCommand[];
// 注册表：Partial<Record<TraceEvent['kind'], TraceMapper>>，缺省 kind 回退 move_to_line。
```

### 5.5 步数预算（呼应 harness 预算理论）
轨迹生成默认上限 `MAX_TRACE_STEPS = 2000`；超出置 `truncated=true` 并停止记录（防死循环把内存/前端打爆）。死循环本身要可观测（截断后由 stage 演「原地无限踱步」提示）。

### 5.6 observability 模块接口（A-otel-foundation 实现，A-harness 可选 import）
```python
# services/pythontopia-graph/pythontopia_graph/observability/otel.py
def init_tracing() -> None: ...                      # 幂等；无 opentelemetry 依赖时 no-op
def traced_tool(name: str): ...                      # 上下文管理器/装饰器，记录 execute_tool span
def record_token_usage(input_tokens: int, output_tokens: int) -> None: ...
```
缺 `opentelemetry` 包时全部降级为 no-op，**不得**让导入失败影响主链路。

## 6. 自测命令（每个 agent 必须在自己范围内跑绿）

- 前端：`cd frontend && npm run typecheck && npm run test -- <你的测试文件>`
- 后端：`cd backend && mvn -q -Dtest=<YourTestClass> test`
- Python graph：`cd services/pythontopia-graph && python -m pytest -q <你的测试文件>`

无法整体编译时（依赖他人未落地的接线），至少保证**自己模块的单元测试**（用 fixtures/mock）通过，并在返回里如实说明哪些集成点待编排者 Phase 2 接线。

## 7. 每个 agent 完成时必须返回
1. 改/建的文件清单（绝对路径）
2. 自测命令与结果（贴关键输出）
3. 留给 Phase 2 的集成点（如「需在 X 处调用 Y」）
4. 任何偏离本契约的地方及原因
