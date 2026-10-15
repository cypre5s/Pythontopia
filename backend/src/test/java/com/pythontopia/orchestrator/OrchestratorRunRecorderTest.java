package com.pythontopia.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.intent.RouteExecutionTokenSignerTest;
import com.pythontopia.loop.LoopRunService;
import com.pythontopia.loop.LoopRuntimeService;
import com.pythontopia.loop.dto.LoopRunDto;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class OrchestratorRunRecorderTest {

  private final LoopRunService loopRunService = mock(LoopRunService.class);
  private final LoopRuntimeService runtimeService = mock(LoopRuntimeService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OrchestratorRunRecorder recorder =
      new OrchestratorRunRecorder(loopRunService, runtimeService, objectMapper);

  @Test
  void recordsStateThenStepArtifactsIncrementallyAndBackfillsFinalInStablePlanOrder() {
    OffsetDateTime now = OffsetDateTime.now();
    when(loopRunService.record(eq(7L), any()))
        .thenReturn(
            new LoopRunDto(
                99L,
                7L,
                "sess-1",
                0,
                4,
                0,
                0,
                List.of("HANDOUT", "PRACTICE"),
                true,
                true,
                0,
                0,
                "route_0123456789abcdef",
                now));
    when(runtimeService.saveCheckpoint(
            eq(7L), eq(99L), anyInt(), anyString(), any(JsonNode.class), anyString()))
        .thenAnswer(
            invocation ->
                new LoopRuntimeService.CheckpointRecord(
                    200L + invocation.getArgument(2, Integer.class),
                    99L,
                    invocation.getArgument(2),
                    invocation.getArgument(3),
                    invocation.getArgument(4),
                    invocation.getArgument(5),
                    now));
    BiConsumer<String, String> onEvent = recorder.onEvent(7L, multiRequest(), "sess-1");

    onEvent.accept("STATE_DELTA", stateDelta());

    verify(loopRunService).record(eq(7L), any());
    verify(runtimeService)
        .recordPolicyDecision(
            eq(7L), eq(99L), anyString(), anyString(), eq("MEDIUM"), eq(false), any(), anyString(), anyString());
    verify(runtimeService, times(2))
        .saveCheckpoint(eq(7L), eq(99L), anyInt(), anyString(), any(), anyString());
    verify(runtimeService)
        .saveLoopState(
            eq(7L), eq(99L), eq("RESOURCE"), anyString(), eq("RUNNING"), any(), anyString(), anyString());

    onEvent.accept("CUSTOM", firstStepResult());
    onEvent.accept("RUN_FINISHED", finalResult());

    ArgumentCaptor<String> agents = ArgumentCaptor.forClass(String.class);
    verify(runtimeService, times(2))
        .recordAgentRun(eq(7L), eq(99L), agents.capture(), anyString(), anyString(), any());
    org.assertj.core.api.Assertions.assertThat(agents.getAllValues())
        .containsExactly("ResourceDesignerAgent", "PracticeAgent");
    ArgumentCaptor<String> tools = ArgumentCaptor.forClass(String.class);
    verify(runtimeService, times(2))
        .recordToolCall(
            eq(7L), eq(99L), anyLong(), tools.capture(), any(), any(), anyString(), anyInt());
    org.assertj.core.api.Assertions.assertThat(tools.getAllValues())
        .containsExactly("HANDOUT", "PRACTICE");
    verify(runtimeService)
        .saveLoopState(
            eq(7L),
            eq(99L),
            eq("RESOURCE"),
            anyString(),
            eq("COMPLETED"),
            org.mockito.ArgumentMatchers.argThat(state -> state.has("taskPlan")),
            anyString(),
            anyString());
  }

  @Test
  void persistenceFailuresAreFailSoftAndDoNotLogExceptionMessages() {
    String openFailure = "private-open-error-sentinel";
    String callbackFailure = "private-callback-error-sentinel";
    when(loopRunService.record(eq(7L), any())).thenThrow(new RuntimeException(openFailure));
    Logger logger = (Logger) LoggerFactory.getLogger(OrchestratorRunRecorder.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThatCode(() -> recorder.onEvent(7L, multiRequest(), "sess-1"))
          .doesNotThrowAnyException();

      when(loopRunService.record(eq(7L), any()))
          .thenReturn(
              new LoopRunDto(
                  99L,
                  7L,
                  "sess-1",
                  0,
                  4,
                  0,
                  0,
                  List.of("HANDOUT", "PRACTICE"),
                  true,
                  true,
                  0,
                  0,
                  "route_0123456789abcdef",
                  OffsetDateTime.now()));
      doThrow(new IllegalStateException(callbackFailure))
          .when(runtimeService)
          .saveLoopState(
              anyLong(), anyLong(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
      BiConsumer<String, String> onEvent = recorder.onEvent(7L, multiRequest(), "sess-1");
      assertThatCode(() -> onEvent.accept("STATE_DELTA", stateDelta()))
          .doesNotThrowAnyException();
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("errorType=RuntimeException"))
        .anyMatch(message -> message.contains("errorType=IllegalStateException"))
        .noneMatch(message -> message.contains(openFailure) || message.contains(callbackFailure));
    assertThat(appender.list).noneMatch(event -> event.getThrowableProxy() != null);
  }

  private static OrchestratorTurnRequest multiRequest() {
    return new OrchestratorTurnRequest(
        "先总结再出题",
        "sess-1",
        null,
        null,
        List.of("PY43-KC-012"),
        null,
        null,
        List.of("PY43-KC-012"),
        RouteExecutionTokenSignerTest.multiDecision("route_0123456789abcdef"),
        "signed-token",
        null);
  }

  private static String stateDelta() {
    return """
        {"delta":{"plan_steps":[
          {"id":"step-summary","agent":"ResourceDesignerAgent","tool_name":"HANDOUT","status":"active"},
          {"id":"step-practice","agent":"PracticeAgent","tool_name":"PRACTICE","status":"pending"}
        ]}}
        """;
  }

  private static String firstStepResult() {
    return """
        {"name":"task_step_result","value":{
          "step_id":"step-summary","agent":"ResourceDesignerAgent","tool_name":"HANDOUT",
          "status":"done","summary":"总结完成","elapsed_ms":12,"artifact":{"title":"循环总结"}
        }}
        """;
  }

  private static String finalResult() {
    return """
        {"result":{
          "task_plan":{"plan_id":"plan_0123456789abcdef","route_id":"route_0123456789abcdef",
            "topology":"SEQUENTIAL","status":"COMPLETED","steps":[
              {"id":"step-summary","agent":"ResourceDesignerAgent","tool_name":"HANDOUT","status":"done"},
              {"id":"step-practice","agent":"PracticeAgent","tool_name":"PRACTICE","status":"done"}
            ]},
          "dispatch_results":[
            {"step_id":"step-practice","agent":"PracticeAgent","ok":true,"summary":"练习完成","elapsed_ms":8,"artifact":{"count":3}},
            {"step_id":"step-summary","agent":"ResourceDesignerAgent","ok":true,"summary":"总结完成","elapsed_ms":12,"artifact":{"title":"循环总结"}}
          ]
        }}
        """;
  }
}
