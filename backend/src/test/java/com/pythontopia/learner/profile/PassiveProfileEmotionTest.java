package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 练习重试/放弃/逆袭 → emotion_persistence 行为信号（情绪化小镇②）。 */
class PassiveProfileEmotionTest {

  private final PassiveProfileService service = mock(PassiveProfileService.class);
  private final PassiveProfileRevisionListener listener =
      new PassiveProfileRevisionListener(service);

  @Test
  void retried_writesPositiveEmotion() {
    listener.onPracticeRetried(new LearningEvent.PracticeRetried(7L, 101L, "PY43-KC-001"));

    ArgumentCaptor<DimensionDelta> cap = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(service).applyDimensionUpdate(eq(7L), eq("emotion_persistence"), cap.capture());
    assertThat(cap.getValue().scoreDelta()).isEqualTo(0.02);
    assertThat(cap.getValue().tagsAdd()).containsExactly("retry");
    assertThat(cap.getValue().evidenceIncrement()).isEqualTo(1);
    assertThat(cap.getValue().source()).isEqualTo("PRACTICE_FAIL");
  }

  @Test
  void abandoned_writesNegativeEmotion() {
    listener.onPracticeAbandoned(new LearningEvent.PracticeAbandoned(7L, 101L, "PY43-KC-001"));

    ArgumentCaptor<DimensionDelta> cap = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(service).applyDimensionUpdate(eq(7L), eq("emotion_persistence"), cap.capture());
    assertThat(cap.getValue().scoreDelta()).isEqualTo(-0.03);
    assertThat(cap.getValue().tagsAdd()).containsExactly("abandon");
  }

  @Test
  void comeback_writesStrongPositiveEmotion() {
    listener.onComebackSolved(new LearningEvent.ComebackSolved(7L, 101L, "PY43-KC-001", 3));

    ArgumentCaptor<DimensionDelta> cap = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(service).applyDimensionUpdate(eq(7L), eq("emotion_persistence"), cap.capture());
    assertThat(cap.getValue().scoreDelta()).isEqualTo(0.04);
    assertThat(cap.getValue().tagsAdd()).containsExactly("comeback");
    assertThat(cap.getValue().source()).isEqualTo("PRACTICE_PASS");
  }
}
