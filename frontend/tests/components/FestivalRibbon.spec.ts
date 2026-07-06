// R-13 Phase 6 迭代 27：FestivalRibbon spec。
// R-12 改造后 FestivalRibbon 数据源由 calendar store 切到 hud.meta.dayIndex
// （world store 推进时通过 syncMeta 注入），故测试改为驱动 hud store。
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import FestivalRibbon from '@/components/hud/FestivalRibbon.vue';
import { useHudStore } from '@stores/hud';

describe('FestivalRibbon', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('当日命中节日时显示节日 label 与描述', async () => {
    const hud = useHudStore();
    hud.syncMeta({ dayIndex: 1 });
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    const ribbon = wrapper.find('[data-testid="festival-ribbon"]');
    expect(ribbon.exists()).toBe(true);
    expect(ribbon.text()).toContain('开学日');
  });

  it('非节日日 ribbon 隐藏（R-14：只在当天节日显示，不再常驻倒数）', async () => {
    const hud = useHudStore();
    hud.syncMeta({ dayIndex: 5 }); // day 5 非节日 → 不显示
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    expect(wrapper.find('[data-testid="festival-ribbon"]').exists()).toBe(false);
  });

  it('超出最后一个节日（day > 112）时 ribbon 隐藏', async () => {
    const hud = useHudStore();
    hud.syncMeta({ dayIndex: 200 });
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    expect(wrapper.find('[data-testid="festival-ribbon"]').exists()).toBe(false);
  });
});
