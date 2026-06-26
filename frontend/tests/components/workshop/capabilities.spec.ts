import { describe, expect, it } from 'vitest';

import { WORKSHOP_CAPABILITIES, capabilityForIntent } from '@/components/workshop/capabilities';

describe('workshop capabilities', () => {
  it('groups the 17 internal intents into six learner-facing capabilities', () => {
    expect(WORKSHOP_CAPABILITIES).toHaveLength(6);
    const all = WORKSHOP_CAPABILITIES.flatMap((capability) => capability.intents);
    expect(new Set(all).size).toBe(17);
    expect(capabilityForIntent('ASK')?.namespace).toBe('ask');
    expect(capabilityForIntent('HANDOUT')?.namespace).toBe('make');
    expect(capabilityForIntent('PRACTICE')?.namespace).toBe('practice');
    expect(capabilityForIntent('PLAN')?.namespace).toBe('plan');
    expect(capabilityForIntent('BROWSE')?.namespace).toBe('find');
    expect(capabilityForIntent('NAVIGATE')?.namespace).toBe('find');
  });
});
