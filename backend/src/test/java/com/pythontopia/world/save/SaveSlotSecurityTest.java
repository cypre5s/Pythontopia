package com.pythontopia.world.save;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.PythontopiaApplication;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.save.dto.SaveSlotDto;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = PythontopiaApplication.class)
class SaveSlotSecurityTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add(
        "pythontopia.security.jwt.secret",
        () -> "test-secret-must-be-long-enough-for-hs256");
    registry.add("pythontopia.security.teacherInviteCode", () -> "test-teacher-code");
  }

  @Autowired SaveSlotService service;
  @Autowired JdbcTemplate jdbcTemplate;

  private Long userId;

  @BeforeEach
  void seedUser() {
    jdbcTemplate.update(
        "DELETE FROM save_slot WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'save-security-%')");
    jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'save-security-%'");
    String username = "save-security-" + System.nanoTime();
    jdbcTemplate.update(
        "INSERT INTO users (username, display_name, password_hash, role) VALUES (?, 'Save Security', 'hash', 'STUDENT')",
        username);
    userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = ?", Long.class, username);
  }

  @Test
  void malformedSnapshotIsRejectedBeforeDatabaseCast() {
    service.create(userId, 0, "Security slot");

    assertThatThrownBy(() -> service.saveSnapshot(userId, 0, "not-json"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            exception -> {
              BusinessException business = (BusinessException) exception;
              assertThat(business.status()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(business.code()).isEqualTo("SAVE_SLOT_SNAPSHOT_INVALID");
            });
  }

  @Test
  void concurrentSameSlotCreationReturnsOneSuccessAndControlledConflicts() throws Exception {
    List<Throwable> failures = runConcurrently(12, () -> service.create(userId, 0, "Race slot"));

    assertThat(service.list(userId)).hasSize(1);
    assertThat(failures).hasSize(11);
    assertThat(failures)
        .allSatisfy(
            failure -> {
              assertThat(failure).isInstanceOf(BusinessException.class);
              BusinessException business = (BusinessException) failure;
              assertThat(business.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(business.code()).isEqualTo("SAVE_SLOT_CONFLICT");
            });
  }

  @Test
  void concurrentActivationsPreserveExactlyOneActiveSlotWithoutDatabaseErrors() throws Exception {
    service.create(userId, 0, "Slot zero");
    service.create(userId, 1, "Slot one");

    var executor = Executors.newFixedThreadPool(12);
    CountDownLatch ready = new CountDownLatch(12);
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<Void>> tasks = new ArrayList<>();
    for (int index = 0; index < 12; index++) {
      int slotIndex = index % 2;
      tasks.add(
          () -> {
            ready.countDown();
            start.await();
            service.activate(userId, slotIndex);
            return null;
          });
    }
    var futures = tasks.stream().map(executor::submit).toList();
    ready.await();
    start.countDown();
    for (var future : futures) {
      future.get();
    }
    executor.shutdownNow();

    assertThat(service.list(userId)).filteredOn(SaveSlotDto::isActive).hasSize(1);
  }

  private List<Throwable> runConcurrently(int count, Callable<?> operation) throws Exception {
    var executor = Executors.newFixedThreadPool(count);
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<Throwable>> tasks = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      tasks.add(
          () -> {
            ready.countDown();
            start.await();
            try {
              operation.call();
              return null;
            } catch (Throwable failure) {
              return failure;
            }
          });
    }
    var futures = tasks.stream().map(executor::submit).toList();
    ready.await();
    start.countDown();
    List<Throwable> failures = new ArrayList<>();
    for (var future : futures) {
      Throwable failure = future.get();
      if (failure != null) {
        failures.add(failure);
      }
    }
    executor.shutdownNow();
    return failures;
  }
}
