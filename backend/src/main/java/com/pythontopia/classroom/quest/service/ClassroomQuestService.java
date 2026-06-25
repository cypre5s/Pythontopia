package com.pythontopia.classroom.quest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.dto.ClassroomMemberDto;
import com.pythontopia.classroom.quest.dto.ClassroomQuestCreateRequest;
import com.pythontopia.classroom.quest.dto.ClassroomQuestDto;
import com.pythontopia.classroom.quest.dto.ClassroomQuestPublishResponse;
import com.pythontopia.classroom.quest.repository.ClassroomQuestRepository;
import com.pythontopia.classroom.quest.repository.ClassroomQuestRepository.ClassroomQuestInsert;
import com.pythontopia.classroom.repository.ClassroomRepository;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.learner.quest.repository.QuestRepository;
import com.pythontopia.learner.quest.repository.QuestRepository.QuestInsert;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomQuestService {

  private static final long DEFAULT_PACK_ID = 43L;
  private static final Set<String> ALLOWED_RESOURCE_TYPES =
      Set.of("HANDOUT", "MIND_MAP", "PRACTICE", "CODE_LAB", "READING", "STORYBOARD");

  private final ClassroomQuestRepository repository;
  private final ClassroomRepository classroomRepository;
  private final QuestRepository questRepository;
  private final ClassroomGuard classroomGuard;
  private final CoursePackRepository coursePackRepository;
  private final ObjectMapper objectMapper;

  public ClassroomQuestService(
      ClassroomQuestRepository repository,
      ClassroomRepository classroomRepository,
      QuestRepository questRepository,
      ClassroomGuard classroomGuard,
      CoursePackRepository coursePackRepository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.classroomRepository = classroomRepository;
    this.questRepository = questRepository;
    this.classroomGuard = classroomGuard;
    this.coursePackRepository = coursePackRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<ClassroomQuestDto> listForStudent(Long userId) {
    return repository.listActiveForStudent(userId, LocalDate.now(ZoneOffset.UTC));
  }

  @Transactional(readOnly = true)
  public List<ClassroomQuestDto> listActiveForClassroom(
      CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    return repository.listActiveForClassroom(classroomId, LocalDate.now(ZoneOffset.UTC));
  }

  @Transactional(readOnly = true)
  public List<ClassroomQuestDto> listAllForClassroom(CurrentUserPrincipal user, Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    return repository.listForClassroom(classroomId);
  }

  @Transactional
  public ClassroomQuestPublishResponse publish(
      CurrentUserPrincipal user, Long classroomId, ClassroomQuestCreateRequest request) {
    classroomGuard.requireOwner(user, classroomId);
    validateRequest(request);

    QuestNodeType nodeType =
        request.targetProblemId() != null
            ? QuestNodeType.SOLVE_PROBLEM
            : QuestNodeType.READ_RESOURCE;

    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("classroomQuestSource", true);
    if (request.targetProblemId() != null) {
      payload.put("targetProblemId", request.targetProblemId());
      payload.put("suggestedProblemId", request.targetProblemId());
    }
    if (request.targetResourceType() != null) {
      payload.put("targetResourceType", request.targetResourceType());
      payload.put("suggestedResourceType", request.targetResourceType());
    }

    Long classroomQuestId =
        repository.insert(
            new ClassroomQuestInsert(
                classroomId,
                request.title(),
                request.description(),
                request.kcCodes(),
                nodeType,
                payload,
                request.dueDate(),
                user.userId(),
                request.targetProblemId(),
                request.targetResourceType()));

    List<ClassroomMemberDto> members = classroomRepository.findMembers(classroomId);
    int created = 0;
    int skipped = 0;
    String kcCode = request.kcCodes().isEmpty() ? null : request.kcCodes().get(0);
    String descriptionForQuest =
        request.description() == null || request.description().isBlank()
            ? "班级任务：" + request.title()
            : request.description();
    String questTitle = "班级任务 · " + request.title();
    String deepLinkRoute = deepLinkRouteFor(nodeType, request.targetResourceType());
    ObjectNode deepLinkParams =
        deepLinkParamsFor(
            nodeType, kcCode, request.targetProblemId(), request.targetResourceType());
    String requiredEvidenceType =
        nodeType == QuestNodeType.SOLVE_PROBLEM ? "submission" : null;
    List<Long> targetProblemIds =
        request.targetProblemId() == null ? List.of() : List.of(request.targetProblemId());
    for (ClassroomMemberDto member : members) {
      try {
        questRepository.insert(
            new QuestInsert(
                member.studentId(),
                QuestSource.CLASSROOM,
                classroomQuestId,
                questTitle,
                descriptionForQuest,
                kcCode,
                nodeType,
                payload,
                request.dueDate(),
                9,
                requiredEvidenceType,
                null,
                targetProblemIds,
                null,
                deepLinkRoute,
                deepLinkParams));
        created += 1;
      } catch (DuplicateKeyException duplicate) {
        skipped += 1;
      }
    }

    ClassroomQuestDto stored =
        repository
            .findById(classroomQuestId)
            .orElseThrow(() -> new BusinessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CLASSROOM_QUEST_PERSIST_FAILED",
                "班级任务写入后未能读回"));
    return new ClassroomQuestPublishResponse(stored, members.size(), created, skipped);
  }

  @Transactional
  public ClassroomQuestDto archive(
      CurrentUserPrincipal user, Long classroomId, Long classroomQuestId) {
    classroomGuard.requireOwner(user, classroomId);
    ClassroomQuestDto existing =
        repository
            .findById(classroomQuestId)
            .orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "CLASSROOM_QUEST_NOT_FOUND", "班级任务不存在"));
    if (!existing.classroomId().equals(classroomId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "班级任务不属于当前班级");
    }
    repository.archive(classroomQuestId);
    return repository
        .findById(classroomQuestId)
        .orElseThrow(() -> new BusinessException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CLASSROOM_QUEST_PERSIST_FAILED",
            "班级任务归档后未能读回"));
  }

  private void validateRequest(ClassroomQuestCreateRequest request) {
    if (request.targetProblemId() == null
        && (request.targetResourceType() == null || request.targetResourceType().isBlank())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "CLASSROOM_QUEST_ANCHOR_REQUIRED",
          "班级任务必须绑定题目或资源类型");
    }
    if (request.targetResourceType() != null
        && !request.targetResourceType().isBlank()
        && !ALLOWED_RESOURCE_TYPES.contains(request.targetResourceType())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "CLASSROOM_QUEST_RESOURCE_TYPE_INVALID",
          "资源类型非法：" + request.targetResourceType());
    }
    if (request.dueDate().isBefore(LocalDate.now(ZoneOffset.UTC))) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "CLASSROOM_QUEST_DUE_DATE_INVALID",
          "截止日期不能早于今天");
    }
    Set<String> known = knownKcCodes();
    List<String> invalid = new ArrayList<>();
    Set<String> unique = new HashSet<>();
    for (String code : request.kcCodes()) {
      if (!known.contains(code)) {
        invalid.add(code);
      }
      if (!unique.add(code)) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "CLASSROOM_QUEST_KC_DUPLICATE", "kcCodes 存在重复：" + code);
      }
    }
    if (!invalid.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "CLASSROOM_QUEST_KC_UNKNOWN",
          "kcCodes 含未知 KC：" + String.join(",", invalid));
    }
    if (request.targetProblemId() != null
        && coursePackRepository.findProblem(DEFAULT_PACK_ID, request.targetProblemId()).isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "CLASSROOM_QUEST_PROBLEM_NOT_FOUND",
          "targetProblemId 不存在：" + request.targetProblemId());
    }
    if (containsPii(request.title()) || containsPii(request.description())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "CLASSROOM_QUEST_PII_BLOCKED",
          "标题或描述疑似包含个人身份信息，请删除后重试");
    }
  }

  Set<String> knownKcCodes() {
    List<CourseKc> kcs = coursePackRepository.findKcs(DEFAULT_PACK_ID, null);
    Set<String> codes = new HashSet<>();
    for (CourseKc kc : kcs) {
      codes.add(kc.kcCode());
    }
    return codes;
  }

  private boolean containsPii(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    return text.matches(".*\\b(?:\\d{15}|\\d{17}[0-9Xx])\\b.*")
        || text.matches(".*1[3-9]\\d{9}.*");
  }

  @Transactional(readOnly = true)
  public List<ClassroomQuestDto> listForClassroom(Long classroomId) {
    return repository.listActiveForClassroom(classroomId, LocalDate.now(ZoneOffset.UTC));
  }

  private String deepLinkRouteFor(QuestNodeType nodeType, String targetResourceType) {
    if (nodeType == QuestNodeType.SOLVE_PROBLEM) {
      return "/training";
    }
    if (nodeType == QuestNodeType.READ_RESOURCE) {
      return "/workshop";
    }
    if (nodeType == QuestNodeType.FIX_BUG) {
      return "/world";
    }
    if (nodeType == QuestNodeType.PLANT_KC) {
      return "/world";
    }
    return "/world";
  }

  private ObjectNode deepLinkParamsFor(
      QuestNodeType nodeType, String kcCode, Long problemId, String resourceType) {
    ObjectNode params = objectMapper.createObjectNode();
    if (nodeType == QuestNodeType.SOLVE_PROBLEM && problemId != null) {
      params.put("problemId", problemId);
    }
    if (nodeType == QuestNodeType.READ_RESOURCE) {
      if (kcCode != null) {
        params.put("kcCode", kcCode);
      }
      if (resourceType != null && !resourceType.isBlank()) {
        params.put("type", resourceType);
      }
    }
    if (nodeType == QuestNodeType.FIX_BUG) {
      // R-14：mine_south 风景地图已删；停在中央小镇，玩家经 town_hub 的「Bug 矿洞」building → /bug-mine 进入调试功能
      params.put("goto", "town_hub");
    }
    if (nodeType == QuestNodeType.PLANT_KC && kcCode != null) {
      params.put("focus", "kc_" + kcCode.toLowerCase(Locale.ROOT));
    }
    return params;
  }
}
