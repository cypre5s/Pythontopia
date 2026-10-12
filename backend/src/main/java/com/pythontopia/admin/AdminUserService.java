package com.pythontopia.admin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

  private final AdminUserRepository repository;

  public AdminUserService(AdminUserRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public AdminUserPageDto page(
      CurrentUserPrincipal admin,
      int page,
      int size,
      String query,
      AdminUserStatus status,
      Role role) {
    requireAdmin(admin);
    String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
    return repository.page(page, size, normalizedQuery, status, role);
  }

  @Transactional
  public AdminUserDto setStatus(
      CurrentUserPrincipal admin, long userId, AdminUserStatus status) {
    requireAdmin(admin);
    if (status == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_USER_STATUS", "账号状态不能为空");
    }
    if (admin.userId().equals(userId) && status == AdminUserStatus.DISABLED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ADMIN_SELF_DISABLE_FORBIDDEN", "不能停用当前管理员账号");
    }
    return repository
        .setStatusAndRevokeSessions(userId, status)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
  }

  private static void requireAdmin(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "仅管理员可管理用户");
    }
  }
}
