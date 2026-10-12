package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TtsPropertiesTest {

  @Test
  void legacyCredentialsDoNotEnableVoiceover() {
    assertThat(new TtsProperties("app", "key", "secret", 0).isEnabled()).isFalse();
  }
}
