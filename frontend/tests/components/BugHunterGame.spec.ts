import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import BugHunterGame from '@/components/feynman/BugHunterGame.vue';
import { useFeynmanStore } from '@/stores/feynman';
import type { FeynmanSessionDto } from '@/api/feynman';

const mocks = vi.hoisted(() => ({ huntFeynmanBug: vi.fn() }));

vi.mock('@/api/feynman', () => mocks);

const SESSION: FeynmanSessionDto = {
  id: 11,
  userId: 3,
  kcCode: 'PY43-KC-021',
  status: 'OPEN',
  tuteeState: { misconceptions: [] },
};

function seedSession(): void {
  const store = useFeynmanStore();
  store.currentSession = SESSION;
}

describe('BugHunterGame', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    mocks.huntFeynmanBug.mockReset();
    mocks.huntFeynmanBug.mockResolvedValue({
      source: 'real_misconception',
      misconceptionTag: 'off_by_one_range',
      buggyCode: `total = 0
for i in range(1, 5):
    total += i
print("1到5的和是", total)`,
      bugLineIndices: [0],
      hintMd: 'range 右端不包含',
    });
  });

  it('开始后展示可点代码行；点中 bug 行提交记为命中', async () => {
    seedSession();
    const wrapper = mount(BugHunterGame);

    await wrapper.get('[data-testid="bug-hunter-start"]').trigger('click');
    await flushPromises();

    expect(mocks.huntFeynmanBug).toHaveBeenCalledWith(11);
    expect(wrapper.get('[data-testid="bug-hunter-code"]').text()).toContain('range(1, 5)');

    await wrapper.get('.code-line[data-line="0"]').trigger('click');
    await wrapper.get('[data-testid="bug-hunter-submit"]').trigger('click');

    const result = wrapper.get('[data-testid="bug-hunter-result"]');
    expect(result.text()).toContain('命中 1');
    expect(result.text()).toContain('误 0');
    expect(result.text()).toContain('漏 0');
  });

  it('点错非 bug 行记为误报，并漏判真实 bug 行', async () => {
    seedSession();
    const wrapper = mount(BugHunterGame);

    await wrapper.get('[data-testid="bug-hunter-start"]').trigger('click');
    await flushPromises();

    await wrapper.get('.code-line[data-line="1"]').trigger('click');
    await wrapper.get('[data-testid="bug-hunter-submit"]').trigger('click');

    const result = wrapper.get('[data-testid="bug-hunter-result"]');
    expect(result.text()).toContain('命中 0');
    expect(result.text()).toContain('误 1');
    expect(result.text()).toContain('漏 1');
  });
});
