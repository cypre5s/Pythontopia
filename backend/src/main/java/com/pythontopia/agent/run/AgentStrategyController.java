package com.pythontopia.agent.run;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 内部 API：供 Python graph 拉取 agent 最新策略参数。 */
@RestController
@RequestMapping("/api/internal/agent-strategy")
public class AgentStrategyController {

  private final MetaReflectionService service;
  public AgentStrategyController(MetaReflectionService service) { this.service = service; }

  @GetMapping("/{agentName}")
  public Map<String, Object> latest(@PathVariable String agentName) {
    return service.latestStrategy(agentName);
  }
}
