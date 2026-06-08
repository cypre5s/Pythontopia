// 工坊「智能体活动」共享状态：把 WorkshopChat / GuideOverlay 的 SSE 实时状态
//（答疑 / 资源生成 / orchestrator 协同）抬到 store，供侧栏 MarvisPanel 常驻展示
//「现在在做什么」。
//
// 写入方：WorkshopChat（ask / resource）、GuideOverlay（orchestrator）。
// MarvisPanel 只读。不新增 SSE 管线，仅镜像现有 useSseStream 的 status/plan/traces。
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
    /** 是否有进行中或刚完工的任务（驱动 marvis 显示工位流水线而非待命名单）。 */
    hasRun: (state): boolean => state.status !== 'idle',
    /** 最新活动节点（用于映射成人话标签）。 */
    activeNode: (state): string => {
      const list = state.traces;
      return list.length > 0 ? (list[list.length - 1]?.node ?? '') : '';
    },
  },
  actions: {
    start(kind: ActivityKind, label: string): void {
      this.status = 'streaming';
      this.kind = kind;
      this.label = label;
      this.plan = null;
      this.traces = [];
      this.startedAt = Date.now();
      this.seq += 1;
    },
    /** 仅在对应 kind 的流活跃时镜像，避免另一条流的陈旧数据串台。 */
    syncTraces(kind: ActivityKind, traces: SseTraceEvent[]): void {
      if (this.status === 'streaming' && this.kind === kind) {
        this.traces = traces;
      }
    },
    syncPlan(kind: ActivityKind, plan: SsePlan | null): void {
      if (this.status === 'streaming' && this.kind === kind) {
        this.plan = plan;
      }
    },
    finish(ok: boolean): void {
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
