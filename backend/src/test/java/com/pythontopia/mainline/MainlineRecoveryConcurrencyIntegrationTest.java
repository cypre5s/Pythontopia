package com.pythontopia.mainline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.pythontopia.PythontopiaApplication;
import com.pythontopia.mainline.domain.MainlineStageProgress;
import com.pythontopia.mainline.star.ChapterStarService;
import com.pythontopia.mainline.star.StarSource;
import com.pythontopia.world.boss.ExpeditionScoreService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Concurrent recovery requests must produce one award result and one audit event. */
@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class MainlineRecoveryConcurrencyIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
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

  @Autowired MainlineRecoveryService recoveryService;

  @Autowired MainlineStageProgressRepository stageProgressRepository;

  @Autowired JdbcTemplate jdbc;

  @MockBean ExpeditionScoreService expeditionScoreService;

  @SpyBean ChapterStarService chapterStarService;

  @Test
  void concurrentEndlessSummaryAwardsOnce() throws Exception {
    long userId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Recovery race', 'test-hash', 'STUDENT') RETURNING id",
            Long.class,
            "recovery-race-" + UUID.randomUUID());
    String runId = UUID.randomUUID().toString();
    when(expeditionScoreService.summarizeVerifiedAttempts(userId, runId, 1))
        .thenReturn(new ExpeditionScoreService.VerifiedAttemptSummary(10, 8));

    CountDownLatch applyCalls = new CountDownLatch(2);
    doAnswer(
            invocation -> {
              applyCalls.countDown();
              applyCalls.await(500, TimeUnit.MILLISECONDS);
              return invocation.callRealMethod();
            })
        .when(chapterStarService)
        .applyRecoveryStar(userId, 1, StarSource.ENDLESS_RECOVERY);

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> first =
          workers.submit(
              () -> {
                start.await();
                return recoveryService.recordEndlessSummary(userId, 1, runId);
              });
      Future<Boolean> second =
          workers.submit(
              () -> {
                start.await();
                return recoveryService.recordEndlessSummary(userId, 1, runId);
              });
      start.countDown();

      List<Boolean> results =
          List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

      assertThat(results).containsExactlyInAnyOrder(true, false);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM star_award_event"
                      + " WHERE user_id = ? AND chapter = 1 AND source = 'ENDLESS_RECOVERY'",
                  Integer.class,
                  userId))
          .isEqualTo(1);
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void videoProgressRepositoryUsesMigratedServerClockColumn() {
    long userId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Video clock', 'test-hash', 'STUDENT') RETURNING id",
            Long.class,
            "video-clock-" + UUID.randomUUID());
    long stageId =
        jdbc.queryForObject("SELECT id FROM mainline_stage ORDER BY id LIMIT 1", Long.class);

    MainlineStageProgress first = stageProgressRepository.addVideoSeconds(userId, stageId, 120);
    jdbc.update(
        "UPDATE mainline_stage_progress SET video_last_reported_at = now() - interval '10 seconds'"
            + " WHERE user_id = ? AND stage_id = ?",
        userId,
        stageId);
    MainlineStageProgress second = stageProgressRepository.addVideoSeconds(userId, stageId, 120);

    assertThat(first.videoWatchedSec()).isZero();
    assertThat(second.videoWatchedSec()).isEqualTo(10);
  }
}
