package com.pythontopia.mainline.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.bugmine.dto.BugMineErrorBookItemDto;
import com.pythontopia.bugmine.dto.BugMineSessionStatus;
import com.pythontopia.bugmine.service.BugMineService;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChapterTestPersonalizationServiceTest {

  private final PassiveProfileService profileService = mock(PassiveProfileService.class);
  private final BugMineService bugMineService = mock(BugMineService.class);
  private final ChapterTestPersonalizationService service =
      new ChapterTestPersonalizationService(profileService, bugMineService, new ObjectMapper());

  @Test
  void mapsOnlyEvidenceBackedProfileSignalsIntoGraphContract() {
    when(profileService.getDimensions(7L))
        .thenReturn(
            List.of(
                dimension("knowledge_base", 0.75, 0.50, List.of()),
                dimension("professional_background", 0.50, 0.50, List.of("CS")),
                dimension("cognitive_style", 0.50, 0.50, List.of("visual")),
                dimension("learning_preference", 0.50, 0.50, List.of("示例", "视频")),
                dimension("error_pattern", 0.50, 0.50, List.of("off-by-one", "缩进")),
                dimension("practical_ability", 0.30, 0.50, List.of()),
                dimension("emotion_persistence", 0.40, 0.25, List.of()),
                // confidence 不足的标签不能冒充真实画像信号。
                dimension("learning_goal", 0.50, 0.10, List.of("被忽略"))));
    when(bugMineService.errorBook(7L)).thenReturn(List.of());

    var context = service.build(7L, List.of("PY43-KC-001"));

    assertThat(context.profileBrief().path("knowledge_level").asInt()).isEqualTo(4);
    assertThat(context.profileBrief().path("major").asText()).isEqualTo("CS");
    assertThat(context.profileBrief().path("learning_goal").asText()).isEmpty();
    assertThat(context.profileBrief().path("learning_preference").asText())
        .isEqualTo("示例,视频");
    assertThat(textValues(context.profileBrief().path("error_patterns")))
        .containsExactly("off-by-one", "缩进");
    assertThat(context.profileBrief().path("practice_ability").asText()).isEqualTo("偏弱");
    assertThat(context.profileBrief().path("cognitive_style").asText()).isEmpty();
    assertThat(context.profileBrief().path("persistence").asText()).isEmpty();
  }

  @Test
  void aggregatesOpenErrorBookWithoutLeakingOtherChapterKcs() {
    when(profileService.getDimensions(9L)).thenReturn(List.of());
    when(bugMineService.errorBook(9L))
        .thenReturn(
            List.of(
                error(1L, "PY43-KC-002", List.of("边界错误", "缩进")),
                error(2L, "PY43-KC-001", List.of("缩进", "类型转换")),
                error(3L, "PY43-KC-999", List.of("不属于本章"))));

    var context = service.build(9L, List.of("PY43-KC-001", "PY43-KC-002"));

    assertThat(textValues(context.errorbookBrief().path("frequent_error_kcs")))
        .containsExactly("PY43-KC-002", "PY43-KC-001");
    assertThat(textValues(context.errorbookBrief().path("top_error_tags")))
        .containsExactly("边界错误", "缩进", "类型转换");
    assertThat(context.errorbookBrief().path("summary").asText())
        .contains("open_items=2", "PY43-KC-002", "边界错误");
  }

  private static ProfileDimensionDto dimension(
      String key, double score, double confidence, List<String> tags) {
    return new ProfileDimensionDto(key, key, score, confidence, 4, false, tags);
  }

  private static List<String> textValues(com.fasterxml.jackson.databind.JsonNode node) {
    return java.util.stream.StreamSupport.stream(node.spliterator(), false)
        .map(com.fasterxml.jackson.databind.JsonNode::asText)
        .toList();
  }

  private static BugMineErrorBookItemDto error(
      long sessionId, String kcCode, List<String> patterns) {
    return new BugMineErrorBookItemDto(
        sessionId,
        sessionId,
        sessionId,
        null,
        kcCode,
        "题目",
        BugMineSessionStatus.OPEN,
        1,
        1,
        1,
        "",
        BigDecimal.valueOf(0.3),
        BigDecimal.valueOf(0.8),
        BigDecimal.valueOf(0.5),
        "",
        patterns,
        true,
        null,
        null,
        List.of());
  }
}
