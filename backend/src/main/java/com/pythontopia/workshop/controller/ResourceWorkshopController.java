package com.pythontopia.workshop.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.workshop.dto.LearningResourceDto;
import com.pythontopia.workshop.dto.ResourceCheckResponse;
import com.pythontopia.workshop.dto.ResourceCheckSubmitRequest;
import com.pythontopia.workshop.dto.ResourceConsumptionResponse;
import com.pythontopia.workshop.dto.ResourceCreateRequest;
import com.pythontopia.workshop.dto.ResourceCreateResponse;
import com.pythontopia.workshop.dto.ResourceEffectivenessDto;
import com.pythontopia.workshop.dto.ResourceFeedbackRequest;
import com.pythontopia.workshop.dto.ResourceListItemDto;
import com.pythontopia.workshop.dto.ResourceType;
import com.pythontopia.workshop.service.ResourceWorkshopService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/workshop/resources")
public class ResourceWorkshopController {

  private final ResourceWorkshopService service;

  public ResourceWorkshopController(ResourceWorkshopService service) {
    this.service = service;
  }

  @PostMapping
  public ResourceCreateResponse create(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody ResourceCreateRequest request) {
    requireStudent(user);
    return service.create(user.userId(), request);
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter createStream(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody ResourceCreateRequest request) {
    requireStudent(user);
    return service.createStream(user.userId(), request);
  }

  @GetMapping
  public List<ResourceListItemDto> list(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(required = false) ResourceType type,
      @RequestParam(required = false) String kcCode,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    requireStudent(user);
    return service.list(user.userId(), type, kcCode, limit, offset);
  }

  @GetMapping("/{id}")
  public LearningResourceDto findOne(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return service.findOne(user.userId(), id);
  }

  @GetMapping("/{id}/effectiveness")
  public ResourceEffectivenessDto effectiveness(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return service.effectiveness(user.userId(), id);
  }

  @PostMapping("/{id}/consumptions")
  public ResourceConsumptionResponse consume(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long id) {
    requireStudent(user);
    return service.consume(user.userId(), id);
  }

  @PostMapping("/{id}/checks")
  public ResourceCheckResponse submitCheck(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody ResourceCheckSubmitRequest request) {
    requireStudent(user);
    return service.submitCheck(user.userId(), id, request);
  }

  @PostMapping("/{id}/feedback")
  public void feedback(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long id,
      @Valid @RequestBody ResourceFeedbackRequest request) {
    requireStudent(user);
    service.submitFeedback(user.userId(), id, request);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可在资源工坊生成资源");
    }
  }
}
