package com.pythontopia.world.save;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.world.save.dto.SaveSlotCreateRequest;
import com.pythontopia.world.save.dto.SaveSlotDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
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

  private static final int SNAPSHOT_WRITE_COST = 10;

  private final SaveSlotService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public SaveSlotController(SaveSlotService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @GetMapping
  public List<SaveSlotDto> list(@CurrentUser CurrentUserPrincipal user) {
    requireStudentOrAdmin(user);
    return service.list(user.userId());
  }

  @PostMapping("/{slotIndex}")
  public SaveSlotDto create(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable @Min(0) @Max(5) int slotIndex,
      @Valid @RequestBody SaveSlotCreateRequest request) {
    requireStudentOrAdmin(user);
    requireWriteCapacity(user.userId(), 1);
    return service.create(user.userId(), slotIndex, request.displayName());
  }

  @PostMapping("/{slotIndex}/activate")
  public SaveSlotDto activate(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Min(0) @Max(5) int slotIndex) {
    requireStudentOrAdmin(user);
    requireWriteCapacity(user.userId(), 1);
    return service.activate(user.userId(), slotIndex);
  }

  @PostMapping("/{slotIndex}/snapshot")
  public SaveSlotDto saveSnapshot(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable @Min(0) @Max(5) int slotIndex,
      @RequestBody(required = false) String snapshot) {
    requireStudentOrAdmin(user);
    requireWriteCapacity(user.userId(), SNAPSHOT_WRITE_COST);
    return service.saveSnapshot(user.userId(), slotIndex, snapshot);
  }

  private void requireWriteCapacity(long userId, int writeCount) {
    if (!studentWriteThrottle.tryAcquire(userId, writeCount)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条记录");
    }
  }

  private void requireStudentOrAdmin(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT && user.role() != Role.ADMIN) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only students or admins have world state");
    }
  }
}
