/**
 * R-13 §-1 续：useProfileSignals + computeAdaptiveDifficulty 单元测试。
 *
 * 覆盖：
 *   1. profile=null → signals 为空对象
 *   2. errorPatterns.frequentKcCodes → 抽取为 weakKcCodes（最多 6 个）
 *   3. baseLevel.pythonSelfRatedLevel 范围校验 0..5
 *   4. practiceCapability.successRate 范围校验 0..1
 *   5. learningPreference.mediaPriority 抽取（最多 4）
 *   6. emotionPersistence.resilience 范围校验 0..1
 *   7. hasAnySignal 在任一字段填充时为 true
 *   8. computeAdaptiveDifficulty：
 *      a) streak >=2 +1 / =1 +0.5
 *      b) failures>=2 + resilience<0.4 → -2；否则 -1
 *      c) successRate>=0.75 +1 / <=0.3 -1
 *      d) clamp 到 1..5
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useProfileSignals, computeAdaptiveDifficulty } from '@/composables/useProfileSignals';
import { useLearningStore } from '@/stores/learning';
import type { LearnerProfileDto } from '@/api/dto/LearnerProfileDto';

function makeProfile(overrides: Partial<LearnerProfileDto> = {}): LearnerProfileDto {
  return {
    profileId: 1,
    userId: 1,
    packId: 43,
    major: '计算机',
    baseLevel: null,
    learningGoal: null,
    cognitiveStyle: null,
    learningPreference: null,
    errorPatterns: null,
    practiceCapability: null,
    emotionPersistence: null,
    narrativeSummary: null,
    ...overrides,
  };
}

describe('useProfileSignals', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('returns empty signals when profile=null', () => {
    const learning = useLearningStore();
    learning.profile = null;
    const { signals, hasAnySignal, weakKcs } = useProfileSignals();
    expect(signals.value).toEqual({});
    expect(hasAnySignal.value).toBe(false);
    expect(weakKcs.value).toEqual([]);
  });

  it('extracts weakKcCodes from errorPatterns (capped to 6)', () => {
    const learning = useLearningStore();
    learning.profile = makeProfile({
      errorPatterns: {
        frequentKcCodes: [
          'PY43-KC-001', 'PY43-KC-002', 'PY43-KC-003',
          'PY43-KC-004', 'PY43-KC-005', 'PY43-KC-006', 'PY43-KC-007',
        ],
        commonCategories: [],
      },
    });
    const { signals, weakKcs } = useProfileSignals();
    expect(signals.value.weakKcCodes).toHaveLength(6);
    expect(weakKcs.value).toContain('PY43-KC-001');
    expect(weakKcs.value).not.toContain('PY43-KC-007');
  });

  it('extracts pythonSelfRatedLevel only if in 0..5', () => {
    const learning = useLearningStore();
    learning.profile = makeProfile({
      baseLevel: { programmingYears: 1, languagesKnown: ['C'], pythonSelfRatedLevel: 3 },
    });
    expect(useProfileSignals().signals.value.pythonSelfRatedLevel).toBe(3);

    learning.profile = makeProfile({
      baseLevel: { programmingYears: 1, languagesKnown: [], pythonSelfRatedLevel: 10 },
    });
    expect(useProfileSignals().signals.value.pythonSelfRatedLevel).toBeUndefined();
  });

  it('extracts successRate only if in 0..1', () => {
    const learning = useLearningStore();
    learning.profile = makeProfile({
      practiceCapability: { avgAttemptsPerProblem: 2, successRate: 0.7 },
    });
    expect(useProfileSignals().signals.value.successRate).toBe(0.7);

    learning.profile = makeProfile({
      practiceCapability: { avgAttemptsPerProblem: 2, successRate: 1.5 },
    });
    expect(useProfileSignals().signals.value.successRate).toBeUndefined();
  });

  it('extracts mediaPriority capped to 4', () => {
    const learning = useLearningStore();
    learning.profile = makeProfile({
      learningPreference: {
        mediaPriority: ['CODE', 'DIAGRAM', 'VIDEO', 'TEXT', 'INTERACTIVE'],
        sessionMinutesPerDay: 30,
      },
    });
    expect(useProfileSignals().signals.value.mediaPriority).toHaveLength(4);
  });

  it('extracts resilience only if in 0..1', () => {
    const learning = useLearningStore();
    learning.profile = makeProfile({
      emotionPersistence: { resilience: 0.6, recentMood: 'neutral' },
    });
    expect(useProfileSignals().signals.value.resilience).toBe(0.6);
  });

  it('includes the remaining bounded background, goal, cognition, and session signals', () => {
    const learning = useLearningStore();
    learning.profile = makeProfile({
      major: 'NON_CS_HUMANITIES',
      learningGoal: {
        kind: 'JOB',
        deadlineIso: null,
        targetKcCodes: ['PY43-KC-031', 'PY43-KC-999'],
      },
      cognitiveStyle: { fieldDependent: 0.7, sequentialVsHolistic: -0.5 },
      learningPreference: { mediaPriority: [], sessionMinutesPerDay: 20 },
    });

    expect(useProfileSignals().signals.value).toMatchObject({
      major: 'NON_CS_HUMANITIES',
      learningGoalKind: 'JOB',
      targetKcCodes: ['PY43-KC-031'],
      fieldDependent: 0.7,
      sequentialVsHolistic: -0.5,
      sessionMinutesPerDay: 20,
    });
  });

  it('hasAnySignal true when at least one field is filled', () => {
    const learning = useLearningStore();
    learning.profile = makeProfile({
      baseLevel: { programmingYears: 0, languagesKnown: [], pythonSelfRatedLevel: 2 },
    });
    expect(useProfileSignals().hasAnySignal.value).toBe(true);
  });
});

describe('computeAdaptiveDifficulty', () => {
  it('streak +1 every 2 hits', () => {
    expect(
      computeAdaptiveDifficulty({
        signals: {},
        baseDifficulty: 2,
        recentFailures: 0,
        recentStreak: 2,
      }),
    ).toBe(3);
  });

  it('streak +0.5 when 1 hit only', () => {
    expect(
      computeAdaptiveDifficulty({
        signals: {},
        baseDifficulty: 3,
        recentFailures: 0,
        recentStreak: 1,
      }),
    ).toBe(4); // 3 + 0.5 = 3.5 → round → 4
  });

  it('failures >=2 with low resilience drops -2', () => {
    expect(
      computeAdaptiveDifficulty({
        signals: { resilience: 0.2 },
        baseDifficulty: 4,
        recentFailures: 2,
        recentStreak: 0,
      }),
    ).toBe(2); // 4 - 2 = 2
  });

  it('high successRate +1 starts harder', () => {
    expect(
      computeAdaptiveDifficulty({
        signals: { successRate: 0.85 },
        baseDifficulty: 2,
        recentFailures: 0,
        recentStreak: 0,
      }),
    ).toBe(3); // 2 + 1 = 3
  });

  it('low successRate -1 starts easier', () => {
    expect(
      computeAdaptiveDifficulty({
        signals: { successRate: 0.2 },
        baseDifficulty: 3,
        recentFailures: 0,
        recentStreak: 0,
      }),
    ).toBe(2);
  });

  it('clamps to 1..5', () => {
    expect(
      computeAdaptiveDifficulty({
        signals: { successRate: 0.95 },
        baseDifficulty: 5,
        recentFailures: 0,
        recentStreak: 2,
      }),
    ).toBe(5);
    expect(
      computeAdaptiveDifficulty({
        signals: { successRate: 0.1, resilience: 0.1 },
        baseDifficulty: 1,
        recentFailures: 3,
        recentStreak: 0,
      }),
    ).toBe(1);
  });
});
