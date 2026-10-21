import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FeynmanPanel from '@/components/feynman/FeynmanPanel.vue';
import { useFeynmanStore } from '@/stores/feynman';

const mocks = vi.hoisted(() => ({
  startFeynmanSession: vi.fn(),
  teachFeynmanSession: vi.fn(),
  huntFeynmanBug: vi.fn(),
  judgeFeynmanSession: vi.fn(),
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
    mocks.huntFeynmanBug.mockReset();
    mocks.judgeFeynmanSession.mockReset();
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
    mocks.huntFeynmanBug.mockResolvedValue(null);
    mocks.judgeFeynmanSession.mockResolvedValue({
      sessionId: 11,
      passed: true,
      status: 'PASSED',
      message: '教学成功',
    });
  });

  it('开始会话后在信息条展示学弟误区，并给出代码草稿与判题占位', async () => {
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
    // 「学习足迹·全程可溯源」整块已删除，不再渲染独立证据链。
    expect(wrapper.find('[data-testid="feynman-evidence-chain"]').exists()).toBe(false);
  });

  it('提交教学输入后展示学弟回复、修正点与真判题结果', async () => {
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-010' },
    });

    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();
    await wrapper
      .get('[data-testid="feynman-teach-input"]')
      .setValue('先读变量名，再看 = 右边的值。');
    await wrapper.get('[data-testid="feynman-teach-submit"]').trigger('click');
    await flushPromises();

    expect(mocks.teachFeynmanSession).toHaveBeenCalledWith(11, '先读变量名，再看 = 右边的值。');
    expect(wrapper.get('[data-testid="feynman-dialog"]').text()).toContain('赋值用 =');
    expect(wrapper.get('[data-testid="feynman-fix"]').text()).toContain('比较和赋值');
    expect(wrapper.get('[data-testid="feynman-code"]').text()).toContain('print(x)');
    expect(wrapper.get('[data-testid="feynman-judge"]').text()).toContain('隐藏用例通过');
  });

  it('代码找茬产出的 bug 提示接入「讲明白」引导，串起出师闭环（B）', async () => {
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-010' },
    });

    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();

    const store = useFeynmanStore();
    store.bugHunt = {
      source: 'real_misconception',
      misconceptionTag: 'off_by_one_range',
      buggyCode: `total = 0
for i in range(1, 5):
    total += i
print("1到5的和是", total)`,
      bugLineIndices: [1],
      hintMd: 'range 右端不包含',
    };
    await flushPromises();

    expect(wrapper.get('[data-testid="feynman-teach-hint"]').text()).toContain('range 右端不包含');
    // 学弟以中性口吻描述代码在做什么，不暴露「我以为…」式自我纠错。
    expect(wrapper.get('[data-testid="feynman-tutee-voice"]').text()).toContain('累加');
    expect(wrapper.get('[data-testid="feynman-tutee-voice"]').text()).not.toContain('我以为');
  });

  it('未开始会话时不渲染教学输入，从结构上杜绝无 session 提交', () => {
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-010' },
    });

    expect(wrapper.find('[data-testid="feynman-teach-input"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="feynman-teach-submit"]').exists()).toBe(false);
  });

  it('没有错题记录时仍可直接讲解知识点，且明确不会生成错题证据', async () => {
    mocks.startFeynmanSession.mockResolvedValueOnce({
      ...session,
      tuteeState: { misconceptions: [], producedCode: null, judgeResult: null },
    });
    mocks.teachFeynmanSession.mockResolvedValueOnce({
      id: 22,
      role: 'TUTEE',
      content: '我听懂了 for 会逐个取出元素。能用一个小例子带我走一遍吗？',
      taughtFix: 'print(1)',
      producedCode: 'print(1)',
      noticeMd: null,
    });
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-031' },
    });

    expect(wrapper.get('.feynman-panel__intro-hint').text()).toContain('讲解知识点');
    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-testid="feynman-independent-teaching"]').text()).toContain(
      '不会创建错题记录',
    );
    await wrapper.get('[data-testid="feynman-teach-input"]').setValue('for 会逐个取出可迭代对象中的元素。');
    await wrapper.get('[data-testid="feynman-teach-submit"]').trigger('click');
    await flushPromises();

    expect(mocks.teachFeynmanSession).toHaveBeenCalledWith(11, 'for 会逐个取出可迭代对象中的元素。');
    expect(wrapper.get('[data-testid="feynman-dialog"]').text()).toContain('能用一个小例子');
    expect(wrapper.find('[data-testid="feynman-tutee-state"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="feynman-judge-submit"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="feynman-judge-submit"]').text()).toContain('自主讲题');
  });

  it('重来时清空尚未提交的旧轮次输入', async () => {
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-031' },
    });
    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="feynman-teach-input"]').setValue('上一轮还没提交的解释');

    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();

    expect((wrapper.get('[data-testid="feynman-teach-input"]').element as HTMLTextAreaElement).value)
      .toBe('');
  });

  it('Graph 失败时保留学生解释并把提交动作变成明确重试', async () => {
    mocks.teachFeynmanSession.mockRejectedValueOnce(new Error('AI 学弟暂时不可用'));
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-010' },
    });
    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();

    const input = wrapper.get('[data-testid="feynman-teach-input"]');
    await input.setValue('range 的右端点不包含，所以应调整 stop。');
    await wrapper.get('[data-testid="feynman-teach-submit"]').trigger('click');
    await flushPromises();

    expect((input.element as HTMLTextAreaElement).value).toContain('右端点不包含');
    expect(wrapper.get('[data-testid="feynman-teach-submit"]').text()).toContain('重试');
    expect(wrapper.get('[role="alert"]').text()).toContain('AI 学弟暂时不可用');
  });

  it('把后端 noticeMd 显示为独立系统提示而不是学弟台词', async () => {
    mocks.teachFeynmanSession.mockResolvedValueOnce({
      id: 22,
      role: 'TUTEE',
      content: '我可以继续听你解释。',
      taughtFix: null,
      noticeMd: '服务暂时繁忙，请稍后重试。',
    });
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-010' },
    });
    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="feynman-teach-input"]').setValue('先解释边界规则。');
    await wrapper.get('[data-testid="feynman-teach-submit"]').trigger('click');
    await flushPromises();

    const notice = wrapper.get('[data-testid="feynman-system-notice"]');
    expect(notice.text()).toContain('服务暂时繁忙');
    expect(notice.classes()).toContain('feynman-panel__system-notice');
    expect(wrapper.get('.chat-msg__bubble').text()).not.toContain('先去做题');
  });

  it('概念模式以中性说明禁用代表题真判题', async () => {
    mocks.startFeynmanSession.mockResolvedValueOnce({
      ...session,
      kcCode: '正则表达式',
      tuteeState: { ...session.tuteeState, producedCode: 'print("regex")' },
    });
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: '正则表达式' },
    });
    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();

    expect(
      wrapper.get('[data-testid="feynman-judge-submit"]').attributes('disabled'),
    ).toBeDefined();
    expect(wrapper.get('[data-testid="feynman-judge-submit"]').text()).toContain('无需判题');
    expect(wrapper.get('[data-testid="feynman-judge"]').text()).toContain('代码找茬');
    expect(wrapper.get('.feynman-panel__verdict').classes()).toContain('feynman-panel__judge--na');
  });

  it('已通过会话禁用重复真判题', async () => {
    mocks.startFeynmanSession.mockResolvedValueOnce({
      ...session,
      status: 'JUDGED_PASS',
      tuteeState: {
        ...session.tuteeState,
        producedCode: 'print(1)',
        judgeResult: null,
      },
    });
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-010' },
    });
    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();

    expect(
      wrapper.get('[data-testid="feynman-judge-submit"]').attributes('disabled'),
    ).toBeDefined();
    expect(wrapper.get('[data-testid="feynman-judge-submit"]').text()).toContain('已通过');
    expect(wrapper.get('.feynman-panel__verdict').classes()).toContain(
      'feynman-panel__judge--passed',
    );
  });

  it.each([
    ['ERROR', 'feynman-panel__judge--error'],
    ['NA', 'feynman-panel__judge--na'],
  ])('保留 %s 的独立判题视觉状态', async (status, expectedClass) => {
    const wrapper = mount(FeynmanPanel, {
      props: { kcCode: 'PY43-KC-010' },
    });
    await wrapper.get('[data-testid="feynman-start"]').trigger('click');
    await flushPromises();
    const store = useFeynmanStore();
    store.latestJudgeResult = {
      status: status as 'ERROR' | 'NA',
      messageZh: status,
      evidenceEventIds: [],
    };
    await flushPromises();

    expect(wrapper.get('.feynman-panel__verdict').classes()).toContain(expectedClass);
  });
});
