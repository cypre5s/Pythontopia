import { describe, it, expect, beforeEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useCompanionContext } from '@/composables/useCompanionContext';
import { useLearningStore } from '@stores/learning';
import { useSessionStore } from '@stores/session';

describe('useCompanionContext', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('derives kcCodes + surface from active field KC and route', () => {
    const learning = useLearningStore();
    learning.field.activeKcCode = 'PY43-KC-016';
    const ctx = useCompanionContext(() => ({ path: '/training', query: { problemId: '5' } }));
    expect(ctx.value.kcCodes).toContain('PY43-KC-016');
    expect(ctx.value.surface).toBe('training');
    expect(ctx.value.context).toContain('training');
    expect(ctx.value.classroomId).toBeNull();
  });

  it('falls back to empty kcCodes when nothing active', () => {
    const ctx = useCompanionContext(() => ({ path: '/world', query: {} }));
    expect(ctx.value.kcCodes).toEqual([]);
    expect(ctx.value.surface).toBe('world');
  });

  it('injects read-only class context for a teacher on the classroom page', () => {
    const session = useSessionStore();
    session.user = { id: 1, username: 't', displayName: '老师', role: 'TEACHER' };
    const ctx = useCompanionContext(() => ({ path: '/teacher/classroom/12', query: {} }));
    expect(ctx.value.classroomId).toBe('12');
    expect(ctx.value.context).toContain('教师');
    expect(ctx.value.context).toContain('12');
    // 红线：教师上下文必须显式约束「只读 + 不泄题」。
    expect(ctx.value.context).toContain('泄题');
  });

  it('does not expose class aggregates to a student on the same path', () => {
    const session = useSessionStore();
    session.user = { id: 2, username: 's', displayName: '同学', role: 'STUDENT' };
    const ctx = useCompanionContext(() => ({ path: '/teacher/classroom/12', query: {} }));
    expect(ctx.value.classroomId).toBeNull();
    expect(ctx.value.context).not.toContain('教师');
  });
});
