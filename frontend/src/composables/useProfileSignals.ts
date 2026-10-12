// 把 LearnerProfileDto 8 维度抽取成出题用的 ProfileSignalsDto。
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

  const fieldDependent = profile.cognitiveStyle?.fieldDependent;
  if (typeof fieldDependent === 'number' && fieldDependent >= 0 && fieldDependent <= 1) {
    out.fieldDependent = fieldDependent;
  }
  const sequentialVsHolistic = profile.cognitiveStyle?.sequentialVsHolistic;
  if (
    typeof sequentialVsHolistic === 'number' &&
    sequentialVsHolistic >= -1 &&
    sequentialVsHolistic <= 1
  ) {
    out.sequentialVsHolistic = sequentialVsHolistic;
  }

  const level = profile.baseLevel?.pythonSelfRatedLevel;
  if (typeof level === 'number' && level >= 0 && level <= 5) {
    out.pythonSelfRatedLevel = level;
  }

  const rate = profile.practiceCapability?.successRate;
  if (typeof rate === 'number' && rate >= 0 && rate <= 1) {
    out.successRate = rate;
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

  const resilience = profile.emotionPersistence?.resilience;
  if (typeof resilience === 'number' && resilience >= 0 && resilience <= 1) {
    out.resilience = resilience;
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
 * 根据画像信号 + 当前难度 + 连胜/失败次数计算下一题难度（1..5）。
 * 比 useEndlessRun 内置的 "+0.5 / -1" 更细：高 successRate 拉高，
 * 低 resilience 时连失 2 次直接 -2，避免逼玩家。
 */
export function computeAdaptiveDifficulty(args: {
  signals: ProfileSignalsDto;
  baseDifficulty: number;
  recentFailures: number;
  recentStreak: number;
}): number {
  let d = args.baseDifficulty;
  const successRate = args.signals.successRate ?? 0.5;
  const resilience = args.signals.resilience ?? 0.5;

  if (args.recentStreak >= 2) d += 1;
  else if (args.recentStreak >= 1) d += 0.5;

  if (args.recentFailures >= 2) {
    d -= resilience < 0.4 ? 2 : 1;
  } else if (args.recentFailures >= 1) {
    d -= 1;
  }

  // 学生本就高水平 → 起始难度高 +1
  if (successRate >= 0.75) d += 1;
  else if (successRate <= 0.3) d -= 1;

  return Math.max(1, Math.min(5, Math.round(d)));
}
