import { describe, expect, it } from 'vitest';
import { useLibrarySplitLayout } from '@/composables/useLibrarySplitLayout';
import { useWorkshopSplitLayout } from '@/composables/useWorkshopSplitLayout';

describe('useResizableSplitLayout presets', () => {
  it('图书馆：左右固定栏 + 拖拽', () => {
    const split = useLibrarySplitLayout();
    expect(split.leftWidth.value).toBeGreaterThanOrEqual(200);
    expect(split.rightWidth.value).toBeGreaterThanOrEqual(200);
    expect(typeof split.onSplitterPointerDown).toBe('function');
    expect(split.paneStyle.left().width).toMatch(/^\d+px$/);
  });

  it('工坊：左中固定栏 + 详情 flex', () => {
    const split = useWorkshopSplitLayout();
    expect(split.leftWidth.value).toBeGreaterThanOrEqual(300);
    expect(split.centerWidth.value).toBeGreaterThanOrEqual(200);
    expect(split.paneStyle.center().width).toMatch(/^\d+px$/);
  });
});
