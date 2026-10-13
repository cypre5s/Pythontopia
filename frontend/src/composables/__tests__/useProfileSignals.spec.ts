import { describe, expect, it } from 'vitest';
import { computeAdaptiveDifficulty } from '../useProfileSignals';

describe('computeAdaptiveDifficulty', () => {
  it('adjusts only after two consecutive outcomes and changes one level at a time', () => {
    expect(computeAdaptiveDifficulty({ baseDifficulty: 3, recentFailures: 1, recentStreak: 0 })).toBe(3);
    expect(computeAdaptiveDifficulty({ baseDifficulty: 3, recentFailures: 2, recentStreak: 0 })).toBe(2);
    expect(computeAdaptiveDifficulty({ baseDifficulty: 3, recentFailures: 0, recentStreak: 2 })).toBe(4);
  });

  it('keeps the local hint within the supported difficulty range', () => {
    expect(computeAdaptiveDifficulty({ baseDifficulty: 5, recentFailures: 0, recentStreak: 2 })).toBe(5);
    expect(computeAdaptiveDifficulty({ baseDifficulty: 1, recentFailures: 2, recentStreak: 0 })).toBe(1);
  });
});
