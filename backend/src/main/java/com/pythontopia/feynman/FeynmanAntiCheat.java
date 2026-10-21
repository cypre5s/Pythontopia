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
 *
 * <p><strong>适用面</strong>：{@link #looksLikeAnswerDump} 只适用于<em>自然语言教学文本</em>。
 * 学弟产出的 {@code produced_code} 本来就该是完整程序（第 6/7 章的 KC 必然出现 {@code def } /
 * {@code import }），对它只能用 {@link #looksLikeLeakedAnswer}，否则合法代码会被判「泄答」。
 */
public final class FeynmanAntiCheat {

  private static final Pattern FULL_PROGRAM =
      Pattern.compile("(?m)^\\s*(def\\s|class\\s|import\\s|from\\s+\\S+\\s+import\\s)");

  private FeynmanAntiCheat() {}

  /** 教学文本侧：answer_key / 三反引号围栏 / 完整程序结构定义行，任一命中即视为贴答案。 */
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
   * 产出代码侧：只查 {@code answer_key} 泄答标记。
   *
   * <p>围栏与 {@code def/class/import} 是「自然语言里混进整段题解」的特征，对一段本该是完整程序的
   * 产出码毫无意义——用它判产出码会把「函数与变量作用域」「文件操作」这类 KC 的正确解一律误杀。
   */
  public static boolean looksLikeLeakedAnswer(String code) {
    if (code == null || code.isBlank()) {
      return false;
    }
    return code.toLowerCase().contains("answer_key");
  }

  /**
   * 剥掉 Markdown 三反引号围栏（LLM 常把代码包在 ```python ... ``` 里），得到可直接送沙箱的源码。
   * 无围栏时原样返回。
   */
  public static String stripCodeFence(String code) {
    if (code == null) {
      return null;
    }
    String trimmed = code.strip();
    if (!trimmed.startsWith("```")) {
      return code;
    }
    int firstBreak = trimmed.indexOf('\n');
    if (firstBreak < 0) {
      // 只有一行 ```python，没有正文。
      return "";
    }
    String body = trimmed.substring(firstBreak + 1);
    int closing = body.lastIndexOf("```");
    return closing < 0 ? body : body.substring(0, closing);
  }
}
