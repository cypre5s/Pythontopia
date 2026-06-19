import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FeynmanPanel from '@/components/feynman/FeynmanPanel.vue';

const mocks = vi.hoisted(() => ({
  startFeynmanSession: vi.fn(),
  teachFeynmanSession: vi.fn(),
}));

vi.mock('@/api/feynman', () => mocks);

const session = {
  id: 11,
  userId: 3,
  kcCode: 'PY43-KC-010',
  status: 'OPEN',
  tuteeState: {
    misconceptions: [
      {
        tag: 'confuses_assignment_and_comparison',
        evidenceEventIds: [101, 102],
        resolved: false,
      },
    ],
    producedCode: 'x = 1\nprint(x)',
    judgeResult: {
      status: 'PENDING',
      messageZh: '等待学弟提交可判题代码',
      evidenceEventIds: [101],
    },
  },
};

describe('FeynmanPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.startFeynmanSession.mockReset();
    mocks.teachFeynmanSession.mockReset();
    mocks.startFeynmanSession.mockResolvedValue(session);
    mocks.teachFeynmanSession.mockResolvedValue({
      id: 21,
      role: 'TUTEE',
      content: '我明白了，赋值用 =，比较才用 ==。',
      taughtFix: '把比较和赋值的用途分开讲清楚了',
      producedCode: 'x = 1\nprint(x)',
      judgeResult: {
        status: 'PASSED',
        messageZh: '隐藏用例通过',
        evidenceEventIds: [101, 102],
      },
    });
  });

  it('开始会话后展示学弟状态、代码草稿与真实证据链', async () => {
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-010' },
    });

    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();

    expect(mocks.startFeynmanSession).toHaveBeenCalledWith('PY43-KC-010');
    expect(wrapper.get('[data-testid="feynman-tutee-state"]').text()).toContain(
      'confuses_assignment_and_comparison',
    );
    expect(wrapper.get('[data-testid="feynman-code"]').text()).toContain('print(x)');
    expect(wrapper.get('[data-testid="feynman-judge"]').text()).toContain('等待学弟提交可判题代码');
    const evidence = wrapper.get('[data-testid="feynman-evidence-chain"]').text();
    expect(evidence).toContain('学习记录 #101');
    expect(evidence).toContain('误区');
  });

  it('提交教学输入后展示学弟回复、修正点、判题结果与证据回链', async () => {
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-010' },
    });

    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="feynman-teach-input"]').setValue('先读变量名，再看 = 右边的值。');
    await wrapper.get('[data-testid="feynman-teach-submit"]').trigger('click');
    await flushPromises();

    expect(mocks.teachFeynmanSession).toHaveBeenCalledWith(11, '先读变量名，再看 = 右边的值。');
    expect(wrapper.get('[data-testid="feynman-dialog"]').text()).toContain('赋值用 =');
    expect(wrapper.get('[data-testid="feynman-fix"]').text()).toContain('比较和赋值');
    expect(wrapper.get('[data-testid="feynman-code"]').text()).toContain('print(x)');
    expect(wrapper.get('[data-testid="feynman-judge"]').text()).toContain('隐藏用例通过');
    expect(wrapper.get('[data-testid="feynman-evidence-chain"]').text()).toContain('学习记录 #102');
  });

  it('未开始会话时教学按钮禁用，避免提交无 session 的解释', () => {
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-010' },
    });

    expect(wrapper.get('[data-testid="feynman-teach-submit"]').attributes('disabled')).toBeDefined();
  });
});
