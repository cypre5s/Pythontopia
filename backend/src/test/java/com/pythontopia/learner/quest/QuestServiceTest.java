package com.pythontopia.learner.quest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.pythontopia.learner.quest.evidence.QuestEvidenceVerifier;
import com.pythontopia.learner.quest.repository.QuestRepository;
import com.pythontopia.learner.quest.service.QuestService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class QuestServiceTest {

  private final QuestRepository repository = mock(QuestRepository.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final QuestEvidenceVerifier evidenceVerifier = mock(QuestEvidenceVerifier.class);
  private final QuestService service =
      new QuestService(repository, publisher, evidenceVerifier);
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void startMovesOpenToInProgress() {
    QuestDto quest = sampleQuest(QuestStatus.OPEN, QuestNodeType.READ_RESOURCE, 10L);
    when(repository.findById(eq(7L), eq(10L))).thenReturn(Optional.of(quest));
    when(repository.updateStatus(7L, 10L, QuestStatus.IN_PROGRESS)).thenReturn(1);
    when(repository.findOwned(eq(7L), eq(10L)))
        .thenReturn(Optional.of(sampleQuest(QuestStatus.IN_PROGRESS, QuestNodeType.READ_RESOURCE, 10L)));

    QuestDto result = service.start(7L, 10L);
    assertThat(result.status()).isEqualTo(QuestStatus.IN_PROGRESS);
    verify(repository).updateStatus(7L, 10L, QuestStatus.IN_PROGRESS);
    verify(repository).recordProgress(10L, "STARTED", null);
  }

  @Test
  void startStopsWhenAnotherRequestAlreadyStartedQuest() {
    QuestDto quest = sampleQuest(QuestStatus.OPEN, QuestNodeType.READ_RESOURCE, 10L);
    when(repository.findById(eq(7L), eq(10L))).thenReturn(Optional.of(quest));
    when(repository.updateStatus(7L, 10L, QuestStatus.IN_PROGRESS)).thenReturn(0);

    assertThatThrownBy(() -> service.start(7L, 10L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("QUEST_STATE_CHANGED");

    verify(repository, never()).recordProgress(anyLong(), anyString(), any());
  }

  @Test
  void completeSolveProblemDoesNotDoubleCountMastery() {
    ObjectNode payload = mapper.createObjectNode();
    payload.put("suggestedProblemId", 42L);
    QuestDto quest = sampleQuest(QuestStatus.IN_PROGRESS, QuestNodeType.SOLVE_PROBLEM, 11L, payload);
    when(repository.findById(eq(7L), eq(11L))).thenReturn(Optional.of(quest));
    when(repository.updateStatus(7L, 11L, QuestStatus.COMPLETED)).thenReturn(1);
    when(repository.findOwned(eq(7L), eq(11L)))
        .thenReturn(Optional.of(sampleQuest(QuestStatus.COMPLETED, QuestNodeType.SOLVE_PROBLEM, 11L, payload)));

    QuestDto result = service.complete(7L, 11L, new QuestCompleteRequest("done"));
    assertThat(result.status()).isEqualTo(QuestStatus.COMPLETED);
    // 判题链路（SubmissionService）在 AC 时已发过 PracticeSucceeded 并随后回调本方法，
    // 这里再发一次会让同一次 AC 被 MasteryEvaluator 记两遍掌握度。
    verify(publisher, never()).publishEvent(any(LearningEvent.PracticeSucceeded.class));
    verify(repository).recordProgress(11L, "COMPLETED", "done");
  }

  @Test
  void completeFixBugStillPublishesDebugResolved() {
    ObjectNode payload = mapper.createObjectNode();
    payload.put("suggestedProblemId", 42L);
    QuestDto quest = sampleQuest(QuestStatus.IN_PROGRESS, QuestNodeType.FIX_BUG, 15L, payload);
    when(repository.findById(eq(7L), eq(15L))).thenReturn(Optional.of(quest));
    when(repository.updateStatus(7L, 15L, QuestStatus.COMPLETED)).thenReturn(1);
    when(repository.findOwned(eq(7L), eq(15L)))
        .thenReturn(Optional.of(sampleQuest(QuestStatus.COMPLETED, QuestNodeType.FIX_BUG, 15L, payload)));

    service.complete(7L, 15L, new QuestCompleteRequest("fixed"));

    ArgumentCaptor<LearningEvent.DebugResolved> event =
        ArgumentCaptor.forClass(LearningEvent.DebugResolved.class);
    verify(publisher, times(1)).publishEvent(event.capture());
    assertThat(event.getValue().idempotencyKey()).isEqualTo("quest:15");
  }

  @Test
  void completeFixBugWithVerifiedSubmissionLeavesDebugResolvedToBugMine() {
    ObjectNode payload = mapper.createObjectNode();
    payload.put("suggestedProblemId", 42L);
    QuestDto quest = sampleQuest(QuestStatus.IN_PROGRESS, QuestNodeType.FIX_BUG, 17L, payload);
    when(repository.findById(eq(7L), eq(17L))).thenReturn(Optional.of(quest));
    when(repository.updateStatus(7L, 17L, QuestStatus.COMPLETED)).thenReturn(1);
    when(repository.findOwned(eq(7L), eq(17L)))
        .thenReturn(Optional.of(sampleQuest(QuestStatus.COMPLETED, QuestNodeType.FIX_BUG, 17L, payload)));

    service.complete(7L, 17L, new QuestCompleteRequest("fixed", 99L, null, null, null));

    verify(publisher, never()).publishEvent(any(LearningEvent.DebugResolved.class));
  }

  @Test
  void completeLosingTheConcurrentRaceConflictsInsteadOfDoubleRecording() {
    QuestDto quest = sampleQuest(QuestStatus.IN_PROGRESS, QuestNodeType.FIX_BUG, 16L);
    when(repository.findById(eq(7L), eq(16L))).thenReturn(Optional.of(quest));
    // 前置状态校验是无锁快照：并发请求已把任务推到 COMPLETED 时 UPDATE 命中 0 行。
    when(repository.updateStatus(7L, 16L, QuestStatus.COMPLETED)).thenReturn(0);

    assertThatThrownBy(() -> service.complete(7L, 16L, new QuestCompleteRequest("double-click")))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("QUEST_ALREADY_FINALIZED");

    // 重复的进度行、重复的事件、翻倍的掌握度都必须被挡住。
    verify(repository, never()).recordProgress(anyLong(), anyString(), any());
    verify(publisher, never()).publishEvent(any(LearningEvent.class));
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
    when(repository.updateStatus(7L, 13L, QuestStatus.SKIPPED)).thenReturn(1);
    when(repository.findOwned(eq(7L), eq(13L)))
        .thenReturn(
            Optional.of(sampleQuest(QuestStatus.SKIPPED, QuestNodeType.PROFILE_UPDATE, 13L)));
    QuestDto result = service.skip(7L, 13L, new QuestSkipRequest("用户主动跳过"));
    assertThat(result.status()).isEqualTo(QuestStatus.SKIPPED);
    verify(repository).recordProgress(13L, "SKIPPED", "用户主动跳过");
  }

  @Test
  void skipStopsWhenAnotherRequestAlreadyFinalizedQuest() {
    QuestDto quest = sampleQuest(QuestStatus.IN_PROGRESS, QuestNodeType.PROFILE_UPDATE, 13L);
    when(repository.findById(eq(7L), eq(13L))).thenReturn(Optional.of(quest));
    when(repository.updateStatus(7L, 13L, QuestStatus.SKIPPED)).thenReturn(0);

    assertThatThrownBy(() -> service.skip(7L, 13L, new QuestSkipRequest("用户主动跳过")))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).code())
        .isEqualTo("QUEST_ALREADY_FINALIZED");

    verify(repository, never()).recordProgress(anyLong(), anyString(), any());
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
        null,
        null,
        List.of(),
        null,
        null,
        mapper.createObjectNode(),
        OffsetDateTime.now(ZoneOffset.UTC),
        OffsetDateTime.now(ZoneOffset.UTC));
  }
}
