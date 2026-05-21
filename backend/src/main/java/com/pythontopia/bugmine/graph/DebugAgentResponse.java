package com.pythontopia.bugmine.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DebugAgentResponse(
    @JsonProperty("counter_example") CounterExamplePayload counterExample,
    @JsonProperty("variable_trace_summary") VariableTracePayload variableTraceSummary,
    @JsonProperty("wrong_assumption") WrongAssumption wrongAssumption,
    @JsonProperty("hint_layers") List<HintLayer> hintLayers,
    @JsonProperty("retry_advice") RetryAdvice retryAdvice,
    @JsonProperty("safety_passed") boolean safetyPassed,
    @JsonProperty("safety_reasons") List<String> safetyReasons) {

  public record CounterExamplePayload(
      @JsonProperty("stdin") String stdin,
      @JsonProperty("expected_stdout") String expectedStdout,
      @JsonProperty("your_stdout") String yourStdout,
      @JsonProperty("diff_summary") String diffSummary,
      @JsonProperty("redacted") boolean redacted) {}

  public record VariableTracePayload(
      @JsonProperty("steps") List<TraceStep> steps,
      @JsonProperty("truncated") boolean truncated) {}

  public record TraceStep(
      @JsonProperty("line") int line,
      @JsonProperty("locals") java.util.Map<String, String> locals) {}

  public record WrongAssumption(
      @JsonProperty("you_thought") String youThought,
      @JsonProperty("actually") String actually,
      @JsonProperty("evidence_kc_codes") List<String> evidenceKcCodes) {}

  public record HintLayer(
      @JsonProperty("layer") int layer,
      @JsonProperty("headline") String headline,
      @JsonProperty("body") String body,
      @JsonProperty("related_pages") List<Integer> relatedPages) {}

  public record RetryAdvice(
      @JsonProperty("summary") String summary,
      @JsonProperty("expected_kc_codes") List<String> expectedKcCodes) {}
}
