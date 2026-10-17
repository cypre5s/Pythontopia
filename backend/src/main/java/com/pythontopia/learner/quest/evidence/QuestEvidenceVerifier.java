package com.pythontopia.learner.quest.evidence;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.quest.dto.QuestCompleteRequest;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.workshop.repository.LearningResourceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 教育闭环关卡：在 QuestService.complete() 真正修改 quest_status 之前，按 quest.requiredEvidenceType
 * 校验证据来源，阻止"误点完成"。PATH_PLANNER 历史任务按节点类型推导证据，其它旧任务保留兼容路径。
 */
@Component
public class QuestEvidenceVerifier {

  static final String SUBMISSION = "submission";
  static final String RESOURCE = "resource";
  static final String PAGE_READ = "page_read";
  static final String KC_MASTERY = "kc_mastery";

  private final PracticeSubmissionRepository submissionRepository;
  private final LearningResourceRepository resourceRepository;
  private final JdbcTemplate jdbcTemplate;

  public QuestEvidenceVerifier(
      PracticeSubmissionRepository submissionRepository,
      LearningResourceRepository resourceRepository,
      JdbcTemplate jdbcTemplate) {
    this.submissionRepository = submissionRepository;
    this.resourceRepository = resourceRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  public void verify(Long userId, QuestDto quest, QuestCompleteRequest request) {
    String type = quest.requiredEvidenceType();
    if ((type == null || type.isBlank()) && quest.source() == QuestSource.PATH_PLANNER) {
      type = legacyPathEvidenceType(quest.nodeType());
    }
    if (type == null || type.isBlank()) {
      return;
    }
    if (request == null) {
      throw evidenceRequired(type);
    }
    switch (type) {
      case SUBMISSION -> verifySubmission(userId, quest, request);
      case RESOURCE -> verifyResource(userId, quest, request);
      case PAGE_READ -> verifyPageRead(userId, request);
      case KC_MASTERY -> verifyKcMastery(userId, quest, request);
      default ->
          throw new BusinessException(
              HttpStatus.INTERNAL_SERVER_ERROR,
              "QUEST_EVIDENCE_TYPE_UNKNOWN",
              "未识别的证据类型：" + type);
    }
  }

  private String legacyPathEvidenceType(QuestNodeType nodeType) {
    return switch (nodeType) {
      case READ_RESOURCE -> RESOURCE;
      case SOLVE_PROBLEM, FIX_BUG -> SUBMISSION;
      case PLANT_KC -> KC_MASTERY;
      case PROFILE_UPDATE ->
          throw new BusinessException(
              HttpStatus.CONFLICT,
              "QUEST_EVIDENCE_UNAVAILABLE",
              "此历史任务缺少可验证证据，请跳过后重新规划学习路径");
    };
  }

  private void verifySubmission(Long userId, QuestDto quest, QuestCompleteRequest request) {
    if (request.submissionId() == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_REQUIRED_SUBMISSION",
          "完成此任务需要附带一次已通过的练习提交");
    }
    SubmissionDto submission =
        submissionRepository
            .findSubmission(userId, request.submissionId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_SUBMISSION",
                        "提交不存在或不属于当前学生"));
    if (submission.status() != SubmissionStatus.PASSED) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_SUBMISSION",
          "需要使用一次状态为 PASSED 的提交作为证据");
    }
    List<Long> allow = quest.targetProblemIds();
    if (allow != null && !allow.isEmpty() && !allow.contains(submission.problemId())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "WRONG_PROBLEM",
          "提交对应的题目不在该任务的目标题集合内");
    }
    if (quest.source() == QuestSource.PATH_PLANNER
        && quest.kcCode() != null
        && !Objects.equals(
            quest.kcCode(),
            submissionRepository.findSubmissionKcCode(userId, request.submissionId()).orElse(null))) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "WRONG_KC", "提交对应的知识点与该学习路径任务不匹配");
    }
  }

  private void verifyResource(Long userId, QuestDto quest, QuestCompleteRequest request) {
    if (request.resourceId() == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_REQUIRED_RESOURCE",
          "完成此任务需要附带一次资源消费记录");
    }
    var resource =
        resourceRepository
        .findById(userId, request.resourceId())
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RESOURCE",
                    "资源不存在或不属于当前学生"));
    if (quest.source() == QuestSource.PATH_PLANNER
        && quest.kcCode() != null
        && (resource.kcCodes() == null || !resource.kcCodes().contains(quest.kcCode()))) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "WRONG_RESOURCE", "资源对应的知识点与该学习路径任务不匹配");
    }
    Long expected = quest.targetResourceId();
    if (expected != null && !expected.equals(request.resourceId())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "WRONG_RESOURCE",
          "资源不是该任务指定的目标资源");
    }
    Boolean consumedAfterQuest =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM learning_event_log"
                + " WHERE user_id = ? AND event_type = 'resource_consumed'"
                + " AND source IN ('backend', 'graph') AND payload->>'resourceId' = ?"
                + " AND created_at >= ?)",
            Boolean.class,
            userId,
            request.resourceId().toString(),
            quest.createdAt());
    if (!Boolean.TRUE.equals(consumedAfterQuest)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_RESOURCE",
          "资源尚未由当前学生消费：id=" + request.resourceId());
    }
  }

  private void verifyPageRead(Long userId, QuestCompleteRequest request) {
    if (request.pageReadId() == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_REQUIRED_PAGE_READ",
          "完成此任务需要附带一次已读页码");
    }
    Boolean readByStudent =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM course_page c WHERE c.id = ?"
                + " AND EXISTS (SELECT 1 FROM mainline_stage_progress p"
                + " WHERE p.user_id = ? AND ? = ANY(p.pages_read)))",
            Boolean.class,
            request.pageReadId(),
            userId,
            request.pageReadId());
    if (!Boolean.TRUE.equals(readByStudent)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_PAGE_READ",
          "页面不存在或当前学生尚未阅读：id=" + request.pageReadId());
    }
  }

  private void verifyKcMastery(Long userId, QuestDto quest, QuestCompleteRequest request) {
    if (request.kcMasteryDelta() == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_REQUIRED_MASTERY",
          "完成此任务需要附带 KC mastery 增量");
    }
    if (quest.kcCode() == null || quest.kcCode().isBlank() || quest.createdAt() == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_REQUIRED_MASTERY",
          "该任务缺少可验证的 KC 掌握度目标");
    }
    BigDecimal actual =
        jdbcTemplate.queryForObject(
            "SELECT CASE WHEN COUNT(*) FILTER (WHERE applied_delta IS NULL) = 0"
                + " THEN COALESCE(SUM(applied_delta), 0) END"
                + " FROM kc_mastery_event WHERE user_id = ? AND kc_code = ? AND created_at >= ?"
                + " AND source IN ('PRACTICE_PASS', 'PRACTICE_FAIL', 'MICRO_CHECK_PASS',"
                + " 'MICRO_CHECK_FAIL', 'DEBUG_FIX')",
            BigDecimal.class,
            userId,
            quest.kcCode(),
            quest.createdAt());
    BigDecimal required = quest.requiredMasteryDelta();
    if (required == null) {
      required = BigDecimal.ZERO;
    }
    if (actual == null || actual.signum() <= 0 || actual.compareTo(required) < 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_REQUIRED_MASTERY",
          "服务端记录的 mastery 增量 " + actual + " 未达到任务要求的 " + required);
    }
  }

  private BusinessException evidenceRequired(String type) {
    return switch (type) {
      case SUBMISSION ->
          new BusinessException(
              HttpStatus.BAD_REQUEST,
              "EVIDENCE_REQUIRED_SUBMISSION",
              "完成此任务需要附带一次已通过的练习提交");
      case RESOURCE ->
          new BusinessException(
              HttpStatus.BAD_REQUEST,
              "EVIDENCE_REQUIRED_RESOURCE",
              "完成此任务需要附带一次资源消费记录");
      case PAGE_READ ->
          new BusinessException(
              HttpStatus.BAD_REQUEST,
              "EVIDENCE_REQUIRED_PAGE_READ",
              "完成此任务需要附带一次已读页码");
      case KC_MASTERY ->
          new BusinessException(
              HttpStatus.BAD_REQUEST,
              "EVIDENCE_REQUIRED_MASTERY",
              "完成此任务需要附带 KC mastery 增量");
      default ->
          new BusinessException(
              HttpStatus.BAD_REQUEST,
              "EVIDENCE_REQUIRED",
              "完成此任务需要附带 " + type + " 类型证据");
    };
  }
}
