// R-13 Phase 6 迭代 28：FastTravelPanel spec。
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import FastTravelPanel from '@/components/hud/FastTravelPanel.vue';
import { useWorldStore } from '@stores/world';

function setWorldPlayer(mapKey: string): void {
  const world = useWorldStore();
  world.player = {
    playerStateId: 1,
    avatarKey: 'student_default',
    mapKey,
    tileX: 0,
    tileY: 0,
    direction: 'DOWN',
  };
}

describe('FastTravelPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('默认不可见', () => {
    const wrapper = mount(FastTravelPanel);
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
  });

  it('监听 CustomEvent 打开后渲染 hub 路线（R-14 单 hub）', async () => {
    setWorldPlayer('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    await flushPromises();
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="fast-travel-hub"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('当前所在地图（hub）按钮被 disabled', async () => {
    setWorldPlayer('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    const hubBtn = wrapper.find('[data-testid="fast-travel-hub"]');
    expect(hubBtn.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('单 hub：外围图路线已移除', async () => {
    setWorldPlayer('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-farm"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="fast-travel-mine"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
