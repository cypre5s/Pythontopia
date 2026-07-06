package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 世界内探针选择 → 经 ProbeCatalog 解析后回写画像维度（玩中建画像③）。 */
class PassiveProfileProbeTest {

  private final PassiveProfileService service = mock(PassiveProfileService.class);
  private final PassiveProfileRevisionListener listener =
      new PassiveProfileRevisionListener(service);

  @Test
  void cogIntroVisual_writesCognitiveStyleVisual() {
    listener.onProbeChoiceMade(new LearningEvent.ProbeChoiceMade(7L, "cog_intro", "visual"));

    ArgumentCaptor<DimensionDelta> cap = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(service).applyDimensionUpdate(eq(7L), eq("cognitive_style"), cap.capture());
    DimensionDelta d = cap.getValue();
    assertThat(d.tagsAdd()).containsExactly("visual");
    assertThat(d.scoreDelta()).isEqualTo(0.03);
    assertThat(d.evidenceIncrement()).isEqualTo(1);
    assertThat(d.source()).isEqualTo("RESOURCE_FEEDBACK");
  }

  @Test
  void failReactSkip_writesEmotionPersistenceNegative() {
    listener.onProbeChoiceMade(new LearningEvent.ProbeChoiceMade(7L, "fail_react", "skip"));

    ArgumentCaptor<DimensionDelta> cap = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(service).applyDimensionUpdate(eq(7L), eq("emotion_persistence"), cap.capture());
    assertThat(cap.getValue().scoreDelta()).isEqualTo(-0.02);
    assertThat(cap.getValue().tagsAdd()).containsExactly("skip");
  }

  @Test
  void unknownChoice_noop() {
    listener.onProbeChoiceMade(new LearningEvent.ProbeChoiceMade(7L, "cog_intro", "bogus"));
    verify(service, never()).applyDimensionUpdate(any(), any(), any());
  }
}
