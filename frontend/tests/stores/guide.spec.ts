/**
 * 伴学向导 store（P1-d 前端多轮对话线程）单测。
 * 覆盖：欢迎语 seed、多轮历史累积、historyForRequest 过滤、定稿计划/澄清/画像、失败标记。
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useGuideStore } from '@/stores/guide';
import type { OrchestratorResult } from '@/api/orchestrator';

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

describe('useGuideStore（伴学向导多轮线程）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    if (typeof window !== 'undefined') {
      window.localStorage.clear();
    }
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
    const aid = g.beginAssistant();
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
    const a1 = g.beginAssistant();
    g.finalizeAssistant(a1, makeResult({ reply_md: '第一答' }), '第一答');
    // 第二轮：取历史应包含「第一句 + 第一答」，但不含 welcome、不含正在流式的占位。
    const histBeforeSecond = g.historyForRequest;
    expect(histBeforeSecond.map((m) => m.content)).toEqual(['第一句', '第一答']);
    g.pushUserMessage('第二句');
    g.beginAssistant(); // 仍在流式
    expect(g.historyForRequest.map((m) => m.content)).toEqual(['第一句', '第一答', '第二句']);
  });

  it('finalize 澄清追问设置 needsUserInput', () => {
    const g = useGuideStore();
    g.openPanel();
    g.pushUserMessage('我想学 Python');
    const aid = g.beginAssistant();
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
    const aid = g.beginAssistant();
    g.failAssistant(aid, '网络错误');
    const assistant = g.messages.find((m) => m.id === aid);
    expect(assistant?.failed).toBe(true);
    expect(g.status).toBe('error');
    expect(g.lastError).toBe('网络错误');
    // 失败占位不进入下一轮历史。
    expect(g.historyForRequest.some((m) => m.failed)).toBe(false);
  });
});
