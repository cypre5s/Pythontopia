package com.pythontopia.learner.recommendation;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner/recommendations")
public class LearnerRecommendationController {

  private final LearnerRecommendationService service;

  public LearnerRecommendationController(LearnerRecommendationService service) {
    this.service = service;
  }

  @GetMapping
  public List<RecommendationDto> getRecommendations(@CurrentUser CurrentUserPrincipal user) {
    return service.recommend(user.userId());
  }
}
