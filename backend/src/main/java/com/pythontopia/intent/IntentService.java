package com.pythontopia.intent;

import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.IntentRouteResponse;
import org.springframework.stereotype.Service;

/**
 * 意图路由服务层，镜像 {@code OrchestratorService}：薄薄一层，直接委托 {@link IntentGraphClient}
 * 调 Python graph。无 Repository——不落库。
 */
@Service
public class IntentService {

  private final IntentGraphClient graphClient;

  public IntentService(IntentGraphClient graphClient) {
    this.graphClient = graphClient;
  }

  public IntentRouteResponse route(IntentRouteRequest request) {
    return graphClient.route(request);
  }
}
