package com.pythontopia.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Dialog templates may use explicit media preferences for presentation; legacy cognitive-style labels are ignored.
 *
 * <p>所有依赖以 Mockito mock 注入；{@code DialogContextRepository} 各聚合查询用默认空值兜底，
 * 仅 {@link ProfileRepository#findDimensions} 按用例桩定 tags，借公开的 {@link
 * DialogContextService#buildContext} 端到端断言结果（兼顾 stats 镜像字段）。
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

  /** 桩定 preference tags（按声明顺序 append，末元素=最新）。 */
  private void stubPreferenceTags(String... tags) {
    ObjectNode dims = MAPPER.createObjectNode();
    ArrayNode arr = dims.putObject("learning_preference").putArray("tags");
    for (String tag : tags) {
      arr.add(tag);
    }
    when(profileRepository.findDimensions(USER_ID)).thenReturn(Optional.of(dims));
  }

  @Test
  void videoPreference_mapsToVisualPresentation() {
    stubPreferenceTags("video");
    DialogContextDto dto = build();
    assertThat(dto.cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_VISUAL);
    assertThat(dto.stats()).containsEntry("cognitiveStyle", "visual");
  }

  @Test
  void textPreference_mapsToLogicalPresentation() {
    stubPreferenceTags("text");
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_LOGICAL);
  }

  @Test
  void codePreference_mapsToTrialPresentation() {
    stubPreferenceTags("code");
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_TRIAL);
  }

  @Test
  void picksLatestPreference() {
    stubPreferenceTags("video", "code", "text");
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_LOGICAL);
  }

  @Test
  void oldCognitiveStyleLabelsAreIgnored() {
    ObjectNode dims = MAPPER.createObjectNode();
    dims.putObject("cognitive_style").putArray("tags").add("trial");
    when(profileRepository.findDimensions(USER_ID)).thenReturn(Optional.of(dims));
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_UNKNOWN);
  }

  @Test
  void unrecognizedTagIsUnknown() {
    stubPreferenceTags("kinesthetic");
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_UNKNOWN);
  }

  @Test
  void noDimensionsIsUnknown() {
    when(profileRepository.findDimensions(USER_ID)).thenReturn(Optional.empty());
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_UNKNOWN);
  }

  @Test
  void emptyTagsIsUnknown() {
    stubPreferenceTags();
    assertThat(build().cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_UNKNOWN);
  }

  @Test
  void dataAccessExceptionDegradesToUnknownWithoutLoggingExceptionDetails() {
    String sentinel = "profile-row-private-sentinel";
    when(profileRepository.findDimensions(USER_ID))
        .thenThrow(new DataAccessResourceFailureException(sentinel));

    Logger logger = (Logger) LoggerFactory.getLogger(DialogContextService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);
    DialogContextDto dto;
    try {
      dto = build();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(dto.cognitiveStyle()).isEqualTo(DialogContextDto.STYLE_UNKNOWN);
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(sentinel));
  }
}
