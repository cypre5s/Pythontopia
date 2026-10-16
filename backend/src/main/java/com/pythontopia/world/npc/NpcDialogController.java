package com.pythontopia.world.npc;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.world.npc.dto.NpcDialogStateDto;
import com.pythontopia.world.npc.dto.NpcTalkRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world/npcs")
public class NpcDialogController {

  private static final Pattern NPC_KEY_PATTERN = Pattern.compile("[a-z0-9_-]{1,64}");
  private final NpcDialogService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public NpcDialogController(NpcDialogService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @GetMapping
  public List<NpcDialogStateDto> listAll(@CurrentUser CurrentUserPrincipal user) {
    return service.listAll(user.userId());
  }

  @GetMapping("/{npcKey}/state")
  public NpcDialogStateDto state(
      @CurrentUser CurrentUserPrincipal user, @PathVariable String npcKey) {
    validateNpcKey(npcKey);
    return service.getOrInit(user.userId(), npcKey);
  }

  @PostMapping("/{npcKey}/talk")
  public NpcDialogStateDto talk(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable String npcKey,
      @Valid @RequestBody(required = false) NpcTalkRequest request) {
    validateNpcKey(npcKey);
    if (!studentWriteThrottle.tryAcquire(user.userId(), 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "STUDENT_WRITE_RATE_LIMITED", "每个账号每分钟最多写入 100 条记录");
    }
    return service.talk(
        user.userId(), npcKey, request == null ? new NpcTalkRequest(null, null, null) : request);
  }

  private void validateNpcKey(String npcKey) {
    if (npcKey == null || !NPC_KEY_PATTERN.matcher(npcKey).matches()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_NPC_KEY", "NPC key is invalid");
    }
  }
}
