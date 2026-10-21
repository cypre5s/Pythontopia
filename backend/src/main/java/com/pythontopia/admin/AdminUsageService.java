package com.pythontopia.admin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUsageService {

  private final AdminUsageRepository repository;

  public AdminUsageService(AdminUsageRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public AdminUsageDto list(CurrentUserPrincipal user, int days, String username) {
    if (user == null || user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "仅管理员可查看资源使用记录");
    }
    return repository.list(days, username == null || username.isBlank() ? null : username.trim());
  }
}
