package com.pythontopia.learner.memory;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.learner.memory.dto.MemoryBriefDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 记忆 brief 只读端点（§3.2）：{@code GET /api/learner/memory/brief?query=...} → {@link MemoryBriefDto}。
 *
 * <p>供闭环 {@code understand} 注入上下文（graph 侧 {@code brief_client.py} 消费）。仅学生可用；
 * <strong>userId 一律取登录态</strong>（不接受 query 传 userId），从源头杜绝越权读他人记忆（§8.6）。
 */
@RestController
@RequestMapping("/api/learner/memory")
public class MemoryBriefController {

  private final MemoryBriefService briefService;

  public MemoryBriefController(MemoryBriefService briefService) {
    this.briefService = briefService;
  }

  @GetMapping("/brief")
  public MemoryBriefDto brief(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(required = false) String query) {
    requireStudent(user);
    return briefService.buildBrief(user.userId(), query);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可获取记忆 brief");
    }
  }
}
