package com.pythontopia.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.bugmine.service.BugMineService;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 纯单元测试：验证学习总管服务把「记忆矿脉」到期 KC（{@code decaying_kcs}）从 {@link BugMineService}
 * 取出并透传给 graph 客户端；取数失败时降级为空列表而不阻断对话。不启动 Spring 上下文。
 */
class OrchestratorServiceTest {

  private static OrchestratorTurnRequest sampleRequest() {
    return new OrchestratorTurnRequest("复习一下循环", null, List.of(), null, null, null, null, null);
  }

  @Test
  void forwardsDecayingKcsFromBugMineService() {
    OrchestratorGraphClient client = mock(OrchestratorGraphClient.class);
    BugMineService bugMine = mock(BugMineService.class);
    when(bugMine.decayingKcCodes(eq(7L), anyInt()))
        .thenReturn(List.of("PY43-KC-016", "PY43-KC-022"));
    OrchestratorService service = new OrchestratorService(client, bugMine);
    OrchestratorTurnRequest request = sampleRequest();

    SseEmitter emitter = service.turn(7L, request);

    assertThat(emitter).isNotNull();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(client)
        .turnStream(eq(7L), eq(request), captor.capture(), any(SseEmitter.class), any(Runnable.class));
    assertThat(captor.getValue()).containsExactly("PY43-KC-016", "PY43-KC-022");
  }

  @Test
  void degradesToEmptyDecayingKcsWhenBugMineFails() {
    OrchestratorGraphClient client = mock(OrchestratorGraphClient.class);
    BugMineService bugMine = mock(BugMineService.class);
    when(bugMine.decayingKcCodes(eq(7L), anyInt())).thenThrow(new RuntimeException("db down"));
    OrchestratorService service = new OrchestratorService(client, bugMine);
    OrchestratorTurnRequest request = sampleRequest();

    SseEmitter emitter = service.turn(7L, request);

    assertThat(emitter).isNotNull();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(client)
        .turnStream(eq(7L), eq(request), captor.capture(), any(SseEmitter.class), any(Runnable.class));
    assertThat(captor.getValue()).isEmpty();
  }
}
