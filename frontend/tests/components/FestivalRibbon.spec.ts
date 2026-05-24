// R-13 Phase 6 迭代 27：FestivalRibbon spec。
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import FestivalRibbon from '@/components/hud/FestivalRibbon.vue';
import { useCalendarStore } from '@stores/calendar';
import type { WorldCalendarDto } from '@/api/dto/WorldCalendarDto';

function makeCalendar(dayIndex: number): WorldCalendarDto {
  return {
    dayIndex,
    weekday: 'MONDAY',
    monthIndex: Math.ceil(dayIndex / 28),
    season: 'spring',
    timeOfDay: 'MORNING',
    weather: 'SUNNY',
  };
}

describe('FestivalRibbon', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('当日命中节日时显示节日 label 与描述', async () => {
    const calendar = useCalendarStore();
    calendar.setBootstrap(makeCalendar(1), []);
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    const ribbon = wrapper.find('[data-testid="festival-ribbon"]');
    expect(ribbon.exists()).toBe(true);
    expect(ribbon.text()).toContain('开学日');
  });

  it('非节日日显示 upcoming festival 倒数提示', async () => {
    const calendar = useCalendarStore();
    calendar.setBootstrap(makeCalendar(5), []); // day 5 → upcoming day 14 春耕祭
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    expect(wrapper.text()).toContain('春耕祭');
    expect(wrapper.text()).toContain('9 天');
  });

  it('超出最后一个节日（day > 112）时 ribbon 隐藏', async () => {
    const calendar = useCalendarStore();
    calendar.setBootstrap(makeCalendar(200), []);
    const wrapper = mount(FestivalRibbon);
    await flushPromises();
    expect(wrapper.find('[data-testid="festival-ribbon"]').exists()).toBe(false);
  });
});
