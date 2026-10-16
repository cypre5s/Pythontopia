package com.pythontopia.learner.path.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.classroom.quest.dto.ClassroomQuestDto;
import com.pythontopia.classroom.quest.repository.ClassroomQuestRepository;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.path.dto.LearningPathDto;
import com.pythontopia.learner.path.dto.LearningPathNodeDto;
import com.pythontopia.learner.path.dto.PathNodeType;
import com.pythontopia.learner.path.dto.PlanPathResponse;
import com.pythontopia.learner.path.dto.SuggestedResourceType;
import com.pythontopia.learner.path.graph.PathPlannerGraphClient;
import com.pythontopia.learner.path.graph.PathPlannerGraphRequest;
import com.pythontopia.learner.path.graph.PathPlannerGraphResponse;
import com.pythontopia.learner.path.repository.LearningPathRepository;
import com.pythontopia.learner.path.repository.LearningPathRepository.LearningPathNodeDraft;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.service.ProfileService;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.learner.quest.dto.QuestStatus;
import com.pythontopia.learner.quest.repository.QuestRepository;
import com.pythontopia.learner.quest.repository.QuestRepository.QuestInsert;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import com.pythontopia.workshop.dto.ResourceListItemDto;
import com.pythontopia.workshop.repository.LearningResourceRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningPathService {

  static final Long DEFAULT_PACK_ID = 43L;
  static final int ARCHIVED_HISTORY_LIMIT = 10;

  private static final Logger log = LoggerFactory.getLogger(LearningPathService.class);

  /** graph PlannerProfileSnapshot.major 仅接受这四个枚举值；自由文本 major 必须归一，否则 graph 422。 */
  private static final Set<String> GRAPH_MAJOR_VALUES =
      Set.of("CS", "NON_CS_STEM", "NON_CS_HUMANITIES", "OTHER");

  /** 反思洞察扫描条数，用于提取 error_pattern 高频坑 KC。 */
  static final int INSIGHT_SCAN_LIMIT = 20;

  private final LearningPathRepository pathRepository;
  private final QuestRepository questRepository;
  private final ClassroomQuestRepository classroomQuestRepository;
  private final CoursePackRepository coursePackRepository;
  private final ProfileService profileService;
  private final MasteryEvaluator masteryEvaluator;
  private final PathPlannerGraphClient graphClient;
  private final ObjectMapper objectMapper;
  private final PracticeSubmissionRepository submissionRepository;
  private final LearningResourceRepository learningResourceRepository;
  private final LearnerInsightRepository insightRepository;

  static final int RECENT_SUBMISSION_LIMIT = 30;
  static final int RECENT_RESOURCE_LIMIT = 30;

  public LearningPathService(
      LearningPathRepository pathRepository,
      QuestRepository questRepository,
      ClassroomQuestRepository classroomQuestRepository,
      CoursePackRepository coursePackRepository,
      ProfileService profileService,
      MasteryEvaluator masteryEvaluator,
      PathPlannerGraphClient graphClient,
      ObjectMapper objectMapper,
      PracticeSubmissionRepository submissionRepository,
      LearningResourceRepository learningResourceRepository,
      LearnerInsightRepository insightRepository) {
    this.pathRepository = pathRepository;
    this.questRepository = questRepository;
    this.classroomQuestRepository = classroomQuestRepository;
    this.coursePackRepository = coursePackRepository;
    this.profileService = profileService;
    this.masteryEvaluator = masteryEvaluator;
    this.graphClient = graphClient;
    this.objectMapper = objectMapper;
    this.submissionRepository = submissionRepository;
    this.learningResourceRepository = learningResourceRepository;
    this.insightRepository = insightRepository;
  }

  @Transactional
  public PlanPathResponse plan(Long userId) {
    PathPlannerGraphResponse response = invokePlanner(userId);

    List<Long> archivedIds = pathRepository.archiveActivePaths(userId, DEFAULT_PACK_ID);
    for (Long archivedId : archivedIds) {
      questRepository.archiveQuestsForPath(userId, archivedId, QuestStatus.EXPIRED);
    }
    questRepository.archiveSystemDefaults(userId);

    int version = pathRepository.nextVersion(userId, DEFAULT_PACK_ID);
    OffsetDateTime nextReviewAt = OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofDays(7));
    Long pathId =
        pathRepository.insertPath(
            userId, DEFAULT_PACK_ID, version, response.goalSummary(), nextReviewAt, null);

    int generatedQuestCount = writeNodesAndQuests(userId, pathId, response, true);

    LearningPathDto stored =
        pathRepository
            .findById(userId, pathId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "PATH_PERSIST_FAILED",
                        "新路径写入后未能读回"));
    return new PlanPathResponse(
        stored, archivedIds, generatedQuestCount, response.status(), "OK");
  }

  /**
   * 生成 DRAFT 路径：不归档当前 ACTIVE，也不发布 quest。
   * 学生在复盘大厅点击"接受新路径"后，须经 {@link #promoteDraft(Long, Long)} 才会真正激活。
   */
  @Transactional
  public LearningPathDto planDraft(Long userId) {
    PathPlannerGraphResponse response = invokePlanner(userId);
    int version = pathRepository.nextVersion(userId, DEFAULT_PACK_ID);
    OffsetDateTime nextReviewAt = OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofDays(7));
    Long pathId =
        pathRepository.insertDraftPath(
            userId, DEFAULT_PACK_ID, version, response.goalSummary(), nextReviewAt, null);
    writeNodesAndQuests(userId, pathId, response, false);
    return pathRepository
        .findById(userId, pathId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PATH_DRAFT_PERSIST_FAILED",
                    "DRAFT 路径写入后未能读回"));
  }

  /** 将 DRAFT 提升为 ACTIVE：归档当前 ACTIVE，并按 DRAFT 节点重新生成 quest。 */
  @Transactional
  public LearningPathDto promoteDraft(Long userId, Long draftPathId) {
    LearningPathDto draft =
        pathRepository
            .findById(userId, draftPathId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "PATH_NOT_FOUND", "未找到该学习路径"));
    if (draft.status() != com.pythontopia.learner.path.dto.LearningPathStatus.DRAFT) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "PATH_NOT_DRAFT", "目标路径不是 DRAFT 状态，无法激活");
    }

    List<Long> archivedIds = pathRepository.archiveActivePaths(userId, DEFAULT_PACK_ID);
    for (Long archivedId : archivedIds) {
      questRepository.archiveQuestsForPath(userId, archivedId, QuestStatus.EXPIRED);
    }
    questRepository.archiveSystemDefaults(userId);

    pathRepository.updateStatus(
        draftPathId, com.pythontopia.learner.path.dto.LearningPathStatus.ACTIVE);

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (draft.nodes() != null) {
      for (LearningPathNodeDto node : draft.nodes()) {
        QuestNodeType questNodeType = QuestNodeType.valueOf(node.nodeType().name());
        QuestInsert insert =
            new QuestInsert(
                userId,
                QuestSource.PATH_PLANNER,
                node.id(),
                questTitleFromDto(node),
                node.reasonSummary(),
                node.kcCode(),
                questNodeType,
                payloadFromDto(node),
                today.plusDays(Math.max(0, node.dayIndex() - 1)),
                questPriority(node.dayIndex(), node.sourceClassroomQuestId()),
                null,
                null,
                List.of(),
                null,
                deepLinkRouteFor(questNodeType),
                deepLinkParamsFor(
                    questNodeType, node.kcCode(), node.suggestedProblemId()));
        questRepository.insert(insert);
      }
    }
    return pathRepository
        .findById(userId, draftPathId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PATH_ACTIVATE_FAILED",
                    "DRAFT 路径激活后未能读回"));
  }

  /** 学生放弃 DRAFT 路径：标记为 ARCHIVED，当前 ACTIVE 不受影响。 */
  @Transactional
  public LearningPathDto archiveDraft(Long userId, Long draftPathId) {
    LearningPathDto draft =
        pathRepository
            .findById(userId, draftPathId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "PATH_NOT_FOUND", "未找到该学习路径"));
    if (draft.status() != com.pythontopia.learner.path.dto.LearningPathStatus.DRAFT) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "PATH_NOT_DRAFT", "目标路径不是 DRAFT 状态");
    }
    pathRepository.updateStatus(
        draftPathId, com.pythontopia.learner.path.dto.LearningPathStatus.ARCHIVED);
    return pathRepository
        .findById(userId, draftPathId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PATH_DRAFT_ARCHIVE_FAILED",
                    "DRAFT 归档后未能读回"));
  }

  private PathPlannerGraphResponse invokePlanner(Long userId) {
    LearnerProfileDto profile =
        profileService
            .findProfile(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT, "PROFILE_REQUIRED", "请先完成画像访谈再规划学习路径"));

    List<CourseKc> kcs = coursePackRepository.findKcs(DEFAULT_PACK_ID, null);
    Map<String, List<String>> prerequisiteIndex =
        coursePackRepository.findKcPrerequisites(DEFAULT_PACK_ID);
    List<MasterySnapshot> snapshots = masteryEvaluator.snapshots(userId);
    Map<String, MasterySnapshot> snapshotIndex = new java.util.HashMap<>();
    for (MasterySnapshot snapshot : snapshots) {
      snapshotIndex.put(snapshot.kcCode(), snapshot);
    }
    List<ClassroomQuestDto> activeClassroomQuests =
        classroomQuestRepository.listActiveForStudent(userId, LocalDate.now(ZoneOffset.UTC));

    PathPlannerGraphRequest graphRequest =
        new PathPlannerGraphRequest(
            userId,
            DEFAULT_PACK_ID,
            buildProfileSnapshot(profile),
            buildKcSnapshots(kcs, snapshotIndex, prerequisiteIndex),
            buildRecentResources(userId),
            buildRecentSubmissions(userId),
            buildClassroomQuests(activeClassroomQuests),
            buildErrorPatternKcs(userId, kcs));

    PathPlannerGraphResponse response = graphClient.plan(graphRequest);
    if (response == null) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "PATH_GRAPH_EMPTY", "路径规划服务返回空响应");
    }
    if (!"SUCCESS".equals(response.status())) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "PATH_PLANNING_FAILED",
          response.status() + ":" + extractReason(response));
    }
    if (response.nodes() == null || response.nodes().isEmpty()) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_ENTITY, "PATH_EMPTY_NODES", "规划结果不包含任何节点");
    }
    return response;
  }

  private int writeNodesAndQuests(
      Long userId, Long pathId, PathPlannerGraphResponse response, boolean writeQuests) {
    int generatedQuestCount = 0;
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (PathPlannerGraphResponse.NodeDraft draft : response.nodes()) {
      LearningPathNodeDraft nodeDraft =
          new LearningPathNodeDraft(
              draft.dayIndex(),
              draft.orderInDay(),
              draft.kcCode(),
              PathNodeType.valueOf(draft.nodeType()),
              draft.reasonSummary(),
              draft.evidenceSummary(),
              draft.suggestedResourceType() == null
                  ? null
                  : SuggestedResourceType.valueOf(draft.suggestedResourceType()),
              draft.suggestedProblemId(),
              draft.sourceClassroomQuestId());
      Long nodeId = pathRepository.insertNode(pathId, nodeDraft);
      if (!writeQuests) {
        continue;
      }
      QuestNodeType questNodeType = mapToQuestNodeType(nodeDraft.nodeType());
      QuestInsert insert =
          new QuestInsert(
              userId,
              QuestSource.PATH_PLANNER,
              nodeId,
              questTitle(nodeDraft),
              draft.reasonSummary(),
              draft.kcCode(),
              questNodeType,
              questPayload(nodeDraft),
              today.plusDays(Math.max(0, draft.dayIndex() - 1)),
              questPriority(draft.dayIndex(), nodeDraft.sourceClassroomQuestId()),
              null,
              null,
              List.of(),
              null,
              deepLinkRouteFor(questNodeType),
              deepLinkParamsFor(
                  questNodeType, nodeDraft.kcCode(), nodeDraft.suggestedProblemId()));
      questRepository.insert(insert);
      generatedQuestCount += 1;
    }
    return generatedQuestCount;
  }

  private String deepLinkRouteFor(QuestNodeType type) {
    return switch (type) {
      case SOLVE_PROBLEM -> "/training";
      case READ_RESOURCE -> "/workshop";
      case FIX_BUG -> "/world";
      case PLANT_KC -> "/mainline-map";
      case PROFILE_UPDATE -> "/profile-room";
    };
  }

  private ObjectNode deepLinkParamsFor(QuestNodeType type, String kcCode, Long problemId) {
    ObjectNode params = objectMapper.createObjectNode();
    switch (type) {
      case SOLVE_PROBLEM -> {
        if (problemId != null) {
          params.put("problemId", problemId);
        }
      }
      case READ_RESOURCE -> {
        if (kcCode != null) {
          params.put("kcCode", kcCode);
        }
      }
      case FIX_BUG -> params.put("goto", "town_hub"); // R-14：mine_south 已删，停小镇经 building → /bug-mine
      case PLANT_KC -> { }
      case PROFILE_UPDATE -> {
        // 画像屋无附加参数
      }
    }
    return params;
  }

  private String questTitleFromDto(LearningPathNodeDto node) {
    return switch (node.nodeType()) {
      case READ_RESOURCE -> "阅读 " + node.kcCode() + " 资源";
      case SOLVE_PROBLEM -> "完成 " + node.kcCode() + " 练习";
      case FIX_BUG -> "矿洞修复 " + node.kcCode();
      case PLANT_KC -> "前往主线解锁 KC " + node.kcCode();
      case PROFILE_UPDATE -> "更新学习画像";
    };
  }

  private Object payloadFromDto(LearningPathNodeDto node) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("dayIndex", node.dayIndex());
    payload.put("orderInDay", node.orderInDay());
    if (node.suggestedResourceType() != null) {
      payload.put("suggestedResourceType", node.suggestedResourceType().name());
    }
    if (node.suggestedProblemId() != null) {
      payload.put("suggestedProblemId", node.suggestedProblemId());
    }
    if (node.sourceClassroomQuestId() != null) {
      payload.put("sourceClassroomQuestId", node.sourceClassroomQuestId());
    }
    return payload;
  }

  @Transactional(readOnly = true)
  public Optional<LearningPathDto> findActive(Long userId) {
    return pathRepository.findActive(userId, DEFAULT_PACK_ID);
  }

  @Transactional(readOnly = true)
  public LearningPathDto findById(Long userId, Long pathId) {
    return pathRepository
        .findById(userId, pathId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "PATH_NOT_FOUND", "未找到该学习路径"));
  }

  @Transactional(readOnly = true)
  public List<LearningPathDto> listArchived(Long userId) {
    return pathRepository.listArchived(userId, DEFAULT_PACK_ID, ARCHIVED_HISTORY_LIMIT);
  }

  private PathPlannerGraphRequest.ProfileSnapshot buildProfileSnapshot(LearnerProfileDto profile) {
    if (profile == null) {
      return new PathPlannerGraphRequest.ProfileSnapshot(false, "OTHER", 2, 999);
    }
    int pythonLevel = 2;
    if (profile.baseLevel() != null && profile.baseLevel().pythonSelfRatedLevel() != null) {
      pythonLevel = profile.baseLevel().pythonSelfRatedLevel();
    }
    // 末位恒传 7：让 planner 将画像视为已满 7 天，从而触发 PROFILE_UPDATE 节点。
    return new PathPlannerGraphRequest.ProfileSnapshot(
        true, graphMajor(profile.major()), pythonLevel, 7);
  }

  /**
   * 把任意 major 文本归一到 graph {@code PlannerProfileSnapshot.major} 接受的四枚举值，避免自由文本
   * （如中文专业名）触发 graph 422 → PATH_PLANNER_GRAPH_UNAVAILABLE。与 {@code ResourceWorkshopService}
   * 的映射保持一致。
   */
  static String graphMajor(String rawMajor) {
    if (rawMajor == null || rawMajor.isBlank()) {
      return "OTHER";
    }
    String normalized = rawMajor.trim().toUpperCase(Locale.ROOT);
    if (GRAPH_MAJOR_VALUES.contains(normalized)) {
      return normalized;
    }
    String lower = rawMajor.trim().toLowerCase(Locale.ROOT);
    if (lower.contains("计算机")
        || lower.contains("软件")
        || lower.contains("computer")
        || lower.contains("cs")) {
      return "CS";
    }
    if (lower.contains("理工")
        || lower.contains("工科")
        || lower.contains("stem")
        || lower.contains("engineering")) {
      return "NON_CS_STEM";
    }
    if (lower.contains("文科") || lower.contains("人文") || lower.contains("humanities")) {
      return "NON_CS_HUMANITIES";
    }
    return "OTHER";
  }

  private List<PathPlannerGraphRequest.KcSnapshot> buildKcSnapshots(
      List<CourseKc> kcs,
      Map<String, MasterySnapshot> snapshotIndex,
      Map<String, List<String>> prerequisiteIndex) {
    List<PathPlannerGraphRequest.KcSnapshot> out = new ArrayList<>();
    for (CourseKc kc : kcs) {
      MasterySnapshot snapshot = snapshotIndex.get(kc.kcCode());
      boolean unlocked = snapshot != null && snapshot.masteryScore() != null;
      double mastery = snapshot == null || snapshot.masteryScore() == null
          ? 0.0
          : snapshot.masteryScore().doubleValue();
      double confidence = snapshot == null || snapshot.confidence() == null
          ? 0.0
          : snapshot.confidence().doubleValue();
      List<String> prerequisites =
          prerequisiteIndex.getOrDefault(kc.kcCode(), List.of());
      out.add(
          new PathPlannerGraphRequest.KcSnapshot(
              kc.kcCode(),
              mastery,
              confidence,
              unlocked,
              kc.chapterId(),
              prerequisites));
    }
    return out;
  }

  /** 最近练习记录 → planner（驱动 FIX_BUG / 难度匹配 / 已做过则不重复出题）。 */
  private List<PathPlannerGraphRequest.ProblemSubmission> buildRecentSubmissions(Long userId) {
    List<PathPlannerGraphRequest.ProblemSubmission> out = new ArrayList<>();
    for (PracticeSubmissionRepository.PlannerSubmission sub :
        submissionRepository.findRecentForPlanner(userId, RECENT_SUBMISSION_LIMIT)) {
      if (sub.kcCode() == null) {
        continue;
      }
      out.add(
          new PathPlannerGraphRequest.ProblemSubmission(
              sub.problemId(), sub.kcCode(), sub.passed()));
    }
    return out;
  }

  /** 最近消费的资源 → planner（已读过的 KC 不再重复安排 READ_RESOURCE）。 */
  private List<PathPlannerGraphRequest.ResourceUsage> buildRecentResources(Long userId) {
    List<PathPlannerGraphRequest.ResourceUsage> out = new ArrayList<>();
    List<ResourceListItemDto> resources =
        learningResourceRepository.list(userId, null, null, RECENT_RESOURCE_LIMIT, 0);
    for (ResourceListItemDto resource : resources) {
      String createdAt = resource.createdAt() == null ? "" : resource.createdAt().toString();
      for (String kcCode : resource.kcCodes()) {
        if (kcCode == null || kcCode.isBlank()) {
          continue;
        }
        out.add(
            new PathPlannerGraphRequest.ResourceUsage(
                kcCode, resource.type().name(), createdAt));
      }
    }
    return out;
  }

  /**
   * 提取反思洞察标记的高频易错 KC（Task 2.2）：取最近 error_pattern 洞察的 tags，过滤到课程包已知 KC
   * code，去重保序。让 planner 把这些坑的「练/复盘」节点前置。任何失败降级为空（不阻断规划）。
   */
  private List<String> buildErrorPatternKcs(Long userId, List<CourseKc> kcs) {
    Set<String> knownKcCodes =
        kcs.stream().map(CourseKc::kcCode).collect(Collectors.toSet());
    if (knownKcCodes.isEmpty()) {
      return List.of();
    }
    try {
      LinkedHashSet<String> flagged = new LinkedHashSet<>();
      for (LearnerInsightDto insight : insightRepository.findByUser(userId, INSIGHT_SCAN_LIMIT)) {
        if (!"error_pattern".equals(insight.dimensionKey()) || insight.tags() == null) {
          continue;
        }
        for (String tag : insight.tags()) {
          if (tag != null && knownKcCodes.contains(tag)) {
            flagged.add(tag);
          }
        }
      }
      return new ArrayList<>(flagged);
    } catch (RuntimeException ex) {
      log.warn(
          "error_pattern KC extraction degraded for user {} errorType={}",
          userId,
          ex.getClass().getSimpleName());
      return List.of();
    }
  }

  private List<PathPlannerGraphRequest.ClassroomQuestSnapshot> buildClassroomQuests(
      List<ClassroomQuestDto> quests) {
    List<PathPlannerGraphRequest.ClassroomQuestSnapshot> out = new ArrayList<>();
    for (ClassroomQuestDto quest : quests) {
      out.add(
          new PathPlannerGraphRequest.ClassroomQuestSnapshot(
              quest.id(),
              quest.classroomId(),
              quest.kcCodes(),
              quest.nodeType().name(),
              quest.title(),
              quest.dueDate().toString()));
    }
    return out;
  }

  private String extractReason(PathPlannerGraphResponse response) {
    PathPlannerGraphResponse.GroundingResult grounding = response.groundingResult();
    if (grounding != null && grounding.reasons() != null && !grounding.reasons().isEmpty()) {
      return String.join(",", grounding.reasons());
    }
    PathPlannerGraphResponse.SafetyResult safety = response.safetyResult();
    if (safety != null && safety.reasons() != null && !safety.reasons().isEmpty()) {
      return String.join(",", safety.reasons());
    }
    return "UNKNOWN";
  }

  private String questTitle(LearningPathNodeDraft node) {
    return switch (node.nodeType()) {
      case READ_RESOURCE -> "阅读 " + node.kcCode() + " 资源";
      case SOLVE_PROBLEM -> "完成 " + node.kcCode() + " 练习";
      case FIX_BUG -> "矿洞修复 " + node.kcCode();
      case PLANT_KC -> "前往主线解锁 KC " + node.kcCode();
      case PROFILE_UPDATE -> "更新学习画像";
    };
  }

  private QuestNodeType mapToQuestNodeType(PathNodeType type) {
    return QuestNodeType.valueOf(type.name());
  }

  private Object questPayload(LearningPathNodeDraft node) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("dayIndex", node.dayIndex());
    payload.put("orderInDay", node.orderInDay());
    if (node.suggestedResourceType() != null) {
      payload.put("suggestedResourceType", node.suggestedResourceType().name());
    }
    if (node.suggestedProblemId() != null) {
      payload.put("suggestedProblemId", node.suggestedProblemId());
    }
    if (node.sourceClassroomQuestId() != null) {
      payload.put("sourceClassroomQuestId", node.sourceClassroomQuestId());
    }
    return payload;
  }

  private int questPriority(int dayIndex, Long sourceClassroomQuestId) {
    if (sourceClassroomQuestId != null) {
      return 9;
    }
    int base = Math.max(1, 8 - dayIndex);
    return Math.min(9, base);
  }

  public LearningPathDto attachNodes(LearningPathDto path) {
    if (path == null) {
      return null;
    }
    if (path.nodes() != null && !path.nodes().isEmpty()) {
      return path;
    }
    List<LearningPathNodeDto> nodes = pathRepository.findNodes(path.id());
    return new LearningPathDto(
        path.id(),
        path.userId(),
        path.packId(),
        path.version(),
        path.status(),
        path.goalSummary(),
        path.generatedAt(),
        path.nextReviewAt(),
        path.sourceRunId(),
        nodes);
  }
}
