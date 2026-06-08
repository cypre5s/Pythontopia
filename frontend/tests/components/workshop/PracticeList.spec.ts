import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import PracticeList from '@/components/workshop/PracticeList.vue';
import { useMainQuestStore } from '@stores/mainQuest';
import { setPyodideRunner } from '@/game/adapters/pyodide';
import type { PracticeContent } from '@/api/dto/Workshop';

const content: PracticeContent = {
  problems: [
    {
      title: '两数之和',
      statement_md: '读入两个整数，输出它们的和。',
      hints: ['用 int() 把输入转成整数'],
      sample_io: [{ input: '1 2', output: '3' }],
      kc_codes: ['PY43-KC-011'],
      difficulty: 'basic',
    },
  ],
};

function mountList(): { wrapper: ReturnType<typeof mount>; router: Router } {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/bug-mine', name: 'bug-mine', component: { template: '<div />' } },
    ],
  });
  const wrapper = mount(PracticeList, {
    props: { content },
    global: { plugins: [createPinia(), router] },
  });
  return { wrapper, router };
}

describe('PracticeList 内联运行', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.removeItem('pyt_main_quest_progress_v1');
  });

  afterEach(() => {
    setPyodideRunner(null);
  });

  it('通过的本地预览推进主线「练」步骤，且不调用正式判题端点', async () => {
    const fakeRunner = {
      init: vi.fn(async () => {}),
      run: vi.fn(async () => ({
        stdout: '3\n',
        stderr: '',
        durationMs: 3,
        isError: false,
      })),
      dispose: vi.fn(),
    };
    setPyodideRunner(fakeRunner);

    const { wrapper } = mountList();
    const mainQuest = useMainQuestStore();
    const advanceSpy = vi.spyOn(mainQuest, 'onSubmissionPassed').mockReturnValue(null);

    await wrapper.find('[data-testid="practice-code-0"]').setValue('print(1 + 2)');
    await wrapper.find('[data-testid="practice-run-0"]').trigger('click');
    await flushPromises();

    expect(fakeRunner.run).toHaveBeenCalledTimes(1);
    // 用第一组样例输入作为 stdin 跑本地预览。
    expect(fakeRunner.run).toHaveBeenCalledWith('print(1 + 2)', '1 2');

    // 通过 → 推进主线「练」（submission 类 evidence 的钩子）。
    expect(advanceSpy).toHaveBeenCalledWith({ kcCode: 'PY43-KC-011' });

    const output = wrapper.find('[data-testid="practice-output-0"]');
    expect(output.exists()).toBe(true);
    expect(output.text()).toContain('3');

    // 通过时不出现「进矿洞」chip。
    expect(wrapper.find('[data-testid="practice-mine-0"]').exists()).toBe(false);
    // 硬红线：Pyodide 预览 ≠ 正式判题。
    expect(wrapper.text()).toContain('正式判题');
  });

  it('未通过的本地预览出现「进矿洞」chip 并跳转 /bug-mine，不推进主线', async () => {
    const fakeRunner = {
      init: vi.fn(async () => {}),
      run: vi.fn(async () => ({
        stdout: '999\n',
        stderr: '',
        durationMs: 3,
        isError: false,
      })),
      dispose: vi.fn(),
    };
    setPyodideRunner(fakeRunner);

    const { wrapper, router } = mountList();
    const pushSpy = vi.spyOn(router, 'push');
    const mainQuest = useMainQuestStore();
    const advanceSpy = vi.spyOn(mainQuest, 'onSubmissionPassed').mockReturnValue(null);

    await wrapper.find('[data-testid="practice-code-0"]').setValue('print(999)');
    await wrapper.find('[data-testid="practice-run-0"]').trigger('click');
    await flushPromises();

    // 输出与预期不符 → 视为未通过，不推进主线。
    expect(advanceSpy).not.toHaveBeenCalled();

    const mineChip = wrapper.find('[data-testid="practice-mine-0"]');
    expect(mineChip.exists()).toBe(true);

    await mineChip.trigger('click');
    await flushPromises();

    expect(pushSpy).toHaveBeenCalledWith({ path: '/bug-mine' });
  });
});
