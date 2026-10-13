package com.pythontopia.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.browse.controller.BrowseController;
import com.pythontopia.classroom.remediation.RemediationController;
import com.pythontopia.learner.evaluation.controller.LearnerEvaluationController;
import com.pythontopia.learner.path.controller.LearningPathController;
import com.pythontopia.media.MediaAssetController;
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
  void deterministicOrDisabledRoutesDoNotConsumeAiBudget() throws NoSuchMethodException {
    assertNotBudgeted(
        BrowseController.class.getMethod(
            "create",
            com.pythontopia.auth.security.CurrentUserPrincipal.class,
            com.pythontopia.browse.dto.BrowseRunCreateRequest.class));
    assertNotBudgeted(
        LearningPathController.class.getMethod(
            "plan", com.pythontopia.auth.security.CurrentUserPrincipal.class));
    assertNotBudgeted(
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
        NpcGreetingController.class.getMethod(
            "greeting", com.pythontopia.auth.security.CurrentUserPrincipal.class, String.class));
  }

  private static void assertBudgeted(Method method) {
    assertThat(method.isAnnotationPresent(AiBudgeted.class)).isTrue();
  }

  private static void assertNotBudgeted(Method method) {
    assertThat(method.isAnnotationPresent(AiBudgeted.class)).isFalse();
  }
}
