package com.pythontopia.browse.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.browse.dto.BrowseRunCreateRequest;
import com.pythontopia.browse.dto.BrowseRunDetailDto;
import com.pythontopia.browse.dto.BrowseRunDto;
import com.pythontopia.browse.service.BrowseRunService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.AiBudgeted;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 联网查证（BROWSE 工具）REST 端点。仅学生可用，且始终以登录态 userId 调服务（杜绝越权）。
 * 白名单由服务端注入，前端只提供 {@code goal} 与可选 {@code maxSteps}。
 */
@RestController
@RequestMapping("/api/browse/runs")
public class BrowseController {

  private final BrowseRunService service;

  public BrowseController(BrowseRunService service) {
    this.service = service;
  }

  @PostMapping
  @AiBudgeted
  public BrowseRunDto create(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody BrowseRunCreateRequest request) {
    requireStudent(user);
    return service.create(user.userId(), request);
  }

  @GetMapping("/{id}")
  public BrowseRunDetailDto detail(
      @CurrentUser CurrentUserPrincipal user, @PathVariable long id) {
    requireStudent(user);
    return service.detail(user.userId(), id);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用联网查证");
    }
  }
}
