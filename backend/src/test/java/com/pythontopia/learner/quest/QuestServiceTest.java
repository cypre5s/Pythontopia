package com.pythontopia.learner.quest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.quest.dto.QuestCompleteRequest;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import com.pythontopia.learner.quest.dto.QuestSkipRequest;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.learner.quest.dto.QuestStatus;
import com.pythontopia.learner.quest.repository.QuestRepository;
import com.pythontopia.learner.quest.service.QuestService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class QuestServiceTest {

  private final QuestRepository repository = mock(QuestRepository.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final QuestService service = new QuestService(repository, publisher);
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void startMovesOpenToInProgress() {
    QuestDto quest = sampleQuest(QuestStatus.OPEN, QuestNodeType.READ_RESOURCE, 10L);
    when(repository.findById(eq(7L), eq(10L))).thenReturn(Optional.of(quest));
    when(repository.findOwned(eq(7L), eq(10L)))
        .thenReturn(Optional.of(sampleQuest(QuestStatus.IN_PROGRESS, QuestNodeType.READ_RESOURCE, 10L)));

    QuestDto result = service.start(7L, 10L);
    assertThat(result.status()).isEqualTo(QuestStatus.IN_PROGRESS);
    verify(repository).updateStatus(7L, 10L, QuestStatus.IN_PROGRESS);
    verify(repository).recordProgress(10L, "STARTED", null);
  }

  @Test
  void completePublishesPracticeSucceededEvent() {
    ObjectNode payload = mapper.createObjectNode();
    payload.put("suggestedProblemId", 42L);
    QuestDto quest = sampleQuest(QuestStatus.IN_PROGRESS, QuestNodeType.SOLVE_PROBLEM, 11L, payload);
    when(repository.findById(eq(7L), eq(11L))).thenReturn(Optional.of(quest));
    when(repository.findOwned(eq(7L), eq(11L)))
        .thenReturn(Optional.of(sampleQuest(QuestStatus.COMPLETED, QuestNodeType.SOLVE_PROBLEM, 11L, payload)));

    QuestDto result = service.complete(7L, 11L, new QuestCompleteRequest("done"));
    assertThat(result.status()).isEqualTo(QuestStatus.COMPLETED);
    verify(publisher, times(1)).publishEvent(any(LearningEvent.PracticeSucceeded.class));
    verify(repository).recordProgress(11L, "COMPLETED", "done");
  }

  @Test
  void startFailsWhenAlreadyCompleted() {
    QuestDto quest = sampleQuest(QuestStatus.COMPLETED, QuestNodeType.READ_RESOURCE, 12L);
    when(repository.findById(eq(7L), eq(12L))).thenReturn(Optional.of(quest));
    assertThatThrownBy(() -> service.start(7L, 12L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("无法开始");
  }

  @Test
  void crossUserAccessForbidden() {
    QuestDto quest = sampleQuest(QuestStatus.OPEN, QuestNodeType.READ_RESOURCE, 12L, 99L);
    when(repository.findById(eq(7L), eq(12L))).thenReturn(Optional.of(quest));
    assertThatThrownBy(() -> service.start(7L, 12L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("QUEST_FORBIDDEN");
  }

  @Test
  void skipTransitionsToSkipped() {
    QuestDto quest = sampleQuest(QuestStatus.IN_PROGRESS, QuestNodeType.PROFILE_UPDATE, 13L);
    when(repository.findById(eq(7L), eq(13L))).thenReturn(Optional.of(quest));
    when(repository.findOwned(eq(7L), eq(13L)))
        .thenReturn(
            Optional.of(sampleQuest(QuestStatus.SKIPPED, QuestNodeType.PROFILE_UPDATE, 13L)));
    QuestDto result = service.skip(7L, 13L, new QuestSkipRequest("用户主动跳过"));
    assertThat(result.status()).isEqualTo(QuestStatus.SKIPPED);
    verify(repository).recordProgress(13L, "SKIPPED", "用户主动跳过");
  }

  private QuestDto sampleQuest(QuestStatus status, QuestNodeType nodeType, Long id) {
    return sampleQuest(status, nodeType, id, mapper.createObjectNode());
  }

  private QuestDto sampleQuest(QuestStatus status, QuestNodeType nodeType, Long id, ObjectNode payload) {
    return sampleQuest(status, nodeType, id, 7L, payload);
  }

  private QuestDto sampleQuest(QuestStatus status, QuestNodeType nodeType, Long id, Long userId) {
    return sampleQuest(status, nodeType, id, userId, mapper.createObjectNode());
  }

  private QuestDto sampleQuest(
      QuestStatus status, QuestNodeType nodeType, Long id, Long userId, ObjectNode payload) {
    return new QuestDto(
        id,
        userId,
        QuestSource.PATH_PLANNER,
        100L + id,
        "测试任务 " + id,
        "demo",
        "PY43-KC-001",
        nodeType,
        payload,
        LocalDate.now(ZoneOffset.UTC),
        5,
        status,
        OffsetDateTime.now(ZoneOffset.UTC),
        OffsetDateTime.now(ZoneOffset.UTC));
  }
}
