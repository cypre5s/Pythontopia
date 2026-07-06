// R-12 W5 能量调参表单测：覆盖 helper 行为、clamp 边界、状态档位切换。
//
// 测试目标：
//   - 默认配置常量不被悄悄改动；
//   - applyDelta clamp 到 [0, 100]，并对 NaN / Infinity / 浮点稳健；
//   - getPyodideMultiplierByEnergy 在 30 / 50 边界切档准确；
//   - getMovementSpeedMultiplier 在 10 边界切档准确；
//   - getStateForEnergy 在 0 / 10 / 30 / 50 四档边界全部正确。
//
// 与设计文档 §5.1 / §6 对齐；helper 是纯函数，不依赖 Phaser / fetch / Pinia。

import { describe, expect, it } from 'vitest';
import {
  DEFAULT_ENERGY_TUNING,
  applyDelta,
  getMovementSpeedMultiplier,
  getPyodideMultiplierByEnergy,
  getStateForEnergy,
  type EnergyTuningConfig,
} from '@/game/config/energyTuning.config';

describe('DEFAULT_ENERGY_TUNING constants', () => {
  it('exposes the canonical max / morning / penalty / overwork numbers', () => {
    expect(DEFAULT_ENERGY_TUNING.maxEnergy).toBe(100);
    expect(DEFAULT_ENERGY_TUNING.morningEnergyFull).toBe(100);
    expect(DEFAULT_ENERGY_TUNING.morningEnergyShort).toBe(80);
  });

  it('exposes movement / farm / submission / dialog / page costs', () => {
    expect(DEFAULT_ENERGY_TUNING.costPerWalkPerHalfMin).toBe(-2);
    expect(DEFAULT_ENERGY_TUNING.costPerRunPerHalfMin).toBe(-5);
    expect(DEFAULT_ENERGY_TUNING.costPerWaterAction).toBe(-3);
    expect(DEFAULT_ENERGY_TUNING.costPerMineAction).toBe(-10);
    expect(DEFAULT_ENERGY_TUNING.costPerSubmission).toBe(-8);
    expect(DEFAULT_ENERGY_TUNING.costPerDialogFiveMin).toBe(-3);
    expect(DEFAULT_ENERGY_TUNING.costPerPageRead).toBe(-2);
    expect(DEFAULT_ENERGY_TUNING.costPerRepeatedKcThird).toBe(-15);
  });

  it('exposes gain values for chef / rest / noon nap', () => {
    expect(DEFAULT_ENERGY_TUNING.gainFromChefMeal).toBe(20);
    expect(DEFAULT_ENERGY_TUNING.gainFromRest5Min).toBe(30);
    expect(DEFAULT_ENERGY_TUNING.gainFromNoonNap).toBe(50);
  });

  it('exposes penalty + overwork thresholds matching the design doc', () => {
    expect(DEFAULT_ENERGY_TUNING.penaltyThresholds).toEqual({
      soft: 50,
      harden: 30,
      severe: 10,
      zero: 0,
    });
    expect(DEFAULT_ENERGY_TUNING.overworkThresholdsMin).toEqual({
      warn: 120,
      capDown: 240,
      forceQuit: 360,
    });
  });

  it('is strictly typed as EnergyTuningConfig (compile-time guard)', () => {
    const _typed: EnergyTuningConfig = DEFAULT_ENERGY_TUNING;
    expect(_typed.maxEnergy).toBeGreaterThan(0);
  });
});

describe('applyDelta', () => {
  it('adds positive delta within range and returns an integer', () => {
    expect(applyDelta(50, 20)).toBe(70);
    expect(applyDelta(0, 1)).toBe(1);
  });

  it('subtracts negative delta within range', () => {
    expect(applyDelta(80, -8)).toBe(72);
    expect(applyDelta(15, -10)).toBe(5);
  });

  it('clamps to 0 when result would go below 0', () => {
    expect(applyDelta(5, -100)).toBe(0);
    expect(applyDelta(0, -1)).toBe(0);
  });

  it('clamps to maxEnergy when result would exceed 100', () => {
    expect(applyDelta(95, 20)).toBe(100);
    expect(applyDelta(100, 50)).toBe(100);
  });

  it('truncates floating-point inputs to integers', () => {
    expect(applyDelta(50.9, 0)).toBe(50);
    expect(applyDelta(50, -3.7)).toBe(47);
    expect(applyDelta(50, 2.4)).toBe(52);
  });

  it('treats NaN / Infinity / non-finite inputs as 0', () => {
    expect(applyDelta(Number.NaN, 20)).toBe(20);
    expect(applyDelta(50, Number.NaN)).toBe(50);
    expect(applyDelta(50, Number.POSITIVE_INFINITY)).toBe(50);
    expect(applyDelta(Number.NEGATIVE_INFINITY, -5)).toBe(0);
  });

  it('handles current energy already out of range by clamping the final value', () => {
    expect(applyDelta(150, -10)).toBe(100);
    expect(applyDelta(-20, 5)).toBe(0);
  });
});

describe('getPyodideMultiplierByEnergy', () => {
  it('returns 1.0 at and above the soft threshold (50)', () => {
    expect(getPyodideMultiplierByEnergy(100)).toBe(1.0);
    expect(getPyodideMultiplierByEnergy(50)).toBe(1.0);
  });

  it('returns 1.3 in the soft band [30, 50)', () => {
    expect(getPyodideMultiplierByEnergy(49)).toBe(1.3);
    expect(getPyodideMultiplierByEnergy(35)).toBe(1.3);
    expect(getPyodideMultiplierByEnergy(30)).toBe(1.3);
  });

  it('returns 1.5 below the harden threshold (< 30)', () => {
    expect(getPyodideMultiplierByEnergy(29)).toBe(1.5);
    expect(getPyodideMultiplierByEnergy(10)).toBe(1.5);
    expect(getPyodideMultiplierByEnergy(0)).toBe(1.5);
  });

  it('falls back to 1.5 (safe-side) for negative / NaN / Infinity', () => {
    expect(getPyodideMultiplierByEnergy(-5)).toBe(1.5);
    expect(getPyodideMultiplierByEnergy(Number.NaN)).toBe(1.5);
    expect(getPyodideMultiplierByEnergy(Number.NEGATIVE_INFINITY)).toBe(1.5);
  });
});

describe('getMovementSpeedMultiplier', () => {
  it('returns 1.0 at and above the severe threshold (10)', () => {
    expect(getMovementSpeedMultiplier(100)).toBe(1.0);
    expect(getMovementSpeedMultiplier(50)).toBe(1.0);
    expect(getMovementSpeedMultiplier(10)).toBe(1.0);
  });

  it('returns 0.7 below the severe threshold (< 10)', () => {
    expect(getMovementSpeedMultiplier(9)).toBe(0.7);
    expect(getMovementSpeedMultiplier(1)).toBe(0.7);
    expect(getMovementSpeedMultiplier(0)).toBe(0.7);
  });

  it('returns 0.7 for negative / NaN inputs (treat as already exhausted)', () => {
    expect(getMovementSpeedMultiplier(-1)).toBe(0.7);
    expect(getMovementSpeedMultiplier(Number.NaN)).toBe(0.7);
  });
});

describe('getStateForEnergy', () => {
  it('classifies normal band (energy ≥ 50)', () => {
    expect(getStateForEnergy(100)).toBe('normal');
    expect(getStateForEnergy(75)).toBe('normal');
    expect(getStateForEnergy(50)).toBe('normal');
  });

  it('classifies soft band [30, 50)', () => {
    expect(getStateForEnergy(49)).toBe('soft');
    expect(getStateForEnergy(40)).toBe('soft');
    expect(getStateForEnergy(30)).toBe('soft');
  });

  it('classifies harden band [10, 30)', () => {
    expect(getStateForEnergy(29)).toBe('harden');
    expect(getStateForEnergy(20)).toBe('harden');
    expect(getStateForEnergy(10)).toBe('harden');
  });

  it('classifies severe band [1, 10)', () => {
    expect(getStateForEnergy(9)).toBe('severe');
    expect(getStateForEnergy(5)).toBe('severe');
    expect(getStateForEnergy(1)).toBe('severe');
  });

  it('classifies zero / negative as zero', () => {
    expect(getStateForEnergy(0)).toBe('zero');
    expect(getStateForEnergy(-5)).toBe('zero');
    expect(getStateForEnergy(Number.NEGATIVE_INFINITY)).toBe('zero');
  });

  it('treats NaN as zero (safe-side: trigger forced-rest modal)', () => {
    expect(getStateForEnergy(Number.NaN)).toBe('zero');
  });
});
