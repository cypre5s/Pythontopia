import { useResizableSplitLayout } from '@/composables/useResizableSplitLayout';

/** 图书馆三栏：左导航 | 中阅读（flex）| 右检索 */
export function useLibrarySplitLayout() {
  return useResizableSplitLayout({
    storageKey: 'pyt_library_split_v1',
    left: { default: 280, min: 200, max: 480 },
    right: { default: 260, min: 200, max: 420 },
    mainMin: 300,
  });
}
