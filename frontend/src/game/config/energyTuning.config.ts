// 能量与疲劳曲线调参表。
//
// 仅承担「数值与纯函数 helper」职责，不接触 Phaser / Pinia / fetch；业务层
// （WorldScene / EnergyHud / API client）读取本表，并通过
// POST /api/world/energy/{consume,restore} 把结果上报到后端 world_session.energy。
//
// 边界约定：
//   - 所有 delta 取整后参与运算；外部传入 NaN / Infinity / 非数字一律视为 0；
//   - 上限封顶 100、下限 0，超出区间通过 applyDelta clamp 处理；
//   - 能量状态仅 5 档（normal / soft / harden / severe / zero），用于驱动
//     UI 颜色、Pyodide 倍率、移动速度等下游表现，不直接扣 mastery。

/**
 * 能量调参字段集合（前端权威配置，后端通过 application.yml 镜像）。
 * 正值代表「获得能量」，负值代表「消耗能量」。
 */
export interface EnergyTuningConfig {
  /** 能量硬上限（默认 100）。 */
  readonly maxEnergy: number;
  /** 健康作息（22:00–01:00 入睡）起床能量。 */
  readonly morningEnergyFull: number;
  /** 晚睡（01:00–04:00 入睡）起床能量。 */
  readonly morningEnergyShort: number;

  /** walk 每 30 真实秒消耗。 */
  readonly costPerWalkPerHalfMin: number;
  /** run 每 30 真实秒消耗。 */
  readonly costPerRunPerHalfMin: number;
  /** 浇水 1 块田消耗（= 阅读对应 KC 1 页课件）。 */
  readonly costPerWaterAction: number;
  /** 矿洞挖 1 次 Bug 矿石消耗。 */
  readonly costPerMineAction: number;
  /** 提交一次正式判题消耗（无论通过/失败）。 */
  readonly costPerSubmission: number;
  /** 与 NPC 对话累计 5 真实分钟消耗。 */
  readonly costPerDialogFiveMin: number;
  /** 阅读课件 1 页消耗（duration ≥ 30s 才计）。 */
  readonly costPerPageRead: number;
  /** 同一 KC 第 3 次连续浇水/读课件叠加惩罚（在常规消耗之上额外扣除）。 */
  readonly costPerRepeatedKcThird: number;

  /** 厨师当日推荐菜（每日 1 次）回复。 */
  readonly gainFromChefMeal: number;
  /** 家中沙发 / 医务室坐席休息 5 真实分钟回复。 */
  readonly gainFromRest5Min: number;
  /** 家中午休（11:00–13:00）回复。 */
  readonly gainFromNoonNap: number;

  /** 疲劳状态阈值（能量低于该值时切换到对应档位）。 */
  readonly penaltyThresholds: {
    /** soft：≤50，Pyodide 慢一档、HUD 转黄。 */
    readonly soft: number;
    /** harden：≤30，判题超时缩短、SafetyAgent 提示、HUD 转橙。 */
    readonly harden: number;
    /** severe：≤10，禁 run / 禁新对话、移动速度 ×0.7、HUD 闪烁。 */
    readonly severe: number;
    /** zero：=0，强制走回家模态，无关闭按钮。 */
    readonly zero: number;
  };

  /** 过劳保护阈值（单位：真实分钟）。 */
  readonly overworkThresholdsMin: {
    /** warn：软提醒 toast。 */
    readonly warn: number;
    /** capDown：硬性把 energy_cap 锁到 80。 */
    readonly capDown: number;
    /** forceQuit：自动存档退出，教育版伦理硬底线。 */
    readonly forceQuit: number;
  };
}

/**
 * 默认能量调参表，凡未在迁移 / AB 测试中显式覆写都以本表为准。
 */
export const DEFAULT_ENERGY_TUNING: EnergyTuningConfig = {
  maxEnergy: 100,
  morningEnergyFull: 100,
  morningEnergyShort: 80,

  costPerWalkPerHalfMin: -2,
  costPerRunPerHalfMin: -5,
  costPerWaterAction: -3,
  costPerMineAction: -10,
  costPerSubmission: -8,
  costPerDialogFiveMin: -3,
  costPerPageRead: -2,
  costPerRepeatedKcThird: -15,

  gainFromChefMeal: 20,
  gainFromRest5Min: 30,
  gainFromNoonNap: 50,

  penaltyThresholds: {
    soft: 50,
    harden: 30,
    severe: 10,
    zero: 0,
  },

  overworkThresholdsMin: {
    warn: 120,
    capDown: 240,
    forceQuit: 360,
  },
};

/** 能量状态档位（驱动 UI 颜色、Pyodide 倍率、移动速度等下游表现）。 */
export type EnergyState = 'normal' | 'soft' | 'harden' | 'severe' | 'zero';

/**
 * 把任意 number（含 NaN / Infinity / 浮点）规约为整数 delta，
 * 失败的输入返回 0 以避免污染上下游运算。
 */
function normalizeDelta(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.trunc(value);
}

/**
 * 应用 delta 并 clamp 到 [0, maxEnergy]。
 *
 * @param currentEnergy 当前能量；非有限值视为 0
 * @param delta         本次增减；非有限值视为 0
 * @returns 新能量整数，永远落在 [0, maxEnergy] 区间
 */
export function applyDelta(currentEnergy: number, delta: number): number {
  const base = Number.isFinite(currentEnergy)
    ? Math.trunc(currentEnergy)
    : 0;
  const next = base + normalizeDelta(delta);
  if (next < 0) return 0;
  if (next > DEFAULT_ENERGY_TUNING.maxEnergy) {
    return DEFAULT_ENERGY_TUNING.maxEnergy;
  }
  return next;
}

/**
 * Pyodide 执行倍率：能量越低执行越慢，对应"看课件走神 / 做题反复出错"的教育隐喻。
 *
 * - energy < 30 → 1.5（severe / harden 下半区）
 * - 30 ≤ energy < 50 → 1.3（soft 区，黄色 HUD bar）
 * - energy ≥ 50 → 1.0（正常）
 *
 * 非有限输入按 0 处理（即取 1.5），保持"宁可慢、不可缺判定"的安全侧。
 */
export function getPyodideMultiplierByEnergy(energy: number): number {
  const safe = Number.isFinite(energy) ? energy : 0;
  if (safe < DEFAULT_ENERGY_TUNING.penaltyThresholds.harden) {
    return 1.5;
  }
  if (safe < DEFAULT_ENERGY_TUNING.penaltyThresholds.soft) {
    return 1.3;
  }
  return 1.0;
}

/**
 * 移动速度倍率：仅在 severe 档位（energy < 10）触发降速。
 *
 * - energy < 10 → 0.7
 * - 其它（含 energy = 0 / 负值 / 非有限）→ 0.7（既已严重过劳，沿用慢速）
 *
 * 注意 energy = 0 时业务层应直接弹"小睡一下吧"模态、强制玩家二选一，
 * 因此这里返回 0.7 而非更慢值，避免与模态强制走回家逻辑互相干扰。
 */
export function getMovementSpeedMultiplier(energy: number): number {
  const safe = Number.isFinite(energy) ? energy : 0;
  if (safe < DEFAULT_ENERGY_TUNING.penaltyThresholds.severe) {
    return 0.7;
  }
  return 1.0;
}

/**
 * 把数值能量映射到 5 档状态，供 UI / 玩法层做"功能性递降"。
 *
 * 区间口径（左闭右开）：
 *   - energy ≥ 50          → normal
 *   - 30 ≤ energy < 50     → soft
 *   - 10 ≤ energy < 30     → harden
 *   -  1 ≤ energy < 10     → severe
 *   - energy ≤ 0           → zero
 */
export function getStateForEnergy(energy: number): EnergyState {
  const safe = Number.isFinite(energy) ? Math.trunc(energy) : 0;
  const t = DEFAULT_ENERGY_TUNING.penaltyThresholds;
  if (safe <= t.zero) return 'zero';
  if (safe < t.severe) return 'severe';
  if (safe < t.harden) return 'harden';
  if (safe < t.soft) return 'soft';
  return 'normal';
}
