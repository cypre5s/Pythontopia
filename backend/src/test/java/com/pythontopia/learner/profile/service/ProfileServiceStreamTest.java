package com.pythontopia.learner.profile.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.agent.run.RunStatus;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.profile.dto.CheckResultDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.ProfileTurnRequest;
import com.pythontopia.learner.profile.dto.ProfileTurnResponse;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ProfileServiceStreamTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void canonicalRunFinishedProjectsTheWrappedProfileDraft() throws Exception {
    LearnerProfileDto draft =
        new LearnerProfileDto(
            null, 7L, 43L, "计算机", null, null, null, null, null, null, null, "画像摘要");
    ProfileTurnResponse response =
        new ProfileTurnResponse(
            null,
            draft,
            true,
            List.of("professional_background"),
            List.of(),
            new CheckResultDto(true, List.of()),
            new CheckResultDto(true, List.of()));
    String data = objectMapper.writeValueAsString(java.util.Map.of("result", response));
    int port = startStreamServer("event: RUN_FINISHED\ndata: " + data + "\n\n");
    ProfileRepository repository = mock(ProfileRepository.class);
    InterviewDimensionMirror dimensionMirror = mock(InterviewDimensionMirror.class);
    ResourceRunRecorder runRecorder = mock(ResourceRunRecorder.class);
    when(repository.ownsInterview(7L, "session-1")).thenReturn(true);
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    when(coursePack.findKcs(43L, null)).thenReturn(List.of());
    when(repository.completeInterview(7L, "session-1")).thenReturn(true);
    ProfileService service =
        newService(
            port,
            repository,
            coursePack,
            runRecorder,
            mock(RestTemplate.class),
            dimensionMirror);
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    service.startTurnStream(
        7L,
        "session-1",
        new ProfileTurnRequest("我是计算机专业", List.of()),
        emitter);
    awaitTerminated(emitter);

    LearnerProfileDto selfReport =
        new LearnerProfileDto(null, 7L, 43L, "计算机", null, null, null, null, null, null, null, null);
    verify(repository).upsertProfile(7L, selfReport);
    verify(dimensionMirror).mirror(7L, selfReport);
    verify(repository).addRevision(7L, "INTERVIEW", selfReport);
    verify(repository).completeInterview(7L, "session-1");
    verify(runRecorder)
        .record(
            eq(7L),
            eq("ProfileAgent"),
            eq("PROFILE_INTERVIEW"),
            any(),
            eq(response),
            eq(List.of()),
            eq(response.safetyResult()),
            eq(response.groundingResult()),
            eq(RunStatus.SUCCESS));
    assertThat(eventsNamed(emitter, "RUN_FINISHED")).hasSize(1);
    assertThat(dataOf(eventsNamed(emitter, "RUN_FINISHED").get(0))).isEqualTo(data);
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void unavailableStreamUsesSuccessfulSyncFallbackAsTheOnlyTerminalResult() throws Exception {
    int port = startHttpErrorServer(503);
    ProfileRepository repository = mock(ProfileRepository.class);
    when(repository.ownsInterview(7L, "session-1")).thenReturn(true);
    when(repository.completeInterview(7L, "session-1")).thenReturn(true);
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    when(coursePack.findKcs(43L, null)).thenReturn(List.of());
    RestTemplate restTemplate = mock(RestTemplate.class);
    LearnerProfileDto draft =
        new LearnerProfileDto(
            null, 7L, 43L, "数学", null, null, null, null, null, null, null, "fallback");
    ProfileTurnResponse fallback = successfulResponse(draft);
    when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(ProfileTurnResponse.class)))
        .thenReturn(fallback);
    InterviewDimensionMirror dimensionMirror = mock(InterviewDimensionMirror.class);
    ResourceRunRecorder runRecorder = mock(ResourceRunRecorder.class);
    ProfileService service =
        newService(
            port,
            repository,
            coursePack,
            runRecorder,
            restTemplate,
            dimensionMirror);
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    service.startTurnStream(
        7L, "session-1", new ProfileTurnRequest("fallback", List.of()), emitter);
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "result")).hasSize(1);
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    LearnerProfileDto selfReport =
        new LearnerProfileDto(null, 7L, 43L, "数学", null, null, null, null, null, null, null, null);
    verify(repository, times(1)).upsertProfile(7L, selfReport);
    verify(dimensionMirror, times(1)).mirror(7L, selfReport);
    verify(repository, times(1)).addRevision(7L, "INTERVIEW", selfReport);
    verify(repository, times(1)).completeInterview(7L, "session-1");
    verify(runRecorder, times(1))
        .record(
            eq(7L),
            eq("ProfileAgent"),
            eq("PROFILE_INTERVIEW"),
            any(),
            eq(fallback),
            eq(List.of()),
            eq(fallback.safetyResult()),
            eq(fallback.groundingResult()),
            eq(RunStatus.SUCCESS));
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void failedSyncFallbackEmitsOneSanitizedErrorWithoutBareEmitterFailure() throws Exception {
    int port = startHttpErrorServer(503);
    ProfileRepository repository = mock(ProfileRepository.class);
    when(repository.ownsInterview(7L, "session-1")).thenReturn(true);
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    when(coursePack.findKcs(43L, null)).thenReturn(List.of());
    RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(ProfileTurnResponse.class)))
        .thenThrow(new ResourceAccessException("internal graph URL failed"));
    ProfileService service =
        newService(
            port,
            repository,
            coursePack,
            mock(ResourceRunRecorder.class),
            restTemplate,
            mock(InterviewDimensionMirror.class));
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    service.startTurnStream(
        7L, "session-1", new ProfileTurnRequest("fail", List.of()), emitter);
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "result")).isEmpty();
    List<String> errors = eventsNamed(emitter, "error");
    assertThat(errors).hasSize(1);
    assertThat(objectMapper.readTree(dataOf(errors.get(0))).path("code").asText())
        .isEqualTo("AI_SERVICE_UNAVAILABLE");
    assertThat(errors.get(0)).doesNotContain("internal graph URL").doesNotContain("127.0.0.1");
    verify(repository, never()).upsertProfile(any(), any());
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void upstreamRunErrorTerminatesWithoutInvokingSyncFallback() throws Exception {
    String upstreamError = "{\"code\":\"PROFILE_TURN_FAILED\",\"message\":\"invalid\"}";
    int port = startStreamServer("event: RUN_ERROR\ndata: " + upstreamError + "\n\n");
    ProfileRepository repository = mock(ProfileRepository.class);
    when(repository.ownsInterview(7L, "session-1")).thenReturn(true);
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    when(coursePack.findKcs(43L, null)).thenReturn(List.of());
    RestTemplate restTemplate = mock(RestTemplate.class);
    ProfileService service =
        newService(
            port,
            repository,
            coursePack,
            mock(ResourceRunRecorder.class),
            restTemplate,
            mock(InterviewDimensionMirror.class));
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    service.startTurnStream(
        7L, "session-1", new ProfileTurnRequest("bad", List.of()), emitter);
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "RUN_ERROR")).hasSize(1);
    assertThat(dataOf(eventsNamed(emitter, "RUN_ERROR").get(0))).isEqualTo(upstreamError);
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    verify(restTemplate, never()).postForObject(anyString(), any(), any());
    assertClosedExactlyOnce(emitter);
  }

  private ProfileService newService(
      int port,
      ProfileRepository repository,
      CoursePackRepository coursePack,
      ResourceRunRecorder runRecorder,
      RestTemplate restTemplate,
      InterviewDimensionMirror dimensionMirror) {
    return new ProfileService(
        repository,
        coursePack,
        runRecorder,
        restTemplate,
        new GraphServiceProperties(
            "http://127.0.0.1:" + port, "http://rag.test", "internal-token"),
        objectMapper,
        new SseRelayExecutor(),
        new ProfileDraftService(repository, dimensionMirror),
        new StudentWriteThrottle(),
        1_000,
        2_000);
  }

  private static ProfileTurnResponse successfulResponse(LearnerProfileDto draft) {
    return new ProfileTurnResponse(
        null,
        draft,
        true,
        List.of("professional_background"),
        List.of(),
        new CheckResultDto(true, List.of()),
        new CheckResultDto(true, List.of()));
  }

  private int startStreamServer(String sseBody) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/agents/profile/turn/stream", exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(sseBody.getBytes(UTF_8));
      }
    });
    server.start();
    return server.getAddress().getPort();
  }

  private int startHttpErrorServer(int status) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/agents/profile/turn/stream", exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
    });
    server.start();
    return server.getAddress().getPort();
  }

  private static List<String> eventsNamed(RecordingSseEmitter emitter, String name) {
    return emitter.events().stream().filter(event -> event.startsWith("event:" + name + "\n")).toList();
  }

  private static String dataOf(String event) {
    int index = event.indexOf("data:");
    assertThat(index).isGreaterThanOrEqualTo(0);
    return event.substring(index + "data:".length()).trim();
  }

  private static void awaitTerminated(RecordingSseEmitter emitter) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5_000L;
    while (System.currentTimeMillis() < deadline) {
      if (emitter.completeCalls() + emitter.completeWithErrorCalls() > 0) {
        return;
      }
      Thread.sleep(20L);
    }
    assertThat(emitter.completeCalls() + emitter.completeWithErrorCalls()).isPositive();
  }

  private static void assertClosedExactlyOnce(RecordingSseEmitter emitter) {
    assertThat(emitter.completeCalls()).isEqualTo(1);
    assertThat(emitter.completeWithErrorCalls()).isZero();
  }

  private static final class RecordingSseEmitter extends SseEmitter {

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final AtomicInteger completeCalls = new AtomicInteger();
    private final AtomicInteger completeWithErrorCalls = new AtomicInteger();

    private RecordingSseEmitter() {
      super(120_000L);
    }

    @Override
    public void send(SseEventBuilder builder) throws java.io.IOException {
      StringBuilder event = new StringBuilder();
      for (ResponseBodyEmitter.DataWithMediaType part : builder.build()) {
        event.append(part.getData());
      }
      events.add(event.toString());
    }

    @Override
    public void complete() {
      completeCalls.incrementAndGet();
    }

    @Override
    public void completeWithError(Throwable ex) {
      completeWithErrorCalls.incrementAndGet();
    }

    private List<String> events() {
      return events;
    }

    private int completeCalls() {
      return completeCalls.get();
    }

    private int completeWithErrorCalls() {
      return completeWithErrorCalls.get();
    }
  }
}
