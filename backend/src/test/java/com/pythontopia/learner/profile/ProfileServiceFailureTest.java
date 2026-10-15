package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.profile.dto.ProfileTurnRequest;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.profile.service.ProfileDraftService;
import com.pythontopia.learner.profile.service.ProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class ProfileServiceFailureTest {

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
            mock(ProfileDraftService.class));

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
}
