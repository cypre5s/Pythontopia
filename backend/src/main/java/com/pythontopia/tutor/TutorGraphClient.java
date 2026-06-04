package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.GraphSseConsumer;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.media.XfyunImageUnderstandingService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class TutorGraphClient {

  private static final Logger log = LoggerFactory.getLogger(TutorGraphClient.class);

  /** 看图答疑单次最多识别的图片数（仿 Cursor 多图附件，逐张顺序识别，控制时延 / 成本）。 */
  private static final int MAX_UNDERSTAND_IMAGES = 3;

  /**
   * 看图答疑：给「图片理解」模型的中性识别指令。强约束只转录 / 描述图中可见内容，严禁解题或给出
   * 完整代码——最终引导仍由文本模型（deepseek-v4-pro）按导师规则（含防泄答）产出。
   */
  private static final String IMAGE_UNDERSTAND_PROMPT =
      "请客观、简洁地描述这张图片中与编程学习相关的内容（例如：Python 代码、报错信息 / 堆栈、"
          + "题目文字、运行输出）。逐字转录可见的关键代码与报错，但只描述图中实际可见的内容，"
          + "不要推测，更不要给出题目的解答或可直接提交的完整代码。";

  private final GraphServiceProperties properties;
  private final ObjectMapper objectMapper;
  private final SseRelayExecutor sseRelayExecutor;
  private final XfyunImageUnderstandingService imageUnderstanding;

  public TutorGraphClient(
      GraphServiceProperties properties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor,
      XfyunImageUnderstandingService imageUnderstanding) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.sseRelayExecutor = sseRelayExecutor;
    this.imageUnderstanding = imageUnderstanding;
  }

  /**
   * Streams the tutor ask response via SSE from the Python graph service.
   * Relays all events through the emitter; calls onResult when the final result arrives.
   *
   * <p>看图答疑（B）：当 {@code images} 非空且图片理解已启用时，先在 relay 线程内逐张调用讯飞
   * 「图片理解」把图片转成客观描述（每张发一组 {@code trace} 事件，驱动前端「助教小队」拟人化动画），
   * 再把描述并入 {@code context} 透传给 graph，让 deepseek-v4-pro 结合课件 RAG 作答；
   * 任一张理解失败 / 未启用自动跳过，绝不阻塞答疑。
   */
  public void askStream(
      Long userId,
      String question,
      List<String> kcCodes,
      String context,
      List<String> images,
      String profileBrief,
      SseEmitter emitter,
      Consumer<String> onResult) {
    String url = properties.graphUrl() + "/agents/tutor/ask/stream";

    sseRelayExecutor.submit(() -> {
      try {
        String effectiveContext = enrichContextWithImages(userId, context, images, emitter);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_id", userId);
        body.put("question", question);
        // 复数 kc_codes，与 Python schema 对齐（修正此前 kc_code 单复数错配的丢参 bug）。
        if (kcCodes != null && !kcCodes.isEmpty()) {
          body.put("kc_codes", kcCodes);
        }
        if (effectiveContext != null && !effectiveContext.isBlank()) {
          body.put("context", effectiveContext);
        }
        // 因材施教：把画像摘要（认知风格/易错点）下传 graph，TutorAgent 据此分支首屏讲法。
        if (profileBrief != null && !profileBrief.isBlank()) {
          body.put("profile_brief", profileBrief);
        }

        GraphSseConsumer.consume(url, body, objectMapper, (event, data) -> {
          try {
            emitter.send(SseEmitter.event().name(event).data(data));
            if ("result".equals(event)) {
              onResult.accept(data);
            }
          } catch (IOException e) {
            log.debug("Tutor SSE emitter write failed, client likely disconnected", e);
          }
        });
        emitter.complete();
      } catch (IOException e) {
        log.error("Tutor graph SSE stream failed", e);
        emitter.completeWithError(e);
      }
    });
  }

  /**
   * 逐张把图片转成描述并前置到 context；任何单张失败都跳过（不抛），整体安全回退原 context。
   */
  private String enrichContextWithImages(
      Long userId, String context, List<String> images, SseEmitter emitter) {
    if (images == null || images.isEmpty() || !imageUnderstanding.isEnabled()) {
      return context;
    }
    int total = Math.min(images.size(), MAX_UNDERSTAND_IMAGES);
    List<String> descriptions = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      String img = images.get(i);
      if (img == null || img.isBlank()) {
        continue;
      }
      int index = i + 1;
      sendTrace(emitter, "image_understanding", "start", index, total, null);
      String desc;
      try {
        desc = imageUnderstanding.understand(userId, img, IMAGE_UNDERSTAND_PROMPT);
      } catch (Exception ex) {
        log.warn("Image understanding threw for image {}: {}", index, ex.getMessage());
        sendTrace(emitter, "image_understanding", "failed", index, total, null);
        continue;
      }
      if (desc == null || desc.isBlank()) {
        sendTrace(emitter, "image_understanding", "empty", index, total, null);
        continue;
      }
      sendTrace(emitter, "image_understanding", "ok", index, total, desc.length());
      descriptions.add(total > 1 ? "图" + index + "：" + desc.trim() : desc.trim());
    }
    if (descriptions.isEmpty()) {
      return context;
    }
    String prefix =
        "【学生上传的图片内容（AI 视觉识别，仅供参考）】\n" + String.join("\n\n", descriptions);
    return (context == null || context.isBlank()) ? prefix : prefix + "\n\n" + context;
  }

  private void sendTrace(
      SseEmitter emitter,
      String node,
      String status,
      Integer index,
      Integer total,
      Integer chars) {
    try {
      Map<String, Object> trace = new LinkedHashMap<>();
      trace.put("node", node);
      trace.put("status", status);
      trace.put("elapsed_ms", 0);
      if (index != null) {
        trace.put("index", index);
      }
      if (total != null) {
        trace.put("total", total);
      }
      if (chars != null) {
        trace.put("chars", chars);
      }
      emitter.send(SseEmitter.event().name("trace").data(objectMapper.writeValueAsString(trace)));
    } catch (IOException e) {
      log.debug("Tutor trace emit failed, client likely disconnected", e);
    }
  }
}
