package com.pythontopia.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.pythontopia.PythontopiaApplication;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.submission.dto.SubmissionCreateRequest;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.judge.JudgeService;
import com.pythontopia.submission.judge.JudgeService.JudgeOutcome;
import com.pythontopia.submission.service.SubmissionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * P0.3 验收：限频 TOCTOU 竞态。50 个线程对同一 user 在 10s 窗口内并发正式提交，
 * 期望「至多允许数（1）成功，其余全部 429，practice_submission 无重复行」。
 *
 * <p>没有 {@code pg_advisory_xact_lock} 串行化时，50 线程会同时读到 count=0 并全部插入（50 成功），
 * 该用例必然失败；加上事务咨询锁后只有 1 个成功，证明竞态被消除（非假绿）。
 *
 * <p>判题被 {@link MockBean} 成 AC，避免依赖真实 Python 沙箱；问题用空 kc_code 的合成题，
 * 使 {@code handleCompletion} 早返回、不触发任何学习事件监听器，把用例聚焦在限频原子性本身。
 */
@Testcontainers
@SpringBootTest(
    classes = PythontopiaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubmissionRateLimitConcurrencyTest {

  private static final long RACE_PROBLEM_ID = 9_000_001L;

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
    // 让 50 个并发提交线程都能各持一条连接，避免连接池饥饿掩盖真实的咨询锁串行化行为。
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "50");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add(
        "pythontopia.security.jwt.secret", () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
    registry.add("pythontopia.services.graphUrl", () -> "http://graph.test");
    registry.add("pythontopia.judge.python", () -> "python3");
  }

  @MockBean JudgeService judgeService;

  @Autowired SubmissionService submissionService;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void fiftyConcurrentSubmissionsAllowOnlyOneWithinWindow() throws Exception {
    when(judgeService.judge(anyLong(), anyLong(), any()))
        .thenReturn(new JudgeOutcome(Verdict.AC, 1, 1, null));

    Long userId =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES ('race-stu', 'race', 'x', 'STUDENT') RETURNING id",
            Long.class);
    // 空 kc_code → SubmissionService.handleCompletion 早返回，不发学习事件（聚焦限频原子性）。
    jdbcTemplate.update(
        "INSERT INTO course_problem (id, pack_id, kc_code, title) VALUES (?, 43, '', 'race-problem')",
        RACE_PROBLEM_ID);

    int threads = 50;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicInteger success = new AtomicInteger();
    AtomicInteger throttled = new AtomicInteger();
    AtomicInteger otherErrors = new AtomicInteger();

    SubmissionCreateRequest request =
        new SubmissionCreateRequest(RACE_PROBLEM_ID, "print(1)", null, SubmissionSource.TRAINING);

    for (int i = 0; i < threads; i++) {
      pool.submit(
          () -> {
            ready.countDown();
            try {
              start.await();
              submissionService.submit(userId, request);
              success.incrementAndGet();
            } catch (BusinessException ex) {
              if ("SUBMISSION_THROTTLED".equals(ex.code())) {
                throttled.incrementAndGet();
              } else {
                otherErrors.incrementAndGet();
              }
            } catch (Exception ex) {
              otherErrors.incrementAndGet();
            } finally {
              done.countDown();
            }
          });
    }

    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
    pool.shutdownNow();

    Integer rows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM practice_submission WHERE user_id = ?", Integer.class, userId);

    assertThat(otherErrors.get()).as("no unexpected errors / no race exceptions").isZero();
    assertThat(success.get()).as("at most the allowed number (1) succeed").isEqualTo(1);
    assertThat(throttled.get()).as("the rest are throttled with 429").isEqualTo(threads - 1);
    assertThat(rows).as("exactly one row persisted, no duplicates").isEqualTo(1);
  }
}
