import { useResizableSplitLayout } from '@/composables/useResizableSplitLayout';

/** 工坊三栏：生成配置 | 书房列表 | 详情预览（flex） */
export function useWorkshopSplitLayout() {
  return useResizableSplitLayout({
    storageKey: 'pyt_workshop_split_v1',
    left: { default: 380, min: 300, max: 560 },
    center: { default: 260, min: 200, max: 400 },
    mainMin: 280,
  });
}
