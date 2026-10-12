package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import org.junit.jupiter.api.Test;

class TtsPropertiesTest {

  @Test
  void legacyCredentialsDoNotEnableVoiceover() {
    assertThat(new TtsProperties("app", "key", "secret", 0).isEnabled()).isFalse();
  }

  @Test
  void legacyCredentialsCannotStartExternalVoiceover() {
    XfyunTtsService service =
        new XfyunTtsService(new TtsProperties("app", "key", "secret", 0), new ObjectMapper());

    assertThatThrownBy(() -> service.synthesize(1L, "测试配音", "x4_yezi"))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).code())
        .isEqualTo("TTS_DISABLED");
  }
}
