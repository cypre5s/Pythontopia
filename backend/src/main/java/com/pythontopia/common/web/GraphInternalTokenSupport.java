package com.pythontopia.common.web;

import com.pythontopia.common.config.GraphServiceProperties;
import org.springframework.http.HttpHeaders;

/** 后端 → graph/rag 内部调用的 {@code X-Internal-Token} 头统一设置。 */
public final class GraphInternalTokenSupport {

  public static final String HEADER = "X-Internal-Token";

  private GraphInternalTokenSupport() {}

  public static void apply(HttpHeaders headers, GraphServiceProperties properties) {
    String token = tokenOrNull(properties);
    if (token != null) {
      headers.set(HEADER, token);
    }
  }

  public static String tokenOrNull(GraphServiceProperties properties) {
    if (properties == null) {
      return null;
    }
    String token = properties.ragAdminToken();
    if (token == null || token.isBlank()) {
      return null;
    }
    return token;
  }
}
