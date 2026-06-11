package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 验证答疑 profile_brief 组装：
 *
 * <ul>
 *   <li>{@link TutorService#resolveProfileBrief}：R-16 起改调 {@link
 *       PassiveProfileService#profileBrief(Long)} 富化 8 维摘要，styleOverride 时替换/前插
 *       「认知风格:」段（{@link TutorService#applyStyleOverride}）；
 *   <li>旧静态 {@code buildProfileBrief}（认知风格门槛 + 易错点两维）保留兼容，行为不变。
 * </ul>
 */
class TutorProfileBriefTest {

  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final TutorService service =
      new TutorService(
          mock(TutorGraphClient.class),
          mock(TutorSessionRepository.class),
          new ObjectMapper(),
          passiveProfileService,
          mock(ApplicationEventPublisher.class),
          mock(ClassroomDashboardService.class),
          mock(MemoryRetrievalService.class));

  private ProfileDimensionDto dim(String key, double confidence, List<String> tags) {
    return new ProfileDimensionDto(key, key, 0.5, confidence, 0, false, tags);
  }

  @Test
  void resolveProfileBriefUsesRichEightDimensionBrief() {
    when(passiveProfileService.profileBrief(7L))
        .thenReturn("认知风格: visual; 知识基础: 入门(0.4); 情绪: 需鼓励");

    assertThat(service.resolveProfileBrief(7L, null))
        .isEqualTo("认知风格: visual; 知识基础: 入门(0.4); 情绪: 需鼓励");
  }

  @Test
  void resolveProfileBriefOverrideReplacesExistingStyleSegment() {
    when(passiveProfileService.profileBrief(7L))
        .thenReturn("认知风格: visual; 知识基础: 入门(0.4); 易错: indent");

    assertThat(service.resolveProfileBrief(7L, "logical"))
        .isEqualTo("认知风格: logical; 知识基础: 入门(0.4); 易错: indent");
  }

  @Test
  void resolveProfileBriefOverridePrependsWhenStyleSegmentMissing() {
    when(passiveProfileService.profileBrief(7L)).thenReturn("知识基础: 入门(0.4); 易错: indent");

    assertThat(service.resolveProfileBrief(7L, "trial"))
        .isEqualTo("认知风格: trial; 知识基础: 入门(0.4); 易错: indent");
  }

  @Test
  void resolveProfileBriefKeepsOverrideWhenProfileFails() {
    when(passiveProfileService.profileBrief(7L)).thenThrow(new IllegalStateException("db down"));

    // 画像取数失败：显式换讲法仍生效；无 override 则降级空串。
    assertThat(service.resolveProfileBrief(7L, "visual")).isEqualTo("认知风格: visual");
    assertThat(service.resolveProfileBrief(7L, null)).isEmpty();
  }

  @Test
  void applyStyleOverrideOnEmptyBriefYieldsStyleOnly() {
    assertThat(TutorService.applyStyleOverride("", "visual")).isEqualTo("认知风格: visual");
    assertThat(TutorService.applyStyleOverride(null, "visual")).isEqualTo("认知风格: visual");
    // override 空白等同无选择，brief 原样返回。
    assertThat(TutorService.applyStyleOverride("情绪: 需鼓励", "  ")).isEqualTo("情绪: 需鼓励");
    assertThat(TutorService.applyStyleOverride("情绪: 需鼓励", null)).isEqualTo("情绪: 需鼓励");
  }

  @Test
  void buildsBriefFromStyleAndErrors() {
    List<ProfileDimensionDto> dims =
        List.of(
            dim("cognitive_style", 0.6, List.of("logical", "visual")),
            dim("error_pattern", 0.5, List.of("indent", "syntax", "off_by_one", "name_error")));

    String brief = TutorService.buildProfileBrief(dims);

    assertThat(brief).contains("认知风格: visual"); // 取最近一次（末尾）信号
    assertThat(brief).contains("易错:");
    assertThat(brief).contains("name_error"); // 取末尾至多 3 个
    assertThat(brief).doesNotContain("indent"); // 超出 3 个的较旧 tag 被裁掉
  }

  @Test
  void skipsLowConfidenceStyle() {
    List<ProfileDimensionDto> dims =
        List.of(
            dim("cognitive_style", 0.1, List.of("visual")),
            dim("error_pattern", 0.5, List.of()));

    assertThat(TutorService.buildProfileBrief(dims)).doesNotContain("认知风格");
  }

  @Test
  void emptyWhenNoSignal() {
    List<ProfileDimensionDto> dims =
        List.of(dim("cognitive_style", 0.0, List.of()), dim("error_pattern", 0.0, List.of()));

    assertThat(TutorService.buildProfileBrief(dims)).isEmpty();
    assertThat(TutorService.buildProfileBrief(null)).isEmpty();
  }

  @Test
  void styleOverrideBypassesConfidenceGate() {
    // 即使认知风格信号不足（置信度 < 0.3 会被门槛过滤），学生显式换讲法仍生效。
    List<ProfileDimensionDto> dims =
        List.of(
            dim("cognitive_style", 0.1, List.of("logical")),
            dim("error_pattern", 0.5, List.of("indent", "syntax")));

    String brief = TutorService.buildProfileBrief(dims, "visual");

    assertThat(brief).contains("认知风格: visual"); // 显式选择无视 0.3 门槛
    assertThat(brief).contains("易错: indent,syntax"); // 仍照常附带易错点
  }

  @Test
  void styleOverrideWinsOverHighConfidenceProfileStyle() {
    // override 优先于画像里高置信度的认知风格。
    List<ProfileDimensionDto> dims =
        List.of(dim("cognitive_style", 0.9, List.of("logical")));

    assertThat(TutorService.buildProfileBrief(dims, "trial")).contains("认知风格: trial");
  }

  @Test
  void styleOverrideAppliesWithNullDims() {
    // 画像取数失败（dims=null）时仍尊重学生的显式换讲法。
    assertThat(TutorService.buildProfileBrief(null, "logical")).isEqualTo("认知风格: logical");
  }

  @Test
  void nullStyleOverrideKeepsGatedBehavior() {
    // 无显式选择时退回原行为：低置信度认知风格仍被门槛过滤。
    List<ProfileDimensionDto> dims = List.of(dim("cognitive_style", 0.1, List.of("visual")));

    assertThat(TutorService.buildProfileBrief(dims, null)).doesNotContain("认知风格");
    assertThat(TutorService.buildProfileBrief(dims, "  ")).doesNotContain("认知风格"); // 空白等同无选择
  }
}
