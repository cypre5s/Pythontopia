package com.pythontopia.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 媒体合成作业的 SSE 订阅注册表：按 {@code jobId} 维护订阅者的 {@link SseEmitter} 列表，worker 逐步
 * 把 {@code trace} 广播给所有订阅者，终态 {@code result}/{@code error} 广播后收尾。
 *
 * <p>事件协议（契约 4.2/4.3）：
 *
 * <ul>
 *   <li>{@code plan}：{@code {nodes:[{node,agent_label,kind,role_key}]}}，role_key=插画师/配音师/剪辑师。
 *   <li>{@code trace}：{@code {node,phase,status,elapsed_ms,role_key,message,percent}}。
 *   <li>{@code result}：{@code {jobId,assetId,url}}。
 *   <li>{@code error}：{@code {code,message}}。
 * </ul>
 *
 * <p>对同一 {@code emitter} 的写入用 {@code synchronized(emitter)} 串行化，避免 worker 广播与订阅时的
 * 终态回放并发写坏同一连接（{@code SseEmitter} 非线程安全）。
 */
@Component
public class MediaJobStreamRegistry {

  private static final Logger log = LoggerFactory.getLogger(MediaJobStreamRegistry.class);

  /** 合成流水线静态节点（插画师→配音师→剪辑师），订阅即下发，供前端搭骨架。 */
  private static final List<Map<String, Object>> PLAN_NODES =
      List.of(
          planNode("illustrator", "插画师"),
          planNode("narrator", "配音师"),
          planNode("editor", "剪辑师"));

  private final ObjectMapper objectMapper;
  private final long streamTimeoutMs;
  private final Map<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

  public MediaJobStreamRegistry(ObjectMapper objectMapper, MediaComposeProperties properties) {
    this.objectMapper = objectMapper;
    this.streamTimeoutMs = properties.streamTimeoutMs();
  }

  /** 新订阅一个作业的进度流。调用方随后应下发 {@code plan} 与当前已知状态回放。 */
  public SseEmitter subscribe(long jobId) {
    SseEmitter emitter = new SseEmitter(streamTimeoutMs);
    subscribers.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    emitter.onCompletion(() -> remove(jobId, emitter));
    emitter.onTimeout(
        () -> {
          remove(jobId, emitter);
          emitter.complete();
        });
    emitter.onError(throwable -> remove(jobId, emitter));
    return emitter;
  }

  public List<Map<String, Object>> planNodes() {
    return PLAN_NODES;
  }

  /** 向单个 emitter 发一个事件（终态回放用）。失败（客户端断开）吞掉并返回 false。 */
  public boolean send(SseEmitter emitter, String event, Object data) {
    try {
      String json = objectMapper.writeValueAsString(data);
      synchronized (emitter) {
        emitter.send(SseEmitter.event().name(event).data(json));
      }
      return true;
    } catch (Exception ex) {
      log.debug("media job SSE send '{}' failed (client likely disconnected): {}", event, ex.toString());
      return false;
    }
  }

  /** 向某作业的全部订阅者广播一个事件（worker 逐步进度用）。 */
  public void broadcast(long jobId, String event, Object data) {
    List<SseEmitter> list = subscribers.get(jobId);
    if (list == null) {
      return;
    }
    for (SseEmitter emitter : list) {
      send(emitter, event, data);
    }
  }

  /** 完成某作业的全部订阅者连接并清理（终态广播后调用）。 */
  public void complete(long jobId) {
    List<SseEmitter> list = subscribers.remove(jobId);
    if (list == null) {
      return;
    }
    for (SseEmitter emitter : list) {
      completeQuietly(emitter);
    }
  }

  /** 安静地完成单个 emitter（终态回放后调用），重复完成不报错。 */
  public void completeQuietly(SseEmitter emitter) {
    try {
      synchronized (emitter) {
        emitter.complete();
      }
    } catch (Exception ignored) {
      // 已完成 / 已断开：忽略
    }
  }

  private void remove(long jobId, SseEmitter emitter) {
    List<SseEmitter> list = subscribers.get(jobId);
    if (list != null) {
      list.remove(emitter);
      if (list.isEmpty()) {
        subscribers.remove(jobId, list);
      }
    }
  }

  private static Map<String, Object> planNode(String roleKey, String label) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("node", roleKey);
    node.put("agent_label", label);
    node.put("kind", "tool");
    node.put("role_key", roleKey);
    return node;
  }
}
