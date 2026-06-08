import { describe, expect, it } from 'vitest';
import {
  DEFAULT_INPUT_TUNING,
  applyDeadzone,
  getSpeedForScene,
  normalizeDiagonal,
  smoothLerp,
} from '@/game/config/inputTuning.config';

describe('DEFAULT_INPUT_TUNING', () => {
  it('exposes the §1 input-feel target values verbatim', () => {
    expect(DEFAULT_INPUT_TUNING.walkSpeed).toBe(120);
    expect(DEFAULT_INPUT_TUNING.runSpeed).toBe(180);
    expect(DEFAULT_INPUT_TUNING.tauAccelMs).toBe(60);
    expect(DEFAULT_INPUT_TUNING.tauBrakeMs).toBe(40);
    expect(DEFAULT_INPUT_TUNING.gamepadDeadzone).toBe(0.15);
    expect(DEFAULT_INPUT_TUNING.touchJoystickRadius).toBe(60);
    expect(DEFAULT_INPUT_TUNING.inputLagBudgetMs).toBe(50);
    expect(DEFAULT_INPUT_TUNING.stopLagBudgetMs).toBe(80);
    expect(DEFAULT_INPUT_TUNING.sceneSpeedMultiplier.town_hub).toBe(1.0);
    expect(DEFAULT_INPUT_TUNING.sceneSpeedMultiplier.mine_south).toBe(0.8);
  });

  it('freezes the config and nested sceneSpeedMultiplier (附录 A.1 兜底)', () => {
    expect(Object.isFrozen(DEFAULT_INPUT_TUNING)).toBe(true);
    expect(Object.isFrozen(DEFAULT_INPUT_TUNING.sceneSpeedMultiplier)).toBe(true);
  });
});

describe('normalizeDiagonal', () => {
  it('returns zero vector when no direction is pressed', () => {
    expect(normalizeDiagonal(0, 0)).toEqual({ dx: 0, dy: 0 });
  });

  it('preserves a unit-length single-axis input (mag == 1 not normalized)', () => {
    expect(normalizeDiagonal(1, 0)).toEqual({ dx: 1, dy: 0 });
    expect(normalizeDiagonal(0, -1)).toEqual({ dx: 0, dy: -1 });
  });

  it('preserves a sub-unit analog stick input to keep "soft push = walk slowly"', () => {
    const result = normalizeDiagonal(0.5, 0);
    expect(result.dx).toBeCloseTo(0.5, 5);
    expect(result.dy).toBe(0);
  });

  it('normalizes diagonal input to magnitude 1 (no √2 speed bug)', () => {
    const result = normalizeDiagonal(1, 1);
    const mag = Math.sqrt(result.dx * result.dx + result.dy * result.dy);
    expect(mag).toBeCloseTo(1, 5);
    expect(result.dx).toBeCloseTo(Math.SQRT1_2, 5);
    expect(result.dy).toBeCloseTo(Math.SQRT1_2, 5);
  });

  it('normalizes negative diagonal input symmetrically', () => {
    const result = normalizeDiagonal(-1, -1);
    expect(result.dx).toBeCloseTo(-Math.SQRT1_2, 5);
    expect(result.dy).toBeCloseTo(-Math.SQRT1_2, 5);
  });
});

describe('smoothLerp', () => {
  it('reaches ~80% of target in 100 ms with tauAccelMs=60', () => {
    const result = smoothLerp(0, 100, DEFAULT_INPUT_TUNING.tauAccelMs, 100);
    expect(result).toBeGreaterThan(78);
    expect(result).toBeLessThan(84);
  });

  it('returns current unchanged when deltaMs <= 0', () => {
    expect(smoothLerp(42, 100, 60, 0)).toBe(42);
    expect(smoothLerp(42, 100, 60, -5)).toBe(42);
  });

  it('jumps to target when tauMs <= 0 (degenerate τ means instant convergence)', () => {
    expect(smoothLerp(0, 100, 0, 16)).toBe(100);
    expect(smoothLerp(0, 100, -1, 16)).toBe(100);
  });

  it('converges monotonically toward target across 60 FPS frames', () => {
    let v = 0;
    const frameMs = 1000 / 60;
    for (let i = 0; i < 60; i++) {
      v = smoothLerp(v, 100, DEFAULT_INPUT_TUNING.tauAccelMs, frameMs);
    }
    expect(v).toBeCloseTo(100, 0);
    expect(v).toBeLessThanOrEqual(100);
  });

  it('reaches ~86% of target when deltaMs ≈ 2τ (matches §1 brake budget intuition)', () => {
    const result = smoothLerp(0, 100, DEFAULT_INPUT_TUNING.tauBrakeMs, 80);
    expect(result).toBeGreaterThan(83);
    expect(result).toBeLessThan(90);
  });
});

describe('getSpeedForScene', () => {
  it('returns base walkSpeed for town_hub (baseline 1.0×)', () => {
    expect(getSpeedForScene('town_hub', false)).toBe(120);
    expect(getSpeedForScene('town_hub', true)).toBe(180);
  });

  it('applies the mine_south 0.8× slowdown for both walk and run', () => {
    expect(getSpeedForScene('mine_south', false)).toBeCloseTo(96, 5);
    expect(getSpeedForScene('mine_south', true)).toBeCloseTo(144, 5);
    expect(getSpeedForScene('mine_south', false)).toBeLessThan(
      getSpeedForScene('town_hub', false),
    );
  });

  it('defaults unknown scene keys to multiplier 1.0 (forward-compat for new maps)', () => {
    expect(getSpeedForScene('unknown_map', false)).toBe(120);
    expect(getSpeedForScene('unknown_map', true)).toBe(180);
  });
});

describe('applyDeadzone', () => {
  it('zeros out magnitudes strictly below the deadzone threshold', () => {
    expect(applyDeadzone(0.1, 0.15)).toBe(0);
    expect(applyDeadzone(0, 0.15)).toBe(0);
  });

  it('passes magnitudes equal to or above the deadzone through unchanged', () => {
    expect(applyDeadzone(0.15, 0.15)).toBe(0.15);
    expect(applyDeadzone(0.2, 0.15)).toBe(0.2);
    expect(applyDeadzone(1, 0.15)).toBe(1);
  });

  it('treats negative magnitudes symmetrically (preserves sign)', () => {
    expect(applyDeadzone(-0.1, 0.15)).toBe(0);
    expect(applyDeadzone(-0.5, 0.15)).toBe(-0.5);
  });

  it('clamps the deadzone parameter to [0, 1] to neutralize bad input', () => {
    expect(applyDeadzone(0.05, -0.1)).toBe(0.05);
    expect(applyDeadzone(0.99, 1.5)).toBe(0);
  });
});
