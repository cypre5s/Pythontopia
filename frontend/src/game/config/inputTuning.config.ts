// 设计原因：本文件实现 docs/gameplay/r12-deepen/01-input-feel-tuning.md 第 9 节
// 「可调参数清单」。所有数值与节内表格一一对应：
//   - walk/run speed 与 lerp τ：§1 输入手感目标值 + §2 加速曲线
//   - sceneSpeedMultiplier：§8.1 五张地图速度倍率
//   - runSpeedMultiplierPort：§8.1 港口跑步 +10% 物理代偿
//   - gamepadDeadzone / touchJoystickRadius：§5 死区与抖动
//   - inputLagBudgetMs / stopLagBudgetMs：§1 表中按键到位移 / 松手到归零的硬目标
// 隔离原因：纯数据 + 纯函数模块。
//   - 不 import Phaser、不依赖 InputAdapter / PlayerStateMachine，仅供它们或
//     未来「老师无障碍 dashboard」按值读取，避免循环依赖。
//   - 配置对象与子对象统一 Object.freeze，对应附录 A.1 兜底约束
//     「MAP_SPEED_FACTOR 在 inputTuning.config.ts 内 freeze，外部 import 只读」。

export interface InputTuningConfig {
  /** 行走速度（px/s）。§1 目标 120。 */
  readonly walkSpeed: number;
  /** 跑步速度（px/s）。§1 目标 180，行走 × 1.5。 */
  readonly runSpeed: number;
  /** 加速 lerp 时间常数（ms）。§2.2 取 60，100 ms 跨过 ~80% 目标。 */
  readonly tauAccelMs: number;
  /** 制动 lerp 时间常数（ms）。§2.2 取 40，比加速更短，主观「松手听话」。 */
  readonly tauBrakeMs: number;
  /** 手柄死区半径。§5.1 默认 0.15，覆盖 90% 摇杆静态漂移。 */
  readonly gamepadDeadzone: number;
  /** 触屏虚拟摇杆半径（px）。§5.1 默认 60，覆盖成人拇指可达半径。 */
  readonly touchJoystickRadius: number;
  /** 键盘按键防抖窗口（ms）。§5.4 浏览器 KeyboardEvent 无抖动，默认 0；
   * 此字段仅保留给极少数误触场景（如车间老旧 USB 键盘）下调。 */
  readonly keyDebounceMs: number;
  /** 按下方向键 → 像素位移 ≥ 1 px 的延迟硬上限（ms）。§1 表中 50 ms。 */
  readonly inputLagBudgetMs: number;
  /** 松开方向键 → 速度归 0 的延迟硬上限（ms）。§1 表中 80 ms。 */
  readonly stopLagBudgetMs: number;
  /** 5 张地图速度倍率。§8.1 表，mine_south 0.8× 给玩家更多时间看 traceback。 */
  readonly sceneSpeedMultiplier: Readonly<Record<string, number>>;
  /** port_west 跑步额外加成。§8.1 港口跑步 1.10，给远海视觉物理代偿。 */
  readonly runSpeedMultiplierPort: number;
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
    farm_north: 1.0,
    mine_south: 0.8,
    campus_east: 1.0,
    port_west: 1.0,
  }),
  runSpeedMultiplierPort: 1.1,
});

/**
 * 对角输入归一化。§3.1：仅当模长 > 1 时归一到 1，模长 ≤ 1 原样返回。
 * 之所以不无脑归一：手柄轻推（如模长 0.4）必须保留「轻推慢走」语义，
 * 强行除以 0.4 会把慢走放大成全速，破坏 §4.1 magnitude 派生规范。
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
 * 指数 lerp 实现 §2.2 的 S 曲线手感。
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
 * 规则（§8）：
 *   1. 基速 = isRunning ? runSpeed : walkSpeed
 *   2. 若 isRunning 且 sceneKey === 'port_west'，额外 × runSpeedMultiplierPort
 *   3. 最终 × sceneSpeedMultiplier[sceneKey]（未知地图按 1.0 兜底）
 * 速度倍率只缩放 vTarget，不改 tauAccelMs / tauBrakeMs，确保 §1 延迟目标
 * 在五张地图均一致，避免进矿洞时手感「黏」一下。
 */
export function getSpeedForScene(
  sceneKey: string,
  isRunning: boolean,
  config: InputTuningConfig = DEFAULT_INPUT_TUNING,
): number {
  const multiplier = config.sceneSpeedMultiplier[sceneKey] ?? 1.0;
  let baseSpeed = isRunning ? config.runSpeed : config.walkSpeed;
  if (isRunning && sceneKey === 'port_west') {
    baseSpeed *= config.runSpeedMultiplierPort;
  }
  return baseSpeed * multiplier;
}

/**
 * 标量死区。§5.1 / §5.2：|magnitude| < deadzone 即归零，
 * 不做平滑过渡——否则会与 §2 的 lerp 复合出现「松手后慢慢漂回中线」的诡异手感。
 * 不在 deadzone 之上时原样透传（与 gamepadAdapter.applyDeadzone 行为对齐，
 * 保留模拟摇杆轻推语义）。
 * deadzone 输入会被 clamp 到 [0, 1]，防止上游传入异常负值或 > 1 的死区把全程归零。
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
