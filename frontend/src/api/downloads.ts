// 客户端安装包下载统计前端访问层（guide 页「下载 Android App」按钮）。
// 计数端点匿名放行（后端 /api/downloads/**）；安装包二进制由主机 Nginx /downloads/ 直出。

import { http } from './http';

export interface DownloadStatDto {
  key: string;
  count: number;
}

/** 读累计下载次数；失败由调用方兜底（不打断页面）。 */
export async function getDownloadStat(key: string): Promise<DownloadStatDto> {
  const res = await http.get<DownloadStatDto>(`/api/downloads/${encodeURIComponent(key)}`, {
    suppressNetworkToast: true,
  });
  return res.data;
}

/** 点击下载即 +1，返回新计数；失败由调用方兜底（不阻断真正的下载跳转）。 */
export async function hitDownload(key: string): Promise<DownloadStatDto> {
  const res = await http.post<DownloadStatDto>(
    `/api/downloads/${encodeURIComponent(key)}/hit`,
    {},
    { suppressNetworkToast: true },
  );
  return res.data;
}
