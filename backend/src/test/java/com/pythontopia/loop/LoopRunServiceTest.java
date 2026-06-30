package com.pythontopia.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.loop.dto.LoopRunDto;
import com.pythontopia.loop.dto.LoopRunRecordRequest;
import java.sql.Array;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 闭环 run 审计落库的真库往返测试，覆盖 §4 S-spine 第 4 条测试矩阵：①闭环 run 落库字段齐全
 * ②查询 run 详情 ③tools_used 数组持久化 ④grounding/safety pass、token、trace_id 记录 ⑤非法输入
 * 校验（含越权）。
 *
 * <p>用真 Postgres + 实跑 Flyway V171（而非 mock）才能真正验证 {@code text[]} 列、CHECK 约束与全字段
 * 往返。刻意<b>不</b>启 {@code @SpringBootTest} 全量上下文：本能力为并发构建的一支，整库上下文会被其它
 * 在途 stream（如 mem-backend 未完工的 bean）拖垮；这里只装配本域 {@link LoopRunRepository} +
 * {@link LoopRunService} + 真实 {@code JdbcTemplate}，与兄弟 stream 的在途代码解耦（见交付回执 Phase 2
 * 接线点）。
 */
@Testcontainers
class LoopRunServiceTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbcTemplate;
  private static LoopRunService service;
  private static LoopRuntimeService runtimeService;
  private static ObjectMapper objectMapper;

  private Long userId;
  private Long otherUserId;

  @BeforeAll
  static void migrateAndWire() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());

    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    jdbcTemplate = new JdbcTemplate(dataSource);
    service = new LoopRunService(new LoopRunRepository(jdbcTemplate));
    objectMapper = new ObjectMapper();
    runtimeService =
        new LoopRuntimeService(
            new LoopRunRepository(jdbcTemplate),
            new LoopRuntimeRepository(jdbcTemplate, objectMapper));
  }

  @BeforeEach
  void seedUsers() {
    jdbcTemplate.update(
        "DELETE FROM loop_eval_result WHERE loop_run_id IN (SELECT id FROM loop_run WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'loop-svc-%'))");
    jdbcTemplate.update(
        "DELETE FROM agent_tool_call WHERE loop_run_id IN (SELECT id FROM loop_run WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'loop-svc-%'))");
    jdbcTemplate.update(
        "DELETE FROM agent_run WHERE loop_run_id IN (SELECT id FROM loop_run WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'loop-svc-%'))");
    jdbcTemplate.update(
        "DELETE FROM loop_checkpoint WHERE loop_run_id IN (SELECT id FROM loop_run WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'loop-svc-%'))");
    jdbcTemplate.update(
        "DELETE FROM loop_run WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'loop-svc-%')");
    jdbcTemplate.update("DELETE FROM policy_registry WHERE namespace LIKE 'loop-svc-%'");
    jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'loop-svc-%'");
    userId = insertStudent("loop-svc-a-" + System.nanoTime());
    otherUserId = insertStudent("loop-svc-b-" + System.nanoTime());
  }

  private Long insertStudent(String username) {
    jdbcTemplate.update(
        "INSERT INTO users (username, display_name, password_hash, role)"
            + " VALUES (?, 'Loop Svc', 'hash', 'STUDENT')",
        username);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username = ?", Long.class, username);
  }

  private LoopRunRecordRequest fullRequest() {
    return new LoopRunRecordRequest(
        userId,
        "orch-session-001",
        2,
        6,
        5,
        1,
        List.of("HANDOUT", "BROWSE"),
        false,
        true,
        1234,
        567,
        "lf-trace-abcdef");
  }

  // ① 闭环 run 落库字段齐全（往返 DTO + 直接核对 DB 行）。
  @Test
  void recordPersistsEveryFieldAndReadsBack() {
    LoopRunDto saved = service.record(userId, fullRequest());

    assertThat(saved.id()).isNotNull().isPositive();
    assertThat(saved.userId()).isEqualTo(userId);
    assertThat(saved.sessionId()).isEqualTo("orch-session-001");
    assertThat(saved.turn()).isEqualTo(2);
    assertThat(saved.maxSteps()).isEqualTo(6);
    assertThat(saved.stepsUsed()).isEqualTo(5);
    assertThat(saved.replanCount()).isEqualTo(1);
    assertThat(saved.toolsUsed()).containsExactly("HANDOUT", "BROWSE");
    assertThat(saved.groundingPass()).isFalse();
    assertThat(saved.safetyPass()).isTrue();
    assertThat(saved.inputTokens()).isEqualTo(1234);
    assertThat(saved.outputTokens()).isEqualTo(567);
    assertThat(saved.traceId()).isEqualTo("lf-trace-abcdef");
    assertThat(saved.createdAt()).isNotNull();

    // 直接核对 DB 行，确保不是只在内存对象里齐全。
    var row =
        jdbcTemplate.queryForMap(
            "SELECT user_id, session_id, turn, max_steps, steps_used, replan_count,"
                + " grounding_pass, safety_pass, input_tokens, output_tokens, trace_id"
                + " FROM loop_run WHERE id = ?",
            saved.id());
    assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userId);
    assertThat(row.get("session_id")).isEqualTo("orch-session-001");
    assertThat(((Number) row.get("turn")).intValue()).isEqualTo(2);
    assertThat(((Number) row.get("max_steps")).intValue()).isEqualTo(6);
    assertThat(((Number) row.get("steps_used")).intValue()).isEqualTo(5);
    assertThat(((Number) row.get("replan_count")).intValue()).isEqualTo(1);
    assertThat(row.get("grounding_pass")).isEqualTo(false);
    assertThat(row.get("safety_pass")).isEqualTo(true);
    assertThat(((Number) row.get("input_tokens")).intValue()).isEqualTo(1234);
    assertThat(((Number) row.get("output_tokens")).intValue()).isEqualTo(567);
    assertThat(row.get("trace_id")).isEqualTo("lf-trace-abcdef");
  }

  // ② 查询 run 详情（按 owner 取回，与落库一致）。
  @Test
  void getForOwnerReturnsRecordedRun() {
    LoopRunDto saved = service.record(userId, fullRequest());

    LoopRunDto fetched = service.getForOwner(userId, saved.id());

    assertThat(fetched.id()).isEqualTo(saved.id());
    assertThat(fetched.sessionId()).isEqualTo(saved.sessionId());
    assertThat(fetched.toolsUsed()).containsExactly("HANDOUT", "BROWSE");
    assertThat(fetched.turn()).isEqualTo(saved.turn());
    assertThat(fetched.maxSteps()).isEqualTo(saved.maxSteps());
    assertThat(fetched.groundingPass()).isEqualTo(saved.groundingPass());
    assertThat(fetched.traceId()).isEqualTo(saved.traceId());
    assertThat(fetched.createdAt()).isEqualTo(saved.createdAt());
  }

  // ③ tools_used 数组持久化：多元素保序 + 空数组 + null 兼容 + 直接读 text[] 列。
  @Test
  void toolsUsedArrayRoundTripsThroughTextColumn() {
    LoopRunDto multi =
        service.record(
            userId,
            new LoopRunRecordRequest(
                userId,
                "sess-tools",
                0,
                4,
                3,
                0,
                List.of("RECALL", "RETRIEVE", "HANDOUT", "BROWSE"),
                true,
                true,
                0,
                0,
                "trace-tools"));
    assertThat(multi.toolsUsed()).containsExactly("RECALL", "RETRIEVE", "HANDOUT", "BROWSE");
    assertThat(readToolsFromDb(multi.id()))
        .containsExactly("RECALL", "RETRIEVE", "HANDOUT", "BROWSE");

    // 空工具链（如纯证据轮）应落库为空数组而非 null，读回也为空列表。
    LoopRunDto empty =
        service.record(
            userId,
            new LoopRunRecordRequest(
                userId, "sess-empty", 0, 4, 0, 0, List.of(), true, true, 0, 0, "trace-empty"));
    assertThat(empty.toolsUsed()).isEmpty();
    assertThat(readToolsFromDb(empty.id())).isEmpty();

    // null 工具链按默认空数组处理（向后兼容）。
    LoopRunDto nullTools =
        service.record(
            userId,
            new LoopRunRecordRequest(
                userId, "sess-null", 0, 4, 0, 0, null, true, true, 0, 0, "trace-null"));
    assertThat(nullTools.toolsUsed()).isEmpty();
    assertThat(readToolsFromDb(nullTools.id())).isEmpty();
  }

  // ④ grounding/safety pass、token、trace_id 记录（含 critic 双拒 + 空 trace 边界）。
  @Test
  void criticVerdictsTokensAndTraceArePersistedExactly() {
    LoopRunDto bothFailed =
        service.record(
            userId,
            new LoopRunRecordRequest(
                userId, "sess-critic", 1, 4, 4, 1, List.of("BROWSE"), false, false, 4096, 2048,
                "trace-critic-1"));
    assertThat(bothFailed.groundingPass()).isFalse();
    assertThat(bothFailed.safetyPass()).isFalse();
    assertThat(bothFailed.inputTokens()).isEqualTo(4096);
    assertThat(bothFailed.outputTokens()).isEqualTo(2048);
    assertThat(bothFailed.traceId()).isEqualTo("trace-critic-1");

    LoopRunDto fetched = service.getForOwner(userId, bothFailed.id());
    assertThat(fetched.groundingPass()).isFalse();
    assertThat(fetched.safetyPass()).isFalse();
    assertThat(fetched.inputTokens()).isEqualTo(4096);
    assertThat(fetched.outputTokens()).isEqualTo(2048);
    assertThat(fetched.traceId()).isEqualTo("trace-critic-1");

    // M0 trace completeness gate：闭环 run 必须可追踪，缺 trace 不能进入审计表。
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(
                    userId, "sess-no-trace", 0, 4, 0, 0, List.of(), true, true, 0, 0, null)),
        HttpStatus.BAD_REQUEST);
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(
                    userId, "sess-blank-trace", 0, 4, 0, 0, List.of(), true, true, 0, 0, " ")),
        HttpStatus.BAD_REQUEST);
  }

  // ⑤ 非法输入校验（字段越界 + 越权）。
  @Test
  void invalidInputsAreRejected() {
    // 空 session_id。
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(
                    userId, "   ", 0, 4, 0, 0, List.of(), true, true, 0, 0, "trace-invalid")),
        HttpStatus.BAD_REQUEST);

    // max_steps 越界（> 8，破坏「有界非自由 ReAct」红线）。
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(userId, "s", 0, 9, 0, 0, List.of(), true, true, 0, 0, "trace-invalid")),
        HttpStatus.BAD_REQUEST);

    // max_steps < 1。
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(userId, "s", 0, 0, 0, 0, List.of(), true, true, 0, 0, "trace-invalid")),
        HttpStatus.BAD_REQUEST);

    // turn 为负。
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(userId, "s", -1, 4, 0, 0, List.of(), true, true, 0, 0, "trace-invalid")),
        HttpStatus.BAD_REQUEST);

    // steps_used 超过 max_steps（违反有界语义）。
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(userId, "s", 0, 4, 5, 0, List.of(), true, true, 0, 0, "trace-invalid")),
        HttpStatus.BAD_REQUEST);

    // token 为负。
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(userId, "s", 0, 4, 0, 0, List.of(), true, true, -1, 0, "")),
        HttpStatus.BAD_REQUEST);

    // 工具名过长。
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(
                    userId, "s", 0, 4, 0, 0, List.of("X".repeat(65)), true, true, 0, 0, "trace-invalid")),
        HttpStatus.BAD_REQUEST);

    // trace_id 过长。
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(
                    userId, "s", 0, 4, 0, 0, List.of(), true, true, 0, 0, "T".repeat(129))),
        HttpStatus.BAD_REQUEST);

    // 越权：body 里的 user_id 与认证用户不一致 → 403。
    assertCode(
        () ->
            service.record(
                userId,
                new LoopRunRecordRequest(
                    otherUserId, "s", 0, 4, 0, 0, List.of(), true, true, 0, 0, "trace-invalid")),
        HttpStatus.FORBIDDEN);
  }

  // 安全：跨用户取详情应隐藏存在性（404），缺失 id 亦 404。
  @Test
  void getForOwnerHidesRunsOwnedByOthers() {
    LoopRunDto saved = service.record(userId, fullRequest());

    assertCode(() -> service.getForOwner(otherUserId, saved.id()), HttpStatus.NOT_FOUND);
    assertCode(() -> service.getForOwner(userId, 999_999_999L), HttpStatus.NOT_FOUND);
  }

  @Test
  void runtimeArtifactsRoundTripAndRestoreByOwner() {
    LoopRunDto run = service.record(userId, fullRequest());

    var policy =
        runtimeService.registerPolicy(
            "loop-svc-runtime",
            "learner-loop",
            "v1",
            "ACTIVE",
            json("max_steps", 4));
    var checkpoint =
        runtimeService.saveCheckpoint(
            userId, run.id(), 2, "READY_TO_RESUME", json("lastNode", "dispatch"), "cursor-2");
    var toolCall =
        runtimeService.recordToolCall(
            userId,
            run.id(),
            checkpoint.id(),
            "resource.generate",
            json("kc", "PY43-KC-001"),
            json("status", "SUCCESS"),
            "SUCCESS",
            321);
    var agentRun =
        runtimeService.recordAgentRun(
            userId,
            run.id(),
            "ResourceDesignerAgent",
            "SUCCESS",
            "生成 input 讲义",
            json("resourceId", 7));
    List<String> loopTypes = List.of("LEARNER", "DEBUG", "RESOURCE", "WORLD", "IMPROVE");
    var evalResults =
        loopTypes.stream()
            .map(
                loopType ->
                    runtimeService.recordEvalResult(
                        userId,
                        run.id(),
                        "agentic-ab-runtime",
                        loopType.toLowerCase() + "-case",
                        loopType,
                        policy.policyVersion(),
                        json("mean_uplift", 0.23),
                        true))
            .toList();

    var snapshot = runtimeService.restoreSnapshot(userId, run.id());

    assertThat(policy.id()).isPositive();
    assertThat(checkpoint.id()).isPositive();
    assertThat(toolCall.id()).isPositive();
    assertThat(agentRun.id()).isPositive();
    assertThat(evalResults).extracting(LoopRuntimeService.EvalResultRecord::id)
        .allSatisfy(id -> assertThat(id).isPositive());
    assertThat(snapshot.loopRun().id()).isEqualTo(run.id());
    assertThat(snapshot.checkpoints()).extracting(LoopRuntimeService.CheckpointRecord::resumeCursor)
        .containsExactly("cursor-2");
    assertThat(snapshot.toolCalls()).extracting(LoopRuntimeService.ToolCallRecord::toolName)
        .containsExactly("resource.generate");
    assertThat(snapshot.agentRuns()).extracting(LoopRuntimeService.AgentRunRecord::agentName)
        .containsExactly("ResourceDesignerAgent");
    assertThat(snapshot.evalResults()).extracting(LoopRuntimeService.EvalResultRecord::loopType)
        .containsExactlyElementsOf(loopTypes);
    assertThat(snapshot.evalResults()).extracting(LoopRuntimeService.EvalResultRecord::datasetId)
        .containsOnly("agentic-ab-runtime");
  }

  @Test
  void runtimeArtifactsAreOwnerScoped() {
    LoopRunDto run = service.record(userId, fullRequest());

    assertCode(
        () ->
            runtimeService.saveCheckpoint(
                otherUserId, run.id(), 1, "READY", json("node", "dispatch"), "cursor"),
        HttpStatus.NOT_FOUND);
    assertCode(() -> runtimeService.restoreSnapshot(otherUserId, run.id()), HttpStatus.NOT_FOUND);
  }

  @Test
  void replayPlansRestoreLearnerAndDebugLoopsFromCheckpoints() {
    LoopRunDto learnerRun = service.record(userId, fullRequest());
    ObjectNode learnerState = objectMapper.createObjectNode();
    learnerState.put("nextAction", "resume micro-check after resource reading");
    learnerState.putArray("evidenceRefs").add("resource:7").add("micro-check:pending");
    learnerState.put("replayReason", "resource consumed -> micro-check pending");
    runtimeService.saveCheckpoint(
        userId, learnerRun.id(), 1, "READY_TO_RESUME", learnerState, "learner-cursor-1");
    runtimeService.recordEvalResult(
        userId,
        learnerRun.id(),
        "m1-learner-golden",
        "learner-repeat-failure",
        "LEARNER",
        "agentic_loop_v1",
        json("passRate", 1.0),
        true);

    var learnerReplay = runtimeService.restoreReplayPlan(userId, learnerRun.id());

    assertThat(learnerReplay.loopType()).isEqualTo("LEARNER");
    assertThat(learnerReplay.resumeCursor()).isEqualTo("learner-cursor-1");
    assertThat(learnerReplay.nextAction()).isEqualTo("resume micro-check after resource reading");
    assertThat(learnerReplay.evidenceRefs()).containsExactly("resource:7", "micro-check:pending");
    assertThat(learnerReplay.replayable()).isTrue();
    assertThat(learnerReplay.reason()).isEqualTo("resource consumed -> micro-check pending");

    LoopRunDto debugRun =
        service.record(
            userId,
            new LoopRunRecordRequest(
                userId,
                "debug-session-001",
                0,
                4,
                3,
                1,
                List.of("JUDGE", "DEBUG_AGENT"),
                true,
                true,
                100,
                60,
                "trace-debug-replay"));
    ObjectNode debugState = objectMapper.createObjectNode();
    debugState.put("nextAction", "replay failed submission in Bug Mine, then request retry");
    debugState.putArray("evidenceRefs").add("submission:2003").add("bug-mine:44").add("retry:pending");
    debugState.put("replayReason", "failed submission -> Bug Mine -> retry/recovery");
    runtimeService.saveCheckpoint(
        userId, debugRun.id(), 2, "READY_TO_RETRY", debugState, "debug-cursor-2");
    runtimeService.recordAgentRun(
        userId,
        debugRun.id(),
        "DebugAgent",
        "WAITING_FOR_RETRY",
        "NameError debug loop",
        json("bugMineSessionId", 44));
    runtimeService.recordEvalResult(
        userId,
        debugRun.id(),
        "m1-debug-golden",
        "debug-runtime-error-replay",
        "DEBUG",
        "agentic_loop_v1",
        json("passRate", 1.0),
        true);

    var debugReplay = runtimeService.restoreReplayPlan(userId, debugRun.id());

    assertThat(debugReplay.loopType()).isEqualTo("DEBUG");
    assertThat(debugReplay.resumeCursor()).isEqualTo("debug-cursor-2");
    assertThat(debugReplay.nextAction()).contains("Bug Mine");
    assertThat(debugReplay.evidenceRefs())
        .containsExactly("submission:2003", "bug-mine:44", "retry:pending");
    assertThat(debugReplay.replayable()).isTrue();
    assertThat(debugReplay.reason()).isEqualTo("failed submission -> Bug Mine -> retry/recovery");
    assertThat(debugReplay.steps()).extracting(LoopRuntimeService.ReplayStep::stepIndex)
        .containsExactly(2);
  }

  @Test
  void databaseRejectsNewLoopRunWithoutTraceId() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO loop_run (user_id, session_id, turn, max_steps, steps_used,"
                        + " replan_count, trace_id) VALUES (?, 'direct-empty-trace', 0, 4, 0, 0, '')",
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private List<String> readToolsFromDb(Long id) {
    return jdbcTemplate.query(
        "SELECT tools_used FROM loop_run WHERE id = ?",
        rs -> {
          if (!rs.next()) {
            return List.of();
          }
          Array array = rs.getArray("tools_used");
          if (array == null) {
            return List.of();
          }
          return List.of((String[]) array.getArray());
        },
        id);
  }

  private void assertCode(Runnable action, HttpStatus expected) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).status())
        .isEqualTo(expected);
  }

  private ObjectNode json(String key, Object value) {
    ObjectNode node = objectMapper.createObjectNode();
    if (value instanceof Number number) {
      node.put(key, number.doubleValue());
    } else if (value instanceof Boolean bool) {
      node.put(key, bool);
    } else {
      node.put(key, String.valueOf(value));
    }
    return node;
  }
}
