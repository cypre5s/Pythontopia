package com.pythontopia.learner.memory;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「Agent 关于你的笔记本」只读端点：返回当前学生 topK 记忆（带 recency/importance/relevance 分解）。
 * 仅学生可用；只读本人数据（以登录态 userId 检索，杜绝越权）。
 */
@RestController
@RequestMapping("/api/learner/memory")
public class MemoryController {

  private final MemoryRetrievalService service;

  public MemoryController(MemoryRetrievalService service) {
    this.service = service;
  }

  @GetMapping
  public List<MemoryItemDto> memory(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(required = false) String focusKc,
      @RequestParam(defaultValue = "8") int limit) {
    requireStudent(user);
    return service.retrieve(user.userId(), focusKc, limit);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可查看记忆笔记本");
    }
  }
}
