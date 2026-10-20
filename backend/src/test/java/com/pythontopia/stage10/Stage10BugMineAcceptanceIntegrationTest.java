package com.pythontopia.stage10;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.PythontopiaApplication;
import com.pythontopia.submission.judge.StubPythonSandboxConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Import(StubPythonSandboxConfiguration.class)
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Stage10BugMineAcceptanceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add(
        "pythontopia.security.jwt.secret",
        () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
    registry.add("pythontopia.services.graphUrl", () -> "http://graph.test");
    registry.add("pythontopia.judge.python", () -> "python3");
    registry.add("pythontopia.judge.provider", () -> "piston");
    registry.add("pythontopia.judge.piston.base-url", () -> "http://piston.test");
    registry.add("pythontopia.judge.piston.python-version", () -> "3.11.0");
    registry.add("pythontopia.bugMine.maxRetriesPerSession", () -> "2");
  }

  @Autowired TestRestTemplate restTemplate;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper objectMapper;
  @Autowired RestTemplate graphRestTemplate;

  @Test
  void openSessionPersistsAllCardsAndHidesHints() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("mine-stu01");
    Long userId = userId("mine-stu01");
    Long submissionId = seedFailedSubmission(userId, 476L);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(token, Map.of("submissionId", submissionId)),
            Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long sessionId = ((Number) response.getBody().get("id")).longValue();

    Integer cards =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bug_mine_card WHERE session_id = ?",
            Integer.class,
            sessionId);
    assertThat(cards).isEqualTo(6);

    Integer revealed =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bug_mine_card WHERE session_id = ? AND revealed = TRUE",
            Integer.class,
            sessionId);
    assertThat(revealed).isZero();

    ResponseEntity<Map> fetched =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId,
            HttpMethod.GET,
            bearer(token, null),
            Map.class);
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody().get("profileDepthTarget")).isNotNull();
    assertThat(fetched.getBody().get("profileGuidance")).isNotNull();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> cardList = (List<Map<String, Object>>) fetched.getBody().get("cards");
    assertThat(cardList).hasSize(6);

    Long layer2CardId =
        cardList.stream()
            .filter(card -> "HINT_LAYER_2".equals(card.get("cardType")))
            .map(card -> ((Number) card.get("id")).longValue())
            .findFirst()
            .orElseThrow();
    ResponseEntity<Map> blocked =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId + "/cards/" + layer2CardId + "/reveal",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(blocked.getBody().get("code")).isEqualTo("BUG_MINE_DEPTH_LOCKED");

    jdbcTemplate.update("UPDATE bug_mine_session SET depth_layer = 4 WHERE id = ?", sessionId);
    ResponseEntity<Map> reveal =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId + "/cards/" + layer2CardId + "/reveal",
            HttpMethod.POST,
            bearer(token, null),
            Map.class);
    assertThat(reveal.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Boolean) reveal.getBody().get("revealed")).isTrue();
    Integer depth =
        jdbcTemplate.queryForObject(
            "SELECT depth_layer FROM bug_mine_session WHERE id = ?",
            Integer.class,
            sessionId);
    assertThat(depth).isEqualTo(4);

    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void retrySubmitResolvedSessionAndIncrementsMastery() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("mine-stu02");
    Long userId = userId("mine-stu02");
    Long questId = seedFixBugQuest(userId, 476L);
    Long submissionId = seedFailedSubmission(userId, 476L, questId);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> open =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(token, Map.of("submissionId", submissionId)),
            Map.class);
    Long sessionId = ((Number) open.getBody().get("id")).longValue();

    String code =
        "radius = float(input())\n"
            + "area = 3.1415 * radius * radius\n"
            + "print(f\"{area:.4f}\")\n";
    ResponseEntity<Map> retry =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId + "/retry-submit",
            HttpMethod.POST,
            bearer(token, Map.of("code", code)),
            Map.class);
    assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(retry.getBody().get("status")).isEqualTo("PASSED");
    assertThat(retry.getBody().get("source")).isEqualTo("BUG_MINE_RETRY");

    String status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM bug_mine_session WHERE id = ?",
            String.class,
            sessionId);
    assertThat(status).isEqualTo("RESOLVED");

    Long resolvedSubmissionId = ((Number) retry.getBody().get("id")).longValue();
    Integer practicePass =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND source = 'PRACTICE_PASS'",
            Integer.class,
            userId);
    Integer duplicateAttemptEvents =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND idempotency_key = ?",
            Integer.class,
            userId,
            "submission:" + resolvedSubmissionId);
    assertThat(practicePass).isEqualTo(1);
    assertThat(duplicateAttemptEvents).isEqualTo(1);
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void crossUserSessionAccessReturns404() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String stuA = registerAndLogin("mine-stu03");
    Long aId = userId("mine-stu03");
    Long submissionId = seedFailedSubmission(aId, 476L);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));
    ResponseEntity<Map> open =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(stuA, Map.of("submissionId", submissionId)),
            Map.class);
    Long sessionId = ((Number) open.getBody().get("id")).longValue();

    String stuB = registerAndLogin("mine-stu04");
    ResponseEntity<Map> blocked =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId,
            HttpMethod.GET,
            bearer(stuB, null),
            Map.class);
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void retryExceedsLimitReturns429() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("mine-stu05");
    Long userId = userId("mine-stu05");
    Long submissionId = seedFailedSubmission(userId, 476L);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));
    ResponseEntity<Map> open =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(token, Map.of("submissionId", submissionId)),
            Map.class);
    Long sessionId = ((Number) open.getBody().get("id")).longValue();

    // Pre-set retry count to the maximum to deterministically hit the limit
    jdbcTemplate.update("UPDATE bug_mine_session SET retry_count = 2 WHERE id = ?", sessionId);
    ResponseEntity<Map> third =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId + "/retry-submit",
            HttpMethod.POST,
            bearer(token, Map.of("code", "print(\"hi\")")),
            Map.class);
    assertThat(third.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(third.getBody().get("code")).isEqualTo("BUG_MINE_RETRY_EXCEEDED");
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void errorBookOrdersOpenSessionsByWeakMasteryAndProfilePatterns() throws Exception {
    String token = registerAndLogin("mine-stu06");
    Long userId = userId("mine-stu06");
    List<Map<String, Object>> problems =
        jdbcTemplate.queryForList(
            """
            SELECT DISTINCT ON (kc_code) id, kc_code, title
            FROM course_problem
            WHERE kc_code <> ''
            ORDER BY kc_code, id
            LIMIT 2
            """);
    assertThat(problems).hasSizeGreaterThanOrEqualTo(2);

    Map<String, Object> weakProblem = problems.get(0);
    Map<String, Object> strongerProblem = problems.get(1);
    Long weakProblemId = ((Number) weakProblem.get("id")).longValue();
    Long strongerProblemId = ((Number) strongerProblem.get("id")).longValue();
    String weakKc = (String) weakProblem.get("kc_code");
    String strongerKc = (String) strongerProblem.get("kc_code");

    Long strongerSubmissionId = seedFailedSubmission(userId, strongerProblemId);
    Long weakSubmissionId = seedFailedSubmission(userId, weakProblemId);
    Long strongerSessionId =
        seedOpenBugMineSession(userId, strongerSubmissionId, strongerProblemId, 1);
    Long weakSessionId = seedOpenBugMineSession(userId, weakSubmissionId, weakProblemId, 3);

    jdbcTemplate.update(
        """
        INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence, last_event_at)
        VALUES (?, ?, 0.820, 0.700, now()), (?, ?, 0.120, 0.500, now())
        ON CONFLICT (user_id, kc_code) DO UPDATE SET
          mastery_score = EXCLUDED.mastery_score,
          confidence = EXCLUDED.confidence,
          last_event_at = EXCLUDED.last_event_at,
          updated_at = now()
        """,
        userId,
        strongerKc,
        userId,
        weakKc);
    jdbcTemplate.update(
        """
        INSERT INTO learner_profile (user_id, pack_id, dimensions)
        VALUES (?, 43, ?::jsonb)
        ON CONFLICT (user_id) DO UPDATE SET dimensions = EXCLUDED.dimensions
        """,
        userId,
        """
        {"error_pattern":{"score":0.74,"tags":["WA","边界条件","变量追踪"]}}
        """);

    ResponseEntity<List> response =
        restTemplate.exchange(
            "/api/bug-mine/errorbook",
            HttpMethod.GET,
            bearer(token, null),
            List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = response.getBody();
    assertThat(items).hasSize(2);
    assertThat(((Number) items.get(0).get("sessionId")).longValue()).isEqualTo(weakSessionId);
    assertThat(((Number) items.get(1).get("sessionId")).longValue()).isEqualTo(strongerSessionId);

    Map<String, Object> first = items.get(0);
    assertThat(first.get("kcCode")).isEqualTo(weakKc);
    assertThat(first.get("mistakePatterns")).asList().contains("WA", "变量追踪");
    assertThat(first.get("patternMatched")).isEqualTo(true);
    assertThat(((Number) first.get("depthLayer")).intValue()).isEqualTo(3);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> steps = (List<Map<String, Object>>) first.get("depthSteps");
    assertThat(steps).hasSize(5);
    assertThat(steps.get(0).get("title")).isEqualTo("L1 裸重试");
    assertThat(steps.get(2).get("unlocked")).isEqualTo(true);
    assertThat(steps.get(3).get("unlocked")).isEqualTo(false);
  }

  @Test
  void errorBookRanksStaleLowConfidenceItemsAsForgettingEchoes() throws Exception {
    String token = registerAndLogin("mine-stu06b");
    Long userId = userId("mine-stu06b");
    List<Map<String, Object>> problems =
        jdbcTemplate.queryForList(
            """
            SELECT DISTINCT ON (kc_code) id, kc_code
            FROM course_problem
            WHERE kc_code <> ''
            ORDER BY kc_code, id
            LIMIT 2
            """);
    assertThat(problems).hasSizeGreaterThanOrEqualTo(2);

    Map<String, Object> staleProblem = problems.get(0);
    Map<String, Object> freshProblem = problems.get(1);
    Long staleProblemId = ((Number) staleProblem.get("id")).longValue();
    Long freshProblemId = ((Number) freshProblem.get("id")).longValue();
    String staleKc = (String) staleProblem.get("kc_code");
    String freshKc = (String) freshProblem.get("kc_code");

    Long staleSubmissionId = seedFailedSubmission(userId, staleProblemId);
    Long freshSubmissionId = seedFailedSubmission(userId, freshProblemId);
    Long staleSessionId = seedOpenBugMineSession(userId, staleSubmissionId, staleProblemId, 1);
    Long freshSessionId = seedOpenBugMineSession(userId, freshSubmissionId, freshProblemId, 1);
    jdbcTemplate.update(
        "UPDATE bug_mine_session SET opened_at = now() - interval '1 day' WHERE id = ?",
        staleSessionId);

    jdbcTemplate.update(
        """
        INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence, last_event_at)
        VALUES
          (?, ?, 0.620, 0.200, now() - interval '35 days'),
          (?, ?, 0.620, 0.900, now())
        ON CONFLICT (user_id, kc_code) DO UPDATE SET
          mastery_score = EXCLUDED.mastery_score,
          confidence = EXCLUDED.confidence,
          last_event_at = EXCLUDED.last_event_at,
          updated_at = now()
        """,
        userId,
        staleKc,
        userId,
        freshKc);

    ResponseEntity<List> response =
        restTemplate.exchange(
            "/api/bug-mine/errorbook",
            HttpMethod.GET,
            bearer(token, null),
            List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = response.getBody();
    assertThat(items).hasSize(2);
    assertThat(((Number) items.get(0).get("sessionId")).longValue()).isEqualTo(staleSessionId);
    assertThat(((Number) items.get(1).get("sessionId")).longValue()).isEqualTo(freshSessionId);

    Map<String, Object> first = items.get(0);
    assertThat(first.get("kcCode")).isEqualTo(staleKc);
    // FSRS-lite：decayRisk = 1 - 可提取性 R；久未复习的项风险应显著高于刚复习的项。
    assertThat(((Number) first.get("decayRisk")).doubleValue()).isGreaterThan(0.2);
    assertThat(((Number) first.get("decayRisk")).doubleValue())
        .isGreaterThan(((Number) items.get(1).get("decayRisk")).doubleValue());
    assertThat(((Number) first.get("minePriority")).doubleValue()).isGreaterThan(0.3);
    assertThat(first.get("recommendationReason").toString()).contains("遗忘回声");
  }

  @Test
  void staleMasteryCanOpenForgettingEchoBugMineSession() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("mine-stu06c");
    Long userId = userId("mine-stu06c");
    Map<String, Object> problem =
        jdbcTemplate.queryForMap(
            """
            SELECT id, kc_code
            FROM course_problem
            WHERE kc_code <> ''
            ORDER BY id
            LIMIT 1
            """);
    String kcCode = (String) problem.get("kc_code");

    jdbcTemplate.update(
        """
        INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence, last_event_at)
        VALUES (?, ?, 0.720, 0.250, now() - interval '40 days')
        ON CONFLICT (user_id, kc_code) DO UPDATE SET
          mastery_score = EXCLUDED.mastery_score,
          confidence = EXCLUDED.confidence,
          last_event_at = EXCLUDED.last_event_at,
          updated_at = now()
        """,
        userId,
        kcCode);

    ResponseEntity<List> echoes =
        restTemplate.exchange(
            "/api/bug-mine/echoes",
            HttpMethod.GET,
            bearer(token, null),
            List.class);
    assertThat(echoes.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> candidates = echoes.getBody();
    assertThat(candidates).isNotEmpty();
    assertThat(candidates.get(0).get("kcCode")).isEqualTo(kcCode);
    assertThat(candidates.get(0).get("recommendationReason").toString()).contains("遗忘回声");

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> opened =
        restTemplate.exchange(
            "/api/bug-mine/echoes",
            HttpMethod.POST,
            bearer(token, Map.of("kcCode", kcCode)),
            Map.class);
    assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long sessionId = ((Number) opened.getBody().get("id")).longValue();
    Long submissionId = ((Number) opened.getBody().get("submissionId")).longValue();
    // E：入矿题按掌握度选恰当难度，未必是最小 id 的题，但必属同一 KC。
    Long openedProblemId = ((Number) opened.getBody().get("problemId")).longValue();
    String openedKc =
        jdbcTemplate.queryForObject(
            "SELECT kc_code FROM course_problem WHERE id = ?", String.class, openedProblemId);
    assertThat(openedKc).isEqualTo(kcCode);
    assertThat(opened.getBody().get("profileGuidance").toString()).contains("遗忘");

    String source =
        jdbcTemplate.queryForObject(
            "SELECT source FROM practice_submission WHERE id = ?",
            String.class,
            submissionId);
    assertThat(source).isEqualTo("BUG_MINE_ECHO");
    Integer cards =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bug_mine_card WHERE session_id = ?",
            Integer.class,
            sessionId);
    assertThat(cards).isEqualTo(6);
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void failedRetryAdvancesDepthLayer() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("mine-stu07");
    Long userId = userId("mine-stu07");
    Long submissionId = seedFailedSubmission(userId, 476L);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> open =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(token, Map.of("submissionId", submissionId)),
            Map.class);
    Long sessionId = ((Number) open.getBody().get("id")).longValue();

    ResponseEntity<Map> retry =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId + "/retry-submit",
            HttpMethod.POST,
            bearer(token, Map.of("code", "print(\"still wrong\")")),
            Map.class);
    assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(retry.getBody().get("status")).isEqualTo("FAILED");

    Integer depth =
        jdbcTemplate.queryForObject(
            "SELECT depth_layer FROM bug_mine_session WHERE id = ?",
            Integer.class,
            sessionId);
    assertThat(depth).isEqualTo(2);
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void errorBookIncludesVariantProblemForSameKc() throws Exception {
    String token = registerAndLogin("mine-stu08");
    Long userId = userId("mine-stu08");
    List<Map<String, Object>> kcPair =
        jdbcTemplate.queryForList(
            """
            SELECT base.id AS base_id, sibling.id AS sibling_id, sibling.title AS sibling_title
            FROM course_problem base
            JOIN course_problem sibling
              ON sibling.kc_code = base.kc_code AND sibling.id <> base.id
            WHERE base.kc_code <> ''
            ORDER BY base.id, sibling.id
            LIMIT 1
            """);
    assertThat(kcPair).isNotEmpty();

    Long baseProblemId = ((Number) kcPair.get(0).get("base_id")).longValue();
    Long siblingProblemId = ((Number) kcPair.get(0).get("sibling_id")).longValue();
    String siblingTitle = (String) kcPair.get(0).get("sibling_title");

    Long submissionId = seedFailedSubmission(userId, baseProblemId);
    seedOpenBugMineSession(userId, submissionId, baseProblemId, 5, siblingProblemId);

    ResponseEntity<List> response =
        restTemplate.exchange(
            "/api/bug-mine/errorbook",
            HttpMethod.GET,
            bearer(token, null),
            List.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> item = (Map<String, Object>) response.getBody().get(0);
    @SuppressWarnings("unchecked")
    Map<String, Object> variant = (Map<String, Object>) item.get("variantProblem");
    assertThat(variant).isNotNull();
    assertThat(((Number) variant.get("id")).longValue()).isEqualTo(siblingProblemId);
    assertThat(variant.get("title")).isEqualTo(siblingTitle);
  }

  @Test
  void variantSubmitBeforeLayer5IsRejected() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("mine-stu09");
    Long userId = userId("mine-stu09");
    Long submissionId = seedFailedSubmission(userId, 476L);

    graphServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> open =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(token, Map.of("submissionId", submissionId)),
            Map.class);
    Long sessionId = ((Number) open.getBody().get("id")).longValue();

    ResponseEntity<Map> blocked =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId + "/variant-submit",
            HttpMethod.POST,
            bearer(token, Map.of("code", "print(1)")),
            Map.class);
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(blocked.getBody().get("code")).isEqualTo("BUG_MINE_VARIANT_LOCKED");
    graphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void variantSubmitAtLayer5UsesVariantSourceAndCanResolveSession() throws Exception {
    String token = registerAndLogin("mine-stu09b");
    Long userId = userId("mine-stu09b");
    List<Map<String, Object>> kcPair =
        jdbcTemplate.queryForList(
            """
            SELECT base.id AS base_id, sibling.id AS sibling_id
            FROM course_problem base
            JOIN course_problem sibling
              ON sibling.kc_code = base.kc_code AND sibling.id <> base.id
            WHERE base.kc_code <> ''
            ORDER BY base.id, sibling.id
            LIMIT 1
            """);
    Assumptions.assumeFalse(kcPair.isEmpty(), "seed 数据缺少同 KC 兄弟题，跳过变式掌握确认用例");

    Long baseProblemId = ((Number) kcPair.get(0).get("base_id")).longValue();
    Long siblingProblemId = ((Number) kcPair.get(0).get("sibling_id")).longValue();
    Long submissionId = seedFailedSubmission(userId, baseProblemId);
    Long sessionId = seedOpenBugMineSession(userId, submissionId, baseProblemId, 5, siblingProblemId);

    String code =
        "radius = float(input())\n"
            + "area = 3.1415 * radius * radius\n"
            + "print(f\"{area:.4f}\")\n";
    ResponseEntity<Map> variant =
        restTemplate.exchange(
            "/api/bug-mine/sessions/" + sessionId + "/variant-submit",
            HttpMethod.POST,
            bearer(token, Map.of("code", code)),
            Map.class);
    assertThat(variant.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(variant.getBody().get("source")).isEqualTo("BUG_MINE_VARIANT");

    if ("PASSED".equals(variant.getBody().get("status"))) {
      String status =
          jdbcTemplate.queryForObject(
              "SELECT status FROM bug_mine_session WHERE id = ?",
              String.class,
              sessionId);
      assertThat(status).isEqualTo("RESOLVED");
      Boolean masteryConfirmed =
          jdbcTemplate.queryForObject(
              "SELECT mastery_confirmed FROM bug_mine_session WHERE id = ?",
              Boolean.class,
              sessionId);
      assertThat(masteryConfirmed).isTrue();
    }
  }

  @Test
  void reopenExistingSessionDoesNotCallDebugGraphAgain() throws Exception {
    MockRestServiceServer graphServer = MockRestServiceServer.bindTo(graphRestTemplate).build();
    String token = registerAndLogin("mine-stu10");
    Long userId = userId("mine-stu10");
    Long submissionId = seedFailedSubmission(userId, 476L);

    graphServer
        .expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo("http://graph.test/agents/debug/analyze"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                graphDebugResponse(), MediaType.APPLICATION_JSON));

    ResponseEntity<Map> first =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(token, Map.of("submissionId", submissionId)),
            Map.class);
    Long sessionId = ((Number) first.getBody().get("id")).longValue();

    ResponseEntity<Map> second =
        restTemplate.exchange(
            "/api/bug-mine/sessions",
            HttpMethod.POST,
            bearer(token, Map.of("submissionId", submissionId)),
            Map.class);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(((Number) second.getBody().get("id")).longValue()).isEqualTo(sessionId);
    graphServer.verify(Duration.ofSeconds(2));
  }

  private String graphDebugResponse() throws Exception {
    Map<String, Object> body =
        Map.of(
            "counter_example",
                Map.of(
                    "stdin", "25",
                    "expected_stdout", "1963.4375",
                    "your_stdout", "25",
                    "diff_summary", "shape diff",
                    "redacted", false),
            "variable_trace_summary", Map.of("steps", List.of(), "truncated", false),
            "wrong_assumption",
                Map.of(
                    "you_thought", "你以为 print 直接给结果",
                    "actually", "实际需要先计算面积",
                    "evidence_kc_codes", List.of("PY43-KC-016")),
            "hint_layers",
                List.of(
                    Map.of(
                        "layer", 1,
                        "headline", "想一想",
                        "body", "请先用文字描述你想做什么",
                        "related_pages", List.of(12)),
                    Map.of(
                        "layer", 2,
                        "headline", "看代码",
                        "body", "关注第 2 行的算式",
                        "related_pages", List.of(13)),
                    Map.of(
                        "layer", 3,
                        "headline", "改造方向",
                        "body", "请先读取输入，再相乘计算面积",
                        "related_pages", List.of(14))),
            "retry_advice",
                Map.of("summary", "再读题面", "expected_kc_codes", List.of("PY43-KC-016")),
            "safety_passed", true,
            "safety_reasons", List.of());
    return objectMapper.writeValueAsString(body);
  }

  private Long seedFailedSubmission(Long userId, Long problemId) {
    return seedFailedSubmission(userId, problemId, null);
  }

  private Long seedFailedSubmission(Long userId, Long problemId, Long questId) {
    Long submissionId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO practice_submission (user_id, problem_id, quest_id, source, language, code, status, submitted_at)
            VALUES (?, ?, ?, 'TRAINING', 'PYTHON_3_11', ?, 'FAILED', now() - interval '2 minutes')
            RETURNING id
            """,
            Long.class,
            userId,
            problemId,
            questId,
            "print(\"42\")");
    jdbcTemplate.update(
        """
        INSERT INTO judge_result (submission_id, verdict, passed_cases, total_cases, failure_reason)
        VALUES (?, 'WA', 0, 3, '与样例不一致')
        """,
        submissionId);
    return submissionId;
  }

  private Long seedFixBugQuest(Long userId, Long problemId) {
    String kcCode =
        jdbcTemplate.queryForObject(
            "SELECT kc_code FROM course_problem WHERE id = ?", String.class, problemId);
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO quest (
          user_id, source, title, kc_code, node_type, payload, due_date, status,
          required_evidence_type, target_problem_ids
        ) VALUES (?, 'SYSTEM_DEFAULT', '修复练习', ?, 'FIX_BUG', '{}'::jsonb,
                  CURRENT_DATE + 1, 'IN_PROGRESS', 'submission', ARRAY[?]::BIGINT[])
        RETURNING id
        """,
        Long.class,
        userId,
        kcCode,
        problemId);
  }

  private Long seedOpenBugMineSession(
      Long userId, Long submissionId, Long problemId, int depthLayer) {
    return seedOpenBugMineSession(userId, submissionId, problemId, depthLayer, null);
  }

  private Long seedOpenBugMineSession(
      Long userId,
      Long submissionId,
      Long problemId,
      int depthLayer,
      Long variantProblemId) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO bug_mine_session (
          user_id, submission_id, problem_id, quest_id, status, depth_layer,
          variant_problem_id, profile_depth_target
        )
        VALUES (?, ?, ?, NULL, 'OPEN', ?, ?, 5)
        RETURNING id
        """,
        Long.class,
        userId,
        submissionId,
        problemId,
        depthLayer,
        variantProblemId);
  }

  private Long userId(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username = ?", Long.class, username);
  }

  private String registerAndLogin(String username) {
    restTemplate.postForEntity(
        "/api/auth/register",
        Map.of(
            "username", username,
            "password", "P@ssw0rd1",
            "displayName", username,
            "role", "STUDENT"),
        Map.class);
    ResponseEntity<Map> response =
        restTemplate.postForEntity(
            "/api/auth/login",
            Map.of("username", username, "password", "P@ssw0rd1"),
            Map.class);
    return (String) response.getBody().get("token");
  }

  private HttpEntity<?> bearer(String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(body, headers);
  }
}
