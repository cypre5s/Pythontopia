package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证 TutorService.buildProfileBrief：认知风格(置信度门槛) + 易错点 → profile_brief 文案。 */
class TutorProfileBriefTest {

  private ProfileDimensionDto dim(String key, double confidence, List<String> tags) {
    return new ProfileDimensionDto(key, key, 0.5, confidence, 0, false, tags);
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
