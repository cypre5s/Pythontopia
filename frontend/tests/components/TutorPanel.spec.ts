/**
 * R-14 即时换讲法（①）：点「换讲法」用同一问题、换认知风格立刻重发并改写当前答案。
 *
 * 验证 TutorPanel.onPickStyle：
 *   - 首次提交不带 style（保持原行为，认知风格由后端按画像置信度门槛决定）。
 *   - 点换讲法后用同一问题带 style 重新发起答疑流（即时改写当前答案）。
 *   - 同时写回画像 styleEngaged（影响后续轮次）。
 *   - 选中风格高亮（active chip）。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/composables/useAudio', () => ({
  playSfx: vi.fn(),
}));

vi.mock('@/api/course', () => ({
  listChapters: vi.fn().mockResolvedValue([]),
  listKnowledgeComponents: vi.fn().mockResolvedValue([]),
}));

vi.mock('@/api/learner', () => ({
  getProfileDimensions: vi.fn().mockResolvedValue([]),
}));

vi.mock('@/api/tutor', () => ({
  styleEngaged: vi.fn().mockResolvedValue(undefined),
  listTutorSessions: vi.fn().mockResolvedValue([]),
}));

// 用真 Vue ref 搭一个可控的 SSE 流桩：start 被调用即「秒回」一个完成态结果，
// 让组件读到 status=done + result，从而渲染助教小队（含「换讲法」按钮）。
vi.mock('@/composables/useSseStream', async () => {
  const { ref } = await import('vue');
  const status = ref('idle');
  const tokens = ref('');
  const result = ref<Record<string, unknown> | null>(null);
  const error = ref<unknown>(null);
  const traces = ref<unknown[]>([]);
  const plan = ref<unknown>(null);
  const start = vi.fn(async (body: Record<string, unknown>) => {
    tokens.value = '答案内容';
    result.value = {
      sessionId: 1,
      question: body.question,
      answerMd: '答案内容',
      evidencePageIds: [],
      groundingStatus: 'PASSED',
    };
    status.value = 'done';
  });
  const abort = vi.fn();
  const state = { status, tokens, result, error, traces, plan, start, abort };
  return { useSseStream: () => state };
});

import TutorPanel from '@/components/tutor/TutorPanel.vue';
import { useSseStream } from '@/composables/useSseStream';
import { styleEngaged } from '@/api/tutor';

interface RefLike<T> {
  value: T;
}
interface MockStream {
  status: RefLike<string>;
  result: RefLike<Record<string, unknown> | null>;
  tokens: RefLike<string>;
  start: ReturnType<typeof vi.fn>;
  abort: ReturnType<typeof vi.fn>;
}

function getStream(): MockStream {
  return useSseStream('/api/tutor/ask') as unknown as MockStream;
}

const stubs = {
  MarkdownView: true,
  CoursewareMentionMenu: true,
  TutorVisualAids: true,
};

// jsdom 未实现 Element.scrollTo；答疑流 token watch 里会调用它，补个 noop 防未处理异常。
Element.prototype.scrollTo = vi.fn();

beforeEach(() => {
  setActivePinia(createPinia());
  const sse = getStream();
  sse.status.value = 'idle';
  sse.result.value = null;
  sse.tokens.value = '';
  sse.start.mockClear();
  (styleEngaged as ReturnType<typeof vi.fn>).mockClear();
});

async function mountAndAsk() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const wrapper = mount(TutorPanel, { global: { plugins: [pinia], stubs } });
  await flushPromises(); // onMounted 异步（课程候选 / 画像信号）落定

  await wrapper.get('[data-testid="tutor-input"]').setValue('什么是变量?');
  await wrapper.get('[data-testid="tutor-submit"]').trigger('click');
  await flushPromises();
  await nextTick();
  return wrapper;
}

describe('TutorPanel · 换讲法即时改写当前答案', () => {
  it('首次提交不带 style；点换讲法用同一问题带 style 重发并写回画像', async () => {
    const sse = getStream();
    const wrapper = await mountAndAsk();

    // 首问已发起一次，且不带 style（保持原行为：风格由后端按画像门槛决定）。
    expect(sse.start).toHaveBeenCalledTimes(1);
    expect(sse.start.mock.calls[0]?.[0]).not.toHaveProperty('style');

    // 答疑完成后助教小队渲染，含 3 个换讲法按钮（图解 / 推理 / 示例）。
    const buttons = wrapper.findAll('.crew__style-btn');
    expect(buttons).toHaveLength(3);

    // 点「图解」(visual)：同一问题带 style 立刻重发，改写当前答案。
    await buttons[0]?.trigger('click');
    await flushPromises();
    await nextTick();

    expect(sse.start).toHaveBeenCalledTimes(2);
    expect(sse.start.mock.calls[1]?.[0]).toMatchObject({
      question: '什么是变量?',
      style: 'visual',
    });

    // (c) 同时写回画像 cognitive_style（影响后续轮次）。
    expect(styleEngaged).toHaveBeenCalledWith('visual', undefined);

    // (a) 选中风格高亮（active chip）。
    expect(wrapper.findAll('.crew__style-btn')[0]?.classes()).toContain('is-active');
  });
});
