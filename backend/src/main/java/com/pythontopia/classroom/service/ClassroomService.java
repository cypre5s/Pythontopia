package com.pythontopia.classroom.service;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.domain.Classroom;
import com.pythontopia.classroom.dto.ClassroomDto;
import com.pythontopia.classroom.dto.ClassroomMemberDto;
import com.pythontopia.classroom.dto.CreateClassroomRequest;
import com.pythontopia.classroom.quest.service.ClassroomQuestService;
import com.pythontopia.classroom.repository.ClassroomRepository;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.ResourceNotFoundException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ClassroomService {

  private static final char[] JOIN_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

  private final ClassroomRepository repository;
  private final ClassroomGuard classroomGuard;
  private final ClassroomQuestService classroomQuestService;
  private final JoinCodeAttemptThrottle joinCodeAttemptThrottle;
  private final SecureRandom random = new SecureRandom();

  @Autowired
  public ClassroomService(
      ClassroomRepository repository,
      ClassroomGuard classroomGuard,
      ClassroomQuestService classroomQuestService,
      JoinCodeAttemptThrottle joinCodeAttemptThrottle) {
    this.repository = repository;
    this.classroomGuard = classroomGuard;
    this.classroomQuestService = classroomQuestService;
    this.joinCodeAttemptThrottle = joinCodeAttemptThrottle;
  }

  ClassroomService(
      ClassroomRepository repository,
      ClassroomGuard classroomGuard,
      ClassroomQuestService classroomQuestService) {
    this(repository, classroomGuard, classroomQuestService, new JoinCodeAttemptThrottle());
  }

  public ClassroomDto create(CurrentUserPrincipal user, CreateClassroomRequest request) {
    if (!canManageClassrooms(user)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Role is not allowed");
    }
    for (int i = 0; i < 10; i++) {
      try {
        return toDto(
            repository.create(request.name(), request.description(), user.userId(), joinCode()),
            true);
      } catch (DuplicateKeyException ignored) {
        // join_code 唯一冲突，重试生成新码
      }
    }
    throw new BusinessException(
        HttpStatus.CONFLICT, "JOIN_CODE_CONFLICT", "Could not allocate join code");
  }

  public List<ClassroomDto> mine(CurrentUserPrincipal user) {
    boolean manager = canManageClassrooms(user);
    List<Classroom> rows =
        switch (user.role()) {
          case ADMIN -> repository.findAll();
          case TEACHER -> repository.findByTeacher(user.userId());
          case STUDENT -> repository.findByStudent(user.userId());
        };
    return rows.stream().map(row -> toDto(row, manager)).toList();
  }

  /**
   * 学生凭加入码入班。<b>可重入</b>：只有当成员行已是 {@code ACTIVE} 时才回 409；被教师移出 （{@code REMOVED}）的学生可以再次加入（{@link
   * ClassroomRepository#addMember} 是 upsert）。 入班成功后立刻补发该班「进行中」的班级任务副本，避免发布后才入班的学生看得到任务卡却点不动。
   */
  public ClassroomDto join(CurrentUserPrincipal user, String joinCode) {
    requireRole(user, Role.STUDENT);
    if (!joinCodeAttemptThrottle.tryAcquire(user.userId())) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "JOIN_CODE_RATE_LIMITED",
          "Too many classroom join attempts. Try again later.");
    }
    Classroom classroom =
        repository
            .findByJoinCode(joinCode)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));
    if (!"ACTIVE".equals(classroom.status())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Classroom is archived");
    }
    Optional<String> existingStatus = repository.findMemberStatus(classroom.id(), user.userId());
    if (existingStatus.filter("ACTIVE"::equals).isPresent()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "CLASSROOM_ALREADY_JOINED", "Student already joined");
    }
    try {
      repository.addMember(classroom.id(), user.userId());
    } catch (DuplicateKeyException exception) {
      // upsert 之后只剩「并发双写」这一种可能：另一个请求刚刚把同一个学生加进来。
      throw new BusinessException(
          HttpStatus.CONFLICT, "CLASSROOM_ALREADY_JOINED", "Student already joined");
    }
    classroomQuestService.backfillForStudent(classroom.id(), user.userId());
    return toDto(classroom, false);
  }

  public List<ClassroomMemberDto> members(CurrentUserPrincipal user, Long classroomId) {
    Classroom classroom = ownedClassroom(user, classroomId);
    return repository.findMembers(classroom.id());
  }

  public void removeMember(CurrentUserPrincipal user, Long classroomId, Long studentId) {
    Classroom classroom = ownedClassroom(user, classroomId);
    repository.removeMember(classroom.id(), studentId);
  }

  public ClassroomDto update(
      CurrentUserPrincipal user, Long classroomId, String name, String description) {
    Classroom classroom = ownedClassroom(user, classroomId);
    repository.updateBasics(classroom.id(), name, description);
    Classroom refreshed = repository.findById(classroom.id()).orElseThrow();
    return toDto(refreshed, true);
  }

  public ClassroomDto regenerateJoinCode(CurrentUserPrincipal user, Long classroomId) {
    Classroom classroom = ownedClassroom(user, classroomId);
    for (int i = 0; i < 10; i++) {
      try {
        repository.updateJoinCode(classroom.id(), joinCode());
        Classroom refreshed = repository.findById(classroom.id()).orElseThrow();
        return toDto(refreshed, true);
      } catch (DuplicateKeyException ignored) {
        // join_code 唯一冲突，重试生成新码
      }
    }
    throw new BusinessException(
        HttpStatus.CONFLICT, "JOIN_CODE_CONFLICT", "Could not allocate join code");
  }

  public List<ClassroomMemberDto> membersByStatus(
      CurrentUserPrincipal user, Long classroomId, String status) {
    Classroom classroom = ownedClassroom(user, classroomId);
    String normalised = status == null || status.isBlank() ? "ACTIVE" : status.toUpperCase();
    if (!normalised.equals("ACTIVE") && !normalised.equals("REMOVED")) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "CLASSROOM_STATUS_INVALID", "status 仅支持 ACTIVE/REMOVED");
    }
    return repository.findMembersByStatus(classroom.id(), normalised);
  }

  /**
   * 教师端班级归属校验：统一复用 {@link ClassroomGuard#requireOwner}，与 dashboard / quest / remediation / live
   * 四条教师入口口径一致（ADMIN 同样放行，不再出现「同一个 ADMIN 能读看板 却在改名 / 重置加入码 / 成员管理上被 403」的自相矛盾）。
   */
  private Classroom ownedClassroom(CurrentUserPrincipal user, Long classroomId) {
    return classroomGuard.requireOwner(user, classroomId);
  }

  private void requireRole(CurrentUserPrincipal user, Role role) {
    if (user.role() != role) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Role is not allowed");
    }
  }

  private boolean canManageClassrooms(CurrentUserPrincipal user) {
    return user.role() == Role.TEACHER || user.role() == Role.ADMIN;
  }

  private String joinCode() {
    StringBuilder builder = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      builder.append(JOIN_CODE_CHARS[random.nextInt(JOIN_CODE_CHARS.length)]);
    }
    return builder.toString();
  }

  private ClassroomDto toDto(Classroom classroom, boolean includeJoinCode) {
    return new ClassroomDto(
        classroom.id(),
        classroom.name(),
        classroom.description(),
        classroom.teacherId(),
        classroom.packId(),
        classroom.status(),
        includeJoinCode ? classroom.joinCode() : null);
  }
}
