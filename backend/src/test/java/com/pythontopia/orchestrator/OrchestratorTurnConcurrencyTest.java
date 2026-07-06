package com.pythontopia.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.bugmine.service.BugMineService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 同一会话并发重复提交防护（TDD 红线测试）：上一条消息的流还在进行时，同 (userId, sessionId)
 * 的新 turn 必须按统一错误约定快速拒绝（409 SESSION_BUSY，中文短文案）；流终结后释放，
 * 不同会话 / 不同用户 / 空 sessionId 互不阻塞。
 */
class OrchestratorTurnConcurrencyTest {

  private final OrchestratorGraphClient client = mock(OrchestratorGraphClient.class);
  private final BugMineService bugMine = mock(BugMineService.class);
  private final OrchestratorService service = new OrchestratorService(client, bugMine);

  private static OrchestratorTurnRequest request(String sessionId) {
    return new OrchestratorTurnRequest("讲讲 for 循环", sessionId, null, null, null, null, null, null);
  }

  private void stubBugMine() {
    when(bugMine.decayingKcCodes(anyLong(), anyInt())).thenReturn(List.of());
  }

  @Test
  void duplicateConcurrentTurnOnSameSessionRejectedWithConflict() {
    stubBugMine();
    service.turn(7L, request("sess-1"));

    assertThatThrownBy(() -> service.turn(7L, request("sess-1")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.code()).isEqualTo("SESSION_BUSY");
              // 用户可见文案：中文、简短、可执行。
              assertThat(ex.getMessage()).matches(".*[\\u4e00-\\u9fa5].*");
              assertThat(ex.getMessage().length()).isLessThanOrEqualTo(30);
            });
  }

  @Test
  void concurrentDuplicateSubmissionsOnlyOneAccepted() throws Exception {
    stubBugMine();
    int threads = 6;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicInteger accepted = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int i = 0; i < threads; i++) {
        pool.submit(() -> {
          try {
            start.await();
            service.turn(7L, request("sess-racy"));
            accepted.incrementAndGet();
          } catch (BusinessException e) {
            rejected.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        });
      }
      start.countDown();
      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    assertThat(accepted.get()).as("同一会话并发重复提交只允许一个进入").isEqualTo(1);
    assertThat(rejected.get()).isEqualTo(threads - 1);
  }

  @Test
  void sessionReleasedAfterStreamFinishedAllowsNextTurn() {
    stubBugMine();
    service.turn(7L, request("sess-1"));

    ArgumentCaptor<Runnable> onFinished = ArgumentCaptor.forClass(Runnable.class);
    verify(client)
        .turnStream(eq(7L), any(), anyList(), any(SseEmitter.class), onFinished.capture());
    onFinished.getValue().run();

    assertThatCode(() -> service.turn(7L, request("sess-1"))).doesNotThrowAnyException();
  }

  @Test
  void differentSessionsDoNotBlockEachOther() {
    stubBugMine();
    assertThatCode(
            () -> {
              service.turn(7L, request("sess-a"));
              service.turn(7L, request("sess-b"));
            })
        .doesNotThrowAnyException();
  }

  @Test
  void differentUsersWithSameSessionIdDoNotCollide() {
    stubBugMine();
    assertThatCode(
            () -> {
              service.turn(7L, request("shared"));
              service.turn(8L, request("shared"));
            })
        .doesNotThrowAnyException();
  }

  @Test
  void blankSessionIdNeverBlocks() {
    stubBugMine();
    assertThatCode(
            () -> {
              service.turn(7L, request(null));
              service.turn(7L, request("   "));
              service.turn(7L, request(null));
            })
        .doesNotThrowAnyException();
  }
}
