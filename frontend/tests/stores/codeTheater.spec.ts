import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useCodeTheaterStore } from '@stores/codeTheater';

describe('codeTheater store · 自由可视化交接', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('默认无待消费载荷', () => {
    const store = useCodeTheaterStore();
    expect(store.pendingFreeRun).toBeNull();
    expect(store.consumeFreeRun()).toBeNull();
  });

  it('setFreeRun 后可被 consume 一次；consume 即清空（刷新/重复进入安全降级）', () => {
    const store = useCodeTheaterStore();
    store.setFreeRun({ code: 'print(1)', stdin: '3\n', label: '两数之和' });
    expect(store.pendingFreeRun).toEqual({ code: 'print(1)', stdin: '3\n', label: '两数之和' });

    const first = store.consumeFreeRun();
    expect(first).toEqual({ code: 'print(1)', stdin: '3\n', label: '两数之和' });
    expect(store.pendingFreeRun).toBeNull();
    expect(store.consumeFreeRun()).toBeNull();
  });

  it('setFreeRun 存副本，后写覆盖前写', () => {
    const store = useCodeTheaterStore();
    store.setFreeRun({ code: 'a', stdin: '', label: 'x' });
    store.setFreeRun({ code: 'b', stdin: '1', label: 'y' });
    expect(store.consumeFreeRun()).toEqual({ code: 'b', stdin: '1', label: 'y' });
  });
});
