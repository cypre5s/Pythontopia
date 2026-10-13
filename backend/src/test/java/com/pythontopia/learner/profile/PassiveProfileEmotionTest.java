package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 练习重试/放弃/逆袭 → emotion_persistence 行为信号（情绪化小镇②）。 */
class PassiveProfileEmotionTest {

  private final ProfileDimensionProjectionOutboxService service =
      mock(ProfileDimensionProjectionOutboxService.class);
  private final PassiveProfileRevisionListener listener =
      new PassiveProfileRevisionListener(service);

  @Test
  void retried_writesPositiveEmotion() {
    listener.onPracticeRetried(new LearningEvent.PracticeRetried(7L, 101L, "PY43-KC-001"));

    ArgumentCaptor<ProfileDimensionProjection> cap =
        ArgumentCaptor.forClass(ProfileDimensionProjection.class);
    verify(service).enqueue(cap.capture());
    assertThat(cap.getValue().dimensionKey()).isEqualTo("emotion_persistence");
    assertThat(cap.getValue().delta().scoreDelta()).isEqualTo(0.02);
    assertThat(cap.getValue().delta().tagsAdd()).containsExactly("retry");
    assertThat(cap.getValue().delta().evidenceIncrement()).isEqualTo(1);
    assertThat(cap.getValue().delta().source()).isEqualTo("PRACTICE_FAIL");
  }

  @Test
  void abandoned_writesNegativeEmotion() {
    listener.onPracticeAbandoned(new LearningEvent.PracticeAbandoned(7L, 101L, "PY43-KC-001"));

    ArgumentCaptor<ProfileDimensionProjection> cap =
        ArgumentCaptor.forClass(ProfileDimensionProjection.class);
    verify(service).enqueue(cap.capture());
    assertThat(cap.getValue().dimensionKey()).isEqualTo("emotion_persistence");
    assertThat(cap.getValue().delta().scoreDelta()).isEqualTo(-0.03);
    assertThat(cap.getValue().delta().tagsAdd()).containsExactly("abandon");
  }

  @Test
  void comeback_writesStrongPositiveEmotion() {
    listener.onComebackSolved(new LearningEvent.ComebackSolved(7L, 101L, "PY43-KC-001", 3));

    ArgumentCaptor<ProfileDimensionProjection> cap =
        ArgumentCaptor.forClass(ProfileDimensionProjection.class);
    verify(service).enqueue(cap.capture());
    assertThat(cap.getValue().dimensionKey()).isEqualTo("emotion_persistence");
    assertThat(cap.getValue().delta().scoreDelta()).isEqualTo(0.04);
    assertThat(cap.getValue().delta().tagsAdd()).containsExactly("comeback");
    assertThat(cap.getValue().delta().source()).isEqualTo("PRACTICE_PASS");
  }
}
