import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useProfileSignals, computeAdaptiveDifficulty } from '@/composables/useProfileSignals';
import { useLearningStore } from '@/stores/learning';
import type { LearnerProfileDto } from '@/api/dto/LearnerProfileDto';

function makeProfile(overrides: Partial<LearnerProfileDto> = {}): LearnerProfileDto {
  return {
    profileId: 1,
    userId: 1,
    packId: 43,
    major: null,
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
  beforeEach(() => setActivePinia(createPinia()));

  it('returns no signals when there is no profile', () => {
    useLearningStore().profile = null;
    const { signals, hasAnySignal, weakKcs } = useProfileSignals();

    expect(signals.value).toEqual({});
    expect(hasAnySignal.value).toBe(false);
    expect(weakKcs.value).toEqual([]);
  });

  it('keeps explicit context, preferences and the bounded KC fallback', () => {
    useLearningStore().profile = makeProfile({
      major: 'NON_CS_HUMANITIES',
      baseLevel: { programmingYears: 1, languagesKnown: ['C'], pythonSelfRatedLevel: 3 },
      learningGoal: {
        kind: 'JOB', deadlineIso: null,
        targetKcCodes: ['PY43-KC-031', 'PY43-KC-031', 'PY43-KC-999'],
      },
      learningPreference: {
        mediaPriority: ['CODE', 'DIAGRAM', 'VIDEO', 'TEXT', 'INTERACTIVE'],
        sessionMinutesPerDay: 30,
      },
      errorPatterns: {
        frequentKcCodes: [
          'PY43-KC-001', 'PY43-KC-002', 'PY43-KC-003', 'PY43-KC-004',
          'PY43-KC-005', 'PY43-KC-006', 'PY43-KC-007',
        ],
        commonCategories: [],
      },
    });
    const { signals, hasAnySignal, weakKcs } = useProfileSignals();

    expect(signals.value).toMatchObject({
      weakKcCodes: [
        'PY43-KC-001', 'PY43-KC-002', 'PY43-KC-003',
        'PY43-KC-004', 'PY43-KC-005', 'PY43-KC-006',
      ],
      major: 'NON_CS_HUMANITIES',
      pythonSelfRatedLevel: 3,
      learningGoalKind: 'JOB',
      targetKcCodes: ['PY43-KC-031'],
      mediaPriority: ['CODE', 'DIAGRAM', 'VIDEO', 'TEXT'],
      sessionMinutesPerDay: 30,
    });
    expect(weakKcs.value).toHaveLength(6);
    expect(hasAnySignal.value).toBe(true);
  });

  it('does not forward inferred ability, emotion or fixed learning-style fields', () => {
    useLearningStore().profile = makeProfile({
      cognitiveStyle: { fieldDependent: 0.7, sequentialVsHolistic: -0.5 },
      practiceCapability: { avgAttemptsPerProblem: 2, successRate: 0.9 },
      emotionPersistence: { resilience: 0.2, recentMood: 'sad' },
    });
    const signals = useProfileSignals().signals.value;

    expect(signals).toEqual({});
    expect(signals).not.toHaveProperty('successRate');
    expect(signals).not.toHaveProperty('resilience');
    expect(signals).not.toHaveProperty('fieldDependent');
    expect(signals).not.toHaveProperty('sequentialVsHolistic');
  });
});

describe('computeAdaptiveDifficulty', () => {
  it('changes only from the recent streak and failure inputs', () => {
    expect(computeAdaptiveDifficulty({ baseDifficulty: 2, recentFailures: 0, recentStreak: 2 }))
      .toBe(3);
    expect(computeAdaptiveDifficulty({ baseDifficulty: 4, recentFailures: 2, recentStreak: 0 }))
      .toBe(3);
  });

  it('clamps the fallback hint to difficulty 1..5', () => {
    expect(computeAdaptiveDifficulty({ baseDifficulty: 5, recentFailures: 0, recentStreak: 2 }))
      .toBe(5);
    expect(computeAdaptiveDifficulty({ baseDifficulty: 1, recentFailures: 3, recentStreak: 0 }))
      .toBe(1);
  });
});
