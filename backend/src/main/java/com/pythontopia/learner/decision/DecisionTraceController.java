package com.pythontopia.learner.decision;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 决策玻璃盒：返回最近资源运行记录（按决策类型分类，可展开 agent + 课件证据链）。 */
@RestController
@RequestMapping("/api/learner/decisions")
public class DecisionTraceController {

  private final DecisionTraceService service;

  public DecisionTraceController(DecisionTraceService service) {
    this.service = service;
  }

  @GetMapping
  public List<DecisionTraceDto> recent(
      @CurrentUser CurrentUserPrincipal user, @RequestParam(defaultValue = "10") int limit) {
    return service.recent(user.userId(), limit);
  }
}
