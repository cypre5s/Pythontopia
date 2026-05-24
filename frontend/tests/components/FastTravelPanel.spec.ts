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

  it('监听 CustomEvent 打开后渲染 5 条路线', async () => {
    setWorldPlayer('town_hub');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    await flushPromises();
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(true);
    const items = wrapper.findAll('[data-testid^="fast-travel-"]');
    // 5 个路线按钮（hub/farm/mine/campus/port）
    expect(items.length).toBeGreaterThanOrEqual(5);
    wrapper.unmount();
  });

  it('当前所在地图按钮被 disabled', async () => {
    setWorldPlayer('farm_north');
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    const farmBtn = wrapper.find('[data-testid="fast-travel-farm"]');
    expect(farmBtn.attributes('disabled')).toBeDefined();
    const hubBtn = wrapper.find('[data-testid="fast-travel-hub"]');
    expect(hubBtn.attributes('disabled')).toBeUndefined();
    wrapper.unmount();
  });

  it('点击非当前路线后更新 worldStore.player.mapKey + 关闭面板', async () => {
    setWorldPlayer('town_hub');
    const world = useWorldStore();
    const wrapper = mount(FastTravelPanel, { attachTo: document.body });
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
    await flushPromises();
    await wrapper.find('[data-testid="fast-travel-mine"]').trigger('click');
    await flushPromises();
    expect(world.player?.mapKey).toBe('mine_south');
    expect(wrapper.find('[data-testid="fast-travel-panel"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
