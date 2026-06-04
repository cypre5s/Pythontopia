package com.pythontopia.learner.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.learner.probe.ProbeCatalog.ProbeSignal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 探针目录（服务端权威）的解析与校验。 */
class ProbeCatalogTest {

  @Test
  void cogIntro_resolvesCognitiveStyleSignal() {
    Optional<ProbeSignal> signal = ProbeCatalog.lookup("cog_intro", "visual");
    assertThat(signal).isPresent();
    assertThat(signal.get().dimensionKey()).isEqualTo("cognitive_style");
    assertThat(signal.get().scoreDelta()).isEqualTo(0.03);
    assertThat(signal.get().tag()).isEqualTo("visual");
    assertThat(signal.get().source()).isEqualTo("RESOURCE_FEEDBACK");
  }

  @Test
  void failReact_skip_resolvesNegativeEmotionSignal() {
    Optional<ProbeSignal> signal = ProbeCatalog.lookup("fail_react", "skip");
    assertThat(signal).isPresent();
    assertThat(signal.get().dimensionKey()).isEqualTo("emotion_persistence");
    assertThat(signal.get().scoreDelta()).isEqualTo(-0.02);
    assertThat(signal.get().tag()).isEqualTo("skip");
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
