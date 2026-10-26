package com.pythontopia.submission.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.quest.dto.QuestCompleteRequest;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.learner.quest.service.QuestService;
import com.pythontopia.submission.dto.PublicProblemDto;
import com.pythontopia.submission.dto.SubmissionCreateRequest;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.judge.JudgeService;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionTransactionService {

  private static final Logger log = LoggerFactory.getLogger(SubmissionTransactionService.class);
  private static final int RATE_LIMIT_SECONDS = 10;

  private final PracticeSubmissionRepository submissionRepository;
  private final PublicProblemRepository problemRepository;
  private final QuestService questService;
  private final ApplicationEventPublisher eventPublisher;

  public SubmissionTransactionService(
      PracticeSubmissionRepository submissionRepository,
      PublicProblemRepository problemRepository,
      QuestService questService,
      ApplicationEventPublisher eventPublisher) {
    this.submissionRepository = submissionRepository;
    this.problemRepository = problemRepository;
    this.questService = questService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Long create(Long userId, SubmissionCreateRequest request) {
    // Keep the per-user rate check atomic with insertion; this transaction ends before judging.
    submissionRepository.acquireUserSubmitLock(userId);
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(RATE_LIMIT_SECONDS);
    if (submissionRepository.countSubmittedAfter(userId, since) >= 1) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "SUBMISSION_THROTTLED",
          "请稍候，" + RATE_LIMIT_SECONDS + " 秒内只能正式提交一次");
    }
    problemRepository
        .findAccessible(43L, request.problemId(), userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在或不属于当前课程"));
    SubmissionSource source =
        request.source() == null ? SubmissionSource.TRAINING : request.source();
    return submissionRepository.insertSubmission(
        userId, request.problemId(), request.questId(), source, request.code());
  }

  @Transactional
  public void markRunning(Long submissionId) {
    submissionRepository.markStatus(submissionId, SubmissionStatus.RUNNING, null, null);
  }

  @Transactional
  public void finishJudge(
      Long submissionId,
      Verdict verdict,
      int passedCases,
      int totalCases,
      String failureReason,
      JsonNode trace,
      Integer runtimeMs,
      Integer memoryKb) {
    submissionRepository.insertJudgeResult(
        submissionId, verdict, passedCases, totalCases, failureReason, trace);
    submissionRepository.markStatus(
        submissionId, statusFor(verdict), runtimeMs, memoryKb);
  }

  @Transactional
  public void discardUnjudged(Long submissionId) {
    submissionRepository.deleteUnjudgedSubmission(submissionId);
  }

  @Transactional
  public SubmissionDto complete(
      Long userId,
      Long submissionId,
      SubmissionCreateRequest request,
      JudgeService.JudgeOutcome outcome) {
    handleCompletion(userId, submissionId, request, outcome);
    return submissionRepository
        .findById(submissionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "SUBMISSION_PERSIST_FAILED", "提交写入后未能读回"));
  }

  private void handleCompletion(
      Long userId, Long submissionId, SubmissionCreateRequest request, JudgeService.JudgeOutcome outcome) {
    if (outcome.verdict() == Verdict.SYSTEM_ERROR) {
      // 判题系统故障不是学生答错，不能污染掌握度或画像。
      log.error("Submission {} ended in SYSTEM_ERROR; no learning event published.", submissionId);
      return;
    }
    String kcCode = problemRepository.findKcCode(request.problemId());
    if (kcCode == null || kcCode.isBlank()) {
      log.warn("Submission {} has no kc_code for problem {}", submissionId, request.problemId());
      return;
    }
    String difficulty =
        problemRepository
            .findAccessible(43L, request.problemId(), userId)
            .map(PublicProblemDto::difficulty)
            .orElse(null);
    int priorFails =
        submissionRepository.countPriorFailures(userId, request.problemId(), submissionId);
    if (outcome.verdict() == Verdict.AC) {
      eventPublisher.publishEvent(
          new LearningEvent.PracticeSucceeded(
              userId,
              kcCode,
              request.problemId(),
              difficulty,
              submissionId,
              null,
              request.tutorWorkflowSessionKey()));
      if (priorFails >= 2) {
        eventPublisher.publishEvent(
            new LearningEvent.ComebackSolved(userId, request.problemId(), kcCode, priorFails));
      }
      if (request.questId() != null) {
        try {
          QuestCompleteRequest autoEvidence =
              new QuestCompleteRequest("AC@submission:" + submissionId, submissionId, null, null, null);
          QuestDto quest = questService.complete(userId, request.questId(), autoEvidence);
          log.info("Quest {} auto-completed via submission {}", quest.id(), submissionId);
        } catch (BusinessException exception) {
          log.warn("Quest auto-completion failed after submission errorCode={}", exception.code());
        }
      }
    } else {
      eventPublisher.publishEvent(
          new LearningEvent.PracticeFailed(
              userId,
              kcCode,
              request.problemId(),
              outcome.verdict().name(),
              difficulty,
              submissionId,
              null,
              request.tutorWorkflowSessionKey()));
      if (priorFails >= 1) {
        eventPublisher.publishEvent(
            new LearningEvent.PracticeRetried(userId, request.problemId(), kcCode));
      }
    }
  }

  private static SubmissionStatus statusFor(Verdict verdict) {
    return switch (verdict) {
      case AC -> SubmissionStatus.PASSED;
      case SYSTEM_ERROR -> SubmissionStatus.ERROR;
      default -> SubmissionStatus.FAILED;
    };
  }
}
