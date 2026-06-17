/**
 * LoopProgress 组件测试 —— Agentic Loop 闭环可视化（F2-loop-ui）。
 *
 * 对齐 §3.8「对齐 Claude Code 网页版」输出格式 + §4 F 第 2-6 条 + §8.4 无障碍（WCAG 2.2 AA）。
 * 覆盖测试矩阵：
 *   ③ 计划 / 工具时间线 / 证据 chips 分区 / 错误态四类渲染；
 *   ④ aria-live + 键盘折叠可达；
 *   ⑤ 绝不渲染 CoT，仅 reasonSummary；
 *   ⑥ reduceMotion 退化（经 F1 AnimAdapter 接口 mock 验证，与真实 anim 实现解耦）；
 *   ⑦ 无 any（本文件全程显式类型）。
 *
 * 动效适配器按 §3.7 / §4 F.1 的 `AnimAdapter` 接口以 mock 注入，不加载真实 gsap/rive。
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import LoopProgress from '@/components/hud/LoopProgress.vue';
import type { AnimAdapter } from '@game/adapters/anim/types';
import type { LoopRunStatus } from '@/stores/learning/loopSlice';
import type { LoopPhase, LoopPlan, LoopStep, LoopStepStatus } from '@/stores/learning/loopTypes';

function makeStep(
  id: string,
  phase: LoopPhase,
  status: LoopStepStatus,
  extra: Partial<LoopStep> = {},
): LoopStep {
  return { id, phase, label: extra.label ?? phase, status, ...extra };
}

function makePlan(steps: LoopStep[], maxSteps = 4): LoopPlan {
  return { steps, bounded: true, maxSteps: Math.max(maxSteps, steps.length) };
}

/** 按 F1 冻结的 `AnimAdapter` 接口（@game/adapters/anim/types）构造 mock，与真实 anim 实现解耦。 */
interface MockAnim {
  reduceMotion: boolean;
  playStepIn: ReturnType<typeof vi.fn>;
  playStepDone: ReturnType<typeof vi.fn>;
  celebrate: ReturnType<typeof vi.fn>;
  dispose: ReturnType<typeof vi.fn>;
}

function makeAnim(reduce = false): MockAnim {
  return {
    reduceMotion: reduce,
    playStepIn: vi.fn(() => Promise.resolve()),
    playStepDone: vi.fn(() => Promise.resolve()),
    celebrate: vi.fn(() => Promise.resolve()),
    dispose: vi.fn(),
  };
}

interface MountProps {
  plan: LoopPlan | null;
  status?: LoopRunStatus;
  runError?: string | null;
  interrupted?: boolean;
  anim?: AnimAdapter | null;
}

function mountProgress(props: MountProps): VueWrapper {
  return mount(LoopProgress, { props });
}

function mockMatchMedia(matches: boolean): void {
  const factory = (query: string): MediaQueryList =>
    ({
      matches,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(() => true),
    }) as unknown as MediaQueryList;
  vi.stubGlobal('matchMedia', vi.fn(factory));
}

enableAutoUnmount(afterEach);

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('LoopProgress —— 可见有界计划 ③', () => {
  it('渲染「分 N 步」开场 + 有界上界 + 每步 checkbox 状态（文字标签，非颜色单通道）', () => {
    const plan = makePlan(
      [
        makeStep('s1', 'plan', 'done', { label: '拆解计划' }),
        makeStep('s2', 'retrieve', 'active', { label: '查证课件' }),
        makeStep('s3', 'compose', 'pending', { label: '组织作答' }),
      ],
      4,
    );
    const wrapper = mountProgress({ plan, status: 'streaming' });

    const intro = wrapper.find('[data-testid="loop-plan-intro"]');
    expect(intro.text()).toContain('3 步');
    expect(intro.text()).toContain('有界');
    expect(intro.text()).toContain('4 步');

    const steps = wrapper.findAll('[data-testid="loop-step"]');
    expect(steps).toHaveLength(3);
    expect(steps[0]?.classes()).toContain('loop-step--done');
    expect(steps[1]?.classes()).toContain('loop-step--active');
    expect(steps[2]?.classes()).toContain('loop-step--pending');

    const statusLabels = wrapper.findAll('[data-testid="loop-step-status"]').map((n) => n.text());
    expect(statusLabels).toEqual(['已完成', '进行中', '待办']);
  });

  it('plan 为空不渲染计划列表', () => {
    const wrapper = mountProgress({ plan: null });
    expect(wrapper.find('[data-testid="loop-plan"]').exists()).toBe(false);
  });
});

describe('LoopProgress —— 工具调用时间线复用 ToolCallTimeline ③', () => {
  it('带 toolName 的步骤投影进 ToolCallTimeline（中文工位名 + 运行中态）', () => {
    const plan = makePlan([
      makeStep('s1', 'retrieve', 'done', { label: '查证课件', toolName: 'HANDOUT', durationMs: 120 }),
      makeStep('s2', 'act', 'active', { label: '联网查证', toolName: 'BROWSE' }),
    ]);
    const wrapper = mountProgress({ plan, status: 'streaming' });

    const timeline = wrapper.find('[data-testid="tool-call-timeline"]');
    expect(timeline.exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="tool-call-step"]')).toHaveLength(2);
    expect(timeline.text()).toContain('查证课件');
    expect(timeline.text()).toContain('联网查证');
    expect(timeline.text()).toContain('运行中');
  });

  it('没有工具步骤时不渲染时间线', () => {
    const plan = makePlan([makeStep('s1', 'plan', 'done', { label: '拆解计划' })]);
    const wrapper = mountProgress({ plan, status: 'done' });
    expect(wrapper.find('[data-testid="tool-call-timeline"]').exists()).toBe(false);
  });
});

describe('LoopProgress —— 证据引用 chips 分区 ③', () => {
  it('课件引用（可点跳书房）与外部引用（标注「外部内容，非课件」）分区呈现', async () => {
    const plan = makePlan([
      makeStep('s1', 'retrieve', 'done', {
        citations: [
          { kind: 'courseware', ref: '12', label: '第 12 页' },
          {
            kind: 'external',
            ref: 'https://docs.python.org/zh-cn/3/library/functions.html#input',
            label: 'Python 官方文档：input',
          },
        ],
      }),
    ]);
    const wrapper = mountProgress({ plan, status: 'done' });

    const courseware = wrapper.find('[data-testid="loop-evidence-courseware"]');
    const external = wrapper.find('[data-testid="loop-evidence-external"]');
    expect(courseware.exists()).toBe(true);
    expect(external.exists()).toBe(true);

    const cwChip = wrapper.find('[data-testid="loop-cite-courseware"]');
    expect(cwChip.text()).toContain('第 12 页');
    await cwChip.trigger('click');
    const emitted = wrapper.emitted('select-citation');
    expect(emitted).toBeTruthy();
    expect(emitted?.[0]?.[0]).toMatchObject({ kind: 'courseware', ref: '12' });

    const extChip = wrapper.find('[data-testid="loop-cite-external"]');
    expect(extChip.attributes('href')).toContain('docs.python.org');
    expect(extChip.attributes('rel')).toContain('noopener');
    expect(external.text()).toContain('外部内容，非课件');
  });

  it('无引用时不渲染证据区', () => {
    const plan = makePlan([makeStep('s1', 'plan', 'done')]);
    const wrapper = mountProgress({ plan, status: 'done' });
    expect(wrapper.find('[data-testid="loop-evidence"]').exists()).toBe(false);
  });
});

describe('LoopProgress —— 仅 reasonSummary，绝不渲染 CoT ⑤', () => {
  it('展示一句可审计摘要，不渲染任何原始推理链字段', () => {
    const sketchy = {
      ...makeStep('s1', 'critique', 'done', { label: '引用三审', reasonSummary: '引用三审通过' }),
      reasoning: '【CoT】第一步推理…第二步推理…机密逐步链',
    } as unknown as LoopStep;
    const wrapper = mountProgress({ plan: makePlan([sketchy]), status: 'done' });

    expect(wrapper.find('[data-testid="loop-step-reason"]').text()).toContain('引用三审通过');
    expect(wrapper.text()).not.toContain('机密逐步链');
    expect(wrapper.text()).not.toContain('CoT');
  });
});

describe('LoopProgress —— 错误态显式 + fallback ③', () => {
  it('失败步红色徽章 + 可读 error + fallback（BROWSE→视频）；run 级错误进 role=alert', () => {
    const plan = makePlan([
      makeStep('s1', 'act', 'failed', {
        label: '联网查证',
        toolName: 'BROWSE',
        error: '白名单外域，已拒绝',
      }),
    ]);
    const wrapper = mountProgress({ plan, status: 'error', runError: '本轮未达证据标准，已诚实降级' });

    const stepEl = wrapper.find('[data-testid="loop-step"]');
    expect(stepEl.classes()).toContain('loop-step--failed');
    expect(wrapper.find('[data-testid="loop-step-status"]').text()).toBe('失败');
    expect(wrapper.find('[data-testid="loop-step-error"]').text()).toContain('白名单外域');
    expect(wrapper.find('[data-testid="loop-step-fallback"]').text()).toContain('视频');

    const alert = wrapper.find('[data-testid="loop-error"]');
    expect(alert.attributes('role')).toBe('alert');
    expect(alert.text()).toContain('未达证据标准');
  });
});

describe('LoopProgress —— 流式可中断 ③', () => {
  it('streaming 时显示停止按钮，点击 emit stop；非 streaming 不显示', async () => {
    const wrapper = mountProgress({
      plan: makePlan([makeStep('s1', 'compose', 'active')]),
      status: 'streaming',
    });
    const stop = wrapper.find('[data-testid="loop-stop"]');
    expect(stop.exists()).toBe(true);
    await stop.trigger('click');
    expect(wrapper.emitted('stop')).toBeTruthy();

    const done = mountProgress({
      plan: makePlan([makeStep('s1', 'compose', 'done')]),
      status: 'done',
    });
    expect(done.find('[data-testid="loop-stop"]').exists()).toBe(false);
  });
});

describe('LoopProgress —— 无障碍 aria-live + 键盘折叠 ④', () => {
  it('状态区 role=status / aria-live=polite 播报当前进行中步骤', () => {
    const plan = makePlan([makeStep('s1', 'retrieve', 'active', { label: '查证课件' })]);
    const wrapper = mountProgress({ plan, status: 'streaming' });

    const live = wrapper.find('[data-testid="loop-live"]');
    expect(live.attributes('role')).toBe('status');
    expect(live.attributes('aria-live')).toBe('polite');
    expect(live.text()).toContain('查证课件');
  });

  it('每步标题为原生 button（键盘可达），可折叠并切换 aria-expanded', async () => {
    const plan = makePlan([
      makeStep('s1', 'retrieve', 'active', { label: '查证课件', reasonSummary: '检索进行中' }),
    ]);
    const wrapper = mountProgress({ plan, status: 'streaming' });

    const toggle = wrapper.find('[data-testid="loop-step-toggle"]');
    expect(toggle.element.tagName).toBe('BUTTON');
    expect(toggle.attributes('aria-expanded')).toBe('true');
    expect(wrapper.find('[data-testid="loop-step-detail"]').isVisible()).toBe(true);

    await toggle.trigger('click');
    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(wrapper.find('[data-testid="loop-step-detail"]').isVisible()).toBe(false);
  });
});

describe('LoopProgress —— 动效经 AnimAdapter 注入 + reduceMotion 退化 ⑥', () => {
  it('active→playStepIn，done→playStepDone，闭环完成→celebrate', async () => {
    const anim = makeAnim(false);
    const wrapper = mountProgress({
      plan: makePlan([makeStep('s1', 'retrieve', 'active')]),
      status: 'streaming',
      anim,
    });
    await flushPromises();
    expect(anim.playStepIn).toHaveBeenCalledTimes(1);

    await wrapper.setProps({ plan: makePlan([makeStep('s1', 'retrieve', 'done')]), status: 'streaming' });
    await flushPromises();
    expect(anim.playStepDone).toHaveBeenCalledTimes(1);
    expect(anim.playStepDone).toHaveBeenCalledWith(expect.anything(), 'done');

    await wrapper.setProps({ status: 'done' });
    await flushPromises();
    expect(anim.celebrate).toHaveBeenCalledTimes(1);
  });

  it('adapter.reduceMotion()=true → 挂 --reduced 降级类', () => {
    const anim = makeAnim(true);
    const wrapper = mountProgress({ plan: makePlan([makeStep('s1', 'plan', 'pending')]), anim });
    expect(wrapper.find('[data-testid="loop-progress"]').classes()).toContain('loop-progress--reduced');
  });

  it('adapter.reduceMotion()=false 且系统未开启 → 不降级', () => {
    mockMatchMedia(false);
    const anim = makeAnim(false);
    const wrapper = mountProgress({ plan: makePlan([makeStep('s1', 'plan', 'pending')]), anim });
    expect(wrapper.find('[data-testid="loop-progress"]').classes()).not.toContain('loop-progress--reduced');
  });

  it('未注入 adapter 时回退系统 prefers-reduced-motion', () => {
    mockMatchMedia(true);
    const wrapper = mountProgress({ plan: makePlan([makeStep('s1', 'plan', 'pending')]) });
    expect(wrapper.find('[data-testid="loop-progress"]').classes()).toContain('loop-progress--reduced');
  });

  it('完成态含失败步骤时不 celebrate（不在失败轮放鞭炮）', async () => {
    const anim = makeAnim(false);
    const wrapper = mountProgress({
      plan: makePlan([makeStep('s1', 'act', 'active')]),
      status: 'streaming',
      anim,
    });
    await flushPromises();
    await wrapper.setProps({
      plan: makePlan([makeStep('s1', 'act', 'failed', { error: '超时' })]),
      status: 'error',
    });
    await flushPromises();
    expect(anim.celebrate).not.toHaveBeenCalled();
  });
});
