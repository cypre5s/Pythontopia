package com.pythontopia.common.web;

import com.pythontopia.common.error.BusinessException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;

/**
 * 阻塞 GraphClient 的统一重试 + 简单熔断器：一次即时重试（transient），5 次连续失败后熔断 30s，
 * 避免下游不可达时重复占用 Tomcat 线程。熔断期间直接抛 502，不发起网络调用。
 *
 * <p>用法：
 * <pre>{@code
 *   BossInternalProblem problem = GraphClientRetryHelper.withRetry("boss",
 *       () -> restTemplate.exchange(url, HttpMethod.POST, entity, BossInternalProblem.class).getBody());
 * }</pre>
 *
 * <p>非 Spring-managed bean——仅依赖 JDK 标准库与项目内 {@link BusinessException}，测试可直接构造。
 */
public final class GraphClientRetryHelper {

  private static final Logger log = LoggerFactory.getLogger(GraphClientRetryHelper.class);

  private static final int MAX_RETRIES = 1;
  private static final int CIRCUIT_OPEN_THRESHOLD = 5;
  private static final long CIRCUIT_RESET_MS = 30_000L;

  private static final Map<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();
  private static final Map<String, Long> circuitOpenTimestamps = new ConcurrentHashMap<>();

  private GraphClientRetryHelper() {}

  /**
   * 包装一次阻塞 graph 调用：熔断器打开时直接抛 502；否则最多重试 {@value #MAX_RETRIES} 次，连续失败
   * 达到 {@value #CIRCUIT_OPEN_THRESHOLD} 次后自动熔断 {@value #CIRCUIT_RESET_MS}ms。
   *
   * @param clientName 标识（用于日志与计数器 key）
   * @param call       实际 HTTP 调用
   * @param <T>        返回类型
   * @return call 的返回值
   * @throws BusinessException (502) 当熔断打开或重试耗尽时
   */
  public static <T> T withRetry(String clientName, Supplier<T> call) {
    if (isCircuitOpen(clientName)) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          clientNameToCode(clientName, "CIRCUIT_OPEN"),
          clientName + " 服务已熔断，稍后重试");
    }

    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        T result = call.get();
        resetCircuit(clientName);
        return result;
      } catch (RestClientException e) {
        if (attempt < MAX_RETRIES) {
          log.debug("{} call failed (attempt {}), retrying: {}", clientName, attempt + 1, e.getMessage());
        }
      }
    }

    recordFailure(clientName);
    throw new BusinessException(
        HttpStatus.BAD_GATEWAY,
        clientNameToCode(clientName, "UNAVAILABLE"),
        clientName + " 服务暂不可用");
  }

  private static boolean isCircuitOpen(String clientName) {
    AtomicInteger failures = failureCounters.get(clientName);
    if (failures == null || failures.get() < CIRCUIT_OPEN_THRESHOLD) {
      return false;
    }
    Long openedAt = circuitOpenTimestamps.get(clientName);
    if (openedAt == null) {
      return false;
    }
    if (System.currentTimeMillis() - openedAt > CIRCUIT_RESET_MS) {
      circuitOpenTimestamps.remove(clientName);
      failures.set(0);
      return false;
    }
    return true;
  }

  private static void recordFailure(String clientName) {
    AtomicInteger counter = failureCounters.computeIfAbsent(clientName, k -> new AtomicInteger(0));
    int failures = counter.incrementAndGet();
    if (failures >= CIRCUIT_OPEN_THRESHOLD) {
      circuitOpenTimestamps.putIfAbsent(clientName, System.currentTimeMillis());
      log.warn("{} circuit OPEN after {} consecutive failures", clientName, failures);
    }
  }

  private static void resetCircuit(String clientName) {
    AtomicInteger counter = failureCounters.get(clientName);
    if (counter != null) {
      counter.set(0);
    }
    circuitOpenTimestamps.remove(clientName);
  }

  private static String clientNameToCode(String clientName, String suffix) {
    return clientName.toUpperCase().replace('-', '_') + "_GRAPH_" + suffix;
  }
}
