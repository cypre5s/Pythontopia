package com.pythontopia.learner.probe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 探针目录（服务端权威）。
 *
 * <p>前端只上报 {@code (probeKey, choiceId)}，由本目录解析出「维度 / score 增量 / tag / 审计 source」，
 * 使画像信号不可被客户端篡改（信号必须服务端可信）。
 *
 * <p>不可变静态数据：{@code ProbeController}（入参校验）与 {@code PassiveProfileRevisionListener}
 * （事件 → 维度映射）共用同一 {@link #lookup}，避免重复与漂移。探针只保存明确的学习媒介偏好，不计入能力证据。
 */
public final class ProbeCatalog {

  private ProbeCatalog() {}

  /** 单个探针选项解析出的画像信号。 */
  public record ProbeSignal(String dimensionKey, Double scoreDelta, String tag, String source) {}

  private static final String SOURCE = "INTERVIEW";

  /** probeKey -> (choiceId -> 信号)。LinkedHashMap 保留声明顺序，便于阅读。 */
  private static final Map<String, Map<String, ProbeSignal>> CATALOG = buildCatalog();

  private static Map<String, Map<String, ProbeSignal>> buildCatalog() {
    Map<String, Map<String, ProbeSignal>> catalog = new LinkedHashMap<>();

    // 入门选择只是学生当前偏好的媒介，不代表固定认知类型。
    Map<String, ProbeSignal> cogIntro = new LinkedHashMap<>();
    cogIntro.put("visual", new ProbeSignal("learning_preference", null, "video", SOURCE));
    cogIntro.put("logical", new ProbeSignal("learning_preference", null, "text", SOURCE));
    cogIntro.put("trial", new ProbeSignal("learning_preference", null, "code", SOURCE));
    catalog.put("cog_intro", Map.copyOf(cogIntro));

    // media_pref：媒介偏好
    Map<String, ProbeSignal> mediaPref = new LinkedHashMap<>();
    mediaPref.put("video", new ProbeSignal("learning_preference", null, "video", SOURCE));
    mediaPref.put("reading", new ProbeSignal("learning_preference", null, "text", SOURCE));
    mediaPref.put("interactive", new ProbeSignal("learning_preference", null, "code", SOURCE));
    catalog.put("media_pref", Map.copyOf(mediaPref));

    return Map.copyOf(catalog);
  }

  /** 解析探针选择为画像信号；{@code probeKey}/{@code choiceId} 不在目录内时返回 empty。 */
  public static Optional<ProbeSignal> lookup(String probeKey, String choiceId) {
    if (probeKey == null || choiceId == null) {
      return Optional.empty();
    }
    Map<String, ProbeSignal> choices = CATALOG.get(probeKey);
    if (choices == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(choices.get(choiceId));
  }

  /** 入参校验：{@code (probeKey, choiceId)} 是否为目录内合法组合。 */
  public static boolean isValid(String probeKey, String choiceId) {
    return lookup(probeKey, choiceId).isPresent();
  }
}
