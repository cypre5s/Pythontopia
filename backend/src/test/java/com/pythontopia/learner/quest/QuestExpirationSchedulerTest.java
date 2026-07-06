package com.pythontopia.learner.quest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.quest.service.QuestExpirationScheduler;
import com.pythontopia.learner.quest.service.QuestService;
import org.junit.jupiter.api.Test;

class QuestExpirationSchedulerTest {

  private final QuestService questService = mock(QuestService.class);
  private final QuestExpirationScheduler scheduler = new QuestExpirationScheduler(questService);

  @Test
  void expireOverdueDoesNotLeakMaintenanceFailuresToSchedulerThread() {
    when(questService.expireOverdue()).thenThrow(new IllegalStateException("database stopped"));

    assertThatCode(scheduler::expireOverdue).doesNotThrowAnyException();

    verify(questService).expireOverdue();
  }
}
