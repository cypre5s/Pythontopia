package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.coursepack.service.CourseVideoTranscriptService;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.tutor.dto.TutorAskRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

class TutorServiceFailureTest {

  @Test
  void sessionPersistenceFailureDoesNotLogTutorAnswerOrException() {
    String privateAnswer = "private-tutor-answer-sentinel";
    TutorGraphClient graphClient = mock(TutorGraphClient.class);
    TutorSessionRepository sessionRepository = mock(TutorSessionRepository.class);
    TutorService service =
        new TutorService(
            graphClient,
            sessionRepository,
            new ObjectMapper(),
            mock(PassiveProfileService.class),
            mock(ApplicationEventPublisher.class),
            mock(ClassroomDashboardService.class),
            mock(MemoryRetrievalService.class),
            mock(CourseVideoTranscriptService.class),
            mock(TutorRequestBudgetService.class),
            mock(TutorDailyRequestLimitService.class));
    doThrow(new IllegalStateException(privateAnswer))
        .when(sessionRepository)
        .insert(eq(7L), eq("what is a loop"), eq(privateAnswer), anyList(), eq("PASSED"), isNull());
    AtomicReference<Consumer<String>> resultCallback = new AtomicReference<>();
    doAnswer(
            invocation -> {
              resultCallback.set(invocation.getArgument(7));
              return null;
            })
        .when(graphClient)
        .askStream(any(), any(), anyList(), any(), any(), any(), any(), any(), any());

    Logger logger = (Logger) LoggerFactory.getLogger(TutorService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      service.ask(
          new CurrentUserPrincipal(7L, "student", Role.STUDENT),
          new TutorAskRequest("what is a loop", List.of(), null, null, null, null, null, null, null));
      resultCallback
          .get()
          .accept(
              "{\"result\":{\"answer_md\":\""
                  + privateAnswer
                  + "\",\"grounding_status\":\"PASSED\"}}");

      verify(sessionRepository)
          .insert(eq(7L), eq("what is a loop"), eq(privateAnswer), anyList(), eq("PASSED"), isNull());
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(message -> assertThat(message).doesNotContain(privateAnswer));
      assertThat(appender.list)
          .extracting(ILoggingEvent::getThrowableProxy)
          .allSatisfy(throwable -> assertThat(throwable).isNull());
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  @Test
  void contextLookupFailuresDoNotLogExceptionMessages() {
    String privateContext = "private-context-sentinel";
    TutorGraphClient graphClient = mock(TutorGraphClient.class);
    TutorSessionRepository sessionRepository = mock(TutorSessionRepository.class);
    PassiveProfileService profileService = mock(PassiveProfileService.class);
    ClassroomDashboardService classroomService = mock(ClassroomDashboardService.class);
    MemoryRetrievalService memoryService = mock(MemoryRetrievalService.class);
    doThrow(new IllegalStateException(privateContext)).when(profileService).profileBrief(7L);
    doThrow(new IllegalStateException(privateContext))
        .when(classroomService)
        .copilotBrief(any(CurrentUserPrincipal.class), eq(43L));
    doThrow(new IllegalStateException(privateContext))
        .when(memoryService)
        .retrieve(eq(7L), eq("PY43-KC-001"), anyInt());
    doThrow(new IllegalStateException(privateContext))
        .when(sessionRepository)
        .findByConversation(eq(7L), eq("conversation-secret-id"), anyInt());
    TutorService service =
        new TutorService(
            graphClient,
            sessionRepository,
            new ObjectMapper(),
            profileService,
            mock(ApplicationEventPublisher.class),
            classroomService,
            memoryService,
            mock(CourseVideoTranscriptService.class),
            mock(TutorRequestBudgetService.class),
            mock(TutorDailyRequestLimitService.class));

    Logger logger = (Logger) LoggerFactory.getLogger(TutorService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      service.ask(
          new CurrentUserPrincipal(7L, "teacher", Role.TEACHER),
          new TutorAskRequest(
              "question", List.of("PY43-KC-001"), null, null, null, "conversation-secret-id",
              43L, null, null));

      assertThat(appender.list).hasSize(4);
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(
              message -> {
                assertThat(message).doesNotContain(privateContext);
                assertThat(message).contains("IllegalStateException");
              });
      assertThat(appender.list)
          .extracting(ILoggingEvent::getThrowableProxy)
          .allSatisfy(throwable -> assertThat(throwable).isNull());
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
