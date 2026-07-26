import { describe, expect, it } from 'vitest';
import {
  resolveClassroomQuestDeepLink,
  resolvePathNodeDeepLink,
  resolveQuestDeepLink,
} from '@/utils/questDeepLink';
import type { LearningPathNodeDto, PathNodeType, SuggestedResourceType } from '@/api/dto/Path';
import type { QuestDto, QuestNodeType } from '@/api/dto/Quest';

function pathNode(
  nodeType: PathNodeType,
  overrides: Partial<LearningPathNodeDto> = {},
): LearningPathNodeDto {
  return {
    id: 1,
    pathId: 9,
    dayIndex: 1,
    orderInDay: 1,
    kcCode: 'PY43-KC-016',
    nodeType,
    reasonSummary: '',
    evidenceSummary: '',
    suggestedResourceType: null,
    suggestedProblemId: null,
    sourceClassroomQuestId: null,
    ...overrides,
  };
}

function quest(nodeType: QuestNodeType, overrides: Partial<QuestDto> = {}): QuestDto {
  return {
    id: 10,
    userId: 7,
    source: 'PATH_PLANNER',
    sourceRefId: 1,
    title: 'test',
    description: null,
    kcCode: 'PY43-KC-016',
    nodeType,
    payload: {},
    dueDate: '2026-06-02',
    priority: 5,
    status: 'OPEN',
    createdAt: '2026-06-01T10:00:00Z',
    updatedAt: '2026-06-01T10:00:00Z',
    ...overrides,
  };
}

describe('questDeepLink fallback routes', () => {
  it('sends READ_RESOURCE to the workshop with the { kc, type } contract WorkshopChat consumes', () => {
    const target = resolvePathNodeDeepLink(
      pathNode('READ_RESOURCE', { suggestedResourceType: 'PRACTICE' as SuggestedResourceType }),
    );

    // WorkshopChat.consumeDeepLink 读的是 route.query.kc / route.query.type，
    // 缺一即整段放弃消费 —— 写成 kcCode 会让「去工坊读资源」丢掉 KC 上下文。
    expect(target).toEqual({
      path: '/workshop',
      query: { kc: 'PY43-KC-016', type: 'PRACTICE' },
    });
  });

  it('keeps FIX_BUG / PLANT_KC aligned with the backend deep link table', () => {
    // 后端 LearningPathService.deepLinkParamsFor：FIX_BUG → /world?goto=town_hub，
    // PLANT_KC → /world?focus=kc_<小写 kcCode>。前端兜底必须是同一张表，
    // 否则「本周路径」与「今日任务」点同一节点会跳到两个地方。
    expect(resolvePathNodeDeepLink(pathNode('FIX_BUG'))).toEqual({
      path: '/world',
      query: { goto: 'town_hub' },
    });
    expect(resolvePathNodeDeepLink(pathNode('PLANT_KC'))).toEqual({
      path: '/world',
      query: { focus: 'kc_py43-kc-016' },
    });
  });

  it('still honours a backend supplied deep link route for quests', () => {
    const target = resolveQuestDeepLink(
      quest('SOLVE_PROBLEM', {
        deepLinkRoute: '/training',
        deepLinkParams: { problemId: 476 },
      } as Partial<QuestDto>),
    );
    expect(target).toEqual({ path: '/training', query: { problemId: 476 } });
  });

  it('falls back to the workshop kc contract for classroom quests without a backend route', () => {
    const target = resolveClassroomQuestDeepLink({
      id: 3,
      classroomId: 1,
      teacherId: 2,
      title: '读讲义',
      description: null,
      nodeType: 'READ_RESOURCE',
      kcCodes: ['PY43-KC-020'],
      targetProblemId: null,
      targetProblemIds: [],
      targetResourceType: 'HANDOUT',
      targetResourceId: null,
      dueDate: null,
      status: 'OPEN',
      deepLinkRoute: null,
      deepLinkParams: {},
      createdAt: '2026-06-01T10:00:00Z',
      updatedAt: '2026-06-01T10:00:00Z',
    } as unknown as Parameters<typeof resolveClassroomQuestDeepLink>[0]);

    expect(target).toEqual({
      path: '/workshop',
      query: { kc: 'PY43-KC-020', type: 'HANDOUT' },
    });
  });
});
