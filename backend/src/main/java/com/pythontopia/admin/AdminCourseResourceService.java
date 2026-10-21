package com.pythontopia.admin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCourseResourceService {

  private final AdminCourseResourceRepository repository;

  public AdminCourseResourceService(AdminCourseResourceRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public AdminCourseResourcePageDto page(
      CurrentUserPrincipal user,
      int page,
      int size,
      String query,
      AdminCourseResourceType type) {
    if (user == null || user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "仅管理员可查看课程资源目录");
    }
    String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
    return repository.page(page, size, normalizedQuery, type);
  }

  @Transactional
  public void setEnabled(CurrentUserPrincipal user, AdminCourseResourceType type, long resourceId, boolean enabled) {
    if (user == null || user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "仅管理员可管理课程资源");
    }
    if (type == null || type == AdminCourseResourceType.ALL) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "COURSE_RESOURCE_TYPE_INVALID", "资源类型无效");
    }
    repository.setEnabled(type, resourceId, enabled, user.userId());
  }
}
