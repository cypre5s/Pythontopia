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
import com.pythontopia.learner.quest.dto.QuestNodeType;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.learner.quest.dto.QuestStatus;
import com.pythontopia.learner.quest.repository.QuestRepository;
import com.pythontopia.learner.quest.repository.QuestRepository.QuestInsert;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningPathService {

  static final Long DEFAULT_PACK_ID = 43L;
  static final int ARCHIVED_HISTORY_LIMIT = 10;

  private final LearningPathRepository pathRepository;
  private final QuestRepository questRepository;
  private final ClassroomQuestRepository classroomQuestRepository;
  private final CoursePackRepository coursePackRepository;
  private final ProfileService profileService;
  private final MasteryEvaluator masteryEvaluator;
  private final PathPlannerGraphClient graphClient;
  private final ObjectMapper objectMapper;

  public LearningPathService(
      LearningPathRepository pathRepository,
      QuestRepository questRepository,
      ClassroomQuestRepository classroomQuestRepository,
      CoursePackRepository coursePackRepository,
      ProfileService profileService,
      MasteryEvaluator masteryEvaluator,
      PathPlannerGraphClient graphClient,
      ObjectMapper objectMapper) {
    this.pathRepository = pathRepository;
    this.questRepository = questRepository;
    this.classroomQuestRepository = classroomQuestRepository;
    this.coursePackRepository = coursePackRepository;
    this.profileService = profileService;
    this.masteryEvaluator = masteryEvaluator;
    this.graphClient = graphClient;
    this.objectMapper = objectMapper;
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
   * 阶段 13: 评估服务调用此方法生成 DRAFT 路径，不归档当前 ACTIVE，不发布 quest。
   * 学生在复盘大厅点击 "接受新路径" 后，通过 {@link #promoteDraft(Long, Long)} 才会真正激活。
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

  /** 阶段 13: DRAFT → ACTIVE，归档当前 ACTIVE 并按 DRAFT 节点重新生成 quest。 */
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
        QuestInsert insert =
            new QuestInsert(
                userId,
                QuestSource.PATH_PLANNER,
                node.id(),
                questTitleFromDto(node),
                node.reasonSummary(),
                node.kcCode(),
                QuestNodeType.valueOf(node.nodeType().name()),
                payloadFromDto(node),
                today.plusDays(Math.max(0, node.dayIndex() - 1)),
                questPriority(node.dayIndex(), node.sourceClassroomQuestId()));
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

  /** 阶段 13: 学生放弃 DRAFT 路径 → 标 ARCHIVED；当前 ACTIVE 不变。 */
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
            buildKcSnapshots(kcs, snapshotIndex),
            List.of(),
            List.of(),
            buildClassroomQuests(activeClassroomQuests));

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
      QuestInsert insert =
          new QuestInsert(
              userId,
              QuestSource.PATH_PLANNER,
              nodeId,
              questTitle(nodeDraft),
              draft.reasonSummary(),
              draft.kcCode(),
              mapToQuestNodeType(nodeDraft.nodeType()),
              questPayload(nodeDraft),
              today.plusDays(Math.max(0, draft.dayIndex() - 1)),
              questPriority(draft.dayIndex(), nodeDraft.sourceClassroomQuestId()));
      questRepository.insert(insert);
      generatedQuestCount += 1;
    }
    return generatedQuestCount;
  }

  private String questTitleFromDto(LearningPathNodeDto node) {
    return switch (node.nodeType()) {
      case READ_RESOURCE -> "阅读 " + node.kcCode() + " 资源";
      case SOLVE_PROBLEM -> "完成 " + node.kcCode() + " 练习";
      case FIX_BUG -> "矿洞修复 " + node.kcCode();
      case PLANT_KC -> "解锁 KC " + node.kcCode();
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

  void ensureDefaultsForNewStudent(Long userId) {
    if (questRepository.hasAnyQuest(userId)) {
      return;
    }
    LocalDate due = LocalDate.now(ZoneOffset.UTC).plusDays(1);
    questRepository.insert(
        new QuestInsert(
            userId,
            QuestSource.SYSTEM_DEFAULT,
            null,
            "完成画像访谈",
            "在画像屋与镇长聊聊，建立你的 8 维学习画像。",
            null,
            QuestNodeType.PROFILE_UPDATE,
            Map.of("kind", "default"),
            due,
            7));
    questRepository.insert(
        new QuestInsert(
            userId,
            QuestSource.SYSTEM_DEFAULT,
            null,
            "初始化知识田",
            "到知识田点击 bootstrap，把课程包导入到你的种植图。",
            null,
            QuestNodeType.PLANT_KC,
            Map.of("kind", "default"),
            due,
            6));
    questRepository.insert(
        new QuestInsert(
            userId,
            QuestSource.SYSTEM_DEFAULT,
            null,
            "在资源工坊生成一份讲义",
            "选 1 个 KC，让多智能体合作生成一份讲义。",
            null,
            QuestNodeType.READ_RESOURCE,
            Map.of("kind", "default"),
            due,
            5));
  }

  private PathPlannerGraphRequest.ProfileSnapshot buildProfileSnapshot(LearnerProfileDto profile) {
    if (profile == null) {
      return new PathPlannerGraphRequest.ProfileSnapshot(false, "OTHER", 2, 999);
    }
    int pythonLevel = 2;
    if (profile.baseLevel() != null && profile.baseLevel().pythonSelfRatedLevel() != null) {
      pythonLevel = profile.baseLevel().pythonSelfRatedLevel();
    }
    String major = profile.major() == null ? "OTHER" : profile.major();
    // Stage 08 默认使用画像创建后 7 天即触发 PROFILE_UPDATE 节点；后续阶段可改为读取 updated_at。
    return new PathPlannerGraphRequest.ProfileSnapshot(true, major, pythonLevel, 7);
  }

  private List<PathPlannerGraphRequest.KcSnapshot> buildKcSnapshots(
      List<CourseKc> kcs, Map<String, MasterySnapshot> snapshotIndex) {
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
      out.add(
          new PathPlannerGraphRequest.KcSnapshot(
              kc.kcCode(),
              mastery,
              confidence,
              unlocked,
              kc.chapterId(),
              List.of()));
    }
    return out;
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
      case PLANT_KC -> "解锁 KC " + node.kcCode();
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
