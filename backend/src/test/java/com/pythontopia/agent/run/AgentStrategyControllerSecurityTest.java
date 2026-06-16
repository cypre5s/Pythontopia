package com.pythontopia.agent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentStrategyControllerSecurityTest {

  private final MetaReflectionService service = mock(MetaReflectionService.class);
  private final AgentStrategyController controller =
      new AgentStrategyController(
          service, new GraphServiceProperties("http://graph", "http://rag", "expected-token"));

  @Test
  void rejectsMissingOrWrongInternalToken() {
    assertThatThrownBy(() -> controller.latest("practice", null))
        .isInstanceOf(BusinessException.class)
        .extracting("status.value")
        .isEqualTo(401);
    assertThatThrownBy(() -> controller.latest("practice", "wrong"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void returnsStrategyForMatchingInternalToken() {
    when(service.latestStrategy("practice")).thenReturn(Map.of("temperature", 0.2));

    assertThat(controller.latest("practice", "expected-token"))
        .containsEntry("temperature", 0.2);
  }
}
