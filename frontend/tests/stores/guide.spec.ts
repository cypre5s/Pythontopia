/**
 * 伴学向导 store（P1-d 前端多轮对话线程）单测。
 *
 * 覆盖（既有回归 + R-14 向导加固 TDD）：
 *   1. 欢迎语 seed、多轮历史累积、historyForRequest 过滤、定稿计划/澄清/画像（既有）；
 *   2. 单飞行约束：streaming 中 beginAssistant / beginRetry 一律拒绝（同一时刻仅一个进行中请求）；
 *   3. 流式失败保留已收到的部分回答；无 token 失败给中文兜底文案；
 *   4. 重试只重发失败那一轮用户输入：历史截到该轮之前、用户气泡不重复、失败占位被替换；
 *   5. 关闭再打开悬浮窗保留会话历史、欢迎语不重复 seed。
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useGuideStore } from '@/stores/guide';
import type { OrchestratorResult } from '@/api/orchestrator';

type GuideStore = ReturnType<typeof useGuideStore>;

function makeResult(partial: Partial<OrchestratorResult>): OrchestratorResult {
  return {
    reply_md: '',
    clarifying_question: null,
    needs_user_input: false,
    plan: [],
    dispatch_results: [],
    profile_updates: [],
    trace: [],
    done: false,
    ...partial,
  };
}

/** 非并发场景下 beginAssistant 必须给出 id；集中断言，测试主体直接拿 string 用。 */
function mustBegin(g: GuideStore): string {
  const id = g.beginAssistant();
  if (!id) {
    throw new Error('beginAssistant 在非 streaming 场景不应返回 null');
  }
  return id;
}

describe('useGuideStore（伴学向导多轮线程）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    if (typeof window !== 'undefined') {
      window.localStorage.clear();
    }
  });

  it('会话 id 按用户命名空间隔离，且忽略旧的无 owner 键', () => {
    window.localStorage.setItem('pyt_guide_session_v1', 'legacy-global-session');
    window.localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 1, username: 'alice', displayName: 'A', role: 'STUDENT' }),
    );
    setActivePinia(createPinia());
    const alice = useGuideStore();

    expect(alice.sessionId).not.toBe('legacy-global-session');
    expect(window.localStorage.getItem('pyt_guide_session_v1@u:alice')).toBe(alice.sessionId);

    window.localStorage.setItem(
      'pythontopia.user',
      JSON.stringify({ id: 2, username: 'bob', displayName: 'B', role: 'STUDENT' }),
    );
    setActivePinia(createPinia());
    const bob = useGuideStore();

    expect(bob.sessionId).not.toBe(alice.sessionId);
    expect(window.localStorage.getItem('pyt_guide_session_v1@u:bob')).toBe(bob.sessionId);
    expect(window.localStorage.getItem('pyt_guide_session_v1')).toBe('legacy-global-session');
  });

  it('openPanel 首次进入 seed 欢迎语，且不污染发往后端的历史', () => {
    const g = useGuideStore();
    expect(g.messages).toHaveLength(0);
    g.openPanel();
    expect(g.open).toBe(true);
    expect(g.messages).toHaveLength(1);
    expect(g.messages[0]?.id).toBe('welcome');
    // 欢迎语不进入请求历史。
    expect(g.historyForRequest).toEqual([]);
  });

  it('一轮对话：pushUser → beginAssistant(streaming) → finalize 定稿计划与画像', () => {
    const g = useGuideStore();
    g.openPanel();
    g.pushUserMessage('我想学循环');
    const aid = mustBegin(g);
    expect(g.isStreaming).toBe(true);

    g.updateAssistantStream(aid, '正在为你规划…', [{ node: 'decompose', elapsed_ms: 12, status: 'ok' }]);
    g.finalizeAssistant(
      aid,
      makeResult({
        reply_md: '先看视频，再练两题。',
        plan: [
          {
            id: 's1', phase: 'watch', title: '看循环讲解', detail: '', agent: 'CurriculumMapperAgent',
            kc_codes: ['PY43-KC-031'], panel: 'lecture', status: 'pending', resource_type: null,
          },
        ],
        profile_updates: [{ dimension: 'learning_goal', value: '掌握循环', confidence: 0.6 }],
        done: false,
      }),
      '正在为你规划…',
    );

    expect(g.isStreaming).toBe(false);
    const assistant = g.messages.find((m) => m.id === aid);
    expect(assistant?.content).toBe('先看视频，再练两题。');
    expect(assistant?.plan).toHaveLength(1);
    expect(assistant?.profileUpdates?.[0]?.dimension).toBe('learning_goal');
    expect(assistant?.streaming).toBeFalsy();
  });

  it('historyForRequest 累积多轮、剔除欢迎语与流式占位', () => {
    const g = useGuideStore();
    g.openPanel();
    g.pushUserMessage('第一句');
    const a1 = mustBegin(g);
    g.finalizeAssistant(a1, makeResult({ reply_md: '第一答' }), '第一答');
    // 第二轮：取历史应包含「第一句 + 第一答」，但不含 welcome、不含正在流式的占位。
    const histBeforeSecond = g.historyForRequest;
    expect(histBeforeSecond.map((m) => m.content)).toEqual(['第一句', '第一答']);
    g.pushUserMessage('第二句');
    mustBegin(g); // 仍在流式
    expect(g.historyForRequest.map((m) => m.content)).toEqual(['第一句', '第一答', '第二句']);
  });

  it('historyForRequest 只发送最近 40 条有效消息', () => {
    const g = useGuideStore();
    for (let i = 0; i < 25; i += 1) {
      g.pushUserMessage(`问题 ${i}`);
      const aid = mustBegin(g);
      g.finalizeAssistant(aid, makeResult({ reply_md: `回答 ${i}` }), `回答 ${i}`);
    }

    const history = g.historyForRequest;

    expect(history).toHaveLength(40);
    expect(history[0]?.content).toBe('问题 5');
    expect(history.at(-1)?.content).toBe('回答 24');
  });

  it('聊天增量更新不复制整条消息数组', () => {
    const g = useGuideStore();
    g.openPanel();
    const messages = g.messages;

    g.pushUserMessage('第一问');
    const aid = mustBegin(g);
    g.updateAssistantStream(aid, '逐步到达的回答', []);
    g.finalizeAssistant(aid, makeResult({ reply_md: '完整回答' }), '完整回答');

    expect(g.messages).toBe(messages);
    expect(g.messages.find((message) => message.id === aid)?.content).toBe('完整回答');
  });

  it('finalize 澄清追问设置 needsUserInput', () => {
    const g = useGuideStore();
    g.openPanel();
    g.pushUserMessage('我想学 Python');
    const aid = mustBegin(g);
    g.finalizeAssistant(
      aid,
      makeResult({ reply_md: '', clarifying_question: '先从第几章开始？', needs_user_input: true }),
      '',
    );
    const assistant = g.messages.find((m) => m.id === aid);
    expect(assistant?.clarifyingQuestion).toBe('先从第几章开始？');
    expect(assistant?.needsUserInput).toBe(true);
    expect(g.lastAssistant?.id).toBe(aid);
  });

  it('failAssistant 标记失败并写 lastError', () => {
    const g = useGuideStore();
    g.openPanel();
    g.pushUserMessage('出错试试');
    const aid = mustBegin(g);
    g.failAssistant(aid, '网络错误');
    const assistant = g.messages.find((m) => m.id === aid);
    expect(assistant?.failed).toBe(true);
    expect(g.status).toBe('error');
    expect(g.lastError).toBe('网络错误');
    // 失败占位不进入下一轮历史。
    expect(g.historyForRequest.some((m) => m.failed)).toBe(false);
  });

  describe('单飞行约束：同一时刻只允许一个进行中请求', () => {
    it('streaming 中再次 beginAssistant 返回 null 且不追加占位消息', () => {
      const g = useGuideStore();
      g.openPanel();
      g.pushUserMessage('第一问');
      const first = g.beginAssistant();
      expect(first).not.toBeNull();
      expect(g.isStreaming).toBe(true);

      const countBefore = g.messages.length;
      const second = g.beginAssistant();
      expect(second).toBeNull();
      expect(g.messages).toHaveLength(countBefore);
      expect(g.status).toBe('streaming');
    });

    it('streaming 中 beginRetry 返回 null（不允许并发重试）', () => {
      const g = useGuideStore();
      g.openPanel();
      g.pushUserMessage('第一问');
      mustBegin(g);
      expect(g.beginRetry()).toBeNull();
    });

    it('失败后允许开启新一轮（error 态不阻塞 beginAssistant）', () => {
      const g = useGuideStore();
      g.openPanel();
      g.pushUserMessage('会失败的一问');
      const a1 = mustBegin(g);
      g.failAssistant(a1, '后端 500');
      // 失败后用户可以另起一轮新提问。
      g.pushUserMessage('换个问题');
      expect(g.beginAssistant()).not.toBeNull();
      expect(g.status).toBe('streaming');
    });
  });

  describe('流式失败后的部分回答保留', () => {
    it('已收到 token 再失败：部分回答仍保留在消息里', () => {
      const g = useGuideStore();
      g.openPanel();
      g.pushUserMessage('讲讲 while 循环');
      const aid = mustBegin(g);
      g.updateAssistantStream(aid, '已经讲到一半的内容', [{ node: 'dispatch', elapsed_ms: 30, status: 'running' }]);
      g.failAssistant(aid, '上游超时');

      const msg = g.messages.find((m) => m.id === aid);
      expect(msg?.failed).toBe(true);
      expect(msg?.content).toContain('已经讲到一半的内容');
      expect(g.status).toBe('error');
      expect(g.lastError).toBe('上游超时');
    });

    it('一个 token 都没收到就失败：给出中文兜底文案', () => {
      const g = useGuideStore();
      g.openPanel();
      g.pushUserMessage('讲讲 for 循环');
      const aid = mustBegin(g);
      g.failAssistant(aid, '连接中断');
      const msg = g.messages.find((m) => m.id === aid);
      expect(msg?.failed).toBe(true);
      expect(msg?.content).toContain('抱歉');
    });
  });

  describe('重试：只重发失败那一轮用户输入', () => {
    it('canRetry 仅在该轮失败后为 true；idle / streaming 下 beginRetry 返回 null', () => {
      const g = useGuideStore();
      g.openPanel();
      // 还没有任何用户输入：不可重试。
      expect(g.canRetry).toBe(false);
      expect(g.beginRetry()).toBeNull();

      g.pushUserMessage('第一问');
      const a1 = mustBegin(g);
      g.finalizeAssistant(a1, makeResult({ reply_md: '第一答' }), '第一答');
      // 成功收尾：不可重试。
      expect(g.canRetry).toBe(false);
      expect(g.beginRetry()).toBeNull();

      g.pushUserMessage('第二问');
      const a2 = mustBegin(g);
      g.failAssistant(a2, 'boom');
      expect(g.canRetry).toBe(true);
    });

    it('beginRetry 只重发失败那轮：历史截到该轮之前、不重复用户气泡、失败占位被替换', () => {
      const g = useGuideStore();
      g.openPanel();
      g.pushUserMessage('第一问');
      const a1 = mustBegin(g);
      g.finalizeAssistant(a1, makeResult({ reply_md: '第一答', done: true }), '第一答');

      g.pushUserMessage('第二问');
      const a2 = mustBegin(g);
      g.updateAssistantStream(a2, '半截回答', []);
      g.failAssistant(a2, '上游超时');

      const turn = g.beginRetry();
      expect(turn).not.toBeNull();
      // 只重发失败那一轮的用户输入。
      expect(turn?.text).toBe('第二问');
      // 历史不含失败那轮的用户输入（避免 message 与 history 重复），也不含失败占位。
      expect(turn?.history).toEqual([
        { role: 'user', content: '第一问' },
        { role: 'assistant', content: '第一答' },
      ]);
      // 用户气泡不重复。
      expect(g.messages.filter((m) => m.role === 'user' && m.content === '第二问')).toHaveLength(1);
      // 失败占位被替换为新的流式占位。
      expect(g.messages.some((m) => m.failed)).toBe(false);
      expect(g.messages.at(-1)?.streaming).toBe(true);
      expect(g.status).toBe('streaming');
      expect(g.lastError).toBeNull();

      // 重试成功收尾。
      if (!turn) throw new Error('unreachable');
      g.finalizeAssistant(turn.assistantId, makeResult({ reply_md: '重试成功的回答', done: true }), '');
      expect(g.status).toBe('idle');
      expect(g.messages.at(-1)?.content).toBe('重试成功的回答');
    });

    it('重试再失败可继续重试，线程里不堆积多个失败占位', () => {
      const g = useGuideStore();
      g.openPanel();
      g.pushUserMessage('唯一一问');
      const a1 = mustBegin(g);
      g.failAssistant(a1, '第一次失败');

      const retry1 = g.beginRetry();
      expect(retry1).not.toBeNull();
      if (!retry1) throw new Error('unreachable');
      g.failAssistant(retry1.assistantId, '第二次失败');

      const retry2 = g.beginRetry();
      expect(retry2).not.toBeNull();
      expect(retry2?.text).toBe('唯一一问');
      // 用户气泡仍只有一个，失败占位不堆积。
      expect(g.messages.filter((m) => m.role === 'user')).toHaveLength(1);
      expect(g.messages.filter((m) => m.failed)).toHaveLength(0);
      expect(g.messages.filter((m) => m.streaming)).toHaveLength(1);
    });
  });

  describe('会话保留：关闭再打开不丢历史', () => {
    it('closePanel 再 openPanel 消息原样保留，欢迎语不重复 seed', () => {
      const g = useGuideStore();
      g.openPanel();
      g.pushUserMessage('第一问');
      const aid = mustBegin(g);
      g.finalizeAssistant(aid, makeResult({ reply_md: '第一答' }), '第一答');

      const snapshot = g.messages.map((m) => ({ id: m.id, content: m.content }));
      g.closePanel();
      expect(g.open).toBe(false);
      g.openPanel();
      expect(g.open).toBe(true);
      expect(g.messages.map((m) => ({ id: m.id, content: m.content }))).toEqual(snapshot);
      expect(g.messages.filter((m) => m.id === 'welcome')).toHaveLength(1);
    });
  });
});
