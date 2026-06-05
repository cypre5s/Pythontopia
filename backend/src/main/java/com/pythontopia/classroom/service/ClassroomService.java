package com.pythontopia.classroom.service;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.domain.Classroom;
import com.pythontopia.classroom.dto.ClassroomDto;
import com.pythontopia.classroom.dto.ClassroomMemberDto;
import com.pythontopia.classroom.dto.CreateClassroomRequest;
import com.pythontopia.classroom.repository.ClassroomRepository;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.ResourceNotFoundException;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ClassroomService {

  private static final char[] JOIN_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

  private final ClassroomRepository repository;
  private final SecureRandom random = new SecureRandom();

  public ClassroomService(ClassroomRepository repository) {
    this.repository = repository;
  }

  public ClassroomDto create(CurrentUserPrincipal user, CreateClassroomRequest request) {
    requireRole(user, Role.TEACHER);
    for (int i = 0; i < 10; i++) {
      try {
        return toDto(repository.create(request.name(), request.description(), user.userId(), joinCode()), true);
      } catch (DuplicateKeyException ignored) {
        // join_code 唯一冲突，重试生成新码
      }
    }
    throw new BusinessException(HttpStatus.CONFLICT, "JOIN_CODE_CONFLICT", "Could not allocate join code");
  }

  public List<ClassroomDto> mine(CurrentUserPrincipal user) {
    boolean teacher = user.role() == Role.TEACHER;
    List<Classroom> rows =
        teacher ? repository.findByTeacher(user.userId()) : repository.findByStudent(user.userId());
    return rows.stream().map(row -> toDto(row, teacher)).toList();
  }

  public ClassroomDto join(CurrentUserPrincipal user, String joinCode) {
    requireRole(user, Role.STUDENT);
    Classroom classroom =
        repository
            .findByJoinCode(joinCode)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));
    if (!"ACTIVE".equals(classroom.status())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Classroom is archived");
    }
    try {
      repository.addMember(classroom.id(), user.userId());
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(HttpStatus.CONFLICT, "CLASSROOM_ALREADY_JOINED", "Student already joined");
    }
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

  private Classroom ownedClassroom(CurrentUserPrincipal user, Long classroomId) {
    requireRole(user, Role.TEACHER);
    Classroom classroom =
        repository
            .findById(classroomId)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));
    if (!classroom.teacherId().equals(user.userId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Classroom belongs to another teacher");
    }
    return classroom;
  }

  private void requireRole(CurrentUserPrincipal user, Role role) {
    if (user.role() != role) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Role is not allowed");
    }
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
