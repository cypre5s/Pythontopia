import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import CodeLabView from '@/components/workshop/CodeLabView.vue';
import { setPyodideRunner } from '@/game/adapters/pyodide';
import type { CodeLabContent } from '@/api/dto/Workshop';

const content: CodeLabContent = {
  walkthroughs: [
    {
      title: '变量与加法',
      narrative: '把两个数相加并打印结果。',
      code: 'a = 1\nb = 2\nprint(a + b)',
      expected_output: '3',
      common_mistakes: ['忘记调用 print'],
    },
  ],
};

function mountView() {
  return mount(CodeLabView, {
    props: { content },
    global: { plugins: [createPinia()] },
  });
}

describe('CodeLabView 内联运行', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    setPyodideRunner(null);
  });

  it('运行前不渲染输出区', () => {
    const wrapper = mountView();
    expect(wrapper.find('[data-testid="codelab-run-0"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="codelab-output-0"]').exists()).toBe(false);
  });

  it('点击「运行」通过 Pyodide 预览运行讲解代码并渲染可折叠输出', async () => {
    const fakeRunner = {
      init: vi.fn(async () => {}),
      run: vi.fn(async () => ({
        stdout: '3\n',
        stderr: '',
        durationMs: 4,
        isError: false,
      })),
      dispose: vi.fn(),
    };
    setPyodideRunner(fakeRunner);

    const wrapper = mountView();
    await wrapper.find('[data-testid="codelab-run-0"]').trigger('click');
    await flushPromises();

    expect(fakeRunner.run).toHaveBeenCalledTimes(1);
    expect(fakeRunner.run).toHaveBeenCalledWith('a = 1\nb = 2\nprint(a + b)', '');

    const output = wrapper.find('[data-testid="codelab-output-0"]');
    expect(output.exists()).toBe(true);
    expect(output.element.tagName.toLowerCase()).toBe('details');
    expect(output.text()).toContain('3');

    // 硬红线：Pyodide 预览必须明确标注「非正式判题」。
    expect(wrapper.text()).toContain('正式判题');
  });
});
