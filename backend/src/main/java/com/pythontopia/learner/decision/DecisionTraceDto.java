package com.pythontopia.learner.decision;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 决策玻璃盒一条记录（P7.1）。{@code decisionKind} 把 resource_run.scenario 归一为前端可分类的决策类型
 * （problem/path/remediation/push/resource/profile），{@code conclusion} 为该决策的人话结论；
 * {@code agentName} 即参与决策的 agent，{@code evidencePageIds} 为课件证据链（可展开溯源）。
 */
public record DecisionTraceDto(
    @JsonProperty("agent_name") String agentName,
    String scenario,
    @JsonProperty("decision_kind") String decisionKind,
    String status,
    String conclusion,
    @JsonProperty("evidence_page_ids") List<Long> evidencePageIds,
    String trace,
    String ts) {}
