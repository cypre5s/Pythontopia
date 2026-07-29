package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.common.config.GraphHttpClientProperties;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.profile.dto.ProfileTurnRequest;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.profile.service.InterviewDimensionMirror;
import com.pythontopia.learner.profile.service.ProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class ProfileServiceFailureTest {

  @Test
  void mapsGraphTimeoutToBadGatewayInsteadOfInternalError() {
    ProfileRepository repository = mock(ProfileRepository.class);
    CoursePackRepository coursePack = mock(CoursePackRepository.class);
    RestTemplate graph = mock(RestTemplate.class);
    when(repository.ownsInterview(7L, "session-1")).thenReturn(true);
    when(coursePack.findKcs(43L, null)).thenReturn(List.of());
    when(graph.postForObject(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new ResourceAccessException("timed out"));
    ProfileService service =
        new ProfileService(
            repository,
            coursePack,
            mock(ResourceRunRecorder.class),
            graph,
            new GraphServiceProperties("http://graph", "http://rag", "token"),
            new GraphHttpClientProperties(null, null),
            new ObjectMapper(),
            mock(SseRelayExecutor.class),
            mock(InterviewDimensionMirror.class));

    assertThatThrownBy(
            () -> service.sendTurn(7L, "session-1", new ProfileTurnRequest("hello", List.of())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
              assertThat(exception.code()).isEqualTo("PROFILE_AGENT_UNAVAILABLE");
            });
  }
}
