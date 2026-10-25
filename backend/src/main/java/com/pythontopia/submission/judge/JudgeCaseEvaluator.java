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

  /** 128 + SIGKILL(9)：被 ulimit 硬上限 / OOM killer 强杀。 */
  private static final int EXIT_SIGKILL = 137;
  /** 128 + SIGXCPU(24)：触发 CPU 时间软上限。 */
  private static final int EXIT_SIGXCPU = 152;

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
    Integer maxMemoryKb = null;
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
      if (result.memoryKb() != null) {
        maxMemoryKb = maxMemoryKb == null ? result.memoryKb() : Math.max(maxMemoryKb, result.memoryKb());
      }
      Verdict verdict = verdictFor(result, testCase);
      entry.put("verdict", verdict.name());
      caseTrace.add(entry);
      if (verdict == Verdict.AC) {
        passed += 1;
        continue;
      }
      finalVerdict = verdict;
      failureReason = summariseFailure(verdict, result, testCase, i + 1);
      break;
    }

    ObjectNode trace = objectMapper.createObjectNode();
    trace.put("sandbox", sandbox.name());
    trace.put("totalRuntimeMs", totalRuntime);
    trace.set("cases", caseTrace);
    return new CaseEvaluation(finalVerdict, passed, totalRuntime, maxMemoryKb, failureReason, trace);
  }

  /** 解析公开样例用例（{@code hidden=false}）。 */
  public List<JudgeTestCase> extractCases(JsonNode array, int maxCases) {
    return extractCases(array, maxCases, false);
  }

  /**
   * 解析测试用例。
   *
   * @param hidden 是否为隐藏用例；隐藏用例的期望输出<strong>绝不</strong>回显给学生
   *     （见 {@link #summariseFailure}）。
   */
  public List<JudgeTestCase> extractCases(JsonNode array, int maxCases, boolean hidden) {
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
      out.add(new JudgeTestCase(input, expected, timeout, hidden));
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
    String errorKind = result.errorKind();
    if ("SYSTEM_ERROR".equals(errorKind)) {
      return Verdict.SYSTEM_ERROR;
    }
    if ("TLE".equals(errorKind)) {
      return Verdict.TLE;
    }
    if ("MLE".equals(errorKind)) {
      return Verdict.MLE;
    }
    if (result.exitCode() != 0) {
      // 进程被信号杀死（128+signal）：CPU 上限 / OOM killer 属超时或超内存，不是学生的运行时错误。
      if (isKilledBySignal(result.exitCode())) {
        return looksLikeMemoryExhaustion(result.stderr()) ? Verdict.MLE : Verdict.TLE;
      }
      return looksLikeMemoryExhaustion(result.stderr()) ? Verdict.MLE : Verdict.RE;
    }
    String expected = normalise(testCase.expectedOutput());
    String actual = normalise(result.stdout());
    return expected.equals(actual) ? Verdict.AC : Verdict.WA;
  }

  /** 128+SIGKILL(9) / 128+SIGXCPU(24)：被 CPU 时间上限或 OOM killer 强杀。 */
  private static boolean isKilledBySignal(int exitCode) {
    return exitCode == EXIT_SIGKILL || exitCode == EXIT_SIGXCPU;
  }

  /** 内存耗尽的特征：ulimit -v 触发的 MemoryError / 分配失败。 */
  private static boolean looksLikeMemoryExhaustion(String stderr) {
    if (stderr == null || stderr.isEmpty()) {
      return false;
    }
    return stderr.contains("MemoryError")
        || stderr.contains("Cannot allocate memory")
        || stderr.contains("Out of memory");
  }

  private String normalise(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\r\n", "\n").replace("\r", "\n").stripTrailing();
  }

  /**
   * 生成学生可见的失败原因。<strong>硬约束</strong>：隐藏用例的期望输出绝不出现在返回文本里
   * （{@code failureReason} 会经 SubmissionDetailDto 直接渲染给学生），只告知第几个隐藏用例不匹配。
   *
   * @param caseNumber 用例序号（从 1 开始）
   */
  private String summariseFailure(
      Verdict verdict, SandboxRunResult result, JudgeTestCase testCase, int caseNumber) {
    return switch (verdict) {
      case WA ->
          testCase.hidden()
              ? "第 " + caseNumber + " 个隐藏用例输出不匹配"
              : "测试用例失败：期望 '"
                  + truncate(testCase.expectedOutput())
                  + "'，实际 '"
                  + truncate(result.stdout())
                  + "'";
      case RE -> "运行时错误：" + truncate(result.stderr());
      case TLE -> "超过时间限制 " + testCase.timeoutMs() + "ms";
      case MLE -> "超出内存限制";
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
      Integer maxMemoryKb,
      String failureReason,
      ObjectNode trace) {}
}
