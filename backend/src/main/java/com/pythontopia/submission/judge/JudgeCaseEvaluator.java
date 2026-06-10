package com.pythontopia.submission.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.submission.dto.Verdict;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JudgeCaseEvaluator {

  private final PythonSandbox sandbox;
  private final ObjectMapper objectMapper;

  public JudgeCaseEvaluator(PythonSandbox sandbox, ObjectMapper objectMapper) {
    this.sandbox = sandbox;
    this.objectMapper = objectMapper;
  }

  public CaseEvaluation evaluate(String code, List<JudgeTestCase> cases) {
    int passed = 0;
    Verdict finalVerdict = Verdict.AC;
    String failureReason = null;
    int totalRuntime = 0;
    ArrayNode caseTrace = objectMapper.createArrayNode();
    for (int i = 0; i < cases.size(); i++) {
      JudgeTestCase testCase = cases.get(i);
      SandboxRunResult result = sandbox.run(code, testCase);
      ObjectNode entry = objectMapper.createObjectNode();
      entry.put("index", i);
      entry.put("durationMs", result.durationMs());
      entry.put("timedOut", result.timedOut());
      entry.put("exitCode", result.exitCode());
      entry.put("stdoutBytes", result.stdout() == null ? 0 : result.stdout().length());
      entry.put("stderrBytes", result.stderr() == null ? 0 : result.stderr().length());
      totalRuntime += (int) result.durationMs();
      Verdict verdict = verdictFor(result, testCase);
      entry.put("verdict", verdict.name());
      caseTrace.add(entry);
      if (verdict == Verdict.AC) {
        passed += 1;
        continue;
      }
      finalVerdict = verdict;
      failureReason = summariseFailure(verdict, result, testCase);
      break;
    }

    ObjectNode trace = objectMapper.createObjectNode();
    trace.put("sandbox", sandbox.name());
    trace.put("totalRuntimeMs", totalRuntime);
    trace.set("cases", caseTrace);
    return new CaseEvaluation(finalVerdict, passed, totalRuntime, failureReason, trace);
  }

  public List<JudgeTestCase> extractCases(JsonNode array, int maxCases) {
    List<JudgeTestCase> out = new ArrayList<>();
    if (array == null || !array.isArray()) {
      return out;
    }
    Iterator<JsonNode> iter = array.elements();
    while (iter.hasNext()) {
      JsonNode element = iter.next();
      String input = element.path("input").asText("");
      String expected = element.path("output").asText("");
      if (expected.isEmpty()) {
        expected = element.path("expected_stdout").asText("");
      }
      int timeout = element.path("timeout_ms").asInt(5000);
      out.add(new JudgeTestCase(input, expected, timeout));
      if (out.size() >= maxCases) {
        break;
      }
    }
    return out;
  }

  private Verdict verdictFor(SandboxRunResult result, JudgeTestCase testCase) {
    if (result.timedOut()) {
      return Verdict.TLE;
    }
    if ("SYSTEM_ERROR".equals(result.errorKind())) {
      return Verdict.SYSTEM_ERROR;
    }
    if (result.exitCode() != 0) {
      return Verdict.RE;
    }
    String expected = normalise(testCase.expectedOutput());
    String actual = normalise(result.stdout());
    return expected.equals(actual) ? Verdict.AC : Verdict.WA;
  }

  private String normalise(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\r\n", "\n").replace("\r", "\n").stripTrailing();
  }

  private String summariseFailure(Verdict verdict, SandboxRunResult result, JudgeTestCase testCase) {
    return switch (verdict) {
      case WA ->
          "测试用例失败：期望 '"
              + truncate(testCase.expectedOutput())
              + "'，实际 '"
              + truncate(result.stdout())
              + "'";
      case RE -> "运行时错误：" + truncate(result.stderr());
      case TLE -> "超过时间限制 " + testCase.timeoutMs() + "ms";
      case SYSTEM_ERROR -> "判题系统异常";
      default -> "失败：" + verdict.name();
    };
  }

  private String truncate(String value) {
    if (value == null) {
      return "";
    }
    String stripped = value.length() > 120 ? value.substring(0, 120) + "…" : value;
    return stripped.replace("\n", "\\n");
  }

  public record CaseEvaluation(
      Verdict finalVerdict,
      int passedCases,
      int totalRuntimeMs,
      String failureReason,
      ObjectNode trace) {}
}
