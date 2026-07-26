package com.pythontopia.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.pythontopia.intent.dto.IntentRouteResponse;
import com.pythontopia.learner.event.LearningEventLogRepository;
import com.pythontopia.learner.mastery.MasteryService;
import com.pythontopia.learner.mastery.dto.MasterySnapshotDto;
import com.pythontopia.learner.memory.MemoryBriefService;
import com.pythontopia.learner.memory.dto.MemoryBriefDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkshopLearningContextServiceTest {

  private final MemoryBriefService memoryBriefService = mock(MemoryBriefService.class);
  private final PassiveProfileService profileService = mock(PassiveProfileService.class);
  private final MasteryService masteryService = mock(MasteryService.class);
  private final PracticeSubmissionRepository submissionRepository =
      mock(PracticeSubmissionRepository.class);
  private final LearningEventLogRepository eventRepository =
      mock(LearningEventLogRepository.class);
  private final WorkshopLearningContextService service =
      new WorkshopLearningContextService(
          memoryBriefService,
          profileService,
          masteryService,
          submissionRepository,
          eventRepository);

  @Test
  void buildsAuthoritativeContextFromExistingProfileMasterySubmissionAndResourceFacts() {
    when(profileService.profileBrief(7L))
        .thenReturn("认知风格: visual; 偏好: 图解; 情绪: 需鼓励");
    when(profileService.getDimensions(7L))
        .thenReturn(
            List.of(
                dimension("cognitive_style", 0.7, 0.8, List.of("visual")),
                dimension("learning_preference", 0.6, 0.8, List.of("图解")),
                dimension("emotion_persistence", 0.3, 0.8, List.of()),
                dimension("knowledge_base", 0.25, 0.8, List.of()),
                dimension("professional_background", 0.5, 0.8, List.of("计算机"))));
    when(memoryBriefService.buildAuthoritativeBrief(7L, "帮我复习循环"))
        .thenReturn(
            new MemoryBriefDto(
                List.of("循环边界连续出错"),
                List.of("PY43-KC-031"),
                List.of("认知风格: visual", "情绪: 需鼓励"),
                "2026-07-22T15:00:00Z",
                List.of("偏好图解"),
                List.of("for 循环边界连续失败", "range 右边界仍不稳定"),
                List.of("刚通过变量题"),
                List.of("短步骤+鼓励反馈")));
    when(masteryService.listSnapshots(7L))
        .thenReturn(
            List.of(
                new MasterySnapshotDto(
                    "PY43-KC-031",
                    new BigDecimal("0.28"),
                    new BigDecimal("0.80"),
                    "2026-07-22T14:50:00Z",
                    "PRACTICE_FAIL")));
    when(submissionRepository.findRecentForPlanner(7L, 12))
        .thenReturn(
            List.of(
                new PracticeSubmissionRepository.PlannerSubmission(40L, "PY43-KC-031", false),
                new PracticeSubmissionRepository.PlannerSubmission(41L, "PY43-KC-031", false)));
    when(eventRepository.findRecentResourceConsumptions(7L, 8))
        .thenReturn(
            List.of(
                new LearningEventLogRepository.ResourceConsumption(
                    91L,
                    "PY43-KC-031",
                    "HANDOUT",
                    Instant.parse("2026-07-22T14:00:00Z"))));

    var context = service.build(7L, "帮我复习循环");

    verify(memoryBriefService).buildAuthoritativeBrief(7L, "帮我复习循环");
    assertThat(context.profileBrief()).contains("visual");
    assertThat(context.kcMastery()).containsEntry("PY43-KC-031", 0.28);
    assertThat(context.kcConfidence()).containsEntry("PY43-KC-031", 0.8);
    assertThat(context.recentErrorKcs()).containsExactly("PY43-KC-031");
    assertThat(context.cognitiveLoad()).isEqualTo("HIGH");
    assertThat(context.frustrationLevel()).isEqualTo("HIGH");
    assertThat(context.cognitiveStyle()).isEqualTo("visual");
    assertThat(context.learningPreferences()).contains("图解", "偏好图解");
    assertThat(context.profileMajor()).isEqualTo("CS");
    assertThat(context.pythonLevel()).isEqualTo(2);
    assertThat(context.recentResources()).singleElement().satisfies(
        item -> {
          assertThat(item.kcCode()).isEqualTo("PY43-KC-031");
          assertThat(item.type()).isEqualTo("HANDOUT");
        });
    assertThat(context.recentSubmissions()).hasSize(2);
  }

  @Test
  void recordsRouteObservationWithoutWritingMasteryOrProfileDirectly() {
    var context = LearnerRoutingContext.empty();
    var response =
        new IntentRouteResponse(
            "ASK",
            List.of("PY43-KC-031"),
            "循环",
            0.9,
            "ok",
            "",
            null,
            "ask",
            List.of("ASK"),
            false,
            "",
            "generative",
            "answer",
            "contextual_match",
            RouteExecutionTokenSignerTest.multiDecision("route_0123456789abcdef"));

    service.recordRoute(7L, response, context);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
    verify(eventRepository)
        .insert(
            eq(7L),
            eq("workshop_route_selected"),
            payload.capture(),
            eq("backend"),
            eq(2));
    assertThat(payload.getValue())
        .containsEntry("routeId", "route_0123456789abcdef")
        .containsEntry("masteryWrite", false);
  }

  private static ProfileDimensionDto dimension(
      String key, double score, double confidence, List<String> tags) {
    return new ProfileDimensionDto(key, key, score, confidence, 6, false, tags);
  }
}
