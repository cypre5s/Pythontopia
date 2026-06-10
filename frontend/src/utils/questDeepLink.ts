import type { RouteLocationRaw } from 'vue-router';
import type { LearningPathNodeDto, PathNodeType } from '@/api/dto/Path';
import type { QuestDto, QuestNodeType } from '@/api/dto/Quest';
import type { ClassroomQuestDto } from '@/api/dto/ClassroomQuest';

/**
 * 教育闭环：把路径节点 / 任务卡片映射为前端"去做"按钮的具体路由。
 *
 * - 后端写入 deep_link_route + deep_link_params 时优先使用后端版本，
 *   只在历史 quest 上兜底用 node_type / payload 推导（不破坏旧数据）。
 * - WorldScene 自身解析 `focus=kc_xxx` 等 query，
 *   这里只负责"把人送到那个 URL"。
 */

type QueryRecord = Record<string, string | number>;

function paramsAsQuery(raw: unknown): QueryRecord {
  if (!raw || typeof raw !== 'object') {
    return {};
  }
  const out: QueryRecord = {};
  for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
    if (value === null || value === undefined) continue;
    if (typeof value === 'string' || typeof value === 'number') {
      out[key] = value;
    } else {
      out[key] = String(value);
    }
  }
  return out;
}

function fallbackRoute(
  nodeType: QuestNodeType | PathNodeType,
  options: { kcCode?: string | null; problemId?: number | null; resourceType?: string | null },
): RouteLocationRaw {
  switch (nodeType) {
    case 'SOLVE_PROBLEM':
      if (options.problemId) {
        return { path: '/training', query: { problemId: options.problemId } };
      }
      return { path: '/training' };
    case 'READ_RESOURCE': {
      const query: QueryRecord = {};
      if (options.kcCode) query.kcCode = options.kcCode;
      if (options.resourceType) query.type = options.resourceType;
      return { path: '/workshop', query };
    }
    case 'FIX_BUG':
      return { path: '/world' };
    case 'PLANT_KC':
      return { path: '/workshop' };
    case 'PROFILE_UPDATE':
      return { path: '/profile-room' };
    default:
      return { path: '/world' };
  }
}

export function resolveQuestDeepLink(quest: QuestDto): RouteLocationRaw {
  const route = quest.deepLinkRoute;
  if (route && route.startsWith('/')) {
    return { path: route, query: paramsAsQuery(quest.deepLinkParams) };
  }
  return fallbackRoute(quest.nodeType, {
    kcCode: quest.kcCode,
    problemId: extractProblemIdFromPayload(quest.payload),
    resourceType: extractResourceTypeFromPayload(quest.payload),
  });
}

export function resolvePathNodeDeepLink(node: LearningPathNodeDto): RouteLocationRaw {
  return fallbackRoute(node.nodeType, {
    kcCode: node.kcCode,
    problemId: node.suggestedProblemId,
    resourceType: node.suggestedResourceType,
  });
}

export function resolveClassroomQuestDeepLink(
  classroomQuest: ClassroomQuestDto,
): RouteLocationRaw {
  if (classroomQuest.deepLinkRoute && classroomQuest.deepLinkRoute.startsWith('/')) {
    return {
      path: classroomQuest.deepLinkRoute,
      query: paramsAsQuery(classroomQuest.deepLinkParams),
    };
  }
  return fallbackRoute(classroomQuest.nodeType, {
    kcCode: classroomQuest.kcCodes[0] ?? null,
    problemId: classroomQuest.targetProblemId,
    resourceType: classroomQuest.targetResourceType,
  });
}

function extractProblemIdFromPayload(payload: Record<string, unknown>): number | null {
  const candidate = payload.suggestedProblemId ?? payload.targetProblemId;
  if (typeof candidate === 'number') return candidate;
  if (typeof candidate === 'string' && /^\d+$/.test(candidate)) return Number(candidate);
  return null;
}

function extractResourceTypeFromPayload(payload: Record<string, unknown>): string | null {
  const candidate = payload.suggestedResourceType ?? payload.targetResourceType;
  if (typeof candidate === 'string') return candidate;
  return null;
}

export function evidenceErrorMessage(code: string | null | undefined): string | null {
  if (!code) return null;
  switch (code) {
    case 'EVIDENCE_REQUIRED_SUBMISSION':
      return '需要先通过一道相关题目，然后选择那次提交作为证据。';
    case 'EVIDENCE_REQUIRED_RESOURCE':
      return '需要先在工坊生成或阅读一份相关资源。';
    case 'EVIDENCE_REQUIRED_PAGE_READ':
      return '需要先阅读对应课本页码。';
    case 'EVIDENCE_REQUIRED_MASTERY':
      return '当前 KC 掌握度还不够，先去练习提升再来。';
    case 'INVALID_SUBMISSION':
      return '所选提交不属于你或还未通过，请选择一次状态为 PASSED 的提交。';
    case 'WRONG_PROBLEM':
      return '所选提交对应的题目不在任务的目标题集合内。';
    case 'INVALID_RESOURCE':
      return '所选资源不存在或不属于你。';
    case 'WRONG_RESOURCE':
      return '所选资源不是该任务指定的目标资源。';
    case 'INVALID_PAGE_READ':
      return '所选页码不存在。';
    default:
      return null;
  }
}
