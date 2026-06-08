import { computed, type ComputedRef } from 'vue';
import { useLearningStore } from '@stores/learning';
import { useSessionStore } from '@stores/session';

export interface CompanionContext {
  kcCodes: string[];
  surface: string;
  context: string;
  /** 教师在班级页时的班级 id；其它情况（含学生）恒为 null，避免向学生暴露班级聚合。 */
  classroomId: string | null;
}

interface RouteLike {
  path: string;
  query: Record<string, unknown>;
}

// 匹配 /teacher/classroom/:id（以及任何 .../classroom/:id 形态）里的班级 id。
const CLASSROOM_PATH = /\/classroom\/([^/]+)/;

export function useCompanionContext(
  routeGetter: () => RouteLike,
): ComputedRef<CompanionContext> {
  const learning = useLearningStore();
  const session = useSessionStore();
  return computed<CompanionContext>(() => {
    const route = routeGetter();
    const surface = route.path.replace(/^\//, '').split('/')[0] || 'world';
    const activeKc = learning.field.activeKcCode;
    const kcCodes = activeKc ? [activeKc] : [];

    // 教师 copilot：仅当「教师/管理员角色」且「身处班级页」时注入只读班级上下文。
    // 学生角色一律拿不到 classroomId 与班级聚合提示（前端第一道闸，后端再做权限闸）。
    const isTeacher = session.role === 'TEACHER' || session.role === 'ADMIN';
    const classMatch = CLASSROOM_PATH.exec(route.path);
    const classroomId = isTeacher && classMatch ? (classMatch[1] ?? null) : null;
    if (classroomId) {
      const context =
        `教师视角·班级 ${classroomId}：可基于本班只读数据（KC 热力 / 高频误区 / 完成率）作答，` +
        '严禁泄题，不得给出可直接提交的完整答案。';
      return { kcCodes, surface, context, classroomId };
    }

    const context = `当前在「${surface}」页面${activeKc ? `，正在学习知识点 ${activeKc}` : ''}`;
    return { kcCodes, surface, context, classroomId: null };
  });
}
