// 隔离原因：移动端触感反馈（navigator.vibrate）的唯一封装层，集中做能力检测与开关，
// 业务/场景层只调用语义化的 Haptics.* 预设，不直接碰 navigator。
//
// 设计原则（吸取「过度特效」教训）：可关 + 尊重无障碍——
//   · 默认开启，可经 setHapticsEnabled(false) 关闭（后续可接入设置面板）；
//   · prefers-reduced-motion: reduce 时自动禁用（震动也是一种运动反馈）；
//   · 设备不支持 navigator.vibrate（多数桌面/iOS Safari）时静默跳过。

let enabled = true;

export function setHapticsEnabled(value: boolean): void {
  enabled = value;
}

export function isHapticsEnabled(): boolean {
  return enabled;
}

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/** 触发一次震动；pattern 为毫秒或毫秒数组（同 navigator.vibrate）。不可用/已关/减弱动效时静默跳过。 */
export function vibrate(pattern: number | number[]): void {
  if (!enabled || prefersReducedMotion()) {
    return;
  }
  if (typeof navigator === 'undefined' || typeof navigator.vibrate !== 'function') {
    return;
  }
  try {
    navigator.vibrate(pattern);
  } catch {
    // 个别浏览器在非用户手势上下文会抛错，忽略。
  }
}

/** 语义化预设，统一各处触感强度。 */
export const Haptics = {
  /** 轻点：交互确认（按 E 与 NPC 对话等）。 */
  tap(): void {
    vibrate(10);
  },
  /** 撞击：撞墙 / 撞 NPC（与顿帧同帧触发）。 */
  bump(): void {
    vibrate(18);
  },
  /** 成功：任务完成 / 掌握达成等正反馈。 */
  success(): void {
    vibrate([12, 40, 24]);
  },
} as const;
