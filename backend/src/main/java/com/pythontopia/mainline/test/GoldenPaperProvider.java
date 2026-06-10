package com.pythontopia.mainline.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 章测 golden 兜底题组的存储/查找机制（r15 计划 §4.1.2/§4.1.3）。
 *
 * <p>按章查找 classpath 资源 {@code mainline/golden/chapter-<n>.json}，缺失时回退
 * {@code chapter-default.json}。graph 生成失败重试耗尽后、或 start 时仍未就绪时使用，保证<b>考试期间零 LLM</b>。
 *
 * <p>TODO(课程策划/教师授权)：当前仅内置「结构合法但内容为占位」的默认题组（通用 Python 通识，无 evidence_page_ids，
 * 题面已显式标注「占位·需教师授权」）。必须按章替换为<b>证据接地</b>的真实章测题，方可上线计星。
 * 本类<b>不</b>伪造课件页码/题目内容（遵守 AGENTS.md 防幻觉）。
 */
@Component
public class GoldenPaperProvider {

  private static final Logger log = LoggerFactory.getLogger(GoldenPaperProvider.class);
  private static final String BASE = "mainline/golden/";

  private final ObjectMapper objectMapper;

  public GoldenPaperProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 返回该章 golden 题面（含 {@code items}）。找不到任何兜底资源属配置错误，fail-fast。 */
  public JsonNode loadQuestions(int chapter) {
    JsonNode paper = read("chapter-" + chapter + ".json");
    if (paper == null) {
      paper = read("chapter-default.json");
    }
    if (paper == null || !paper.has("items") || !paper.get("items").isArray()) {
      throw new ChapterTestGenerationException(
          "golden 兜底题组缺失或非法（chapter=" + chapter + "），无法发卷");
    }
    ObjectNode questions = objectMapper.createObjectNode();
    questions.set("items", paper.get("items"));
    return questions;
  }

  /** golden 来源的生成审计（标注 fallback，便于教师/审计区分非 AI 题）。 */
  public JsonNode meta(int chapter) {
    ObjectNode meta = objectMapper.createObjectNode();
    meta.put("source", "golden");
    meta.put("placeholder", true);
    meta.put("chapter", chapter);
    meta.put("generated_at", Instant.now().toString());
    meta.put("todo", "替换为教师授权的证据接地章测题后方可正式计星");
    return meta;
  }

  private JsonNode read(String name) {
    ClassPathResource resource = new ClassPathResource(BASE + name);
    if (!resource.exists()) {
      return null;
    }
    try (InputStream in = resource.getInputStream()) {
      return objectMapper.readTree(in);
    } catch (IOException ex) {
      log.warn("读取 golden 资源失败 {}: {}", name, ex.getMessage());
      return null;
    }
  }
}
