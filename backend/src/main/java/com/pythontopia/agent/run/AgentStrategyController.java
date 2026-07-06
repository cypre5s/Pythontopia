package com.pythontopia.agent.run;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 内部 API：供 Python graph 拉取 agent 最新策略参数。 */
@RestController
@RequestMapping("/api/internal/agent-strategy")
public class AgentStrategyController {

  private final MetaReflectionService service;
  private final String internalToken;

  public AgentStrategyController(
      MetaReflectionService service, GraphServiceProperties graphServiceProperties) {
    this.service = service;
    this.internalToken = graphServiceProperties.ragAdminToken();
  }

  @GetMapping("/{agentName}")
  public Map<String, Object> latest(
      @PathVariable String agentName,
      @RequestHeader(value = "X-Internal-Token", required = false) String providedToken) {
    verifyInternalToken(providedToken);
    return service.latestStrategy(agentName);
  }

  private void verifyInternalToken(String providedToken) {
    if (internalToken == null || internalToken.isBlank()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "INTERNAL_AUTH_NOT_CONFIGURED",
          "Internal service authentication is not configured");
    }
    byte[] expected = internalToken.getBytes(StandardCharsets.UTF_8);
    byte[] provided =
        (providedToken == null ? "" : providedToken).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, provided)) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_TOKEN", "Invalid internal service token");
    }
  }
}
