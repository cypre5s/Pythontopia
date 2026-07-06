/**
 * R-14 域1①：删助教小队后，TutorPanel 只保留「答案 + 接地状态 + 证据页码回链」，
 * 不再渲染助教小队（换讲法）。本测试验证：
 *   - 提交后渲染答案与接地徽标「引用充分」+ 证据页码回链 p.X。
 *   - 不再出现助教小队 / 换讲法按钮（.crew__style-btn / .tutor-crew）。
 *   - 首次提交不带 style 字段（认知风格仍由后端按画像门槛决定）。
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

vi.mock('@/api/tutor', () => ({
  listTutorSessions: vi.fn().mockResolvedValue([]),
}));

// 可控 SSE 流桩：start 被调用即「秒回」一个完成态结果（含接地状态 + 证据页码）。
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
      evidencePageIds: [12],
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
});

async function mountAndAsk() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const wrapper = mount(TutorPanel, { global: { plugins: [pinia], stubs } });
  await flushPromises(); // onMounted 异步（课程候选）落定

  await wrapper.get('[data-testid="tutor-input"]').setValue('什么是变量?');
  await wrapper.get('[data-testid="tutor-submit"]').trigger('click');
  await flushPromises();
  await nextTick();
  return wrapper;
}

describe('TutorPanel · 删助教小队后保留答案 + 证据回链', () => {
  it('提交后渲染接地徽标与证据页码回链，且不再有换讲法按钮', async () => {
    const sse = getStream();
    const wrapper = await mountAndAsk();

    // 发起一次答疑，首次不带 style（风格仍由后端按画像门槛决定）。
    expect(sse.start).toHaveBeenCalledTimes(1);
    expect(sse.start.mock.calls[0]?.[0]).not.toHaveProperty('style');

    // 接地徽标 + 证据页码回链（证据页码能力保留）。
    expect(wrapper.get('[data-testid="grounding-chip"]').text()).toContain('引用充分');
    const badges = wrapper.findAll('.evidence-badge');
    expect(badges).toHaveLength(1);
    expect(badges[0]?.text()).toContain('p.12');

    // 助教小队 / 换讲法已删除。
    expect(wrapper.findAll('.crew__style-btn')).toHaveLength(0);
    expect(wrapper.find('.tutor-crew').exists()).toBe(false);
  });
});
