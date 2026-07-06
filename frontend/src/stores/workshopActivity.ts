// 工坊「智能体活动」共享状态：GuideOverlay 的 orchestrator SSE 状态写入这里，
// WorkshopChat 读取后用 ThinkingStream 呈现同一份「正在协同」文字。
//
// 不新增 SSE 管线，只镜像现有 useSseStream 的 status/plan/traces。
import { defineStore } from 'pinia';
import type { SsePlan, SseTraceEvent } from '@/composables/useSseStream';

export type ActivityKind = 'ask' | 'resource' | 'orchestrator';
export type ActivityStatus = 'idle' | 'streaming' | 'done' | 'error';

interface WorkshopActivityState {
  status: ActivityStatus;
  kind: ActivityKind | null;
  /** 当前任务一句话（如「答疑：for 循环怎么用」）。 */
  label: string;
  plan: SsePlan | null;
  traces: SseTraceEvent[];
  /** 本次任务开始时间戳（ms），用于「已等 Ns」。 */
  startedAt: number;
  /** 自增任务序号：用于「完工后延迟收尾」时判断是否已被新任务取代（防串台）。 */
  seq: number;
}

export const useWorkshopActivityStore = defineStore('workshopActivity', {
  state: (): WorkshopActivityState => ({
    status: 'idle',
    kind: null,
    label: '',
    plan: null,
    traces: [],
    startedAt: 0,
    seq: 0,
  }),
  getters: {
    isBusy: (state): boolean => state.status === 'streaming',
    /** 是否有进行中或刚完工的任务（驱动 ThinkingStream 显示协同状态）。 */
    hasRun: (state): boolean => state.status !== 'idle',
    /** 最新活动节点（用于映射成人话标签）。 */
    activeNode: (state): string => {
      const list = state.traces;
      return list.length > 0 ? (list[list.length - 1]?.node ?? '') : '';
    },
  },
  actions: {
    /**
     * 开始一轮新活动并返回「运行令牌」（即新 seq）。
     * 发起方应持令牌调 syncTraces/syncPlan/finish——新活动开始后，旧令牌的写入一律被忽略，
     * 保证过期的流不会覆盖新活动、旧会话残留数据不回灌。
     */
    start(kind: ActivityKind, label: string): number {
      this.status = 'streaming';
      this.kind = kind;
      this.label = label;
      this.plan = null;
      this.traces = [];
      this.startedAt = Date.now();
      this.seq += 1;
      return this.seq;
    },
    /**
     * 当前是否接受该（kind, token）来源的镜像：
     * 流活跃 + kind 匹配 + 令牌（若携带）等于当前 seq。
     * 不带令牌为旧签名兼容路径（GuideOverlay 现网调用），保持原有 kind+streaming 判定。
     */
    acceptsRun(kind: ActivityKind, token?: number): boolean {
      if (this.status !== 'streaming' || this.kind !== kind) return false;
      return token === undefined || token === this.seq;
    },
    /** 仅在对应 kind 的流活跃（且令牌未过期）时镜像，避免另一条/过期流的陈旧数据串台。 */
    syncTraces(kind: ActivityKind, traces: SseTraceEvent[], token?: number): void {
      if (this.acceptsRun(kind, token)) {
        this.traces = traces;
      }
    },
    syncPlan(kind: ActivityKind, plan: SsePlan | null, token?: number): void {
      if (this.acceptsRun(kind, token)) {
        this.plan = plan;
      }
    },
    /** 收尾。携带令牌且已被新活动取代时忽略——过期流不得把新活动标记完结。 */
    finish(ok: boolean, token?: number): void {
      if (token !== undefined && token !== this.seq) return;
      this.status = ok ? 'done' : 'error';
    },
    idle(): void {
      this.status = 'idle';
      this.kind = null;
      this.label = '';
      this.plan = null;
      this.traces = [];
      this.startedAt = 0;
    },
  },
});
