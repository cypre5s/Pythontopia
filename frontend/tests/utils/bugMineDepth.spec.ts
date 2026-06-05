import { describe, expect, it } from 'vitest';
import { canAccessCard, depthRequiredForCard, depthLockMessage } from '@/utils/bugMineDepth';
import type { BugMineCardDto } from '@/api/dto/BugMine';

function card(type: BugMineCardDto['cardType']): BugMineCardDto {
  return {
    id: 1,
    sessionId: 1,
    cardType: type,
    payload: {},
    revealed: false,
    createdAt: '2026-05-21T00:00:00Z',
  };
}

describe('bugMineDepth', () => {
  it('maps card types to required depth layers', () => {
    expect(depthRequiredForCard('COUNTER_EXAMPLE')).toBe(2);
    expect(depthRequiredForCard('VARIABLE_TRACE')).toBe(3);
    expect(depthRequiredForCard('HINT_LAYER_2')).toBe(4);
  });

  it('blocks access until depth is deep enough', () => {
    expect(canAccessCard(1, card('COUNTER_EXAMPLE'))).toBe(false);
    expect(canAccessCard(2, card('COUNTER_EXAMPLE'))).toBe(true);
    expect(canAccessCard(3, card('HINT_LAYER_1'))).toBe(false);
    expect(canAccessCard(4, card('HINT_LAYER_1'))).toBe(true);
  });

  it('returns lock message for shallow depth', () => {
    expect(depthLockMessage(1, 'COUNTER_EXAMPLE')).toContain('L2');
  });
});
