package com.pythontopia.learner.profile.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.ProfileRevisionDto;
import com.pythontopia.learner.profile.service.ProfileService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner/profile")
public class ProfileController {

  private final ProfileService profileService;

  public ProfileController(ProfileService profileService) {
    this.profileService = profileService;
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
}
