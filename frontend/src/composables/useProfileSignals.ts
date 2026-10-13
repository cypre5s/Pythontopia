// 只抽取自述目标与学习偏好；能力信号由服务端依据已验证的学习记录计算。
//
// 设计目标：不让 boss/endless/arena 出题代码反复 import LearnerProfileDto 全字段，
// 而是收敛到这一个 composable，只把出题真正需要的信号传给后端，
// 后端 / graph 服务只关心 ProfileSignalsDto，不耦合前端 8 维度演化。

import { computed, type ComputedRef } from 'vue';
import { useLearningStore } from '@stores/learning';
import type { ProfileSignalsDto } from '@/api/dto/Boss';
import type { LearnerProfileDto } from '@/api/dto/LearnerProfileDto';
import { KC_NAMES } from '@/data/kcNames';

const MAX_WEAK_KCS = 6;
const MAJOR_CODES = new Set(['CS', 'NON_CS_STEM', 'NON_CS_HUMANITIES', 'OTHER']);
const GOAL_KINDS = new Set(['EXAM', 'PROJECT', 'INTEREST', 'JOB']);

function deriveSignals(profile: LearnerProfileDto | null): ProfileSignalsDto {
  if (!profile) return {};
  const out: ProfileSignalsDto = {};

  const weak = profile.errorPatterns?.frequentKcCodes;
  if (Array.isArray(weak) && weak.length > 0) {
    out.weakKcCodes = weak.slice(0, MAX_WEAK_KCS);
  }

  if (profile.major && MAJOR_CODES.has(profile.major)) {
    out.major = profile.major;
  }

  const goal = profile.learningGoal;
  if (goal && GOAL_KINDS.has(goal.kind)) {
    out.learningGoalKind = goal.kind;
  }
  const targetKcs = goal?.targetKcCodes?.filter((code) =>
    Object.prototype.hasOwnProperty.call(KC_NAMES, code),
  );
  if (targetKcs?.length) {
    out.targetKcCodes = [...new Set(targetKcs)].slice(0, MAX_WEAK_KCS);
  }

  const level = profile.baseLevel?.pythonSelfRatedLevel;
  if (typeof level === 'number' && level >= 0 && level <= 5) {
    out.pythonSelfRatedLevel = level;
  }

  const media = profile.learningPreference?.mediaPriority;
  if (Array.isArray(media) && media.length > 0) {
    out.mediaPriority = media.slice(0, 4);
  }
  const sessionMinutes = profile.learningPreference?.sessionMinutesPerDay;
  if (
    typeof sessionMinutes === 'number' &&
    Number.isInteger(sessionMinutes) &&
    sessionMinutes >= 5 &&
    sessionMinutes <= 240
  ) {
    out.sessionMinutesPerDay = sessionMinutes;
  }

  return out;
}

export interface UseProfileSignalsReturn {
  signals: ComputedRef<ProfileSignalsDto>;
  /** 当前是否有任何画像信号（用于 UI 显示 "未接入画像，按默认出题" 提示） */
  hasAnySignal: ComputedRef<boolean>;
  /** 弱项 KC，去重后的列表（可能为空） */
  weakKcs: ComputedRef<readonly string[]>;
}

export function useProfileSignals(): UseProfileSignalsReturn {
  const learning = useLearningStore();
  const signals = computed<ProfileSignalsDto>(() => deriveSignals(learning.profile));
  const hasAnySignal = computed<boolean>(() => Object.keys(signals.value).length > 0);
  const weakKcs = computed<readonly string[]>(() => signals.value.weakKcCodes ?? []);
  return { signals, hasAnySignal, weakKcs };
}

/**
 * 本地展示用难度提示只响应连续练习结果；正式难度由服务端学情策略决定。
 */
export function computeAdaptiveDifficulty(args: {
  baseDifficulty: number;
  recentFailures: number;
  recentStreak: number;
}): number {
  let d = args.baseDifficulty;

  if (args.recentStreak >= 2) d += 1;

  if (args.recentFailures >= 2) d -= 1;

  return Math.max(1, Math.min(5, Math.round(d)));
}
