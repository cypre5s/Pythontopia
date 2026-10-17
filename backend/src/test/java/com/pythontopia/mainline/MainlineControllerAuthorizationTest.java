package com.pythontopia.mainline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.mainline.dto.ChapterTestSubmitRequest;
import com.pythontopia.mainline.dto.EndlessSummaryRequest;
import com.pythontopia.mainline.dto.ExampleDoneRequest;
import com.pythontopia.mainline.dto.PageReadRequest;
import com.pythontopia.mainline.dto.PagesReadRequest;
import com.pythontopia.mainline.practice.MainlinePracticeController;
import com.pythontopia.mainline.practice.MainlinePracticeService;
import com.pythontopia.mainline.practice.dto.PracticeAnswerRequest;
import com.pythontopia.mainline.test.ChapterQuizController;
import com.pythontopia.mainline.test.ChapterTestController;
import com.pythontopia.mainline.test.ChapterTestService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MainlineControllerAuthorizationTest.TestConfiguration.class)
class MainlineControllerAuthorizationTest {

  @Autowired private MainlineController mainlineController;
  @Autowired private MainlinePracticeController practiceController;
  @Autowired private MainlineService mainlineService;
  @Autowired private MainlineRecoveryService mainlineRecoveryService;
  @Autowired private MainlinePracticeService practiceService;
  @Autowired private ChapterTestController chapterTestController;
  @Autowired private ChapterQuizController chapterQuizController;
  @Autowired private ChapterTestService chapterTestService;
  @Autowired private StudentWriteThrottle studentWriteThrottle;

  @BeforeEach
  void clearMockInvocations() {
    clearInvocations(
        mainlineService, mainlineRecoveryService, practiceService, studentWriteThrottle);
  }

  @Test
  @WithMockUser(roles = "TEACHER")
  void teacherCannotUseStudentMainlineApi() {
    assertThatThrownBy(() -> mainlineController.overview(principal(Role.TEACHER)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(mainlineService, studentWriteThrottle);
  }

  @Test
  @WithMockUser(roles = "TEACHER")
  void teacherCannotSubmitStudentMainlinePractice() {
    assertThatThrownBy(
            () ->
                practiceController.answer(
                    1L,
                    1L,
                    new PracticeAnswerRequest(java.util.List.of("A")),
                    principal(Role.TEACHER)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(practiceService, studentWriteThrottle);
  }

  @Test
  @WithMockUser(roles = "STUDENT")
  void mainlineProgressAndPracticeWritesUseAccountBudget() {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);
    CurrentUserPrincipal student = principal(Role.STUDENT);

    assertWriteBudgetRejected(
        () -> mainlineController.pageRead(1, new PageReadRequest(101L), student));
    assertWriteBudgetRejected(
        () -> mainlineController.pagesRead(1, new PagesReadRequest(List.of(101L)), student));
    assertWriteBudgetRejected(
        () -> mainlineController.exampleDone(1, new ExampleDoneRequest(11L, null), student));
    assertWriteBudgetRejected(
        () ->
            mainlineController.endlessSummary(
                new EndlessSummaryRequest(1, "123e4567-e89b-12d3-a456-426614174000"), student));
    assertWriteBudgetRejected(() -> mainlineController.recoveryErrorbook(1, student));
    assertWriteBudgetRejected(
        () -> practiceController.answer(1, 1, new PracticeAnswerRequest(List.of("A")), student));

    verify(studentWriteThrottle, times(6)).tryAcquire(7L, 1);
    verifyNoInteractions(mainlineService, mainlineRecoveryService, practiceService);
  }

  private void assertWriteBudgetRejected(Runnable action) {
    Throwable failure = catchThrowable(action::run);
    assertThat(failure).isInstanceOf(BusinessException.class);
    BusinessException business = (BusinessException) failure;
    assertThat(business.code()).isEqualTo("STUDENT_WRITE_RATE_LIMITED");
    assertThat(business.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  @WithMockUser(roles = "TEACHER")
  void teacherCannotStartStudentChapterTest() {
    assertThatThrownBy(() -> chapterTestController.start(1, principal(Role.TEACHER)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(chapterTestService, studentWriteThrottle);
  }

  @Test
  @WithMockUser(roles = "TEACHER")
  void teacherCannotStartStudentChapterQuiz() {
    assertThatThrownBy(() -> chapterQuizController.start(1, principal(Role.TEACHER)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(chapterTestService, studentWriteThrottle);
  }

  @Test
  @WithMockUser(roles = "STUDENT")
  void chapterAssessmentStartsAndSubmissionsUseAccountBudget() {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);
    CurrentUserPrincipal student = principal(Role.STUDENT);
    ChapterTestSubmitRequest request = new ChapterTestSubmitRequest(Map.of(), null);

    assertWriteBudgetRejected(() -> chapterTestController.start(1, student));
    assertWriteBudgetRejected(() -> chapterTestController.submit(1, 1, request, student));
    assertWriteBudgetRejected(() -> chapterQuizController.start(1, student));
    assertWriteBudgetRejected(() -> chapterQuizController.submit(1, 1, request, student));

    verify(studentWriteThrottle, times(4)).tryAcquire(7L, 1);
    verifyNoInteractions(chapterTestService);
  }

  private CurrentUserPrincipal principal(Role role) {
    return new CurrentUserPrincipal(7L, "teacher", role);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  @Import({
    MainlineController.class,
    MainlinePracticeController.class,
    ChapterTestController.class,
    ChapterQuizController.class
  })
  static class TestConfiguration {

    @Bean
    MainlineService mainlineService() {
      return mock(MainlineService.class);
    }

    @Bean
    MainlineRecoveryService mainlineRecoveryService() {
      return mock(MainlineRecoveryService.class);
    }

    @Bean
    MainlinePracticeService mainlinePracticeService() {
      return mock(MainlinePracticeService.class);
    }

    @Bean
    StudentWriteThrottle studentWriteThrottle() {
      return mock(StudentWriteThrottle.class);
    }

    @Bean
    ChapterTestService chapterTestService() {
      return mock(ChapterTestService.class);
    }
  }
}
