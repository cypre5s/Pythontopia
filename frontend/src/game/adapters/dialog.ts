// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
import { useDialogStore } from '@stores/dialog';

export function openNpcDialog(npcKey: string): void {
  useDialogStore().open(npcKey);
}
