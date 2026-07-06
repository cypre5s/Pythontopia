/** 客户端安装包下载计数：guide「下载原生客户端」三端按钮共用。 */
import { onMounted, ref } from 'vue';
import { getDownloadStat, hitDownload } from '@/api/downloads';

export function useDownloadStats(statKeys: readonly string[]) {
  const downloadCounts = ref<Record<string, number | null>>({});

  onMounted(async () => {
    if (statKeys.length === 0) return;
    const entries = await Promise.all(
      statKeys.map(async (key): Promise<[string, number | null]> => {
        try {
          return [key, (await getDownloadStat(key)).count];
        } catch {
          return [key, null];
        }
      }),
    );
    downloadCounts.value = { ...downloadCounts.value, ...Object.fromEntries(entries) };
  });

  /** 点击下载：乐观 +1，后端 best-effort 计数，不阻断真实下载跳转。 */
  function onDownloadClick(key: string): void {
    const current = downloadCounts.value[key] ?? 0;
    downloadCounts.value = { ...downloadCounts.value, [key]: current + 1 };
    void hitDownload(key)
      .then((stat) => {
        downloadCounts.value = { ...downloadCounts.value, [key]: stat.count };
      })
      .catch(() => {
        /* 计数失败忽略 */
      });
  }

  return { downloadCounts, onDownloadClick };
}
