// Agentic Loop 前端冻结类型（Phase 0 预置）。
// F2 的 loopSlice / LoopProgress 与 B2b 的 BrowseTimeline 只读引用，禁止各自重定义。
// 镜像 services/pythontopia-graph/pythontopia_graph/schemas/agentic_loop.py（HTTP/镜像契约）。

export type LoopPhase = 'plan' | 'recall' | 'retrieve' | 'act' | 'critique' | 'compose'
export type LoopStepStatus = 'pending' | 'active' | 'done' | 'failed'
export type CitationKind = 'courseware' | 'external'

export interface LoopCitation {
  kind: CitationKind
  ref: string
  label: string
}

export interface LoopStep {
  id: string
  phase: LoopPhase
  label: string
  toolName?: string
  status: LoopStepStatus
  durationMs?: number
  citations?: LoopCitation[]
  reasonSummary?: string // 仅一句摘要；禁原始 CoT
  error?: string
}

export interface LoopPlan {
  steps: LoopStep[]
  bounded: true
  maxSteps: number
}
