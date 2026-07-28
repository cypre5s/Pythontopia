package com.pythontopia.feynman;

import java.util.regex.Pattern;

/**
 * 费曼反演防作弊：拦截「直接贴答案 / 完整代码」式的教学输入与产出。
 *
 * <p>红线（AGENTS.md / 计划 §7）：不泄答；safety 拦 {@code answer_key} 与完整代码。
 * 学生必须用「讲原因」教会学弟，而不是把题解整段贴给它，因此命中以下任一即视为作弊样本：
 *
 * <ul>
 *   <li>含 {@code answer_key} 关键字；
 *   <li>含三反引号围栏代码块（``` ）——通常是整段题解粘贴；
 *   <li>出现完整程序结构定义行（{@code def } / {@code class } / {@code import } / {@code from ... import}）。
 * </ul>
 *
 * <p>对「用自然语言讲边界、给一个 range(1,6) 这样的短示例」这类正常教学不会误伤。
 */
public final class FeynmanAntiCheat {

  private static final Pattern FULL_PROGRAM =
      Pattern.compile("(?m)^\\s*(def\\s|class\\s|import\\s|from\\s+\\S+\\s+import\\s)");

  private FeynmanAntiCheat() {}

  public static boolean looksLikeAnswerDump(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    if (looksLikeLeakedAnswer(text)) {
      return true;
    }
    if (text.contains("```")) {
      return true;
    }
    return FULL_PROGRAM.matcher(text).find();
  }

  /**
   * 学弟产出代码本来就应是完整程序，因此只检查明确的答案标记，不复用教学文本的
   * {@code def/import/```} 规则。
   */
  public static boolean looksLikeLeakedAnswer(String code) {
    return code != null
        && !code.isBlank()
        && code.toLowerCase(java.util.Locale.ROOT).contains("answer_key");
  }

  /** 剥离 LLM 常附带的 Markdown 代码围栏；没有完整围栏时保留原文。 */
  public static String stripCodeFence(String code) {
    if (code == null) {
      return null;
    }
    String trimmed = code.strip();
    if (!trimmed.startsWith("```")) {
      return code;
    }
    int firstLineBreak = trimmed.indexOf('\n');
    if (firstLineBreak < 0) {
      return "";
    }
    String body = trimmed.substring(firstLineBreak + 1);
    int closingFence = body.lastIndexOf("```");
    return closingFence < 0 ? code : body.substring(0, closingFence);
  }
}
