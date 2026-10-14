package com.pythontopia.mainline.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pythontopia.PythontopiaApplication;
import com.pythontopia.mainline.dto.ChapterTestSubmitRequest;
import com.pythontopia.mainline.test.ChapterTestJudge.JudgeReport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** Concurrent submissions for one exam must wait on its database row before judging. */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class ChapterTestSubmitConcurrencyIntegrationTest {

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
        "pythontopia.security.jwt.secret", () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
  }

  @MockBean ChapterTestJudge judge;

  @Autowired ChapterTestService service;
  @Autowired JdbcTemplate jdbc;

  @Test
  void concurrentSubmissionsJudgeOnceAndReturnTheStoredResult() throws Exception {
    long userId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Chapter test race', 'x', 'STUDENT') RETURNING id",
            Long.class,
            "chapter-race-" + UUID.randomUUID());
    long testId =
        jdbc.queryForObject(
            "INSERT INTO chapter_test (user_id, chapter, assessment_kind, status, questions,"
                + " started_at, deadline_at)"
                + " VALUES (?, 2, 'QUIZ', 'IN_PROGRESS', '{\"items\":[]}'::jsonb,"
                + " now(), now() + interval '1 hour') RETURNING id",
            Long.class,
            userId);

    CountDownLatch firstJudgeStarted = new CountDownLatch(1);
    CountDownLatch releaseFirstJudge = new CountDownLatch(1);
    CountDownLatch secondRequestStarted = new CountDownLatch(1);
    CountDownLatch secondJudgeStarted = new CountDownLatch(1);
    AtomicInteger judgeCalls = new AtomicInteger();
    when(judge.judge(any(), any()))
        .thenAnswer(
            invocation -> {
              if (judgeCalls.incrementAndGet() == 1) {
                firstJudgeStarted.countDown();
                assertThat(releaseFirstJudge.await(10, TimeUnit.SECONDS)).isTrue();
              } else {
                secondJudgeStarted.countDown();
              }
              return new JudgeReport(100, 100, List.of());
            });

    ChapterTestSubmitRequest request = new ChapterTestSubmitRequest(Map.of("q1", "A"), null);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<?> first = workers.submit(() -> service.submitQuiz(userId, 2, testId, request));
      assertThat(firstJudgeStarted.await(10, TimeUnit.SECONDS)).isTrue();

      Future<?> second =
          workers.submit(
              () -> {
                secondRequestStarted.countDown();
                return service.submitQuiz(userId, 2, testId, request);
              });
      assertThat(secondRequestStarted.await(10, TimeUnit.SECONDS)).isTrue();
      boolean duplicateReachedJudge = secondJudgeStarted.await(300, TimeUnit.MILLISECONDS);
      releaseFirstJudge.countDown();

      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);

      assertThat(duplicateReachedJudge).isFalse();
      assertThat(judgeCalls).hasValue(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM chapter_test WHERE id = ?", String.class, testId))
          .isEqualTo("SUBMITTED");
    } finally {
      releaseFirstJudge.countDown();
      workers.shutdownNow();
    }
  }
}
