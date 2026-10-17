package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.profile.dto.ProfileTurnRequest;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.profile.service.ProfileDraftService;
import com.pythontopia.learner.profile.service.ProfileService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ProfileServiceFailureTest {

  @Test
  void reportsWhenInterviewCannotBeFinished() {
    ProfileRepository repository = mock(ProfileRepository.class);
    when(repository.completeInterview(7L, "missing-session")).thenReturn(false);
    ProfileService service =
        new ProfileService(
            repository,
            mock(CoursePackRepository.class),
            mock(ResourceRunRecorder.class),
            mock(RestTemplate.class),
            new GraphServiceProperties("http://graph", "http://rag", "token"),
            new ObjectMapper(),
            mock(SseRelayExecutor.class),
            mock(ProfileDraftService.class),
            new StudentWriteThrottle());

    assertThatThrownBy(() -> service.finishInterview(7L, "missing-session"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.code()).isEqualTo("PROFILE_INTERVIEW_NOT_FOUND");
            });
  }

  @Test
  void finishingAnInterviewAlreadyCompletedByItsFinalTurnIsIdempotent() {
    ProfileRepository repository = mock(ProfileRepository.class);
    when(repository.completeInterview(7L, "session-1")).thenReturn(false);
    when(repository.isInterviewCompleted(7L, "session-1")).thenReturn(true);
    ProfileService service =
        new ProfileService(
            repository,
            mock(CoursePackRepository.class),
            mock(ResourceRunRecorder.class),
            mock(RestTemplate.class),
            new GraphServiceProperties("http://graph", "http://rag", "token"),
            new ObjectMapper(),
            mock(SseRelayExecutor.class),
            mock(ProfileDraftService.class),
            new StudentWriteThrottle());

    service.finishInterview(7L, "session-1");
  }

  @Test
  void mapsGraphTimeoutWithoutLoggingRemoteDetails() {
    String privateContent = "private-profile-response-sentinel";
    ProfileRepository repository = mock(ProfileRepository.class);
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    RestTemplate graph = mock(RestTemplate.class);
    when(repository.ownsInterview(7L, "session-1")).thenReturn(true);
    when(coursePack.findKcs(43L, null)).thenReturn(List.of());
    when(graph.postForObject(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new ResourceAccessException(privateContent));
    ProfileService service =
        new ProfileService(
            repository,
            coursePack,
            mock(ResourceRunRecorder.class),
            graph,
            new GraphServiceProperties("http://graph", "http://rag", "token"),
            new ObjectMapper(),
            mock(SseRelayExecutor.class),
            mock(ProfileDraftService.class),
            new StudentWriteThrottle());

    Logger logger = (Logger) LoggerFactory.getLogger(ProfileService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      assertThatThrownBy(
              () -> service.sendTurn(7L, "session-1", new ProfileTurnRequest("hello", List.of())))
          .isInstanceOfSatisfying(
              BusinessException.class,
              exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(exception.code()).isEqualTo("PROFILE_AGENT_UNAVAILABLE");
                assertThat(exception.getMessage()).doesNotContain(privateContent);
              });

      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(message -> assertThat(message).doesNotContain(privateContent));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  @Test
  void streamPersistenceFailureDoesNotLogExceptionBody() throws Exception {
    String sentinel = "private-profile-stream-log-sentinel";
    HttpServer sseServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    sseServer.createContext(
        "/agents/profile/turn/stream",
        exchange -> {
          byte[] body =
              "event: result\ndata: {\"next_question\":\"继续说明\",\"done\":false}\n\n"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    sseServer.start();

    ProfileRepository repository = mock(ProfileRepository.class);
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    RestTemplate graph = mock(RestTemplate.class);
    SseRelayExecutor relayExecutor = mock(SseRelayExecutor.class);
    ResourceRunRecorder runRecorder = mock(ResourceRunRecorder.class);
    when(repository.ownsInterview(7L, "session-1")).thenReturn(true);
    when(coursePack.findKcs(43L, null)).thenReturn(List.of());
    doThrow(new IllegalStateException(sentinel))
        .when(runRecorder)
        .record(anyLong(), anyString(), anyString(), any(), any(), anyList(), any(), any(), any());
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            })
        .when(relayExecutor)
        .submit(any(Runnable.class));
    ProfileService service =
        new ProfileService(
            repository,
            coursePack,
            runRecorder,
            graph,
            new GraphServiceProperties(
                "http://127.0.0.1:" + sseServer.getAddress().getPort(), "http://rag", "token"),
            new ObjectMapper(),
            relayExecutor,
            mock(ProfileDraftService.class),
            new StudentWriteThrottle());
    Logger logger = (Logger) LoggerFactory.getLogger(ProfileService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      service.sendTurnStream(7L, "session-1", new ProfileTurnRequest("学生访谈", List.of()));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
      sseServer.stop(0);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("Failed to handle profile turn SSE result"));
    assertThat(appender.list)
        .extracting(ILoggingEvent::getThrowableProxy)
        .allSatisfy(throwable -> assertThat(throwable).isNull());
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(sentinel));
  }
}
