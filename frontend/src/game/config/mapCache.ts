/**
 * 地图数据（`/assets/maps/*.json`）的版本号与取址。
 *
 * 为什么需要：部署机外层 nginx 对静态 json 加了 `max-age=604800`（7 天）。地图文件名
 * 固定为 `town_hub.json` 时，换图后浏览器会继续用旧缓存，玩家看到的是上一版小镇
 * （2026-10 R-14 换图时实际踩过：热替换成功但画面没变）。加 `?v=` 后缓存键随版本变化，
 * 无需改名也无需改 nginx。
 *
 * 规则：**每次改动 `scripts/generate-maps.mjs` 的输出（地图 JSON）就把版本号 +1。**
 */
export const MAP_DATA_REVISION = 2;

export function mapDataUrl(fileName: string): string {
  return `/assets/maps/${fileName}?v=${MAP_DATA_REVISION}`;
}
