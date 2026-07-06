import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import { useHudStore } from '@stores/hud';

describe('hud store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('默认 state 与 4 个 quick slot 互斥单选 + 顶/底栏 shown', () => {
    const store = useHudStore();
    expect(store.topBar).toBe('shown');
    expect(store.bottomBar).toBe('shown');
    expect(store.activeSlot).toBeNull();
    expect(store.meta).toEqual({
      weather: 'SUNNY',
      dayIndex: 1,
      timeOfDay: 'MORNING',
      season: 'SPRING',
      energy: 100,
      masteryAvg: 0,
    });
  });

  it('toggleSlot 切换激活：null → quest', () => {
    const store = useHudStore();
    store.toggleSlot('quest');
    expect(store.activeSlot).toBe('quest');
  });

  it('toggleSlot 同 slot 再次调用关闭：map → null', () => {
    const store = useHudStore();
    store.toggleSlot('map');
    expect(store.activeSlot).toBe('map');
    store.toggleSlot('map');
    expect(store.activeSlot).toBeNull();
  });

  it('toggleSlot 切换不同 slot：quest → map（互斥替换，不是关闭）', () => {
    const store = useHudStore();
    store.toggleSlot('quest');
    store.toggleSlot('map');
    expect(store.activeSlot).toBe('map');
    store.toggleSlot('skill');
    expect(store.activeSlot).toBe('skill');
  });

  it('toggleSlot 在 bottomBar=hidden 时被忽略（避免程序化误弹）', () => {
    const store = useHudStore();
    store.setVisibility('bottom', 'hidden');
    store.toggleSlot('map');
    expect(store.activeSlot).toBeNull();
    expect(store.canToggleSlot).toBe(false);
  });

  it('closeAll 把 activeSlot 强制清空', () => {
    const store = useHudStore();
    store.toggleSlot('dialog');
    expect(store.activeSlot).toBe('dialog');
    store.closeAll();
    expect(store.activeSlot).toBeNull();
  });

  it('setVisibility 分别控制 top / bottom 三态', () => {
    const store = useHudStore();
    store.setVisibility('top', 'collapsed');
    store.setVisibility('bottom', 'hidden');
    expect(store.topBar).toBe('collapsed');
    expect(store.bottomBar).toBe('hidden');
    store.setVisibility('top', 'hidden');
    store.setVisibility('bottom', 'shown');
    expect(store.topBar).toBe('hidden');
    expect(store.bottomBar).toBe('shown');
  });

  it('syncMeta 局部 patch 不覆盖未传字段', () => {
    const store = useHudStore();
    store.syncMeta({ dayIndex: 7, energy: 42 });
    expect(store.meta.dayIndex).toBe(7);
    expect(store.meta.energy).toBe(42);
    expect(store.meta.weather).toBe('SUNNY');
    expect(store.meta.season).toBe('SPRING');
    store.syncMeta({ weather: 'RAINY' });
    expect(store.meta.weather).toBe('RAINY');
    expect(store.meta.dayIndex).toBe(7);
  });

  it('reset 把 state 拨回默认（含 activeSlot / visibility / meta）', () => {
    const store = useHudStore();
    store.toggleSlot('skill');
    store.setVisibility('top', 'hidden');
    store.setVisibility('bottom', 'collapsed');
    store.syncMeta({ dayIndex: 30 });

    store.reset();

    expect(store.topBar).toBe('shown');
    expect(store.bottomBar).toBe('shown');
    expect(store.activeSlot).toBeNull();
    expect(store.meta.dayIndex).toBe(1);
    expect(store.meta.weather).toBe('SUNNY');
  });

  it('4 个面板 getter（quest/dialog/map/skill）随 activeSlot 切换返回对应布尔', () => {
    const store = useHudStore();
    expect(store.isQuestOpen).toBe(false);
    expect(store.isDialogOpen).toBe(false);
    expect(store.isMapOpen).toBe(false);
    expect(store.isSkillOpen).toBe(false);

    store.toggleSlot('quest');
    expect(store.isQuestOpen).toBe(true);
    expect(store.isDialogOpen).toBe(false);

    store.toggleSlot('dialog');
    expect(store.isQuestOpen).toBe(false);
    expect(store.isDialogOpen).toBe(true);

    store.toggleSlot('map');
    expect(store.isMapOpen).toBe(true);
    expect(store.isDialogOpen).toBe(false);

    store.toggleSlot('skill');
    expect(store.isSkillOpen).toBe(true);
    expect(store.isMapOpen).toBe(false);
  });

  it('canToggleSlot 严格反映 bottomBar 状态（shown / collapsed 允许，hidden 禁止）', () => {
    const store = useHudStore();
    expect(store.canToggleSlot).toBe(true);
    store.setVisibility('bottom', 'collapsed');
    expect(store.canToggleSlot).toBe(true);
    store.setVisibility('bottom', 'hidden');
    expect(store.canToggleSlot).toBe(false);
    store.setVisibility('bottom', 'shown');
    expect(store.canToggleSlot).toBe(true);
  });
});
