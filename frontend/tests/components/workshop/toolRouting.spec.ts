import { describe, expect, it } from 'vitest';

import {
  candidateIntentsForCapability,
  shouldClarifyRoutePlan,
  type WorkshopToolRoutePlan,
} from '@/components/workshop/toolRouting';

describe('workshop tool routing', () => {
  it('limits candidate intents by capability namespace', () => {
    expect(candidateIntentsForCapability('practice')).toEqual(['PRACTICE', 'CODE_LAB']);
    expect(candidateIntentsForCapability('ask')).toEqual(['ASK', 'EXPLAIN_CODE', 'FIX_BUG']);
  });

  it('clarifies low-confidence free text plans', () => {
    const plan: WorkshopToolRoutePlan = {
      source: 'composer_text',
      namespace: 'ask',
      intent: 'ASK',
      args: {},
      confidence: 0.4,
      needsClarification: true,
      riskClass: 'read_only',
      renderMode: 'clarification',
      candidateIntents: ['ASK'],
      reasonCode: 'missing_required_context',
    };

    expect(shouldClarifyRoutePlan(plan)).toBe(true);
  });

  it('allows medium confidence when a learner explicitly clicked a capability', () => {
    const plan: WorkshopToolRoutePlan = {
      source: 'capability_launcher',
      namespace: 'practice',
      intent: 'PRACTICE',
      args: {},
      confidence: 0.55,
      needsClarification: false,
      riskClass: 'generative',
      renderMode: 'practice',
      candidateIntents: ['PRACTICE', 'CODE_LAB'],
      reasonCode: 'explicit_command',
    };

    expect(shouldClarifyRoutePlan(plan)).toBe(false);
  });

  it('uses 0.55 as the free-text low-confidence boundary', () => {
    const plan: WorkshopToolRoutePlan = {
      source: 'composer_text',
      namespace: 'ask',
      intent: 'ASK',
      args: {},
      confidence: 0.55,
      needsClarification: false,
      riskClass: 'generative',
      renderMode: 'answer',
      candidateIntents: ['ASK'],
      reasonCode: 'contextual_match',
    };

    expect(shouldClarifyRoutePlan(plan)).toBe(false);
    expect(shouldClarifyRoutePlan({ ...plan, confidence: 0.54 })).toBe(true);
  });
});
