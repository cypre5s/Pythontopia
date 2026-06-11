package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link PassiveProfileService#profileBrief(Long)}：8 维全量组装紧凑中文画像摘要。
 *
 * <ul>
 *   <li>常规维度置信度 ≥0.3（evidence_count ≥3）才注入；emotion_persistence ≥0.25（≥2）即注入；
 *   <li>score 分档：&lt;0.45 → 入门/偏弱、0.45~0.7 → 进阶/一般、&gt;0.7 → 扎实/较强；
 *   <li>tag 维度取最近 ≤3 个逗号拼接；全空返回 ""；任何取数异常降级 ""。
 * </ul>
 *
 * <p>fixture 不写 last_updated，避免读时 decay 干扰分档断言；confidence = evidence_count/8。
 */
class PassiveProfileBriefTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ProfileRepository profileRepository = mock(ProfileRepository.class);
  private final PassiveProfileService service =
      new PassiveProfileService(profileRepository, objectMapper);

  private final ObjectNode dims = objectMapper.createObjectNode();

  private void putDim(String key, double score, int evidenceCount, String... tags) {
    ObjectNode dim = dims.putObject(key);
    dim.put("score", score);
    dim.put("evidence_count", evidenceCount);
    if (tags.length > 0) {
      ArrayNode arr = dim.putArray("tags");
      for (String tag : tags) {
        arr.add(tag);
      }
    }
  }

  @Test
  void assemblesAllEightDimensionsInFixedOrder() {
    putDim("cognitive_style", 0.5, 8, "logical", "visual");
    putDim("knowledge_base", 0.4, 8);
    putDim("learning_goal", 0.5, 8, "期末备考");
    putDim("learning_preference", 0.5, 8, "视频", "实例");
    putDim("error_pattern", 0.5, 8, "off-by-one", "缩进");
    putDim("emotion_persistence", 0.3, 8);
    putDim("practical_ability", 0.4, 8);
    putDim("professional_background", 0.5, 8, "文科");
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    assertThat(service.profileBrief(7L))
        .isEqualTo(
            "认知风格: visual; 知识基础: 入门(0.4); 目标: 期末备考; 偏好: 视频,实例; "
                + "易错: off-by-one,缩进; 情绪: 需鼓励; 实践: 偏弱(0.4); 专业: 文科");
  }

  @Test
  void partialSignalsOnlyEmitDimensionsWithSignal() {
    putDim("knowledge_base", 0.8, 8);
    // 4 个 tag 只取最近 3 个。
    putDim("error_pattern", 0.5, 8, "indent", "syntax", "off_by_one", "name_error");
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    assertThat(service.profileBrief(7L))
        .isEqualTo("知识基础: 扎实(0.8); 易错: syntax,off_by_one,name_error");
  }

  @Test
  void scoreBandsMapToBandWords() {
    putDim("knowledge_base", 0.5, 8);
    putDim("practical_ability", 0.9, 8);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    assertThat(service.profileBrief(7L)).isEqualTo("知识基础: 进阶(0.5); 实践: 较强(0.9)");
  }

  @Test
  void stableEmotionWhenScoreAtLeastHalf() {
    putDim("emotion_persistence", 0.5, 8);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    assertThat(service.profileBrief(7L)).isEqualTo("情绪: 稳定");
  }

  @Test
  void emptyWhenNoDimensionsStored() {
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.empty());

    assertThat(service.profileBrief(7L)).isEmpty();
  }

  @Test
  void lowConfidenceDimensionsAreFiltered() {
    // evidence_count=2 → confidence 0.25 < 0.3：常规维度全部被门槛过滤（即便有 tag/score）。
    putDim("cognitive_style", 0.5, 2, "visual");
    putDim("knowledge_base", 0.2, 2);
    putDim("practical_ability", 0.9, 2);
    putDim("learning_goal", 0.5, 1, "期末备考");
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    assertThat(service.profileBrief(7L)).isEmpty();
  }

  @Test
  void emotionUsesLowerQuarterThreshold() {
    // emotion_persistence 门槛 0.25（evidence_count=2 恰好达标）——情绪宁可敏感。
    putDim("emotion_persistence", 0.2, 2);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));
    assertThat(service.profileBrief(7L)).isEqualTo("情绪: 需鼓励");

    // evidence_count=1 → 0.125 仍低于 0.25，被过滤。
    dims.removeAll();
    putDim("emotion_persistence", 0.2, 1);
    when(profileRepository.findDimensions(8L)).thenReturn(Optional.of(dims));
    assertThat(service.profileBrief(8L)).isEmpty();
  }

  @Test
  void tagDimensionWithoutTagsIsSkipped() {
    // 置信度达标但无 tag 的 tag 类维度不注入（无内容可写）。
    putDim("learning_goal", 0.5, 8);
    putDim("professional_background", 0.5, 8, "文科");
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    assertThat(service.profileBrief(7L)).isEqualTo("专业: 文科");
  }

  @Test
  void degradesToEmptyOnRepositoryFailure() {
    when(profileRepository.findDimensions(7L)).thenThrow(new IllegalStateException("db down"));

    assertThat(service.profileBrief(7L)).isEmpty();
  }
}
