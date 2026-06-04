package com.pythontopia.learner.probe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 探针目录（服务端权威）——玩中建画像③。
 *
 * <p>前端只上报 {@code (probeKey, choiceId)}，由本目录解析出「维度 / score 增量 / tag / 审计 source」。
 * 这样画像信号不可被客户端篡改（学习科学约束：信号必须服务端可信）。
 *
 * <p>设计为不可变静态数据：{@code ProbeController}（入参校验）与 {@code PassiveProfileRevisionListener}
 * （事件 → 维度映射）共用同一 {@link #lookup}，避免重复与漂移。{@code source} 一律复用 V091 已有枚举
 * {@code RESOURCE_FEEDBACK}，故无需新增数据库迁移。
 */
public final class ProbeCatalog {

  private ProbeCatalog() {}

  /** 单个探针选项解析出的画像信号。 */
  public record ProbeSignal(String dimensionKey, double scoreDelta, String tag, String source) {}

  private static final String SOURCE = "RESOURCE_FEEDBACK";

  /** probeKey -> (choiceId -> 信号)。LinkedHashMap 保留声明顺序，便于阅读。 */
  private static final Map<String, Map<String, ProbeSignal>> CATALOG = buildCatalog();

  private static Map<String, Map<String, ProbeSignal>> buildCatalog() {
    Map<String, Map<String, ProbeSignal>> catalog = new LinkedHashMap<>();

    // cog_intro：认知风格（入门偏好）→ cognitive_style
    Map<String, ProbeSignal> cogIntro = new LinkedHashMap<>();
    cogIntro.put("visual", new ProbeSignal("cognitive_style", 0.03, "visual", SOURCE));
    cogIntro.put("logical", new ProbeSignal("cognitive_style", 0.03, "logical", SOURCE));
    cogIntro.put("trial", new ProbeSignal("cognitive_style", 0.03, "trial", SOURCE));
    catalog.put("cog_intro", Map.copyOf(cogIntro));

    // fail_react：失败后反应 → emotion_persistence（坚持度行为信号）
    Map<String, ProbeSignal> failReact = new LinkedHashMap<>();
    failReact.put("retry", new ProbeSignal("emotion_persistence", 0.02, "retry", SOURCE));
    failReact.put("hint", new ProbeSignal("emotion_persistence", 0.0, "hint", SOURCE));
    failReact.put("skip", new ProbeSignal("emotion_persistence", -0.02, "skip", SOURCE));
    catalog.put("fail_react", Map.copyOf(failReact));

    // media_pref：媒介偏好 → learning_preference
    Map<String, ProbeSignal> mediaPref = new LinkedHashMap<>();
    mediaPref.put("video", new ProbeSignal("learning_preference", 0.01, "video", SOURCE));
    mediaPref.put("reading", new ProbeSignal("learning_preference", 0.01, "text", SOURCE));
    mediaPref.put("interactive", new ProbeSignal("learning_preference", 0.01, "interactive", SOURCE));
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
