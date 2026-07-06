package com.pythontopia.classroom.security;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.domain.Classroom;
import com.pythontopia.classroom.repository.ClassroomRepository;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 班级归属鉴权：既作为 @PreAuthorize SpEL bean 使用，也供 service 直接调用，校验教师/管理员对班级的归属。 */
@Component("classroomGuard")
public class ClassroomGuard {

  private final ClassroomRepository repository;

  public ClassroomGuard(ClassroomRepository repository) {
    this.repository = repository;
  }

  public Classroom requireOwner(CurrentUserPrincipal user, Long classroomId) {
    if (user == null) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录");
    }
    if (user.role() != Role.TEACHER && user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅教师可访问教师入口");
    }
    Classroom classroom =
        repository
            .findById(classroomId)
            .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));
    if (user.role() != Role.ADMIN && !classroom.teacherId().equals(user.userId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "班级不属于当前教师");
    }
    return classroom;
  }

  public boolean isOwner(Long classroomId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    Object principal = authentication.getPrincipal();
    if (!(principal instanceof CurrentUserPrincipal user)) {
      return false;
    }
    if (user.role() == Role.ADMIN) {
      return repository.findById(classroomId).isPresent();
    }
    if (user.role() != Role.TEACHER) {
      return false;
    }
    return repository
        .findById(classroomId)
        .map(c -> c.teacherId().equals(user.userId()))
        .orElse(false);
  }
}
