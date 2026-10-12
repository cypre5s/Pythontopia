import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { ref } from 'vue';

// mock useSseStream：返回可控的 start/status/result/tokens/error，验证锚定问答接线
// （提交时调 contextProvider 取当前上下文并随 body 传 /api/tutor/ask）。
const startSpy = vi.fn();
const statusRef = ref<'idle' | 'connecting' | 'streaming' | 'done' | 'error'>('idle');
const resultRef = ref<unknown>(null);
const tokensRef = ref<string>('');
const errorRef = ref<{ code: string; message: string } | null>(null);

vi.mock('@/composables/useSseStream', () => ({
  useSseStream: () => ({
    start: startSpy,
    status: statusRef,
    result: resultRef,
    tokens: tokensRef,
    error: errorRef,
    traces: ref([]),
    plan: ref(null),
    abort: vi.fn(),
  }),
}));

// MarkdownView 轻量 stub（避免 markdown 渲染依赖）。
vi.mock('@/components/common/MarkdownView.vue', () => ({
  default: {
    name: 'MarkdownView',
    props: { source: { type: String, default: '' } },
    template: '<div data-testid="md-stub">{{ source }}</div>',
  },
}));

import StageContextTutor from '@/components/mainline-stage/StageContextTutor.vue';

function mountTutor(contextProvider: () => string | Promise<string>, kcCodes: string[] = ['PY43-KC-006']) {
  return mount(StageContextTutor, {
    props: { kcCodes, scopeLabel: '这一页课件', contextProvider },
  });
}

describe('StageContextTutor（锚定上下文的百晓助教问答）', () => {
  beforeEach(() => {
    startSpy.mockReset();
    statusRef.value = 'idle';
    resultRef.value = null;
    tokensRef.value = '';
    errorRef.value = null;
  });
  afterEach(() => {
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it('提交问答：调 contextProvider 取当前上下文，并随 question/kcCodes 传给 SSE', async () => {
    statusRef.value = 'done';
    resultRef.value = { answer_md: 'for 循环用于遍历序列。', grounding_result: { passed: true, reasons: [] } };
    const provider = vi.fn().mockResolvedValue('【第 3 页课件】for 循环讲解…');
    const wrapper = mountTutor(provider);

    await wrapper.find('[data-testid="stage-context-tutor-input"]').setValue('这页讲的什么？');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(provider).toHaveBeenCalledTimes(1);
    expect(startSpy).toHaveBeenCalledWith({
      question: '这页讲的什么？',
      kcCodes: ['PY43-KC-006'],
      context: '【第 3 页课件】for 循环讲解…',
    });
    expect(wrapper.find('[data-testid="stage-context-tutor-answer"]').text()).toContain('for 循环用于遍历序列');
  });

  it('把图片附件随锚定问答提交', async () => {
    vi.stubGlobal('FileReader', class {
      result: string | null = null;
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      readAsDataURL(file: Blob): void {
        this.result = `data:${file.type};base64,iVBORw0KGgo=`;
        this.onload?.();
      }
    });
    statusRef.value = 'done';
    resultRef.value = { answer_md: '图中展示了循环。', grounding_result: { passed: true, reasons: [] } };
    const wrapper = mountTutor(vi.fn().mockResolvedValue('ctx'));
    const file = new File([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])], 'page.png', { type: 'image/png' });
    const input = wrapper.get('[data-testid="stage-context-tutor-image-input"]');
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true });
    await input.trigger('change');
    await flushPromises();
    expect(wrapper.find('[data-testid="stage-context-tutor-attachments"]').exists()).toBe(true);
    await wrapper.find('[data-testid="stage-context-tutor-input"]').setValue('请解释图中代码');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(startSpy).toHaveBeenCalledWith(expect.objectContaining({
      question: '请解释图中代码',
      images: [expect.stringMatching(/^data:image\/png;base64,/)],
    }));
  });

  it('空问题不提交', async () => {
    const provider = vi.fn().mockResolvedValue('ctx');
    const wrapper = mountTutor(provider);
    await wrapper.find('[data-testid="stage-context-tutor-submit"]').trigger('click');
    await flushPromises();
    expect(startSpy).not.toHaveBeenCalled();
    expect(provider).not.toHaveBeenCalled();
  });

  it('SSE 出错时显示错误提示', async () => {
    statusRef.value = 'error';
    errorRef.value = { code: 'X', message: '网络抖动' };
    const wrapper = mountTutor(vi.fn().mockResolvedValue('ctx'));
    await wrapper.find('[data-testid="stage-context-tutor-input"]').setValue('Q');
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(wrapper.find('[data-testid="stage-context-tutor-error"]').text()).toContain('网络抖动');
  });
});
