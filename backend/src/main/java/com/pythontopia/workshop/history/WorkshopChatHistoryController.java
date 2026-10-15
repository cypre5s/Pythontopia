package com.pythontopia.workshop.history;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workshop/chat-history")
public class WorkshopChatHistoryController {

  private final WorkshopChatHistoryService service;

  public WorkshopChatHistoryController(WorkshopChatHistoryService service) {
    this.service = service;
  }

  @GetMapping("/turns")
  public List<WorkshopChatHistoryTurnDto> listTurns(
      @CurrentUser CurrentUserPrincipal user, @RequestParam(defaultValue = "50") int limit) {
    requireStudent(user);
    return service.listTurns(user.userId(), limit);
  }

  @PostMapping("/turns")
  public WorkshopChatHistoryRecordResponse recordTurn(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody WorkshopChatHistoryRecordRequest request) {
    requireStudent(user);
    return service.recordTurn(user.userId(), request);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可保存百晓工坊聊天历史");
    }
  }
}
