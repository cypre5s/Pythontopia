package com.pythontopia.learner.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.learner.probe.ProbeCatalog.ProbeSignal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 探针目录（服务端权威）的解析与校验。 */
class ProbeCatalogTest {

  @Test
  void cogIntro_resolvesExplicitMediaPreferenceWithoutAbilityEvidence() {
    Optional<ProbeSignal> signal = ProbeCatalog.lookup("cog_intro", "visual");
    assertThat(signal).isPresent();
    assertThat(signal.get().dimensionKey()).isEqualTo("learning_preference");
    assertThat(signal.get().scoreDelta()).isNull();
    assertThat(signal.get().tag()).isEqualTo("video");
    assertThat(signal.get().source()).isEqualTo("INTERVIEW");
  }

  @Test
  void trialPreferenceMapsToSupportedCodeMedia() {
    assertThat(ProbeCatalog.lookup("cog_intro", "trial").orElseThrow().tag()).isEqualTo("code");
  }

  @Test
  void failureReactionNoLongerMapsToEmotion() {
    assertThat(ProbeCatalog.lookup("fail_react", "skip")).isEmpty();
    assertThat(ProbeCatalog.isValid("fail_react", "skip")).isFalse();
  }

  @Test
  void unknownProbeOrChoice_isEmptyAndInvalid() {
    assertThat(ProbeCatalog.lookup("cog_intro", "nope")).isEmpty();
    assertThat(ProbeCatalog.lookup("nope", "visual")).isEmpty();
    assertThat(ProbeCatalog.lookup(null, "visual")).isEmpty();
    assertThat(ProbeCatalog.isValid("cog_intro", "logical")).isTrue();
    assertThat(ProbeCatalog.isValid("cog_intro", "nope")).isFalse();
  }
}
