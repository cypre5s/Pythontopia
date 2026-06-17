package com.pythontopia.loop;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.loop.dto.LoopRunDto;
import com.pythontopia.loop.dto.LoopRunRecordRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 有界 Agentic Loop run 审计入口。{@code POST /runs} 在每轮闭环结束后落库审计（供 graph S1 回写，
 * Phase 2 接线时随附学生 JWT 或由后端编排器在进程内调用 {@link LoopRunService}）；
 * {@code GET /runs/{id}} 取本人某轮闭环审计详情。两端 {@code requireStudent} + 按 owner 隔离（§8.6）。
 */
@RestController
@RequestMapping("/api/loop")
public class LoopController {

  private final LoopRunService service;

  public LoopController(LoopRunService service) {
    this.service = service;
  }

  @PostMapping("/runs")
  @ResponseStatus(HttpStatus.CREATED)
  public LoopRunDto recordRun(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody LoopRunRecordRequest request) {
    requireStudent(user);
    return service.record(user.userId(), request);
  }

  @GetMapping("/runs/{id}")
  public LoopRunDto getRun(@CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return service.getForOwner(user.userId(), id);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可记录/查看闭环 run 审计");
    }
  }
}
