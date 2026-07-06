/**
 * 学情顿悟洞察（被动画像沉淀出的「我发现你在…」结论）。
 * 后端 GET /api/learner/insights 返回，按 created_at 倒序；字段 camelCase。
 */
export interface LearnerInsightDto {
  id: number;
  /** 洞察文本，如「列表别名可变性反复出错」。 */
  text: string;
  /** 命中的画像维度 key（error_pattern / cognitive_style / ...）。 */
  dimensionKey: string;
  tags: string[];
  /** 证据 event id，可回放溯源。 */
  evidenceEventIds: number[];
  /** 置信度 0-1。 */
  confidence: number;
  /** 来源：GRAPH（多智能体）/ RULE（规则）。 */
  source: string;
  createdAt: string;
}
