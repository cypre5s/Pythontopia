package com.pythontopia.bugmine.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DebugAgentRequest(
    @JsonProperty("user_id") Long userId,
    @JsonProperty("submission_id") Long submissionId,
    @JsonProperty("problem_id") Long problemId,
    @JsonProperty("kc_codes") List<String> kcCodes,
    @JsonProperty("verdict") String verdict,
    @JsonProperty("code") String code,
    @JsonProperty("counter_example") CounterExamplePayload counterExample,
    @JsonProperty("variable_trace") VariableTracePayload variableTrace,
    @JsonProperty("rag_pages") List<Integer> ragPages) {

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
}
