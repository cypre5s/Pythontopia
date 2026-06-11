/**
 * AgentStudioStream.spec.ts —— 多智能体「工作室」双层思考链单测（Track D / SA4）。
 *
 * 覆盖：
 *   ① 缩略默认 + 点击展开切换（含 defaultExpanded）；
 *   ② role_key → 角色名 / 复用 NPC 头像映射，未知 node 回退兜底人物；
 *   ③ 头像 @error → emoji 兜底（不白屏）；
 *   ④ percent 进度文本（取最新、防 NaN、越界钳制）+ 无 percent 走「N/M 步」；
 *   ⑤ streaming / done / error 三态（spark 旋转、流式光标、红框 + 错误行 + 末步标失败）；
 *   外加：相邻同角色折叠、空 traces 占位行（边角态）。
 *
 * 纯展示组件：不依赖 store / 网络，直接喂 props（traces/status/...）断言渲染。
 */
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import AgentStudioStream from '@/components/workshop/AgentStudioStream.vue';
import type { SsePlan, SseTraceEvent } from '@/composables/useSseStream';

function trace(over: Partial<SseTraceEvent> = {}): SseTraceEvent {
  return { node: 'designer', status: 'running', elapsed_ms: 0, ...over };
}

const HEAD = '/assets/pixel/portraits/heads';

describe('AgentStudioStream · 双层思考链', () => {
  // ① 缩略默认 + 展开切换
  it('缩略态默认渲染人物卡，点击「详情」展开全员流水线', async () => {
    const wrapper = mount(AgentStudioStream, {
      props: {
        traces: [trace({ role_key: 'illustrator', message: '绘制第 1 张' })],
        status: 'streaming',
      },
    });

    expect(wrapper.find('[data-testid="agent-studio-brief"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="agent-studio-lines"]').exists()).toBe(false);

    const toggle = wrapper.find('[data-testid="agent-studio-toggle"]');
    expect(toggle.attributes('aria-expanded')).toBe('false');
    await toggle.trigger('click');

    expect(wrapper.find('[data-testid="agent-studio-brief"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="agent-studio-lines"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="agent-studio-toggle"]').attributes('aria-expanded')).toBe('true');
  });

  it('defaultExpanded=true 时初始即展开', () => {
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [trace({ role_key: 'illustrator' })], status: 'streaming', defaultExpanded: true },
    });

    expect(wrapper.find('[data-testid="agent-studio-lines"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="agent-studio-brief"]').exists()).toBe(false);
  });

  // ② role 映射 + 未知回退
  it('role_key 映射到中文角色名与复用的小镇 NPC 头像', () => {
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [trace({ role_key: 'illustrator' })], status: 'streaming' },
    });

    expect(wrapper.find('[data-testid="agent-studio-brief"]').text()).toContain('插画师');
    const img = wrapper.find('.studio__face-img');
    expect(img.attributes('src')).toBe(`${HEAD}/portrait_gardener_head.png`);
    expect(img.attributes('alt')).toBe('插画师');
  });

  it('后端未发 role_key 时按 node 名回退推断（narrator）', () => {
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [trace({ node: 'tts_synthesize' })], status: 'streaming' },
    });

    expect(wrapper.find('[data-testid="agent-studio-brief"]').text()).toContain('配音师');
    expect(wrapper.find('.studio__face-img').attributes('src')).toBe(`${HEAD}/portrait_merchant_head.png`);
  });

  it('未知 node 且无 role_key → 回退「工作室成员」兜底人物', () => {
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [trace({ node: 'mystery_node' })], status: 'streaming' },
    });

    expect(wrapper.find('[data-testid="agent-studio-brief"]').text()).toContain('工作室成员');
    expect(wrapper.find('.studio__face-img').attributes('src')).toBe(
      `${HEAD}/portrait_workshop_master_head.png`,
    );
  });

  // ③ 头像缺图回退 emoji
  it('头像加载失败 @error → 回退 emoji（不白屏）', async () => {
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [trace({ role_key: 'illustrator' })], status: 'streaming' },
    });

    const img = wrapper.find('.studio__face-img');
    expect(img.exists()).toBe(true);
    await img.trigger('error');

    expect(wrapper.find('.studio__face-img').exists()).toBe(false);
    expect(wrapper.find('.studio__face-emoji').text()).toBe('🎨');
  });

  // ④ percent 进度
  it('trace 带 percent → 顶部进度取最新有效值', () => {
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [trace({ percent: 42 })], status: 'streaming' },
    });
    expect(wrapper.find('[data-testid="agent-studio-progress"]').text()).toBe('42%');
  });

  it('percent 为 NaN 时跳过，回退上一条有效值；越界钳制到 0–100', () => {
    const skipNan = mount(AgentStudioStream, {
      props: { traces: [trace({ percent: 30 }), trace({ percent: Number.NaN })], status: 'streaming' },
    });
    expect(skipNan.find('[data-testid="agent-studio-progress"]').text()).toBe('30%');

    const clamped = mount(AgentStudioStream, {
      props: { traces: [trace({ percent: 150 })], status: 'streaming' },
    });
    expect(clamped.find('[data-testid="agent-studio-progress"]').text()).toBe('100%');
  });

  it('无 percent 但有 plan → 进度按「N/M 步」', () => {
    const plan: SsePlan = {
      type: 'plan',
      nodes: [
        { node: 'a', agent_label: 'A', kind: 'agent' },
        { node: 'b', agent_label: 'B', kind: 'agent' },
        { node: 'c', agent_label: 'C', kind: 'agent' },
      ],
    };
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [trace({ role_key: 'illustrator' })], status: 'streaming', plan },
    });
    expect(wrapper.find('[data-testid="agent-studio-progress"]').text()).toBe('1/3 步');
  });

  // ⑤ streaming / done / error 三态
  it('streaming 态：spark 旋转 + 缩略态显示流式光标', () => {
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [trace({ role_key: 'illustrator' })], status: 'streaming' },
    });
    expect(wrapper.find('.studio__spark').classes()).toContain('studio__spark--spin');
    expect(wrapper.find('[data-testid="agent-studio-brief"] .studio__cursor').exists()).toBe(true);
  });

  it('done 态：无旋转、无光标，展开后末步显示 ✓', async () => {
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [trace({ role_key: 'illustrator', status: 'finish', elapsed_ms: 900 })], status: 'done' },
    });
    expect(wrapper.find('.studio__spark').classes()).not.toContain('studio__spark--spin');
    expect(wrapper.find('[data-testid="agent-studio-brief"] .studio__cursor').exists()).toBe(false);

    await wrapper.find('[data-testid="agent-studio-toggle"]').trigger('click');
    expect(wrapper.find('.studio__bullet').text()).toBe('✓');
  });

  it('error 态：红框 + 错误行展示 errorMessage，展开后末步标失败 ✗', async () => {
    const wrapper = mount(AgentStudioStream, {
      props: {
        traces: [trace({ role_key: 'narrator' })],
        status: 'error',
        errorMessage: '配音合成失败',
      },
    });

    expect(wrapper.find('[data-testid="agent-studio-stream"]').classes()).toContain('studio--error');
    const errLine = wrapper.find('[data-testid="agent-studio-error"]');
    expect(errLine.exists()).toBe(true);
    expect(errLine.text()).toContain('配音合成失败');

    await wrapper.find('[data-testid="agent-studio-toggle"]').trigger('click');
    expect(wrapper.find('.studio__line--failed').exists()).toBe(true);
    expect(wrapper.find('.studio__bullet').text()).toBe('✗');
  });

  it('error 态缺省 errorMessage 时用兜底文案，且空 traces 不崩', () => {
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [], status: 'error' },
    });
    expect(wrapper.find('[data-testid="agent-studio-error"]').text()).toContain('出了点状况');
  });

  // 边角态：折叠 + 空占位
  it('相邻同角色 trace 折叠为一行、不同角色分行，并回填耗时/中间产物', () => {
    const traces: SseTraceEvent[] = [
      trace({ role_key: 'illustrator', phase: 'start', message: '绘制分镜' }),
      trace({ role_key: 'illustrator', phase: 'finish', status: 'finish', elapsed_ms: 1200, note: '产出 3 张图' }),
      trace({ role_key: 'narrator', message: '合成旁白', elapsed_ms: 800 }),
    ];
    const wrapper = mount(AgentStudioStream, {
      props: { traces, status: 'streaming', defaultExpanded: true },
    });

    const lines = wrapper.findAll('[data-testid="agent-studio-lines"] .studio__line');
    expect(lines).toHaveLength(2);
    expect(lines[0]?.text()).toContain('插画师');
    expect(lines[0]?.text()).toContain('绘制分镜');
    expect(lines[0]?.text()).toContain('1200ms');
    expect(lines[0]?.text()).toContain('产出 3 张图');
    expect(lines[1]?.text()).toContain('配音师');
    expect(lines[1]?.text()).toContain('800ms');
  });

  it('空 traces 展开时给占位行（永不白屏）', () => {
    const wrapper = mount(AgentStudioStream, {
      props: { traces: [], status: 'done', defaultExpanded: true },
    });
    expect(wrapper.find('[data-testid="agent-studio-empty"]').exists()).toBe(true);
  });
});
