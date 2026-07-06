package com.pythontopia.world.save;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.save.dto.SaveSlotCreateRequest;
import com.pythontopia.world.save.dto.SaveSlotDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/world/saves")
public class SaveSlotController {

  private final SaveSlotService service;

  public SaveSlotController(SaveSlotService service) {
    this.service = service;
  }

  @GetMapping
  public List<SaveSlotDto> list(@CurrentUser CurrentUserPrincipal user) {
    return service.list(user.userId());
  }

  @PostMapping("/{slotIndex}")
  public SaveSlotDto create(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable @Min(0) @Max(5) int slotIndex,
      @Valid @RequestBody SaveSlotCreateRequest request) {
    return service.create(user.userId(), slotIndex, request.displayName());
  }

  @PostMapping("/{slotIndex}/activate")
  public SaveSlotDto activate(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Min(0) @Max(5) int slotIndex) {
    return service.activate(user.userId(), slotIndex);
  }

  @PostMapping("/{slotIndex}/snapshot")
  public SaveSlotDto saveSnapshot(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable @Min(0) @Max(5) int slotIndex,
      @RequestBody(required = false) String snapshot) {
    return service.saveSnapshot(user.userId(), slotIndex, snapshot);
  }
}
