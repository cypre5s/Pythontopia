/**
 * learning 各切片向其它 store（mood / probe）发的跨域信号。
 * 统一用动态 import 解耦：避免反向静态依赖成环；信号全部 fire-and-forget、失败静默，
 * 绝不阻断主流程。
 *
 * R-15 退役旧 mainQuest 后，主线进度改由后端权威 mainline 域（stage 三件套 API）记录，
 * 不再从客户端 evidence 钩子推进，故原 notifyMainQuestSubmission / notifyMainQuestResource /
 * notifyMainQuestMastery 系列已整体移除。
 */

// 正式判题后刷新世界状态里的客观连击；氛围字段仅为固定中性兼容值。
export function notifyMoodRefresh(): void {
  if (typeof window === 'undefined') return;
  void import('@stores/mood').then((mod) => {
    try {
      void mod.useMoodStore().refresh();
    } catch {
      /* mood store 不可用时静默 */
    }
  });
}
