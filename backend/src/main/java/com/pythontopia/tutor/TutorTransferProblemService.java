package com.pythontopia.tutor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.submission.dto.PublicProblemDto;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.judge.JudgeCaseEvaluator;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.JudgeWorkerPool;
import com.pythontopia.submission.judge.StaticCodeChecker;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes an AI Tutor transfer draft only after its reference solution passes the real
 * sandbox.
 */
@Service
public class TutorTransferProblemService {

  private static final Logger log = LoggerFactory.getLogger(TutorTransferProblemService.class);
  private static final int MAX_CASES = 10;
  private static final int CASE_TIMEOUT_MS = 5_000;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final PublicProblemRepository problemRepository;
  private final StaticCodeChecker staticCodeChecker;
  private final JudgeWorkerPool judgeWorkerPool;
  private final JudgeCaseEvaluator caseEvaluator;

  public TutorTransferProblemService(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      PublicProblemRepository problemRepository,
      StaticCodeChecker staticCodeChecker,
      JudgeWorkerPool judgeWorkerPool,
      JudgeCaseEvaluator caseEvaluator) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.problemRepository = problemRepository;
    this.staticCodeChecker = staticCodeChecker;
    this.judgeWorkerPool = judgeWorkerPool;
    this.caseEvaluator = caseEvaluator;
  }

  @Transactional
  public PublicProblemDto materialize(
      Long userId, Long sessionId, UUID runId, Long sourceProblemId, JsonNode rawDraft) {
    String stage = "request_validation";
    try {
      if (userId == null || userId < 1 || sessionId == null || sessionId < 1 || runId == null) {
        throw new IllegalArgumentException("迁移题必须绑定有效的学生、会话和运行记录");
      }
      stage = "source_problem";
      PublicProblemDto source =
          problemRepository
              .findPublic(43L, sourceProblemId)
              .orElseThrow(() -> new IllegalArgumentException("迁移题源题不可用"));
      String transferDifficulty = nextDifficulty(source.difficulty());
      stage = "draft_validation";
      TransferDraft draft = parseAndValidate(rawDraft, source.kcCode());
      String requestHash = sha256(rawDraft);
      ExistingTransfer existing = findExisting(userId, sessionId, runId);
      if (existing != null) {
        if (!requestHash.equals(existing.requestHash())) {
          throw new IllegalStateException("迁移题运行标识对应了不同草稿");
        }
        return toPublicProblem(existing.id(), source, draft, transferDifficulty);
      }

      stage = "reference_security_check";
      StaticCodeChecker.CheckResult syntax =
          judgeWorkerPool.submitAndWait(
              () -> staticCodeChecker.check(draft.referenceSolutionCode()));
      if (!syntax.passed()) {
        String failureCategory =
            syntax.failureReason() == null ? "unknown" : syntax.failureReason().split(":", 2)[0];
        String rejectedImport =
            "banned_import".equals(failureCategory)
                    && syntax.failureReason().split(":", 3).length > 1
                ? syntax.failureReason().split(":", 3)[1]
                : "none";
        log.warn(
            "AI Tutor transfer reference rejected by static checker category={} import={}"
                + " systemError={}",
            failureCategory,
            rejectedImport,
            syntax.systemError());
        throw new IllegalArgumentException("迁移题参考解未通过安全检查");
      }
      stage = "reference_self_test";
      List<JudgeTestCase> cases = new ArrayList<>();
      draft
          .samples()
          .forEach(
              sample ->
                  cases.add(new JudgeTestCase(sample.input(), sample.output(), CASE_TIMEOUT_MS)));
      draft
          .testCases()
          .forEach(
              testCase ->
                  cases.add(
                      new JudgeTestCase(
                          testCase.input(), testCase.output(), CASE_TIMEOUT_MS, true)));
      JudgeCaseEvaluator.CaseEvaluation validation =
          judgeWorkerPool.submitAndWait(
              () -> caseEvaluator.evaluate(draft.referenceSolutionCode(), cases));
      if (validation.finalVerdict() != Verdict.AC || validation.passedCases() != cases.size()) {
        log.warn(
            "AI Tutor transfer reference rejected verdict={} passedCases={} totalCases={}",
            validation.finalVerdict(),
            validation.passedCases(),
            cases.size());
        throw new IllegalArgumentException("迁移题参考解自测未通过");
      }

      stage = "persist";
      Long problemId =
          jdbcTemplate.queryForObject(
              "SELECT nextval('tutor_transfer_problem_id_seq')", Long.class);
      if (problemId == null) {
        throw new IllegalStateException("无法分配迁移题编号");
      }
      jdbcTemplate.update(
          """
        INSERT INTO course_problem (
          id, pack_id, kc_code, title, statement_markdown, input_description,
          output_description, difficulty, samples, hidden_test_cases, source_label,
          reference_solution_language, reference_solution_code, tutor_owner_user_id,
          tutor_source_problem_id, tutor_session_id, tutor_run_id, tutor_request_hash
        ) VALUES (?, 43, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, 'AI Tutor transfer',
          'Python3', ?, ?, ?, ?, ?, ?)
        """,
          problemId,
          source.kcCode(),
          draft.title(),
          draft.description(),
          draft.inputDescription(),
          draft.outputDescription(),
          transferDifficulty,
          writeJson(draft.samples()),
          writeJson(draft.testCases()),
          draft.referenceSolutionCode(),
          userId,
          sourceProblemId,
          sessionId,
          runId,
          requestHash);
      return toPublicProblem(problemId, source, draft, transferDifficulty);
    } catch (RuntimeException e) {
      log.warn(
          "AI Tutor transfer materialization failed stage={} errorType={}",
          stage,
          e.getClass().getSimpleName());
      throw e;
    }
  }

  private ExistingTransfer findExisting(Long userId, Long sessionId, UUID runId) {
    List<ExistingTransfer> rows =
        jdbcTemplate.query(
            """
            SELECT id, tutor_request_hash FROM course_problem
            WHERE tutor_owner_user_id = ? AND tutor_session_id = ? AND tutor_run_id = ?
            """,
            (rs, rowNum) ->
                new ExistingTransfer(rs.getLong("id"), rs.getString("tutor_request_hash")),
            userId,
            sessionId,
            runId);
    return rows.stream().findFirst().orElse(null);
  }

  private TransferDraft parseAndValidate(JsonNode raw, String sourceKcCode) {
    if (raw == null || !raw.isObject()) {
      throw new IllegalArgumentException("迁移题草稿格式无效");
    }
    try {
      TransferDraft draft = objectMapper.treeToValue(raw, TransferDraft.class);
      requireText(draft.title(), 200, "title");
      requireText(draft.description(), 10_000, "description");
      requireText(draft.inputDescription(), 2_000, "input_description");
      requireText(draft.outputDescription(), 2_000, "output_description");
      requireText(draft.hint(), 1_000, "hint");
      requireText(draft.referenceSolutionCode(), 8_192, "reference_solution_code");
      if (!"Python3".equals(draft.referenceSolutionLanguage())) {
        throw new IllegalArgumentException("迁移题参考解语言必须是 Python3");
      }
      if (sourceKcCode == null
          || sourceKcCode.isBlank()
          || !draft.targetKcs().contains(sourceKcCode)) {
        throw new IllegalArgumentException("迁移题知识点必须来自源题");
      }
      if (draft.samples().isEmpty()
          || draft.testCases().isEmpty()
          || draft.samples().size() + draft.testCases().size() > MAX_CASES) {
        throw new IllegalArgumentException("迁移题至少需要一个样例和一个隐藏测试，最多十个用例");
      }
      HashSet<String> inputs = new HashSet<>();
      for (TransferSample sample : draft.samples()) {
        validatePair(sample, inputs);
      }
      for (TransferSample testCase : draft.testCases()) {
        validatePair(testCase, inputs);
      }
      return draft;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("迁移题草稿字段无效", e);
    }
  }

  private void validatePair(TransferSample sample, HashSet<String> inputs) {
    if (sample == null) {
      throw new IllegalArgumentException("迁移题用例无效");
    }
    requireText(sample.input(), 2_000, "test_case.input");
    requireText(sample.output(), 2_000, "test_case.output");
    if (!inputs.add(sample.input())) {
      throw new IllegalArgumentException("迁移题公开样例和隐藏测试不能重复");
    }
  }

  private void requireText(String value, int maxLength, String field) {
    if (value == null || value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException("迁移题字段无效：" + field);
    }
  }

  private String sha256(JsonNode value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(value));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException | JsonProcessingException e) {
      throw new IllegalStateException("无法校验迁移题草稿", e);
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("无法保存迁移题用例", e);
    }
  }

  private String nextDifficulty(String sourceDifficulty) {
    return switch (sourceDifficulty == null ? "" : sourceDifficulty.toLowerCase(Locale.ROOT)) {
      case "basic" -> "medium";
      case "medium", "hard" -> "hard";
      default -> throw new IllegalArgumentException("迁移题源题难度不可用");
    };
  }

  private PublicProblemDto toPublicProblem(
      Long id, PublicProblemDto source, TransferDraft draft, String difficulty) {
    return new PublicProblemDto(
        id,
        43L,
        source.kcCode(),
        draft.title(),
        draft.description(),
        draft.inputDescription(),
        draft.outputDescription(),
        difficulty,
        objectMapper.valueToTree(draft.samples()));
  }

  private record ExistingTransfer(Long id, String requestHash) {}

  private record TransferDraft(
      String title,
      String description,
      @JsonProperty("input_description") String inputDescription,
      @JsonProperty("output_description") String outputDescription,
      List<TransferSample> samples,
      String hint,
      @JsonProperty("target_kcs") List<String> targetKcs,
      @JsonProperty("reference_solution_language") String referenceSolutionLanguage,
      @JsonProperty("reference_solution_code") String referenceSolutionCode,
      @JsonProperty("test_cases") List<TransferSample> testCases) {}

  private record TransferSample(String input, String output) {}
}
