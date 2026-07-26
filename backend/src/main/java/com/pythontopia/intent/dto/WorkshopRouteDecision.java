package com.pythontopia.intent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

/** 百晓工坊统一路由决策；Graph snake_case 输入通过 JsonAlias 映射，对前端保持 camelCase。 */
public record WorkshopRouteDecision(
    @JsonAlias("route_id") String routeId,
    int version,
    Task task,
    Orchestration orchestration,
    Tools tools,
    Policies policies,
    Output output,
    Fallback fallback,
    Personalization personalization,
    Search search) {

  public WorkshopRouteDecision(
      String routeId,
      int version,
      Task task,
      Orchestration orchestration,
      Tools tools,
      Policies policies,
      Output output,
      Fallback fallback) {
    this(
        routeId,
        version,
        task,
        orchestration,
        tools,
        policies,
        output,
        fallback,
        new Personalization("STANDARD", List.of(), List.of(), "DOMAIN_EVENTS_ONLY"),
        null);
  }

  public WorkshopRouteDecision(
      String routeId,
      int version,
      Task task,
      Orchestration orchestration,
      Tools tools,
      Policies policies,
      Output output,
      Fallback fallback,
      Personalization personalization) {
    this(
        routeId,
        version,
        task,
        orchestration,
        tools,
        policies,
        output,
        fallback,
        personalization,
        null);
  }

  public record Task(
      String family,
      String subtype,
      @JsonAlias("sub_intents") List<String> subIntents,
      String complexity,
      double confidence,
      @JsonAlias("needs_clarification") boolean needsClarification,
      @JsonAlias("clarification_question") String clarificationQuestion) {}

  public record Orchestration(
      String topology,
      @JsonAlias("primary_agent") String primaryAgent,
      @JsonAlias("supporting_agents") List<String> supportingAgents) {}

  public record Tools(List<String> required, List<String> optional, List<String> forbidden) {}

  public record Policies(
      @JsonAlias("max_tool_calls") int maxToolCalls,
      @JsonAlias("max_calls_per_tool") int maxCallsPerTool,
      @JsonAlias("max_agent_calls") int maxAgentCalls,
      @JsonAlias("max_planning_depth") int maxPlanningDepth,
      @JsonAlias("max_parallel_agents") int maxParallelAgents,
      @JsonAlias("max_replans") int maxReplans,
      @JsonAlias("require_grounding") boolean requireGrounding,
      @JsonAlias("require_safety") boolean requireSafety,
      @JsonAlias("allow_common_knowledge") boolean allowCommonKnowledge,
      @JsonAlias("read_before_write") boolean readBeforeWrite) {}

  public record Output(
      @JsonAlias("schema_name") String schemaName,
      @JsonAlias("render_mode") String renderMode,
      @JsonAlias("require_evidence") boolean requireEvidence) {}

  public record Fallback(
      @JsonAlias("on_low_confidence") String onLowConfidence,
      @JsonAlias("on_missing_context") String onMissingContext,
      @JsonAlias("on_no_evidence") String onNoEvidence,
      @JsonAlias("on_tool_failure") String onToolFailure,
      @JsonAlias("on_model_failure") String onModelFailure,
      @JsonAlias("on_guard_failure") String onGuardFailure) {}

  public record Personalization(
      String strategy,
      @JsonAlias("signals_used") List<String> signalsUsed,
      List<String> explanations,
      @JsonAlias("writeback_mode") String writebackMode) {}

  /** 仅 C3 携带的有界 ToT + A* 公开搜索合同；非 C3 为 null。只含公开路径与数值成本。 */
  public record Search(
      String strategy,
      List<SearchCandidate> candidates,
      @JsonAlias("selected_candidate_id") String selectedCandidateId,
      @JsonAlias("max_depth") int maxDepth,
      @JsonAlias("score_formula") String scoreFormula,
      @JsonAlias("replan_policy") ReplanPolicy replanPolicy) {}

  public record SearchCandidate(
      @JsonAlias("candidate_id") String candidateId,
      String label,
      @JsonAlias("agent_path") List<String> agentPath,
      @JsonAlias("tool_path") List<String> toolPath,
      int depth,
      @JsonAlias("g_cost") double gCost,
      @JsonAlias("h_cost") double hCost,
      double penalty,
      @JsonAlias("total_cost") double totalCost) {}

  public record ReplanPolicy(@JsonAlias("max_candidate_switches") int maxCandidateSwitches) {}
}
