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
}
