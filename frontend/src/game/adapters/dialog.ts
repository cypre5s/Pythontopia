// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
import { useDialogStore } from '@stores/dialog';

/**
 * 打开指定 NPC 的对话树（异步 fetch /data/npcs/<key>/{meta,dialog}.json）。
 *
 * TownScene 等场景仍以同步签名调用，store 内部会自行 await 加载，
 * 失败时通过 loadError 在 DialogTree 上展示，不会抛出。
 */
export function openNpcDialog(npcKey: string): void {
  void useDialogStore().openWithTree(npcKey);
}
