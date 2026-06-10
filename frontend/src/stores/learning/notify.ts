import type { MasterySnapshotDto } from '@/api/dto/MasterySnapshotDto';

/**
 * learning 各切片向其它 store（mainQuest / mood / probe）发的跨域信号。
 * 统一用动态 import 解耦：learning ↔ mainQuest 等存在反向静态依赖，
 * 静态 import 会成环；信号全部 fire-and-forget、失败静默，绝不阻断主流程。
 */

// 训练真题判定通过后，推进主线 submission 子任务。
export function notifyMainQuestSubmission(payload: {
  problemId?: number | string;
  kcCode?: string;
}): void {
  if (typeof window === 'undefined') return;
  void import('@stores/mainQuest').then((mod) => {
    try {
      mod.useMainQuestStore().onSubmissionPassed(payload);
    } catch {
      /* mainQuest store 不可用时静默 */
    }
  });
}

// 工坊资源生成成功后，触发主线 evidence_type='resource_consumed' 检测。
export function notifyMainQuestResource(
  resourceType: string,
  kcCodes: readonly string[],
): void {
  if (typeof window === 'undefined') return;
  void import('@stores/mainQuest').then((mod) => {
    try {
      mod.useMainQuestStore().onResourceCreated(resourceType, kcCodes);
    } catch {
      /* mainQuest store 不可用时静默 */
    }
  });
}

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

// 掌握度快照刷新后，联动主线 kc_mastery「测」子任务。
// 后端 masteryScore 为 0..1；onMasteryReached 阈值（mainQuests thresholdScore）为 0..100，
// 故乘 100 归一到同一标度后再比较。
export function notifyMainQuestMastery(
  snapshots: readonly MasterySnapshotDto[],
): void {
  if (typeof window === 'undefined') return;
  void import('@stores/mainQuest').then((mod) => {
    try {
      const mq = mod.useMainQuestStore();
      for (const snap of snapshots) {
        mq.onMasteryReached(snap.kcCode, Math.round((snap.masteryScore ?? 0) * 100));
      }
    } catch {
      /* mainQuest store 不可用时静默 */
    }
  });
}
