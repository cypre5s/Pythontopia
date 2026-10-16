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
import com.pythontopia.learner.quest.repository.QuestRepository.QuestInsert;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
  private final ClassroomGuard classroomGuard;
  private final CoursePackRepository coursePackRepository;
  private final ObjectMapper objectMapper;

  public ClassroomQuestService(
      ClassroomQuestRepository repository,
      ClassroomRepository classroomRepository,
      ClassroomGuard classroomGuard,
      CoursePackRepository coursePackRepository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.classroomRepository = classroomRepository;
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
    StudentQuestTemplate template =
        template(
            classroomQuestId,
            request.title(),
            request.description(),
            request.kcCodes(),
            nodeType,
            payload,
            request.dueDate(),
            request.targetProblemId(),
            request.targetResourceType());
    int created = 0;
    int skipped = 0;
    for (ClassroomMemberDto member : members) {
      // 幂等分发：命中 uniq_quest_classroom_user 直接跳过，不会把 @Transactional 事务标脏。
      if (repository.insertStudentQuestIfAbsent(template.forStudent(member.studentId())).isPresent()) {
        created += 1;
      } else {
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
    // 归档必须同时撤回已分发的学生副本，否则 /api/quests/today（只看 quest.status + due_date）
    // 仍会把它当今日任务展示、允许完成并触发 mastery 加分。
    repository.skipDistributedQuests(classroomQuestId);
    return repository
        .findById(classroomQuestId)
        .orElseThrow(() -> new BusinessException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CLASSROOM_QUEST_PERSIST_FAILED",
            "班级任务归档后未能读回"));
  }

  /**
   * 新成员入班后补发该班「仍在进行中」的班级任务副本（status=ACTIVE 且未过截止日）。
   *
   * <p>没有它的话：发布之后才入班的学生在公告板上<b>看得到</b>任务卡（listActiveForStudent 用
   * LEFT JOIN quest），但 {@code studentQuestId} 为 null，点「提交完成」只会得到「请刷新公告板」
   * 且刷新永远无效。分发走 {@code ON CONFLICT DO NOTHING}，重复调用天然幂等。
   *
   * @return 实际补建的副本数
   */
  @Transactional
  public int backfillForStudent(Long classroomId, Long studentId) {
    List<ClassroomQuestDto> active =
        repository.listActiveForClassroom(classroomId, LocalDate.now(ZoneOffset.UTC));
    int created = 0;
    for (ClassroomQuestDto quest : active) {
      if (repository.insertStudentQuestIfAbsent(template(quest).forStudent(studentId)).isPresent()) {
        created += 1;
      }
    }
    return created;
  }

  /** 由已落库的班级任务重建分发模板（补发路径与发布路径共用同一份投影规则）。 */
  private StudentQuestTemplate template(ClassroomQuestDto quest) {
    return template(
        quest.id(),
        quest.title(),
        quest.description(),
        quest.kcCodes(),
        quest.nodeType(),
        quest.payload(),
        quest.dueDate(),
        quest.targetProblemId(),
        quest.targetResourceType());
  }

  private StudentQuestTemplate template(
      Long classroomQuestId,
      String title,
      String description,
      List<String> kcCodes,
      QuestNodeType nodeType,
      Object payload,
      LocalDate dueDate,
      Long targetProblemId,
      String targetResourceType) {
    String kcCode = kcCodes == null || kcCodes.isEmpty() ? null : kcCodes.get(0);
    return new StudentQuestTemplate(
        classroomQuestId,
        "班级任务 · " + title,
        description == null || description.isBlank() ? "班级任务：" + title : description,
        kcCode,
        nodeType,
        payload,
        dueDate,
        nodeType == QuestNodeType.SOLVE_PROBLEM ? "submission" : null,
        targetProblemId == null ? List.of() : List.of(targetProblemId),
        deepLinkRouteFor(nodeType, targetResourceType),
        deepLinkParamsFor(nodeType, kcCode, targetProblemId, targetResourceType));
  }

  /** 一条班级任务对全班共享的学生副本投影；{@link #forStudent} 只换 userId。 */
  private record StudentQuestTemplate(
      Long classroomQuestId,
      String questTitle,
      String description,
      String kcCode,
      QuestNodeType nodeType,
      Object payload,
      LocalDate dueDate,
      String requiredEvidenceType,
      List<Long> targetProblemIds,
      String deepLinkRoute,
      Object deepLinkParams) {

    QuestInsert forStudent(Long studentId) {
      return new QuestInsert(
          studentId,
          QuestSource.CLASSROOM,
          classroomQuestId,
          questTitle,
          description,
          kcCode,
          nodeType,
          payload,
          dueDate,
          9,
          requiredEvidenceType,
          null,
          targetProblemIds,
          null,
          deepLinkRoute,
          deepLinkParams);
    }
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
      return "/mainline-map";
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
    return params;
  }
}
