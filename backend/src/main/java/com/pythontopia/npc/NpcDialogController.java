package com.pythontopia.npc;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.npc.dto.NpcDialogRequest;
import com.pythontopia.npc.dto.NpcDialogResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/npc")
public class NpcDialogController {

  private final NpcDialogService dialogService;

  public NpcDialogController(NpcDialogService dialogService) {
    this.dialogService = dialogService;
  }

  @PostMapping("/{npcKey}/dialog")
  public NpcDialogResponse talk(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable String npcKey,
      @Valid @RequestBody NpcDialogRequest request) {
    requireStudent(user);
    return dialogService.talk(user.userId(), npcKey, request.message());
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可与 NPC 对话");
    }
  }
}
