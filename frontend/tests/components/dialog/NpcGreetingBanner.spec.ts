/**
 * NpcGreetingBanner.spec.ts —— NPC 首句个性化寒暄横幅组件单测（R-15 §8）。
 *
 * 覆盖：
 *   - ready：渲染个性化寒暄文本；
 *   - fallback：渲染「（记忆服务暂不可用）」提示；
 *   - loading：渲染「正在回忆…」低调提示；
 *   - none / idle：不渲染（直接走脚本）；
 *   - dialog 不可见或加载出错时：不渲染。
 *
 * 隔离：直接 setActivePinia + 真实 dialog store，手动写 store 状态（不触发网络/openWithTree）。
 */
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import NpcGreetingBanner from '@/components/dialog/NpcGreetingBanner.vue';
import { useDialogStore } from '@/stores/dialog';

describe('NpcGreetingBanner.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('renders the personalized greeting before the script when ready', () => {
    const store = useDialogStore();
    store.visible = true;
    store.greetingStatus = 'ready';
    store.greetingText = '上次 for 循环错了 3 次，今天去矿洞复盘？';

    const wrapper = mount(NpcGreetingBanner);

    expect(wrapper.find('[data-testid="npc-greeting-banner"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="npc-greeting"]').text()).toContain('for 循环错了 3 次');
    expect(wrapper.find('[data-testid="npc-greeting-fallback"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="npc-greeting-loading"]').exists()).toBe(false);
  });

  it('renders the memory-unavailable notice on fallback', () => {
    const store = useDialogStore();
    store.visible = true;
    store.greetingStatus = 'fallback';

    const wrapper = mount(NpcGreetingBanner);

    expect(wrapper.find('[data-testid="npc-greeting-fallback"]').text()).toContain('记忆服务暂不可用');
    expect(wrapper.find('[data-testid="npc-greeting"]').exists()).toBe(false);
  });

  it('renders a loading hint while fetching in parallel', () => {
    const store = useDialogStore();
    store.visible = true;
    store.greetingStatus = 'loading';

    const wrapper = mount(NpcGreetingBanner);

    expect(wrapper.find('[data-testid="npc-greeting-loading"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="npc-greeting"]').exists()).toBe(false);
  });

  it('renders nothing for the none status (new learner, no memory)', () => {
    const store = useDialogStore();
    store.visible = true;
    store.greetingStatus = 'none';

    const wrapper = mount(NpcGreetingBanner);

    expect(wrapper.find('[data-testid="npc-greeting-banner"]').exists()).toBe(false);
  });

  it('renders nothing for the idle status (default)', () => {
    const store = useDialogStore();
    store.visible = true;

    const wrapper = mount(NpcGreetingBanner);

    expect(wrapper.find('[data-testid="npc-greeting-banner"]').exists()).toBe(false);
  });

  it('renders nothing when the dialog is not visible', () => {
    const store = useDialogStore();
    store.visible = false;
    store.greetingStatus = 'ready';
    store.greetingText = '你好';

    const wrapper = mount(NpcGreetingBanner);

    expect(wrapper.find('[data-testid="npc-greeting-banner"]').exists()).toBe(false);
  });

  it('hides when the dialog has a load error', () => {
    const store = useDialogStore();
    store.visible = true;
    store.greetingStatus = 'ready';
    store.greetingText = '你好';
    store.loadError = 'NPC 数据加载失败：x';

    const wrapper = mount(NpcGreetingBanner);

    expect(wrapper.find('[data-testid="npc-greeting-banner"]').exists()).toBe(false);
  });
});
