import type { IntentKind } from '@/api/intent';

import { WORKSHOP_CAPABILITIES, type WorkshopCapabilityNamespace } from './capabilities';

export type { WorkshopCapabilityNamespace };

export type WorkshopToolRouteSource =
  | 'composer_text'
  | 'slash_command'
  | 'capability_launcher'
  | 'contextual_suggestion';

export type WorkshopRenderMode =
  | 'answer'
  | 'resource'
  | 'practice'
  | 'plan'
  | 'progress'
  | 'browse'
  | 'navigation'
  | 'clarification';

export interface WorkshopToolRoutePlan {
  source: WorkshopToolRouteSource;
  namespace: WorkshopCapabilityNamespace;
  intent: IntentKind;
  args: Record<string, unknown>;
  confidence: number;
  secondaryIntent?: IntentKind | null;
  needsClarification: boolean;
  clarificationPrompt?: string;
  riskClass: 'read_only' | 'navigational' | 'generative';
  renderMode: WorkshopRenderMode;
  candidateIntents: IntentKind[];
  reasonCode:
    | 'explicit_command'
    | 'contextual_match'
    | 'missing_required_context'
    | 'multi_intent'
    | 'safety_constrained'
    | 'fallback';
}

export function candidateIntentsForCapability(namespace: WorkshopCapabilityNamespace): IntentKind[] {
  return WORKSHOP_CAPABILITIES.find((capability) => capability.namespace === namespace)?.intents ?? ['ASK'];
}

export function shouldClarifyRoutePlan(plan: WorkshopToolRoutePlan): boolean {
  if (plan.needsClarification) return true;
  if (plan.confidence < 0.45) return true;
  return plan.source === 'composer_text' && plan.confidence < 0.75;
}
