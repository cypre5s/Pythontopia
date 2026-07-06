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

/** 讲法采纳事件 → cognitive_style 回写（闭环）。 */
class PassiveProfileCognitiveStyleTest {

  private final PassiveProfileService service = mock(PassiveProfileService.class);
  private final PassiveProfileRevisionListener listener =
      new PassiveProfileRevisionListener(service);

  @Test
  void styleEngaged_writesCognitiveStyleTag() {
    listener.onTutorStyleEngaged(
        new LearningEvent.TutorStyleEngaged(7L, "visual", "PY43-KC-001"));

    ArgumentCaptor<DimensionDelta> cap = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(service).applyDimensionUpdate(eq(7L), eq("cognitive_style"), cap.capture());
    DimensionDelta d = cap.getValue();
    assertThat(d.tagsAdd()).contains("visual");
    assertThat(d.evidenceIncrement()).isEqualTo(1);
    assertThat(d.source()).isEqualTo("RESOURCE_FEEDBACK");
  }

  @Test
  void styleEngaged_blankStyle_noop() {
    listener.onTutorStyleEngaged(new LearningEvent.TutorStyleEngaged(7L, "  ", null));
    verify(service, never()).applyDimensionUpdate(any(), any(), any());
  }
}
