package com.pythontopia.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.auth.security.Role;
import com.pythontopia.browse.controller.BrowseController;
import com.pythontopia.classroom.remediation.RemediationController;
import com.pythontopia.learner.evaluation.controller.LearnerEvaluationController;
import com.pythontopia.learner.path.controller.LearningPathController;
import com.pythontopia.mainline.test.ChapterQuizController;
import com.pythontopia.mainline.test.ChapterTestController;
import com.pythontopia.media.MediaAsrController;
import com.pythontopia.media.MediaAssetController;
import com.pythontopia.media.MediaJobController;
import com.pythontopia.media.MediaTtsController;
import com.pythontopia.npc.NpcGreetingController;
import com.pythontopia.video.BilibiliVideoController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AiBudgetRouteCoverageTest {

  @Test
  void realModelRoutesReserveDailyBudget() throws NoSuchMethodException {
    assertBudgeted(BilibiliVideoController.class.getMethod("search", String.class, int.class));
    assertBudgeted(
        RemediationController.class.getMethod(
            "trigger", com.pythontopia.auth.security.CurrentUserPrincipal.class, Long.class));
  }

  @Test
  void chapterTestGenerationIsStudentOnly() throws NoSuchMethodException {
    assertStudentBudgeted(ChapterTestController.class.getMethod(
        "prepare", int.class, com.pythontopia.auth.security.CurrentUserPrincipal.class));
    assertStudentBudgeted(ChapterQuizController.class.getMethod(
        "prepare", int.class, com.pythontopia.auth.security.CurrentUserPrincipal.class));
  }

  @Test
  void aiBudgetCoverageMatchesRuntimeCalls() throws NoSuchMethodException {
    assertThat(new com.pythontopia.media.ImageProperties(null, null, null, null, 50).isEnabled())
        .isFalse();
    assertThat(new com.pythontopia.media.TtsProperties(null, null, null, 100).isEnabled())
        .isFalse();
    assertThat(new com.pythontopia.media.AsrProperties(null, null, null, null, 100).isEnabled())
        .isFalse();
    assertThat(
            new com.pythontopia.media.ImageUnderstandingProperties(
                    null, null, null, null, null, 100, 50, 1024)
                .isEnabled())
        .isFalse();
    assertStudentBudgeted(
        BrowseController.class.getMethod(
            "create",
            com.pythontopia.auth.security.CurrentUserPrincipal.class,
            com.pythontopia.browse.dto.BrowseRunCreateRequest.class));
    assertBudgeted(
        LearningPathController.class.getMethod(
            "plan", com.pythontopia.auth.security.CurrentUserPrincipal.class));
    assertBudgeted(
        LearnerEvaluationController.class.getMethod(
            "compute",
            com.pythontopia.auth.security.CurrentUserPrincipal.class,
            com.pythontopia.learner.evaluation.dto.EvaluationRequest.class));
    assertNotBudgeted(
        MediaAssetController.class.getMethod(
            "generateImage",
            com.pythontopia.auth.security.CurrentUserPrincipal.class,
            com.pythontopia.media.dto.ImageGenerationRequest.class));
    assertNotBudgeted(
        MediaAssetController.class.getMethod(
            "composeStoryboardVideo",
            com.pythontopia.auth.security.CurrentUserPrincipal.class,
            com.pythontopia.media.dto.StoryboardVideoRequest.class));
    assertNotBudgeted(
        MediaJobController.class.getMethod(
            "submitJob",
            com.pythontopia.auth.security.CurrentUserPrincipal.class,
            com.pythontopia.media.dto.StoryboardVideoRequest.class));
    assertNotBudgeted(
        MediaTtsController.class.getMethod(
            "synthesize",
            com.pythontopia.auth.security.CurrentUserPrincipal.class,
            com.pythontopia.media.dto.TtsRequest.class));
    assertNotBudgeted(
        MediaAsrController.class.getMethod(
            "transcribe",
            com.pythontopia.auth.security.CurrentUserPrincipal.class,
            org.springframework.web.multipart.MultipartFile.class));
    assertNotBudgeted(
        NpcGreetingController.class.getMethod(
            "greeting", com.pythontopia.auth.security.CurrentUserPrincipal.class, String.class));
  }

  private static void assertBudgeted(Method method) {
    assertThat(method.isAnnotationPresent(AiBudgeted.class)).isTrue();
  }

  private static void assertNotBudgeted(Method method) {
    assertThat(method.isAnnotationPresent(AiBudgeted.class)).isFalse();
  }

  private static void assertStudentBudgeted(Method method) {
    assertThat(method.getAnnotation(AiBudgeted.class).roles()).containsExactly(Role.STUDENT);
  }
}
