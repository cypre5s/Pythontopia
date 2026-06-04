// 智能体事务所证据卡数据契约（与 AgentOfficeScene.vue 共享）。
import type { WorkshopStatusFlag } from '@/api/dto/Workshop';

export interface OfficeEvidence {
  resourceId: number;
  runId: number;
  status: string;
  groundingStatus: WorkshopStatusFlag;
  safetyStatus: WorkshopStatusFlag;
  /** 课本页码（page_no）列表，从资源内容里收集，用于「内容依据：课本第 X 页」。 */
  pageNos: number[];
  /** 是否基于通识知识（无课件证据）。 */
  basedOnGeneral: boolean;
}
