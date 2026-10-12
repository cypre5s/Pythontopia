package com.pythontopia.learner.memory.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 旧版外部记忆 provider 的配置兼容层。运行时路由固定返回 {@code local}，这些外部 URL/key 不会被调用。
 *
 * <ul>
 *   <li>{@code MEMORY_PROVIDER}：兼容旧配置，但本项目固定使用 {@code local}，不向其它记忆服务发送学习数据。
 *   <li>旧 Mem0/Zep URL、key 和 timeout 字段只为兼容现有配置与适配器测试保留；生产 router 不会选中它们。
 * </ul>
 */
@Component
public class MemoryProviderProperties {

  /** 合法 provider 取值（须与 {@code agent_memory_ref.provider} CHECK 约束一致）。 */
  public static final String LOCAL = "local";

  public static final String MEM0 = "mem0";
  public static final String ZEP = "zep";

  private final String provider;
  private final String mem0BaseUrl;
  private final String mem0ApiKey;
  private final String zepBaseUrl;
  private final String zepApiKey;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;

  public MemoryProviderProperties(
      @Value("${MEMORY_PROVIDER:local}") String provider,
      @Value("${MEM0_BASE_URL:}") String mem0BaseUrl,
      @Value("${MEM0_API_KEY:}") String mem0ApiKey,
      @Value("${ZEP_BASE_URL:}") String zepBaseUrl,
      @Value("${ZEP_API_KEY:}") String zepApiKey,
      @Value("${MEMORY_HTTP_CONNECT_TIMEOUT_MS:2000}") int connectTimeoutMs,
      @Value("${MEMORY_HTTP_READ_TIMEOUT_MS:4000}") int readTimeoutMs) {
    this.provider = normalize(provider);
    this.mem0BaseUrl = trimTrailingSlash(mem0BaseUrl);
    this.mem0ApiKey = mem0ApiKey == null ? "" : mem0ApiKey.trim();
    this.zepBaseUrl = trimTrailingSlash(zepBaseUrl);
    this.zepApiKey = zepApiKey == null ? "" : zepApiKey.trim();
    this.connectTimeoutMs = connectTimeoutMs <= 0 ? 2000 : connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs <= 0 ? 4000 : readTimeoutMs;
  }

  /** 外部记忆服务可能接收学习记录；运行时固定走本地存储。 */
  private static String normalize(String raw) {
    return LOCAL;
  }

  private static String trimTrailingSlash(String url) {
    if (url == null) {
      return "";
    }
    String trimmed = url.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  public String provider() {
    return provider;
  }

  public String mem0BaseUrl() {
    return mem0BaseUrl;
  }

  public String mem0ApiKey() {
    return mem0ApiKey;
  }

  public String zepBaseUrl() {
    return zepBaseUrl;
  }

  public String zepApiKey() {
    return zepApiKey;
  }

  public int connectTimeoutMs() {
    return connectTimeoutMs;
  }

  public int readTimeoutMs() {
    return readTimeoutMs;
  }
}
