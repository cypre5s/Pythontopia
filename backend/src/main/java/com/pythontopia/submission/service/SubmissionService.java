package com.pythontopia.submission.service;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.submission.dto.JudgeResultDto;
import com.pythontopia.submission.dto.SubmissionCreateRequest;
import com.pythontopia.submission.dto.SubmissionDetailDto;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionPerformanceDto;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.judge.JudgeService;
import com.pythontopia.submission.judge.JudgeService.JudgeOutcome;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {

  private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

  private final PracticeSubmissionRepository submissionRepository;
  private final JudgeService judgeService;
  private final SubmissionTransactionService submissionTransactions;

  public SubmissionService(
      PracticeSubmissionRepository submissionRepository,
      JudgeService judgeService,
      SubmissionTransactionService submissionTransactions) {
    this.submissionRepository = submissionRepository;
    this.judgeService = judgeService;
    this.submissionTransactions = submissionTransactions;
  }

  public SubmissionDto submit(Long userId, SubmissionCreateRequest request) {
    if (request.code() == null || request.code().isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_CODE_EMPTY", "代码不能为空");
    }
    Long submissionId = submissionTransactions.create(userId, request);
    JudgeOutcome outcome;
    try {
      outcome = judgeService.judge(submissionId, request.problemId(), request.code(), userId);
    } catch (RuntimeException exception) {
      try {
        submissionTransactions.discardUnjudged(submissionId);
      } catch (RuntimeException cleanupException) {
        log.error(
            "Failed to discard unjudged submission errorType={}",
            cleanupException.getClass().getSimpleName());
      }
      throw exception;
    }
    return submissionTransactions.complete(userId, submissionId, request, outcome);
  }

  @Transactional(readOnly = true)
  public SubmissionDetailDto findOne(Long userId, Long submissionId) {
    SubmissionDto submission =
        submissionRepository
            .findSubmission(userId, submissionId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "提交不存在"));
    JudgeResultDto judgeResult = submissionRepository.findJudgeResult(submissionId).orElse(null);
    return new SubmissionDetailDto(submission, judgeResult);
  }

  @Transactional(readOnly = true)
  public SubmissionPerformanceDto performance(Long userId, Long submissionId) {
    SubmissionDto submission =
        submissionRepository
            .findSubmission(userId, submissionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "提交不存在"));
    if (submission.status() != SubmissionStatus.PASSED) {
      return SubmissionPerformanceDto.hidden();
    }
    var samples =
        submissionRepository.findPerformanceSamples(
            submission.problemId(),
            submission.language(),
            submission.runtimeMs(),
            submission.memoryKb());
    boolean showRuntime = submission.runtimeMs() != null && samples.runtimeSamples() >= 10;
    boolean showMemory = submission.memoryKb() != null && samples.memorySamples() >= 10;
    return new SubmissionPerformanceDto(
        showRuntime ? beatsPercent(samples.runtimeBeaten(), samples.runtimeSamples()) : null,
        showRuntime ? samples.runtimeSamples() : null,
        showMemory ? beatsPercent(samples.memoryBeaten(), samples.memorySamples()) : null,
        showMemory ? samples.memorySamples() : null);
  }

  private double beatsPercent(int beaten, int samples) {
    return Math.round(1000.0 * beaten / samples) / 10.0;
  }

  @Transactional(readOnly = true)
  public List<SubmissionDto> list(
      Long userId, Long problemId, SubmissionStatus status, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(100, limit));
    int safeOffset = Math.max(0, offset);
    return submissionRepository.list(userId, problemId, status, safeLimit, safeOffset);
  }

}
