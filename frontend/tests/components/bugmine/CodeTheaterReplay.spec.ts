import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

import CodeTheaterReplay from '@/components/bugmine/CodeTheaterReplay.vue';
import type { CodeTheaterReplayPort, DebugHintLayerInput } from '@/services/codeTheater/debug';
import type { Trace, TraceEvent } from '@/services/codeTheater/types';

function exceptionTrace(): Trace {
  const events: TraceEvent[] = [
    { step: 0, line: 1, kind: 'assign', varName: 'a', value: '10', scope: 'global' },
    { step: 1, line: 2, kind: 'assign', varName: 'b', value: '0', scope: 'global' },
    { step: 2, line: 3, kind: 'exception', excType: 'ZeroDivisionError', message: 'division by zero' },
  ];
  return {
    version: 1,
    source: 'a = 10\nb = 0\nc = a / b\n',
    events,
    stdout: '',
    truncated: false,
    stepCount: events.length,
  };
}

function makePort(): CodeTheaterReplayPort {
  return {
    load: vi.fn(),
    play: vi.fn(),
    pause: vi.fn(),
    seekToStep: vi.fn(),
    highlightLine: vi.fn(),
  };
}

describe('CodeTheaterReplay', () => {
  it('渲染诊断焦点：现象句 + 出错行高亮 + 变量快照', () => {
    const wrapper = mount(CodeTheaterReplay, { props: { trace: exceptionTrace() } });

    const phenomenon = wrapper.find('[data-testid="focus-phenomenon"]');
    expect(phenomenon.text()).toContain('第 3 行');
    expect(phenomenon.text()).toContain('ZeroDivisionError');
    expect(wrapper.find('[data-testid="focus-kind"]').text()).toBe('运行时绊倒');

    const focusLine = wrapper.find('.code__line--focus');
    expect(focusLine.exists()).toBe(true);
    expect(focusLine.attributes('data-line')).toBe('3');

    const snapshot = wrapper.find('[data-testid="variable-snapshot"]');
    expect(snapshot.text()).toContain('a');
    expect(snapshot.text()).toContain('10');
    expect(snapshot.text()).toContain('b');
  });

  it('回放时间线包含切片步，点击可选中并驱动端口 seek', async () => {
    const port = makePort();
    const wrapper = mount(CodeTheaterReplay, { props: { trace: exceptionTrace(), port } });

    const step = wrapper.find('[data-testid="replay-step-1"]');
    expect(step.exists()).toBe(true);
    await step.trigger('click');
    expect(port.seekToStep).toHaveBeenCalledWith(1);
  });

  it('引导阶梯逐阶解锁，绝不一次性铺开', async () => {
    const wrapper = mount(CodeTheaterReplay, { props: { trace: exceptionTrace() } });

    expect(wrapper.find('[data-testid="guide-redline"]').text()).toContain('只引导不给答案');
    expect(wrapper.find('[data-testid="guide-step-1"]').exists()).toBe(false);

    await wrapper.find('[data-testid="reveal-hint"]').trigger('click');
    expect(wrapper.find('[data-testid="guide-step-1"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="guide-step-2"]').exists()).toBe(false);

    await wrapper.find('[data-testid="reveal-hint"]').trigger('click');
    expect(wrapper.find('[data-testid="guide-step-2"]').exists()).toBe(true);
  });

  it('未注入舞台端口时，重演按钮禁用并提示 Phase 2 接线', () => {
    const wrapper = mount(CodeTheaterReplay, { props: { trace: exceptionTrace() } });
    const button = wrapper.find('[data-testid="replay-on-stage"]');
    expect(button.attributes('disabled')).toBeDefined();
    expect(wrapper.find('[data-testid="stage-pending"]').exists()).toBe(true);
  });

  it('注入舞台端口时，重演调用 load → highlightLine → play', async () => {
    const port = makePort();
    const wrapper = mount(CodeTheaterReplay, { props: { trace: exceptionTrace(), port } });

    expect(wrapper.find('[data-testid="stage-pending"]').exists()).toBe(false);
    await wrapper.find('[data-testid="replay-on-stage"]').trigger('click');
    await flushPromises();

    expect(port.load).toHaveBeenCalledTimes(1);
    expect(port.highlightLine).toHaveBeenCalledWith(3);
    expect(port.play).toHaveBeenCalledTimes(1);
  });

  it('DebugAgent 提示疑似泄漏完整代码时，前端展示脱敏徽标且不外泄答案', async () => {
    const hintLayers: DebugHintLayerInput[] = [
      { layer: 1, headline: '换述', body: '用自己的话复述目标' },
      { layer: 2, headline: '拆解', body: '看第 2 行变量' },
      {
        layer: 3,
        headline: '指方向',
        body: ['n = int(input())', 'for i in range(n):', '    print(i)'].join('\n'),
      },
    ];
    const wrapper = mount(CodeTheaterReplay, { props: { trace: exceptionTrace(), hintLayers } });

    await wrapper.find('[data-testid="reveal-hint"]').trigger('click');
    await wrapper.find('[data-testid="reveal-hint"]').trigger('click');
    await wrapper.find('[data-testid="reveal-hint"]').trigger('click');

    const thirdStep = wrapper.find('[data-testid="guide-step-3"]');
    expect(thirdStep.exists()).toBe(true);
    expect(thirdStep.find('[data-testid="guide-redacted"]').exists()).toBe(true);
    expect(thirdStep.text()).not.toContain('range(n)');
    expect(thirdStep.text()).toContain('脱敏');
  });

  it('空轨迹渲染空状态', () => {
    const trace: Trace = {
      version: 1,
      source: '',
      events: [],
      stdout: '',
      truncated: false,
      stepCount: 0,
    };
    const wrapper = mount(CodeTheaterReplay, { props: { trace } });
    expect(wrapper.find('[data-testid="diagnostic-focus"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('没有可回放的轨迹');
  });
});
