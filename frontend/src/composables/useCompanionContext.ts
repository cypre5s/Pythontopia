import { computed, type ComputedRef } from 'vue';
import { useLearningStore } from '@stores/learning';

export interface CompanionContext {
  kcCodes: string[];
  surface: string;
  context: string;
}

interface RouteLike {
  path: string;
  query: Record<string, unknown>;
}

export function useCompanionContext(
  routeGetter: () => RouteLike,
): ComputedRef<CompanionContext> {
  const learning = useLearningStore();
  return computed<CompanionContext>(() => {
    const route = routeGetter();
    const surface = route.path.replace(/^\//, '').split('/')[0] || 'world';
    const activeKc = learning.field.activeKcCode;
    const kcCodes = activeKc ? [activeKc] : [];
    const context = `当前在「${surface}」页面${activeKc ? `，正在学习知识点 ${activeKc}` : ''}`;
    return { kcCodes, surface, context };
  });
}
