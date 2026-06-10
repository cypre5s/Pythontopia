package com.pythontopia.npc;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.npc.dto.NpcGreetingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NPC 首句个性化寒暄端点（R-15 §8）。
 *
 * <p>{@code GET /api/npc/{npcKey}/greeting}（JWT + STUDENT）：返回基于学情记忆 + 画像的 1–2 句寒暄，
 * 或结构化降级标志（fail-fast，不伪造个性化内容）。
 */
@RestController("npcGreetingController")
@RequestMapping("/api/npc")
public class NpcGreetingController {

  private final NpcGreetingService greetingService;

  public NpcGreetingController(NpcGreetingService greetingService) {
    this.greetingService = greetingService;
  }

  @GetMapping("/{npcKey}/greeting")
  public NpcGreetingResponse greeting(
      @CurrentUser CurrentUserPrincipal user, @PathVariable String npcKey) {
    requireStudent(user);
    return greetingService.greeting(user.userId(), npcKey);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可获取 NPC 寒暄");
    }
  }
}
