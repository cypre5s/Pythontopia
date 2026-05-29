package com.pythontopia.world.npc;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.npc.dto.NpcDialogStateDto;
import com.pythontopia.world.npc.dto.NpcTalkRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world/npcs")
public class NpcDialogController {

  private final NpcDialogService service;

  public NpcDialogController(NpcDialogService service) {
    this.service = service;
  }

  @GetMapping
  public List<NpcDialogStateDto> listAll(@CurrentUser CurrentUserPrincipal user) {
    return service.listAll(user.userId());
  }

  @GetMapping("/{npcKey}/state")
  public NpcDialogStateDto state(
      @CurrentUser CurrentUserPrincipal user, @PathVariable String npcKey) {
    return service.getOrInit(user.userId(), npcKey);
  }

  @PostMapping("/{npcKey}/talk")
  public NpcDialogStateDto talk(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable String npcKey,
      @RequestBody(required = false) NpcTalkRequest request) {
    return service.talk(user.userId(), npcKey, request == null ? new NpcTalkRequest(null, null, null) : request);
  }
}
