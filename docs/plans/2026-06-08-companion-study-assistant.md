# 随身助教 Companion 实施计划（多智能体 · 多模态 · 日常答疑）

> **For Claude:** REQUIRED SUB-SKILL: 实施时用 executing-plans / subagent-driven-development，逐任务推进，每步先写测试再实现，频繁提交。

**Goal:** 把工坊里的多智能体答疑「请出来」，做成一个可在任何页面呼出的极简「随身助教」（一个像素陪伴 + 一条 ask 栏），并在其后端逐步叠加工具调用、辩论复核、主动陪伴与跨场景能力。

**Architecture:** 前端只新增「一个全局组件 + 一个 store + 一个上下文 composable」，其余全部复用既有 `useSseStream('/api/tutor/ask')`、`MarkdownView`、证据卡、Marvis 流水线与立绘资产；所有「深度」（工具验证、辩论、主动）都长在后端 / Python graph，UI 不膨胀。脊柱是「随身助教 Companion」，H–M 六个方向都挂在它后面。

**Tech Stack:** 前端 Vue 3 + TS strict + Pinia + Vitest；SSE via `useSseStream`；后端 Spring Boot（Java 21）；智能体 FastAPI + LangGraph（`services/pythontopia-graph`）；多模态复用讯飞图片理解（已接）/ 浏览器 Web Speech API（ASR MVP）。

**硬约束（来自用户）：** 前端不要太复杂、少组件少文字、必须融入游戏主线（看→学→练→测）。深度往后端长，前端保持「一个陪伴 + 一条 ask 栏」。

---

## 现状基线（实施前必读，避免重复造轮子）

- **ASK 已具备**：`POST /api/tutor/ask`（SSE），请求体 `TutorAskRequest` 已含 `question / kcCodes / context / images[]（看图答疑，后端已接讯飞图片理解）/ style / conversationId`；流事件 `token / trace / result / error`，`image_understanding` trace 带 `index/total/chars`。
- **总管已具备**：`POST /api/orchestrator/turn/stream`（LangGraph：理解→澄清/拆解看学练测→分派→失败自动插矿洞→抽画像→流式作答，含 negotiate 调难度、多轮记忆）。
- **答疑 Agent 已具备**：RAG 证据 `[页X]`、无证据标「基于通识」、防泄答、按认知风格因材施教、按需 mermaid。
- **多模态已具备**：TTS、文生图、合成视频、B站精选；**缺**：语音输入（ASR）、答疑时真跑代码验证、辩论复核、主动冒泡、全局/跨场景入口。
- **可复用前端件**：`useSseStream.ts`、`MarkdownView.vue`、`EvidenceTag.vue`、`components/tutor/TutorPanel.vue`（答疑面板逻辑）、`CoursewareEvidence.vue`、`MarvisPanel.vue`（立绘 + 流水线）、`stores/workshopActivity.ts`。
- **全局挂载点**：`frontend/src/app/AppLayout.vue`。

---

## 分期总览与总验收

| 阶段 | 内容 | 方向 | 交付即验收（顶层） |
| --- | --- | --- | --- |
| Phase 0 | 全局 CompanionDock 脊柱（接现有 ASK + 上下文 + 主线动作芯片） | H、M(基线) | 任意页面可呼出助教、提问得流式答案+证据、动作芯片跳主线；typecheck/lint/单测绿 |
| Phase 1 | 多模态输入：语音(Web Speech) + 拍照/粘贴(images[]) | I | ask 栏可语音转写、可贴 1–3 图并得到引用图片的答案 |
| Phase 2 | 工具型智能体：答疑真跑代码验证 + 按需再检索 | J（含旧 C） | 「这段代码为什么错」会出现 run_python 步骤且答案反映真实输出；pytest 绿 |
| Phase 3 | 辩论复核 + 主动陪伴 + 教师 copilot | K、L、M | 难题出现 debate 步骤；到期/卡点冒泡提醒；教师页可呼出 copilot |

**全局完成定义（DoD）**：四端不受影响；`npm run typecheck`、相关 `vitest`、`python -m pytest -q`、`mvn -q -DskipTests compile` 全绿；无 `any` 扩散；无新增独立仪表盘；助教在 ≥3 个页面可用且上下文正确。

---

## Phase 0：CompanionDock 脊柱（H + M 基线）

**目标**：一个全局浮动像素助教 + 折叠 ask 栏；提问复用 `/api/tutor/ask`；自动带当前 KC/页面上下文；答案下方给「去练 / 看课件 / 进矿洞」动作芯片。**只新增 1 组件 + 1 store + 1 composable。**

### Task 0.1：Companion 上下文 composable

**Files:**
- Create: `frontend/src/composables/useCompanionContext.ts`
- Test: `frontend/tests/composables/useCompanionContext.spec.ts`

**Step 1：写失败测试**

```ts
// useCompanionContext.spec.ts
import { describe, it, expect, beforeEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useCompanionContext } from '@/composables/useCompanionContext';
import { useLearningStore } from '@stores/learning';

describe('useCompanionContext', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('derives kcCodes + surface from active field KC and route', () => {
    const learning = useLearningStore();
    learning.field.activeKcCode = 'PY43-KC-016';
    const ctx = useCompanionContext(() => ({ path: '/training', query: { problemId: '5' } }));
    expect(ctx.value.kcCodes).toContain('PY43-KC-016');
    expect(ctx.value.surface).toBe('training');
    expect(ctx.value.context).toContain('training');
  });

  it('falls back to empty kcCodes when nothing active', () => {
    const ctx = useCompanionContext(() => ({ path: '/world', query: {} }));
    expect(ctx.value.kcCodes).toEqual([]);
    expect(ctx.value.surface).toBe('world');
  });
});
```

**Step 2：跑测试确认失败** — `npx vitest run tests/composables/useCompanionContext.spec.ts`（Expected: FAIL，模块不存在）。

**Step 3：最小实现**

```ts
// useCompanionContext.ts
import { computed, type ComputedRef } from 'vue';
import { useLearningStore } from '@stores/learning';

export interface CompanionContext {
  kcCodes: string[];
  surface: string;     // world / training / library / bug-mine / workshop / arena / classroom ...
  context: string;     // 给后端 TutorAskRequest.context 的人话上下文摘要
}

type RouteLike = { path: string; query: Record<string, unknown> };

export function useCompanionContext(
  routeGetter: () => RouteLike,
): ComputedRef<CompanionContext> {
  const learning = useLearningStore();
  return computed(() => {
    const route = routeGetter();
    const surface = route.path.replace(/^\//, '').split('/')[0] || 'world';
    const activeKc = learning.field.activeKcCode;
    const kcCodes = activeKc ? [activeKc] : [];
    const context = `当前在「${surface}」页面${activeKc ? `，正在学习知识点 ${activeKc}` : ''}`;
    return { kcCodes, surface, context };
  });
}
```

**Step 4：跑测试确认通过** — `npx vitest run tests/composables/useCompanionContext.spec.ts`（Expected: PASS）。
**Step 5：提交** — `git add frontend/src/composables/useCompanionContext.ts frontend/tests/composables/useCompanionContext.spec.ts && git commit -m "feat(companion): add useCompanionContext composable"`

**验收标准 0.1**：composable 在 `/training` 且有 activeKc 时返回该 KC + surface=training；无活跃时返回空 kcCodes；单测通过。

---

### Task 0.2：Companion store（状态 + ASK SSE 接线）

**Files:**
- Create: `frontend/src/stores/companion.ts`
- Test: `frontend/tests/stores/companion.spec.ts`

**Step 1：写失败测试**（mock `useSseStream`，断言 ask 把 question/kcCodes/context 传给 `/api/tutor/ask`，并把 token 累积进当前消息）

```ts
// companion.spec.ts （要点）
// - vi.mock('@/composables/useSseStream', ...) 返回可控的 tokens/result/status
// - useCompanionStore().ask('input', { kcCodes:['K1'], surface:'training', context:'c' })
// - expect 最近一次 start 调用 body 含 { question:'input', kcCodes:['K1'], context:'c' }
// - 模拟 token 推送后 messages 最后一条 role==='assistant' 且 content 累积
```

**Step 2：跑测试确认失败** — `npx vitest run tests/stores/companion.spec.ts`

**Step 3：最小实现**（store 持有 `open`、`messages`、`busy`、`lastEvidencePageIds`；`ask()` 调 `useSseStream('/api/tutor/ask').start({...})`，watch tokens→更新末条消息，result→落定 evidence；复用 `TutorAskRequest` 形状，不重复定义类型）

```ts
// companion.ts （骨架）
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useSseStream } from '@/composables/useSseStream';
import type { EvidenceCitation } from '@/api/tutor';

export interface CompanionMessage {
  role: 'user' | 'assistant';
  content: string;
  evidence?: EvidenceCitation[];
  kcCodes?: string[];
}
interface AskCtx { kcCodes: string[]; surface: string; context: string }

export const useCompanionStore = defineStore('companion', () => {
  const open = ref(false);
  const busy = ref(false);
  const messages = ref<CompanionMessage[]>([]);
  const sse = useSseStream<{ answerMd: string; evidence: EvidenceCitation[]; evidencePageIds: number[] }>(
    '/api/tutor/ask',
  );
  // watch(sse.tokens) → 累积到末条 assistant；watch(sse.result) → 落 evidence；watch(sse.status) → busy
  async function ask(input: string, ctx: AskCtx): Promise<void> {
    messages.value.push({ role: 'user', content: input });
    messages.value.push({ role: 'assistant', content: '' });
    busy.value = true;
    await sse.start({ question: input, kcCodes: ctx.kcCodes, context: ctx.context });
  }
  function toggle(): void { open.value = !open.value; }
  return { open, busy, messages, ask, toggle, sse };
});
```

**Step 4：跑测试确认通过** — `npx vitest run tests/stores/companion.spec.ts`
**Step 5：提交** — `git commit -m "feat(companion): add companion store wired to /api/tutor/ask"`

**验收标准 0.2**：`ask()` 把 question+kcCodes+context 正确发给 `/api/tutor/ask`；token 流式累积进末条 assistant 消息；result 落定证据；busy 随 status 变化；单测通过。

---

### Task 0.3：CompanionDock 组件（浮动小人 + ask 栏 + 迷你对话）

**Files:**
- Create: `frontend/src/components/companion/CompanionDock.vue`
- Test: `frontend/tests/components/CompanionDock.spec.ts`

**要求（少组件少文字，复用为先）：**
- 折叠态：右下角一个像素小人按钮（复用 `MarvisPanel` 的 LPC sprite，`npc_tutor_ai`），`data-testid="companion-fab"`。
- 展开态：一块紧凑面板：消息列表（`MarkdownView` 渲染答案，证据用 `EvidenceTag`/页码芯片）+ 一行输入 + 发送；`data-testid="companion-panel"`、输入 `companion-input`、发送 `companion-send`。
- 答案下方动作芯片：`去练`(→`/training?problemId=` 或 `/training`)、`看课件`(→`/library?pageId=` 用首个 evidencePageId)、`进矿洞`(→`/bug-mine`)，`data-testid="companion-action-*"`，依据当前 ctx/证据条件渲染。
- 上下文：用 `useCompanionContext(() => route)` 拼 ctx 传给 `store.ask`。

**Step 1：写失败测试**（mount，点 fab 展开，输入并发送 → 调 `store.ask`；assistant 文本渲染；动作芯片存在且点击 `router.push` 正确）。
**Step 2：跑测试确认失败** — `npx vitest run tests/components/CompanionDock.spec.ts`
**Step 3：实现组件**（fixed 定位，z-index 用 `--pyt-z-*`；reduced-motion 友好；窄屏自适应）。
**Step 4：跑测试确认通过**。
**Step 5：提交** — `git commit -m "feat(companion): add CompanionDock component"`

**验收标准 0.3**：点 fab 展开/收起；发送问题调 store.ask 且渲染流式答案 + 证据页芯片；动作芯片按上下文出现且跳转正确；`prefers-reduced-motion` 下无动画；组件单测通过。

---

### Task 0.4：全局挂载 + 主线集成

**Files:**
- Modify: `frontend/src/app/AppLayout.vue`（在 `</main>` 后挂 `<CompanionDock />`，全局可用 = M 基线）
- Test: `frontend/tests/components/AppLayout.spec.ts`（若不存在则新建，仅断言渲染了 `companion-fab`）

**Step 1–2：** 写/跑断言「AppLayout 渲染 companion-fab」的测试（先失败）。
**Step 3：** 挂载组件（fullscreen 世界页是否显示由 `route.meta` 控制：默认显示；如遮挡像素操作，提供 `route.meta.hideCompanion` 开关）。
**Step 4–5：** 测试通过并提交 — `git commit -m "feat(companion): mount CompanionDock globally in AppLayout"`

**验收标准 0.4（Phase 0 总验收）：**
1. 在 `/world`、`/training`、`/library`、`/bug-mine` 至少 4 个页面看到右下角助教小人。
2. 在 `/training` 提一个问题 → 流式答案出现，证据页码芯片可点跳 `/library?pageId=`。
3. 在有 activeKc 的页面提问，请求体 `kcCodes` 非空（可在测试或 network 断言）。
4. 动作芯片「进矿洞 / 去练 / 看课件」跳转正确。
5. `npm run typecheck` 通过；`npx eslint src/components/companion/** src/stores/companion.ts src/composables/useCompanionContext.ts --ext .ts,.vue` 0 error；新增单测全绿。
6. 既有页面无布局回归（手验 world/workshop 正常）。

---

## Phase 1：多模态输入（I）

**目标**：ask 栏支持 🎤语音 与 📷拍照/粘贴。后端 `images[]` 已就绪（`TutorAskRequest.images`，讯飞图片理解已接），重点在前端采集。**前置**：Phase 0 完成。

### Task 1.1：语音输入 composable（Web Speech API，零后端 MVP）

**Files:**
- Create: `frontend/src/composables/useVoiceInput.ts`
- Test: `frontend/tests/composables/useVoiceInput.spec.ts`

**Step 1：写失败测试**

```ts
// useVoiceInput.spec.ts
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useVoiceInput } from '@/composables/useVoiceInput';

class FakeRecognition {
  lang = '';
  continuous = false;
  interimResults = false;
  onresult: ((e: unknown) => void) | null = null;
  onend: (() => void) | null = null;
  start = vi.fn(() => { /* 由测试手动触发 onresult */ });
  stop = vi.fn(() => { this.onend?.(); });
}

describe('useVoiceInput', () => {
  beforeEach(() => {
    // @ts-expect-error 注入浏览器 API
    globalThis.window.webkitSpeechRecognition = FakeRecognition;
  });

  it('reports supported when SpeechRecognition exists', () => {
    const v = useVoiceInput();
    expect(v.supported.value).toBe(true);
  });

  it('updates transcript on result and toggles listening', () => {
    const v = useVoiceInput();
    v.start();
    expect(v.listening.value).toBe(true);
    // @ts-expect-error 取内部实例触发
    v._recognition.onresult({ results: [[{ transcript: '什么是列表推导' }]] });
    expect(v.transcript.value).toBe('什么是列表推导');
    v.stop();
    expect(v.listening.value).toBe(false);
  });

  it('reports unsupported gracefully', () => {
    // @ts-expect-error 移除 API
    delete globalThis.window.webkitSpeechRecognition;
    // @ts-expect-error
    delete globalThis.window.SpeechRecognition;
    const v = useVoiceInput();
    expect(v.supported.value).toBe(false);
    expect(() => v.start()).not.toThrow();
  });
});
```

**Step 2：跑测试确认失败** — `npx vitest run tests/composables/useVoiceInput.spec.ts`（Expected: FAIL，模块不存在）。

**Step 3：最小实现**

```ts
// useVoiceInput.ts
import { ref, type Ref } from 'vue';

interface SpeechRecognitionLike {
  lang: string; continuous: boolean; interimResults: boolean;
  onresult: ((e: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onend: (() => void) | null;
  start(): void; stop(): void;
}

export interface VoiceInput {
  supported: Ref<boolean>;
  listening: Ref<boolean>;
  transcript: Ref<string>;
  start: () => void;
  stop: () => void;
  _recognition: SpeechRecognitionLike | null; // 仅测试可见
}

export function useVoiceInput(): VoiceInput {
  const Ctor = (window as unknown as {
    SpeechRecognition?: new () => SpeechRecognitionLike;
    webkitSpeechRecognition?: new () => SpeechRecognitionLike;
  });
  const Recognition = Ctor.SpeechRecognition ?? Ctor.webkitSpeechRecognition ?? null;
  const supported = ref(Recognition !== null);
  const listening = ref(false);
  const transcript = ref('');
  let rec: SpeechRecognitionLike | null = null;

  function start(): void {
    if (!Recognition) return;
    rec = new Recognition();
    rec.lang = 'zh-CN';
    rec.interimResults = false;
    rec.onresult = (e) => { transcript.value = e.results[0]?.[0]?.transcript ?? ''; };
    rec.onend = () => { listening.value = false; };
    listening.value = true;
    rec.start();
    api._recognition = rec;
  }
  function stop(): void { rec?.stop(); listening.value = false; }

  const api: VoiceInput = { supported, listening, transcript, start, stop, _recognition: null };
  return api;
}
```

**Step 4：跑测试确认通过** — `npx vitest run tests/composables/useVoiceInput.spec.ts`（Expected: PASS）。
**Step 5：提交** — `git commit -m "feat(companion): add useVoiceInput (Web Speech ASR MVP)"`

**验收标准 1.1**：支持的浏览器返回 supported=true、start→onresult→transcript 更新；不支持时 supported=false 且 start 不抛错；单测通过。

---

### Task 1.2：CompanionDock 接入麦克风

**Files:**
- Modify: `frontend/src/components/companion/CompanionDock.vue`（加 `data-testid="companion-mic"` 按钮，按住/点按 `start`，结果写入输入框 `companion-input`；`supported=false` 时不渲染）
- Test: 扩展 `frontend/tests/components/CompanionDock.spec.ts`

**Step 1：写失败测试** — mount，mock `useVoiceInput` 返回 supported=true，点 `companion-mic` 触发 `start`；将 transcript 注入输入框值。
**Step 2：跑失败** — `npx vitest run tests/components/CompanionDock.spec.ts`
**Step 3：实现** — 引入 `useVoiceInput`，`watch(transcript)` → 写入输入；`v-if="voice.supported"` 控制按钮。
**Step 4：跑绿** **Step 5：提交** — `git commit -m "feat(companion): wire mic button into ask bar"`

**验收标准 1.2**：点麦克风→（说话/测试触发）→文字进输入框可直接发送；不支持浏览器隐藏按钮且无报错；单测通过。

---

### Task 1.3：拍照 / 粘贴 / 选图（images[]，后端已支持）

**Files:**
- Modify: `frontend/src/stores/companion.ts`（`ask(input, ctx, images?)` 透传 `images` 给 `/api/tutor/ask`）
- Modify: `frontend/src/components/companion/CompanionDock.vue`（`data-testid="companion-attach"` file input + 监听 `paste` 取剪贴板图片；最多 3 张；转 base64 dataURL；缩略图 `companion-thumb-*` 可删）
- Test: 扩展 `companion.spec.ts` 与 `CompanionDock.spec.ts`

**Step 1：写失败测试（store）**

```ts
it('passes images[] to /api/tutor/ask and caps at 3', async () => {
  const store = useCompanionStore();
  const imgs = ['data:image/png;base64,a', 'data:...b', 'data:...c', 'data:...d'];
  await store.ask('看这题', { kcCodes: [], surface: 'world', context: '' }, imgs);
  const body = lastStartBody(); // 测试夹具读取最近一次 sse.start 入参
  expect(body.images).toHaveLength(3); // 超过 3 张被截断
  expect(body.question).toBe('看这题');
});
```

**Step 2：跑失败** — `npx vitest run tests/stores/companion.spec.ts`
**Step 3：实现** — `ask` 增加可选 `images`，`images: images?.slice(0, 3)`；组件侧 file/paste → dataURL，存 `pendingImages.value`，发送时传入、清空。
**Step 4：跑绿（store + 组件）** **Step 5：提交** — `git commit -m "feat(companion): photo/paste image input for look-and-ask"`

**验收标准 1.3**：贴/选 1–3 张图 → 缩略图渲染可删 → 发送 body 含 `images` 且 ≤3；流里出现 `image_understanding` trace（index/total）→ 答案引用图片内容；单测通过。

---

### Task 1.4（可选）：服务端 ASR 增强（讯飞）
- **Files:** Modify backend `MediaAssetController`（加 `POST /api/media/asr`：音频 → 文本）；Create `frontend/src/api/media.ts::transcribeAudio()`；CompanionDock 在 Web Speech 不可用时回退录音上传。
- **步骤**：Java 单测 + 前端 `media.spec.ts`；TDD 同上。
- **验收**：不支持 Web Speech 的环境可录音→转写→提问；有 Web Speech 时优先本地、无网络依赖。
- **备注**：可选，不阻塞 Phase 1 MVP。

**Phase 1 总验收**：可语音问、可贴图问；`npm run typecheck`、`npx eslint` 0 error、相关 `vitest` 全绿；后端无需改动（除可选 Task 1.4）。

---

## Phase 2：工具型智能体（J，含旧 C「代码可运行」）

**目标**：答疑不再「一次性生成」，而是能**真跑代码验证**后再定稿；前端只多显示一个 `code_runner` 步骤 + 「已验证」角标。**前置**：Phase 0 完成（Phase 1 可并行）。

### Task 2.1：code_runner 隔离执行节点（Python graph）

**Files:**
- Create: `services/pythontopia-graph/pythontopia_graph/nodes/code_runner.py`
- Test: `services/pythontopia-graph/tests/test_code_runner.py`

**Step 1：写失败测试**

```python
# test_code_runner.py
import pytest
from pythontopia_graph.nodes.code_runner import run_python, CodeRunResult

def test_runs_and_captures_stdout():
    r = run_python("print(1 + 1)")
    assert isinstance(r, CodeRunResult)
    assert r.stdout.strip() == "2"
    assert r.exit_code == 0
    assert r.timed_out is False

def test_captures_stderr_on_error():
    r = run_python("raise ValueError('boom')")
    assert r.exit_code != 0
    assert "ValueError" in r.stderr

def test_times_out_long_loop():
    r = run_python("while True: pass", timeout_s=1)
    assert r.timed_out is True

def test_truncates_huge_output():
    r = run_python("print('x' * 100000)", max_output=2000)
    assert len(r.stdout) <= 2000 + 16  # 含截断标记
    assert r.truncated is True
```

**Step 2：跑测试确认失败** — `cd services/pythontopia-graph && python -m pytest -q tests/test_code_runner.py`（Expected: FAIL，模块不存在）。

**Step 3：最小实现**（受控子进程：超时、stdin 关闭、输出截断；隔离策略对齐 backend 判题沙箱 `SubprocessPythonSandbox`——禁网络/限资源由部署层 seccomp/容器保证，节点层先做超时+截断+无 shell）

```python
# code_runner.py
from __future__ import annotations
import subprocess
import sys
from dataclasses import dataclass

_TRUNC = "\n…[输出已截断]"

@dataclass
class CodeRunResult:
    stdout: str
    stderr: str
    exit_code: int
    timed_out: bool
    truncated: bool

def _clip(text: str, limit: int) -> tuple[str, bool]:
    if len(text) <= limit:
        return text, False
    return text[:limit] + _TRUNC, True

def run_python(code: str, *, timeout_s: float = 5.0, max_output: int = 8000) -> CodeRunResult:
    """在受限子进程里执行片段；仅用于答疑「验证」，绝不执行不可信网络/文件操作。

    隔离：无 shell、关闭 stdin、超时 kill、输出截断。生产部署须在容器/seccomp 下运行
    （与 backend SubprocessPythonSandbox 同级隔离）。
    """
    try:
        proc = subprocess.run(
            [sys.executable, "-I", "-S", "-c", code],
            capture_output=True, text=True, timeout=timeout_s,
            stdin=subprocess.DEVNULL,
        )
        out, t1 = _clip(proc.stdout, max_output)
        err, t2 = _clip(proc.stderr, max_output)
        return CodeRunResult(out, err, proc.returncode, False, t1 or t2)
    except subprocess.TimeoutExpired as e:
        out, t1 = _clip(e.stdout or "", max_output) if isinstance(e.stdout, str) else ("", False)
        return CodeRunResult(out, "执行超时", -1, True, t1)
```

**Step 4：跑测试确认通过** — `python -m pytest -q tests/test_code_runner.py`（Expected: PASS）。
**Step 5：提交** — `git commit -m "feat(graph): add isolated code_runner node for answer verification"`

**验收标准 2.1**：四个用例通过；超时被 kill、输出截断、stderr 捕获；节点不开 shell、关 stdin。

---

### Task 2.2：把验证回路接入答疑（TutorAgent）

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/agents/tutor_agent.py`（加 `should_verify(question)` 启发式 + `verify_and_answer()`：检测到「为什么错/输出不对/报错」且消息含代码块时，先 `run_python`，把真实 stdout/stderr 作为附加 system 证据再作答）
- Test: `services/pythontopia-graph/tests/test_tutor_verify.py`

**Step 1：写失败测试**

```python
# test_tutor_verify.py（要点）
# - should_verify("这段代码为什么输出不对\n```python\nprint(2*3)\n```") is True
# - should_verify("什么是列表推导") is False
# - 注入 fake provider + 监视：含代码的提问会触发 run_python，且重答 messages 含真实输出
```

**Step 2：跑失败** — `python -m pytest -q tests/test_tutor_verify.py`
**Step 3：实现**（`should_verify` 正则：`(为什么|输出不对|报错|error|不对|wrong)` 且含 ```` ```python ```` 围栏 → 抽取代码 → `run_python` → 把 `实际运行：stdout=... stderr=...` 加入 messages 再 `provider.chat`；仍受 SYSTEM_PROMPT 防泄答约束，只给引导不给完整答案）。
**Step 4：跑绿** **Step 5：提交** — `git commit -m "feat(graph): tutor verifies code by running it before answering"`

**验收标准 2.2**：含代码的「为什么错」问题会调用 `run_python` 且答案反映真实输出；纯概念问题不触发运行；防泄答不破坏；pytest 绿。

---

### Task 2.3：SSE 暴露 code_runner trace + 前端「已验证」角标

**Files:**
- Modify: 答疑流装配处 emit `code_runner` trace（`phase: start/finish`，对齐 `SseTraceEvent`）
- Verify: backend Java 代理（`GraphSseConsumer` / orchestrator 透传）原样转发该 trace（若未透传则补）
- Modify: `frontend/src/components/companion/CompanionDock.vue`（`traces` 含 `code_runner` 时显示「正在运行代码验证…」；`finish` 后在该条 assistant 消息上加 `data-testid="companion-verified"` 角标「已验证」）
- Test: `tests/components/CompanionDock.spec.ts`（注入含 `code_runner` 的 traces → 断言角标渲染）

**Step 1–5：** 同 TDD 流程（写断言→失败→实现→绿→提交 `git commit -m "feat(companion): show code-verified badge from code_runner trace"`）。

**验收标准 2.3**：问「这段代码为什么输出不对：<片段>」→ 流水线出现运行步骤 → 答案下方出现「已验证」角标；`python -m pytest -q`、`mvn -q -DskipTests compile`、`npx vitest run tests/components/CompanionDock.spec.ts` 全绿。

---

### Task 2.4（收口旧 C）：工坊 CodeLab / Practice 内嵌运行
- **Files:** Modify `frontend/src/components/workshop/CodeLabView.vue` 与 `PracticeList.vue`：每个 walkthrough/problem 加「▶ 运行」（复用前端 Pyodide runner，见 `learning.runPyodidePreview` 同款）；输出折叠展示；Practice 通过→ `notifyMainQuest*` 推进「练」、失败→「进矿洞」芯片。Test 扩展对应 spec。
- **步骤**：TDD —（1）测点运行后渲染输出区；（2）测 Practice 通过触发主线推进事件、失败渲染进矿洞芯片；（3）实现；（4）绿；（5）提交。
- **验收**：卡片内可运行、结果回写主线/矿洞；不调用服务端判题冒充正式判题（沿用「Pyodide 预览 ≠ 正式判题」红线）；单测绿。

**Phase 2 总验收**：关键答疑有真实运行证据；`python -m pytest -q`（graph）、`mvn -q -DskipTests compile`、`npm run typecheck` + 相关 `vitest` 全绿；前端仅多「已验证」角标 + 卡片运行区，无新面板。

---

## Phase 3：辩论复核（K）+ 主动陪伴（L）+ 教师 copilot（M 扩展）

**前置**：Phase 0 完成（2/3 可并行）。

### Task 3.1：辩论 / 复核子图（K）

**Files:**
- Create: `services/pythontopia-graph/pythontopia_graph/graph/debate_graph.py`（Solver→Critic→Referee 的 StateGraph）
- Modify: `services/pythontopia-graph/.../agents/tutor_agent.py` 或答疑装配处（按 `confidence < 阈值` 或问题判定为「难」时路由进 debate；否则走普通答疑）
- Test: `services/pythontopia-graph/tests/test_debate_graph.py`

**Step 1：写失败测试**

```python
# test_debate_graph.py（要点）
# - solver 产草答 → critic 提出问题 → referee 给出复核后答案
# - 测：debate.run(question, evidence) 返回 {answer, rounds:[solver,critic,referee], confidence}
# - 测：critic 无异议时 referee 直接采纳 solver
# - 测：任一步异常 → 回退到 None（调用方走普通答疑），不抛
```

**Step 2：跑失败** — `cd services/pythontopia-graph && python -m pytest -q tests/test_debate_graph.py`

**Step 3：实现**（三节点 StateGraph：`solver`(初答)→`critic`(挑错/找反例/查证据缺口)→`referee`(综合定稿+给 confidence)；每节点 emit trace `solver/critic/referee`，phase start/finish；全程沿用防泄答与 grounding）。

**Step 4：跑绿** **Step 5：提交** — `git commit -m "feat(graph): add solver-critic-referee debate subgraph for hard questions"`

**验收标准 3.1**：低置信/难题路由进 debate 并出现 `solver/critic/referee` 三步、产出更一致答案；critic 无异议时短路；任一步失败回退普通答疑且不抛；pytest 绿。前端无需改动（trace 自动进现有流水线可视化）。

---

### Task 3.2：主动陪伴冒泡（L）

**Files:**
- Modify: `frontend/src/stores/companion.ts`（加 `nudge` 状态 + `loadNudge()`：复用 `getBugMineEchoes()` 取最高优先到期 KC，拼一句提醒；`dismissNudge()`）
- Modify: `frontend/src/components/companion/CompanionDock.vue`（折叠态在小人上方显示**一条**可关闭气泡 `data-testid="companion-nudge"`，点击→`/bug-mine`）
- Test: 扩展 `companion.spec.ts`（mock echoes 有/无）

**Step 1：写失败测试**

```ts
it('builds a nudge from the top due echo', async () => {
  vi.mocked(getBugMineEchoes).mockResolvedValue([
    { kcCode: 'PY43-KC-016', problemTitle: '圆面积', minePriority: 0.8, /* ... */ } as any,
  ]);
  const store = useCompanionStore();
  await store.loadNudge();
  expect(store.nudge?.kcCode).toBe('PY43-KC-016');
  expect(store.nudge?.text).toContain('复习');
  store.dismissNudge();
  expect(store.nudge).toBeNull();
});

it('no nudge when nothing due', async () => {
  vi.mocked(getBugMineEchoes).mockResolvedValue([]);
  const store = useCompanionStore();
  await store.loadNudge();
  expect(store.nudge).toBeNull();
});
```

**Step 2：跑失败** — `npx vitest run tests/stores/companion.spec.ts`
**Step 3：实现**（`loadNudge` 取 echoes[0]，无则 null；气泡文案「该复习「{title}」了，要去矿洞看看吗？」；`dismissNudge` 置 null；组件 `onMounted`/进入页面时 `loadNudge`，节流避免打扰）。
**Step 4：跑绿** **Step 5：提交** — `git commit -m "feat(companion): proactive review nudge from FSRS echoes"`

**验收标准 3.2**：有到期 echo 时冒**一条**可关闭气泡并能一键进矿洞；无到期不打扰；关闭后当次不再弹；单测覆盖有/无两分支。

---

### Task 3.3：教师 copilot（M 扩展）

**Files:**
- Modify: `frontend/src/composables/useCompanionContext.ts`（识别 `surface==='classroom'` 或教师角色 → 注入班级上下文：当前班级 id / KC 热力摘要）
- Modify: 课堂页（复用全局 CompanionDock，无需新组件）；后端 `/api/tutor/ask` 教师上下文走只读班级数据（KC 热力、高频误区），严禁泄题
- Test: `tests/composables/useCompanionContext.spec.ts` 增「教师/课堂」用例；后端权限测试

**Step 1–5：** TDD —（1）测 classroom surface 注入班级上下文；（2）测非教师角色不暴露班级数据；（3）实现；（4）绿；（5）提交 `git commit -m "feat(companion): teacher copilot context on classroom surface"`

**验收标准 3.3**：教师在课堂页可呼出助教并就班级只读数据作答；学生角色拿不到班级聚合数据；不泄题；单测 + 后端权限测试绿。

**Phase 3 总验收**：难题更可靠（debate 三步）、到期主动提醒（一条气泡）、教师可用（复用同一助教）；`python -m pytest -q`、相关 `vitest`、后端权限测试全绿；前端仍只是「一个陪伴 + 一条 ask 栏 + 一条气泡」。

---

## 风险与回退
- **SSE 透传**：Java 代理需确认转发新 trace 节点（code_runner/debate）；不透传则前端降级为「思考中」，不报错。
- **沙箱安全**：`code_runner` 必须超时 + 资源限制 + 输出截断，复用既有判题隔离，严禁执行不可信网络/文件操作。
- **防泄答红线**：所有阶段沿用 TutorAgent 的「不给完整答案」与 safety 审查；运行结果只用于引导。
- **多端契约**：助教是 Web 端能力，遵循 `docs/clients/api-contract.md`；如需 Android/小程序同享，复用同一 `/api/tutor/ask` 契约。
- **不做**：不新增独立仪表盘、不堆文字、不在前端散 `any`、不改既有六类资源的核心业务逻辑。

## 执行交接
- 建议在独立 worktree 执行；**Phase 0 先做且可独立交付**（H+M 基线已具用户价值）。
- 每个 Task 完成即跑对应 `typecheck/eslint/vitest/pytest` 并提交；Phase 末跑该阶段总验收清单。
