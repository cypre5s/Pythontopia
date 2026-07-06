/**
 * learning 各切片向其它 store（mood / probe）发的跨域信号。
 * 统一用动态 import 解耦：避免反向静态依赖成环；信号全部 fire-and-forget、失败静默，
 * 绝不阻断主流程。
 *
 * R-15 退役旧 mainQuest 后，主线进度改由后端权威 mainline 域（stage 三件套 API）记录，
 * 不再从客户端 evidence 钩子推进，故原 notifyMainQuestSubmission / notifyMainQuestResource /
 * notifyMainQuestMastery 系列已整体移除。
 */

// 一次服务端判题（无论 AC/WA）后刷新世界情绪氛围——连挫触发细雨+助教台阶、逆袭回暖到晴。
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

// 练习失败后请求 fail_react 探针（emotion_persistence 行为信号）。
// probe store 仅置 requested 标记，由世界内 ProbeCard 监听弹出
// （失败发生在训练页、探针卡在世界页，借 store 跨页传信号）。
export function notifyProbeRequest(probeKey: string): void {
  if (typeof window === 'undefined') return;
  void import('@stores/probe').then((mod) => {
    try {
      mod.useProbeStore().request(probeKey);
    } catch {
      /* probe store 不可用时静默 */
    }
  });
}
