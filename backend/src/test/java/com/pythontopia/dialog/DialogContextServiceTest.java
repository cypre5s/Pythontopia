package com.pythontopia.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * 看阶段 NPC 对话 3-way 个性化：{@link DialogContextService} 把 {@code
 * learner_profile.dimensions} 的 {@code cognitive_style.tags} 最新/主导标签推断为三态 {@code
 * cognitiveStyle} ∈ {visual, logical, trial, unknown}。
 *
 * <p>所有依赖以 Mockito mock 注入；{@code DialogContextRepository} 各聚合查询用默认空值兜底，
 * 仅 {@link ProfileRepository#findDimensions} 按用例桩定 tags，借公开的 {@link
 * DialogContextService#buildContext} 端到端断言推断结果（兼顾 stats 镜像字段）。
 */
class DialogContextServiceTest {

  private static final long USER_ID = 7L;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final DialogContextRepository repository = mock(DialogContextRepository.class);
  private final ProfileRepository profileRepository = mock(ProfileRepository.class);
  private final UserRepository userRepository = mock(UserRepository.class);

  private final DialogContextService service =
      new DialogContextService(repository, profileRepository, userRepository);

  private DialogContextDto build() {
    return service.buildContext(USER_ID, "tester");
  }

  /** 桩定 cognitive_style.tags（按声明顺序 append，末元素=最新/主导）。 */
  private void stubTags(String... tags) {
    ObjectNode dims = MAPPER.createObjectNode();
    ArrayNode arr = dims.putObject("cognitive_style").putArray("tags");
    for (String tag : tags) {
      arr.add(tag);
    }
    when(profileRepository.findDimensions(USER_ID)).thenReturn(Optional.of(dims));
  }

  @Test
  void visualTag_mapsToVisual() {
    stubTags("visual");
    DialogContextDto dto = build();
    assertThat(dto.cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_VISUAL);
    assertThat(dto.stats()).containsEntry("cognitiveStyle", "visual");
  }

  @Test
  void logicalTag_mapsToLogical() {
    stubTags("logical");
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_LOGICAL);
  }

  @Test
  void trialTag_mapsToTrial() {
    stubTags("trial");
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_TRIAL);
  }

  @Test
  void picksLatestDominantTag() {
    stubTags("visual", "trial", "logical");
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_LOGICAL);
  }

  @Test
  void aliasTagNormalizesToTrial() {
    stubTags("trial-and-error");
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_TRIAL);
  }

  @Test
  void unrecognizedTagIsUnknown() {
    stubTags("kinesthetic");
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_UNKNOWN);
  }

  @Test
  void noDimensionsIsUnknown() {
    when(profileRepository.findDimensions(USER_ID)).thenReturn(Optional.empty());
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_UNKNOWN);
  }

  @Test
  void emptyTagsIsUnknown() {
    stubTags();
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_UNKNOWN);
  }

  @Test
  void dataAccessExceptionDegradesToUnknown() {
    when(profileRepository.findDimensions(USER_ID))
        .thenThrow(new DataAccessResourceFailureException("dimensions column missing"));
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_UNKNOWN);
  }
}
