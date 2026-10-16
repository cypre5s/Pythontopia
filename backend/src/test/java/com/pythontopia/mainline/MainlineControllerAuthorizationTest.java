package com.pythontopia.mainline;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.mainline.practice.MainlinePracticeController;
import com.pythontopia.mainline.practice.MainlinePracticeService;
import com.pythontopia.mainline.practice.dto.PracticeAnswerRequest;
import com.pythontopia.mainline.test.ChapterQuizController;
import com.pythontopia.mainline.test.ChapterTestController;
import com.pythontopia.mainline.test.ChapterTestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
  @Autowired private MainlinePracticeService practiceService;
  @Autowired private ChapterTestController chapterTestController;
  @Autowired private ChapterQuizController chapterQuizController;
  @Autowired private ChapterTestService chapterTestService;

  @Test
  @WithMockUser(roles = "TEACHER")
  void teacherCannotUseStudentMainlineApi() {
    assertThatThrownBy(() -> mainlineController.overview(principal(Role.TEACHER)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(mainlineService);
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
    verifyNoInteractions(practiceService);
  }

  @Test
  @WithMockUser(roles = "TEACHER")
  void teacherCannotStartStudentChapterTest() {
    assertThatThrownBy(() -> chapterTestController.start(1, principal(Role.TEACHER)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(chapterTestService);
  }

  @Test
  @WithMockUser(roles = "TEACHER")
  void teacherCannotStartStudentChapterQuiz() {
    assertThatThrownBy(() -> chapterQuizController.start(1, principal(Role.TEACHER)))
        .isInstanceOf(AccessDeniedException.class);
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
    ChapterTestService chapterTestService() {
      return mock(ChapterTestService.class);
    }
  }
}
