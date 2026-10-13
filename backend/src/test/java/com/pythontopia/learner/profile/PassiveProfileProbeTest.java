package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

import com.pythontopia.learner.event.LearningEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 世界内探针选择 → 经 ProbeCatalog 解析后回写画像维度（玩中建画像③）。 */
class PassiveProfileProbeTest {

  private final ProfileDimensionProjectionOutboxService service =
      mock(ProfileDimensionProjectionOutboxService.class);
  private final PassiveProfileRevisionListener listener =
      new PassiveProfileRevisionListener(service);

  @Test
  void cogIntroVisual_writesSelfReportedMediaPreferenceWithoutEvidence() {
    listener.onProbeChoiceMade(new LearningEvent.ProbeChoiceMade(7L, "cog_intro", "visual"));

    ArgumentCaptor<ProfileDimensionProjection> cap =
        ArgumentCaptor.forClass(ProfileDimensionProjection.class);
    verify(service).enqueue(cap.capture());
    assertThat(cap.getValue().dimensionKey()).isEqualTo("learning_preference");
    var d = cap.getValue().delta();
    assertThat(d.tagsAdd()).containsExactly("video");
    assertThat(d.scoreDelta()).isNull();
    assertThat(d.evidenceIncrement()).isNull();
    assertThat(d.source()).isEqualTo("INTERVIEW");
  }

  @Test
  void failReactChoiceNoLongerChangesProfile() {
    listener.onProbeChoiceMade(new LearningEvent.ProbeChoiceMade(7L, "fail_react", "skip"));

    verifyNoInteractions(service);
  }

  @Test
  void unknownChoice_noop() {
    listener.onProbeChoiceMade(new LearningEvent.ProbeChoiceMade(7L, "cog_intro", "bogus"));
    verify(service, never()).enqueue(any());
  }
}
