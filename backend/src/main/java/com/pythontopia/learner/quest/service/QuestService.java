package com.pythontopia.learner.quest.service;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.quest.dto.QuestCompleteRequest;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.learner.quest.dto.QuestSkipRequest;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.learner.quest.dto.QuestStatus;
import com.pythontopia.learner.quest.evidence.QuestEvidenceVerifier;
import com.pythontopia.learner.quest.repository.QuestRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestService {

  private static final EnumSet<QuestStatus> STARTABLE = EnumSet.of(QuestStatus.OPEN);
  private static final EnumSet<QuestStatus> COMPLETABLE =
      EnumSet.of(QuestStatus.OPEN, QuestStatus.IN_PROGRESS);
  private static final EnumSet<QuestStatus> SKIPPABLE =
      EnumSet.of(QuestStatus.OPEN, QuestStatus.IN_PROGRESS);

  private final QuestRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final QuestEvidenceVerifier evidenceVerifier;

  public QuestService(
      QuestRepository repository,
      ApplicationEventPublisher eventPublisher,
      QuestEvidenceVerifier evidenceVerifier) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.evidenceVerifier = evidenceVerifier;
  }

  @Transactional(readOnly = true)
  public List<QuestDto> today(Long userId) {
    return repository.findTodayQuests(userId, LocalDate.now(ZoneOffset.UTC));
  }

  @Transactional(readOnly = true)
  public List<QuestDto> list(Long userId, QuestStatus status, QuestSource source, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(100, limit));
    int safeOffset = Math.max(0, offset);
    return repository.list(userId, status, source, safeLimit, safeOffset);
  }

  @Transactional
  public QuestDto start(Long userId, Long questId) {
    QuestDto quest = requireOwned(userId, questId);
    if (!STARTABLE.contains(quest.status())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "QUEST_INVALID_STATE",
          "任务当前状态 " + quest.status() + " 无法开始");
    }
    int affected = repository.updateStatus(userId, questId, QuestStatus.IN_PROGRESS);
    if (affected == 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "QUEST_STATE_CHANGED", "任务状态已被其他请求更新，请刷新后查看");
    }
    repository.recordProgress(questId, "STARTED", null);
    return refresh(userId, questId);
  }

  @Transactional
  public QuestDto complete(Long userId, Long questId, QuestCompleteRequest request) {
    QuestDto quest = requireOwned(userId, questId);
    if (!COMPLETABLE.contains(quest.status())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "QUEST_INVALID_STATE",
          "任务当前状态 " + quest.status() + " 无法完成");
    }
    evidenceVerifier.verify(userId, quest, request);
    // 上面的状态校验是无锁快照，真正的并发守卫在 UPDATE 的 WHERE status IN (...) 上：
    // 命中 0 行说明任务已被并发请求（双击 / 客户端重试）终结，必须在此中止，
    // 否则会重复写 COMPLETED 进度、重复发事件、把掌握度记两次。
    int affected = repository.updateStatus(userId, questId, QuestStatus.COMPLETED);
    if (affected == 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "QUEST_ALREADY_FINALIZED", "任务已被其他请求完成，请刷新后查看");
    }
    repository.recordProgress(questId, "COMPLETED", request == null ? null : request.note());
    publishCompletionEvent(quest, request);
    return refresh(userId, questId);
  }

  @Transactional
  public QuestDto skip(Long userId, Long questId, QuestSkipRequest request) {
    QuestDto quest = requireOwned(userId, questId);
    if (!SKIPPABLE.contains(quest.status())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "QUEST_INVALID_STATE",
          "任务当前状态 " + quest.status() + " 无法跳过");
    }
    int affected = repository.updateStatus(userId, questId, QuestStatus.SKIPPED);
    if (affected == 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "QUEST_ALREADY_FINALIZED", "任务已被其他请求完成，请刷新后查看");
    }
    repository.recordProgress(questId, "SKIPPED", request == null ? null : request.reason());
    return refresh(userId, questId);
  }

  @Transactional
  public int expireOverdue() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    int count = repository.expireOverdue(today);
    return count;
  }

  private QuestDto refresh(Long userId, Long questId) {
    return repository
        .findOwned(userId, questId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "QUEST_NOT_FOUND", "任务不存在或不属于当前用户"));
  }

  private QuestDto requireOwned(Long userId, Long questId) {
    return repository
        .findById(userId, questId)
        .map(quest -> {
          if (!quest.userId().equals(userId)) {
            throw new BusinessException(
                HttpStatus.FORBIDDEN, "QUEST_FORBIDDEN", "无权访问他人任务");
          }
          return quest;
        })
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "QUEST_NOT_FOUND", "任务不存在"));
  }

  private void publishCompletionEvent(QuestDto quest, QuestCompleteRequest request) {
    if (quest.kcCode() == null || quest.kcCode().isBlank()) {
      return;
    }
    switch (quest.nodeType()) {
      case READ_RESOURCE -> eventPublisher.publishEvent(
          new LearningEvent.ResourceConsumed(quest.userId(), quest.kcCode(), "quest"));
      case SOLVE_PROBLEM -> {
        // 刻意不发 PracticeSucceeded：SOLVE_PROBLEM 任务的完成证据就是一次 AC 提交，
        // 而判题链路（SubmissionService.handleCompletion）在 AC 时已经发过一次同样的事件，
        // 随后才回调本方法完成任务。这里再发一次会让 MasteryEvaluator 对同一次 AC 记两遍掌握度
        // （它不按 problemId 去重），也会把 practical_ability 维度的证据数灌成双倍。
      }
      case FIX_BUG -> {
        // A verified submission publishes its own practice event; BugMine publishes the single
        // DebugResolved event when its session transitions to RESOLVED.
        if (request == null || request.submissionId() == null) {
          eventPublisher.publishEvent(
              new LearningEvent.DebugResolved(
                  quest.userId(),
                  quest.kcCode(),
                  extractProblemId(quest),
                  "quest:" + quest.id()));
        }
      }
      case PLANT_KC, PROFILE_UPDATE -> {
        // PLANT_KC 与 PROFILE_UPDATE 不直接驱动 mastery 加分，留待复盘大厅处理。
      }
    }
  }

  private Long extractProblemId(QuestDto quest) {
    if (quest.payload() != null && quest.payload().hasNonNull("suggestedProblemId")) {
      return quest.payload().get("suggestedProblemId").asLong();
    }
    return 0L;
  }
}
