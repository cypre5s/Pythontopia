import { describe, expect, it } from 'vitest';
import { useLibrarySplitLayout } from '@/composables/useLibrarySplitLayout';

describe('useResizableSplitLayout presets', () => {
  it('图书馆：左右固定栏 + 拖拽', () => {
    const split = useLibrarySplitLayout();
    expect(split.leftWidth.value).toBeGreaterThanOrEqual(200);
    expect(split.rightWidth.value).toBeGreaterThanOrEqual(200);
    expect(typeof split.onSplitterPointerDown).toBe('function');
    expect(split.paneStyle.left().width).toMatch(/^\d+px$/);
  });
});
