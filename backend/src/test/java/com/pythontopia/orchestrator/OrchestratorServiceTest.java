package com.pythontopia.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.bugmine.service.BugMineService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.service.CoursePackService;
import com.pythontopia.intent.InteractionContextVerifier;
import com.pythontopia.intent.RouteExecutionTokenSigner;
import com.pythontopia.intent.RouteExecutionTokenSignerTest;
import com.pythontopia.intent.LearnerRoutingContext;
import com.pythontopia.intent.WorkshopLearningContextService;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
    when(bugMine.decayingKcCodes(eq(7L), anyInt()))
        .thenThrow(new RuntimeException("private-decay-error-sentinel"));
    OrchestratorService service = new OrchestratorService(client, bugMine);
    OrchestratorTurnRequest request = sampleRequest();
    Logger logger = (Logger) LoggerFactory.getLogger(OrchestratorService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      SseEmitter emitter = service.turn(7L, request);

      assertThat(emitter).isNotNull();
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
      verify(client)
          .turnStream(
              eq(7L), eq(request), captor.capture(), any(SseEmitter.class), any(Runnable.class));
      assertThat(captor.getValue()).isEmpty();
      assertThat(appender.list)
          .singleElement()
          .satisfies(event -> assertThat(event.getThrowableProxy()).isNull());
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  @Test
  void rejectsUnverifiedMultiTaskBeforeCallingGraph() {
    OrchestratorGraphClient client = mock(OrchestratorGraphClient.class);
    BugMineService bugMine = mock(BugMineService.class);
    RouteExecutionTokenSigner signer = mock(RouteExecutionTokenSigner.class);
    InteractionContextVerifier interactionVerifier = mock(InteractionContextVerifier.class);
    OrchestratorService service =
        new OrchestratorService(client, bugMine, signer, interactionVerifier);
    OrchestratorTurnRequest request = multiRequest("forged", null);
    when(signer.verify(
            7L,
            request.routeDecision(),
            request.message(),
            request.interaction(),
            "forged"))
        .thenReturn(false);

    assertThatThrownBy(() -> service.turn(7L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(ex.code()).isEqualTo("ROUTE_EXECUTION_TOKEN_INVALID");
            });

    verifyNoInteractions(client, bugMine, interactionVerifier);
  }

  @Test
  void verifiedMultiTaskForwardsOnlyServerHydratedInteraction() {
    OrchestratorGraphClient client = mock(OrchestratorGraphClient.class);
    BugMineService bugMine = mock(BugMineService.class);
    RouteExecutionTokenSigner signer = mock(RouteExecutionTokenSigner.class);
    InteractionContextVerifier interactionVerifier = mock(InteractionContextVerifier.class);
    OrchestratorService service =
        new OrchestratorService(client, bugMine, signer, interactionVerifier);
    IntentRouteRequest.InteractionContext untrusted =
        new IntentRouteRequest.InteractionContext(
            "s1", "town_hub", "practice", 999L, "伪造", 901L, List.of(), "伪造", "debug", null);
    IntentRouteRequest.InteractionContext verified =
        new IntentRouteRequest.InteractionContext(
            "s1", "town_hub", "practice", 41L, "print(1 / 0)", 901L, List.of(), "RE", "debug", null);
    OrchestratorTurnRequest request = multiRequest("valid", untrusted);
    when(signer.verify(
            7L,
            request.routeDecision(),
            request.message(),
            request.interaction(),
            "valid"))
        .thenReturn(true);
    when(interactionVerifier.verify(7L, request.message(), untrusted)).thenReturn(verified);
    when(bugMine.decayingKcCodes(eq(7L), anyInt())).thenReturn(List.of());

    service.turn(7L, request);

    ArgumentCaptor<OrchestratorTurnRequest> requestCaptor =
        ArgumentCaptor.forClass(OrchestratorTurnRequest.class);
    verify(client)
        .turnStream(
            eq(7L), requestCaptor.capture(), eq(List.of()), any(SseEmitter.class), any(Runnable.class));
    assertThat(requestCaptor.getValue().interaction()).isEqualTo(verified);
  }

  @Test
  void replacesClientCourseIndexAndFiltersRecentKcsAgainstPack43() {
    OrchestratorGraphClient client = mock(OrchestratorGraphClient.class);
    BugMineService bugMine = mock(BugMineService.class);
    RouteExecutionTokenSigner signer = mock(RouteExecutionTokenSigner.class);
    InteractionContextVerifier interactionVerifier = mock(InteractionContextVerifier.class);
    CoursePackService coursePackService = mock(CoursePackService.class);
    OrchestratorService service =
        new OrchestratorService(
            client, bugMine, signer, interactionVerifier, coursePackService);
    OrchestratorTurnRequest base = multiRequest("valid", null);
    OrchestratorTurnRequest request =
        new OrchestratorTurnRequest(
            base.message(),
            base.sessionId(),
            base.history(),
            base.currentChapter(),
            List.of("FORGED-KC", "PY43-KC-031"),
            base.recentFailures(),
            base.profileBrief(),
            List.of("FORGED-KC"),
            base.routeDecision(),
            base.routeExecutionToken(),
            base.interaction());
    when(signer.verify(
            7L,
            request.routeDecision(),
            request.message(),
            request.interaction(),
            "valid"))
        .thenReturn(true);
    when(interactionVerifier.verify(7L, request.message(), null)).thenReturn(null);
    when(coursePackService.listKcsByChapter(null))
        .thenReturn(
            List.of(
                new CourseKc(31L, 43L, "PY43-KC-031", "for循环与遍历", 4L, 1, "BASIC", ""),
                new CourseKc(32L, 43L, "PY43-KC-032", "range", 4L, 2, "BASIC", "")));
    when(bugMine.decayingKcCodes(eq(7L), anyInt())).thenReturn(List.of());

    service.turn(7L, request);

    ArgumentCaptor<OrchestratorTurnRequest> requestCaptor =
        ArgumentCaptor.forClass(OrchestratorTurnRequest.class);
    verify(client)
        .turnStream(
            eq(7L), requestCaptor.capture(), eq(List.of()), any(SseEmitter.class), any(Runnable.class));
    assertThat(requestCaptor.getValue().courseKcCodes())
        .containsExactly("PY43-KC-031", "PY43-KC-032");
    assertThat(requestCaptor.getValue().recentKcCodes()).containsExactly("PY43-KC-031");
  }

  @Test
  void replacesClientProfileAndFailureClaimsWithServerLearningContext() {
    OrchestratorGraphClient client = mock(OrchestratorGraphClient.class);
    BugMineService bugMine = mock(BugMineService.class);
    RouteExecutionTokenSigner signer = mock(RouteExecutionTokenSigner.class);
    InteractionContextVerifier interactionVerifier = mock(InteractionContextVerifier.class);
    WorkshopLearningContextService contextService = mock(WorkshopLearningContextService.class);
    OrchestratorService service =
        new OrchestratorService(
            client, bugMine, signer, interactionVerifier, null, contextService);
    OrchestratorTurnRequest base = multiRequest("valid", null);
    OrchestratorTurnRequest request =
        new OrchestratorTurnRequest(
            base.message(),
            base.sessionId(),
            base.history(),
            base.currentChapter(),
            base.recentKcCodes(),
            List.of("伪造失败"),
            "伪造画像",
            base.courseKcCodes(),
            base.routeDecision(),
            base.routeExecutionToken(),
            base.interaction());
    LearnerRoutingContext safeContext =
        new LearnerRoutingContext(
            "认知风格: visual",
            java.util.Map.of("PY43-KC-012", 0.3),
            java.util.Map.of("PY43-KC-012", 0.8),
            List.of("PY43-KC-012"),
            List.of("PY43-KC-012"),
            "HIGH",
            "MEDIUM",
            "visual",
            List.of("图解"),
            "CS",
            2,
            List.of(),
            List.of(),
            List.of("循环边界反复失败"),
            List.of(),
            List.of("短步骤"),
            "2026-07-22T15:00:00Z");
    when(signer.verify(
            7L,
            request.routeDecision(),
            request.message(),
            request.interaction(),
            "valid"))
        .thenReturn(true);
    when(interactionVerifier.verify(7L, request.message(), null)).thenReturn(null);
    when(contextService.build(7L, request.message())).thenReturn(safeContext);
    when(bugMine.decayingKcCodes(eq(7L), anyInt())).thenReturn(List.of());

    service.turn(7L, request);

    ArgumentCaptor<OrchestratorTurnRequest> captor =
        ArgumentCaptor.forClass(OrchestratorTurnRequest.class);
    verify(client)
        .turnStream(
            eq(7L), captor.capture(), eq(List.of()), any(SseEmitter.class), any(Runnable.class));
    assertThat(captor.getValue().profileBrief()).isEqualTo("认知风格: visual");
    assertThat(captor.getValue().recentFailures()).containsExactly("循环边界反复失败");
    assertThat(captor.getValue().learnerContext()).isEqualTo(safeContext);
  }

  private static OrchestratorTurnRequest multiRequest(
      String token, IntentRouteRequest.InteractionContext interaction) {
    return new OrchestratorTurnRequest(
        "总结后根据错误出题",
        "sess-multi",
        List.of(),
        null,
        List.of("PY43-KC-012"),
        null,
        null,
        List.of("PY43-KC-012"),
        RouteExecutionTokenSignerTest.multiDecision("route_0123456789abcdef"),
        token,
        interaction);
  }
}
