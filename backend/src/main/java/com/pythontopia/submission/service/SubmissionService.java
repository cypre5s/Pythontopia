package com.pythontopia.submission.service;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.quest.dto.QuestCompleteRequest;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.learner.quest.service.QuestService;
import com.pythontopia.submission.dto.JudgeResultDto;
import com.pythontopia.submission.dto.PublicProblemDto;
import com.pythontopia.submission.dto.SubmissionCreateRequest;
import com.pythontopia.submission.dto.SubmissionDetailDto;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.judge.JudgeService;
import com.pythontopia.submission.judge.JudgeService.JudgeOutcome;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {

  private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);
  private static final int RATE_LIMIT_SECONDS = 10;

  private final PracticeSubmissionRepository submissionRepository;
  private final PublicProblemRepository problemRepository;
  private final JudgeService judgeService;
  private final QuestService questService;
  private final ApplicationEventPublisher eventPublisher;

  public SubmissionService(
      PracticeSubmissionRepository submissionRepository,
      PublicProblemRepository problemRepository,
      JudgeService judgeService,
      QuestService questService,
      ApplicationEventPublisher eventPublisher) {
    this.submissionRepository = submissionRepository;
    this.problemRepository = problemRepository;
    this.judgeService = judgeService;
    this.questService = questService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public SubmissionDto submit(Long userId, SubmissionCreateRequest request) {
    if (request.code() == null || request.code().isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "SUBMISSION_CODE_EMPTY", "代码不能为空");
    }
    // 先取每用户事务咨询锁，让同一 userId 的并发提交串行化；之后的「count + insert」整体原子，
    // 消除限频 TOCTOU 竞态。锁在本事务提交/回滚时自动释放。
    submissionRepository.acquireUserSubmitLock(userId);
    enforceRateLimit(userId);
    problemRepository
        .findPublic(43L, request.problemId())
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在或不属于当前课程"));
    SubmissionSource source = request.source() == null ? SubmissionSource.TRAINING : request.source();
    Long submissionId =
        submissionRepository.insertSubmission(
            userId, request.problemId(), request.questId(), source, request.code());
    JudgeOutcome outcome = judgeService.judge(submissionId, request.problemId(), request.code());
    handleCompletion(userId, submissionId, request, outcome);
    return submissionRepository
        .findById(submissionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SUBMISSION_PERSIST_FAILED",
                    "提交写入后未能读回"));
  }

  @Transactional(readOnly = true)
  public SubmissionDetailDto findOne(Long userId, Long submissionId) {
    SubmissionDto submission =
        submissionRepository
            .findSubmission(userId, submissionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "提交不存在"));
    JudgeResultDto judgeResult = submissionRepository.findJudgeResult(submissionId).orElse(null);
    return new SubmissionDetailDto(submission, judgeResult);
  }

  @Transactional(readOnly = true)
  public List<SubmissionDto> list(
      Long userId, Long problemId, SubmissionStatus status, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(100, limit));
    int safeOffset = Math.max(0, offset);
    return submissionRepository.list(userId, problemId, status, safeLimit, safeOffset);
  }

  private void enforceRateLimit(Long userId) {
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(RATE_LIMIT_SECONDS);
    int recent = submissionRepository.countSubmittedAfter(userId, since);
    if (recent >= 1) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "SUBMISSION_THROTTLED",
          "请稍候，" + RATE_LIMIT_SECONDS + " 秒内只能正式提交一次");
    }
  }

  private void handleCompletion(
      Long userId, Long submissionId, SubmissionCreateRequest request, JudgeOutcome outcome) {
    String kcCode = problemRepository.findKcCode(request.problemId());
    if (kcCode == null || kcCode.isBlank()) {
      log.warn("Submission {} has no kc_code for problem {}", submissionId, request.problemId());
      return;
    }
    String difficulty =
        problemRepository.findPublic(43L, request.problemId()).map(PublicProblemDto::difficulty).orElse(null);
    // 本次提交前同题失败次数，用于判定重试 / 逆袭行为信号。
    int priorFails = submissionRepository.countPriorFailures(userId, request.problemId(), submissionId);
    if (outcome.verdict() == Verdict.AC) {
      eventPublisher.publishEvent(
          new LearningEvent.PracticeSucceeded(userId, kcCode, request.problemId(), difficulty));
      if (priorFails >= 2) {
        eventPublisher.publishEvent(
            new LearningEvent.ComebackSolved(userId, request.problemId(), kcCode, priorFails));
      }
      if (request.questId() != null) {
        try {
          QuestCompleteRequest autoEvidence =
              new QuestCompleteRequest(
                  "AC@submission:" + submissionId, submissionId, null, null, null);
          QuestDto quest = questService.complete(userId, request.questId(), autoEvidence);
          log.info("Quest {} auto-completed via submission {}", quest.id(), submissionId);
        } catch (BusinessException exception) {
          log.warn(
              "Quest {} could not be completed via submission {}: {}",
              request.questId(),
              submissionId,
              exception.getMessage());
        }
      }
    } else {
      eventPublisher.publishEvent(
          new LearningEvent.PracticeFailed(
              userId, kcCode, request.problemId(), outcome.verdict().name(), difficulty));
      if (priorFails >= 1) {
        eventPublisher.publishEvent(
            new LearningEvent.PracticeRetried(userId, request.problemId(), kcCode));
      }
    }
  }
}
