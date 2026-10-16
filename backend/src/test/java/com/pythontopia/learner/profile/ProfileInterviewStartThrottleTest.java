package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.agent.run.ResourceRunRecorder;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.profile.dto.ProfileInterviewStartResponse;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.profile.service.ProfileDraftService;
import com.pythontopia.learner.profile.service.ProfileService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

class ProfileInterviewStartThrottleTest {

  private static final long USER_ID = 7L;

  private final ProfileRepository profileRepository = mock(ProfileRepository.class);
  private final StudentWriteThrottle writeThrottle = mock(StudentWriteThrottle.class);
  private ProfileService service;

  @BeforeEach
  void setUp() {
    service =
        new ProfileService(
            profileRepository,
            mock(CoursePackRepository.class),
            mock(ResourceRunRecorder.class),
            mock(RestTemplate.class),
            new GraphServiceProperties("http://graph", "http://rag", "token"),
            new ObjectMapper(),
            mock(SseRelayExecutor.class),
            mock(ProfileDraftService.class),
            writeThrottle);
  }

  @Test
  void rejectsCreatingAnotherInterviewAfterTheAccountBudgetIsExhausted() {
    when(profileRepository.findOpenInterview(USER_ID)).thenReturn(Optional.empty());
    when(writeThrottle.tryAcquireProfileInterview(USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.openOrResumeInterview(USER_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(error.code()).isEqualTo("STUDENT_WRITE_RATE_LIMITED");
            });

    verify(profileRepository, never()).createInterview(USER_ID);
  }

  @Test
  void resumesAnOpenInterviewWithoutSpendingTheNewInterviewBudget() {
    ProfileInterviewStartResponse open =
        new ProfileInterviewStartResponse("session-1", "OPEN", List.of(), null);
    when(profileRepository.findOpenInterview(USER_ID)).thenReturn(Optional.of(open));

    assertThat(service.openOrResumeInterview(USER_ID)).isSameAs(open);

    verify(writeThrottle, never()).tryAcquireProfileInterview(USER_ID);
  }

  @Test
  void createsANewInterviewWhenTheAccountBudgetIsAvailable() {
    ProfileInterviewStartResponse created =
        new ProfileInterviewStartResponse("session-2", "OPEN", List.of(), null);
    when(profileRepository.findOpenInterview(USER_ID)).thenReturn(Optional.empty());
    when(writeThrottle.tryAcquireProfileInterview(USER_ID)).thenReturn(true);
    when(profileRepository.createInterview(USER_ID)).thenReturn(created);

    assertThat(service.openOrResumeInterview(USER_ID)).isSameAs(created);

    verify(writeThrottle).tryAcquireProfileInterview(USER_ID);
  }
}
