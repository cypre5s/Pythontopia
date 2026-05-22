package com.pythontopia.learner.profile.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.learner.profile.dto.DimensionUpdateResult;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.ProfileRevisionDto;
import com.pythontopia.learner.profile.dto.SelfDimensionRequest;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.profile.service.ProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner/profile")
public class ProfileController {

  private final ProfileService profileService;
  private final PassiveProfileService passiveProfileService;

  public ProfileController(
      ProfileService profileService, PassiveProfileService passiveProfileService) {
    this.profileService = profileService;
    this.passiveProfileService = passiveProfileService;
  }

  @GetMapping
  ResponseEntity<LearnerProfileDto> getProfile(@CurrentUser CurrentUserPrincipal user) {
    return profileService
        .findProfile(user.userId())
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/revisions")
  List<ProfileRevisionDto> revisions(
      @CurrentUser CurrentUserPrincipal user, @RequestParam(defaultValue = "10") int limit) {
    return profileService.findRevisions(user.userId(), limit);
  }

  /**
   * 学生 / 教师对单个维度的直写接口。
   *
   * <p>学生（STUDENT）只能直写 {@link PassiveProfileService#SELF_EDITABLE_DIMENSIONS} 中的 4 个主观维度；
   * 教师（TEACHER）/ 管理员（ADMIN）可对自身画像的所有 8 维直写（教师改学生需走单独后台接口，暂未实现）。
   */
  @PutMapping("/dimensions/{dimensionKey}")
  DimensionUpdateResult selfUpdate(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable String dimensionKey,
      @Valid @RequestBody SelfDimensionRequest request) {
    if (request.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PROFILE_DELTA_EMPTY",
          "Delta must contain at least one of scoreDelta / evidenceIncrement / tagsAdd / metadata");
    }
    boolean privileged = user.role() == Role.TEACHER || user.role() == Role.ADMIN;
    if (!privileged && !passiveProfileService.isSelfEditable(dimensionKey)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "PROFILE_DIMENSION_NOT_SELF_EDITABLE",
          "Students can only self-update dimensions: " + PassiveProfileService.SELF_EDITABLE_DIMENSIONS);
    }
    return passiveProfileService.applyDimensionUpdate(
        user.userId(), dimensionKey, request.toDelta("INTERVIEW"));
  }
}
