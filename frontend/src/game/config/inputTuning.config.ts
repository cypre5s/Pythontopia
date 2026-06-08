// 输入手感调参：集中 walk/run 速度、lerp τ、死区、地图速度倍率等魔术数字。
//
// 隔离原因：纯数据 + 纯函数模块，不 import Phaser、不依赖 InputAdapter /
// PlayerStateMachine，避免循环依赖。配置对象与子对象统一 Object.freeze，外部 import 只读。

export interface InputTuningConfig {
  /** 行走速度（px/s）。 */
  readonly walkSpeed: number;
  /** 跑步速度（px/s），约行走 × 1.5。 */
  readonly runSpeed: number;
  /** 加速 lerp 时间常数（ms）：约 100 ms 跨过 80% 目标。 */
  readonly tauAccelMs: number;
  /** 制动 lerp 时间常数（ms）：比加速更短，主观「松手听话」。 */
  readonly tauBrakeMs: number;
  /** 手柄死区半径，覆盖约 90% 摇杆静态漂移。 */
  readonly gamepadDeadzone: number;
  /** 触屏虚拟摇杆半径（px），覆盖成人拇指可达半径。 */
  readonly touchJoystickRadius: number;
  /** 键盘按键防抖窗口（ms）：浏览器 KeyboardEvent 一般无抖动，仅极少数误触场景
   * （如老旧 USB 键盘）需上调。 */
  readonly keyDebounceMs: number;
  /** 按下方向键 → 像素位移 ≥ 1 px 的延迟硬上限（ms）。 */
  readonly inputLagBudgetMs: number;
  /** 松开方向键 → 速度归 0 的延迟硬上限（ms）。 */
  readonly stopLagBudgetMs: number;
  /** 各地图速度倍率：mine_south 0.8× 给玩家更多时间看 traceback。 */
  readonly sceneSpeedMultiplier: Readonly<Record<string, number>>;
}

export const DEFAULT_INPUT_TUNING: InputTuningConfig = Object.freeze({
  walkSpeed: 120,
  runSpeed: 180,
  tauAccelMs: 60,
  tauBrakeMs: 40,
  gamepadDeadzone: 0.15,
  touchJoystickRadius: 60,
  keyDebounceMs: 8,
  inputLagBudgetMs: 50,
  stopLagBudgetMs: 80,
  sceneSpeedMultiplier: Object.freeze({
    town_hub: 1.0,
    mine_south: 0.8,
  }),
});

/**
 * 对角输入归一化：仅当模长 > 1 时归一到 1，模长 ≤ 1 原样返回。
 * 不无脑归一是为了保留手柄轻推（如模长 0.4）的「轻推慢走」语义，
 * 强行除以 0.4 会把慢走放大成全速。
 */
export function normalizeDiagonal(dx: number, dy: number): { dx: number; dy: number } {
  const mag = Math.sqrt(dx * dx + dy * dy);
  if (mag <= 0) {
    return { dx: 0, dy: 0 };
  }
  if (mag <= 1) {
    return { dx, dy };
  }
  return { dx: dx / mag, dy: dy / mag };
}

/**
 * 指数 lerp 实现 S 曲线手感。
 * α = 1 - exp(-Δt/τ)，τ 越小越「跟手」。
 * 经验：τ=60 时 Δt=100 → α≈0.81，即「按下 100 ms 达 80% 目标速度」。
 * 边界：tauMs ≤ 0 立即跳到 target；deltaMs ≤ 0 保持 current；α clamp 到 [0, 1]。
 */
export function smoothLerp(
  current: number,
  target: number,
  tauMs: number,
  deltaMs: number,
): number {
  if (!Number.isFinite(current) || !Number.isFinite(target)) {
    return target;
  }
  if (tauMs <= 0) {
    return target;
  }
  if (deltaMs <= 0) {
    return current;
  }
  const rawAlpha = 1 - Math.exp(-deltaMs / tauMs);
  const alpha = rawAlpha < 0 ? 0 : rawAlpha > 1 ? 1 : rawAlpha;
  return current + (target - current) * alpha;
}

/**
 * 按地图与是否跑步派生 vTarget 的标量速度（px/s）。
 * 规则：
 *   1. 基速 = isRunning ? runSpeed : walkSpeed
 *   2. 最终 × sceneSpeedMultiplier[sceneKey]（未知地图按 1.0 兜底）
 * 速度倍率只缩放 vTarget，不改 tauAccelMs / tauBrakeMs，确保延迟目标在各地图均一致，
 * 避免进矿洞时手感「黏」一下。
 */
export function getSpeedForScene(
  sceneKey: string,
  isRunning: boolean,
  config: InputTuningConfig = DEFAULT_INPUT_TUNING,
): number {
  const multiplier = config.sceneSpeedMultiplier[sceneKey] ?? 1.0;
  const baseSpeed = isRunning ? config.runSpeed : config.walkSpeed;
  return baseSpeed * multiplier;
}

/**
 * 标量死区：|magnitude| < deadzone 即归零，不做平滑过渡——否则会与 lerp 复合出现
 * 「松手后慢慢漂回中线」的诡异手感。deadzone 之上原样透传（与 gamepadAdapter.applyDeadzone
 * 行为对齐，保留模拟摇杆轻推语义）。deadzone 会被 clamp 到 [0, 1]，防止异常负值或 > 1 把全程归零。
 */
export function applyDeadzone(magnitude: number, deadzone: number): number {
  if (!Number.isFinite(magnitude)) {
    return 0;
  }
  const safeDeadzone = deadzone < 0 ? 0 : deadzone > 1 ? 1 : deadzone;
  if (Math.abs(magnitude) < safeDeadzone) {
    return 0;
  }
  return magnitude;
}
