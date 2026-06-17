package com.pythontopia.learner.memory.provider;

import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 记忆 provider 选型与外部端点配置，直接绑定 §8.8 约定的 env（{@code MEMORY_PROVIDER} 等），无需改
 * application.yml；本地 / ECS / 容器一致，符合「全部经 env 选型」的可移植性门。
 *
 * <ul>
 *   <li>{@code MEMORY_PROVIDER}：{@code local}（默认，复用既有 memory_embedding）/ {@code mem0} / {@code zep}；
 *       非法值归一为 {@code local}（fail-soft）。
 *   <li>{@code MEM0_BASE_URL} / {@code MEM0_API_KEY}：Mem0 REST 端点与鉴权。
 *   <li>{@code ZEP_BASE_URL} / {@code ZEP_API_KEY}：Zep REST 端点与鉴权。
 *   <li>{@code MEMORY_HTTP_CONNECT_TIMEOUT_MS} / {@code MEMORY_HTTP_READ_TIMEOUT_MS}：外部调用超时（fail-fast）。
 * </ul>
 */
@Component
public class MemoryProviderProperties {

  /** 合法 provider 取值（须与 {@code agent_memory_ref.provider} CHECK 约束一致）。 */
  public static final String LOCAL = "local";

  public static final String MEM0 = "mem0";
  public static final String ZEP = "zep";

  private static final Set<String> KNOWN = Set.of(LOCAL, MEM0, ZEP);

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

  /** 非法 / 空白 provider 归一为 {@code local}（安全默认 + fail-soft）。 */
  private static String normalize(String raw) {
    if (raw == null) {
      return LOCAL;
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    return KNOWN.contains(value) ? value : LOCAL;
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
