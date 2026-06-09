package com.pythontopia.learner.oracle;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner/oracle")
public class OracleController {
  private final OracleService service;
  public OracleController(OracleService s) { this.service = s; }

  @GetMapping("/risk")
  public List<OracleService.RiskPrediction> risk(@CurrentUser CurrentUserPrincipal u) {
    if (u.role() != Role.STUDENT)
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可用");
    return service.predict(u.userId());
  }
}
