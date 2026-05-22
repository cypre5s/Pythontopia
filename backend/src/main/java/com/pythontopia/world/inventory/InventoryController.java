package com.pythontopia.world.inventory;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.inventory.dto.InventoryGrantRequest;
import com.pythontopia.world.inventory.dto.InventoryItemDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world/inventory")
public class InventoryController {

  private final InventoryService service;

  public InventoryController(InventoryService service) {
    this.service = service;
  }

  @GetMapping
  public List<InventoryItemDto> list(@CurrentUser CurrentUserPrincipal user) {
    return service.list(user.userId());
  }

  @PostMapping("/{itemKey}/use")
  public InventoryItemDto use(
      @CurrentUser CurrentUserPrincipal user, @PathVariable String itemKey) {
    return service.useOne(user.userId(), itemKey);
  }

  @PostMapping("/{itemKey}/grant")
  public InventoryItemDto grant(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable String itemKey,
      @Valid @RequestBody InventoryGrantRequest request) {
    if (user.role() != Role.ADMIN && user.role() != Role.TEACHER) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only teacher or admin can grant items");
    }
    return service.grant(user.userId(), itemKey, request.qty());
  }
}
