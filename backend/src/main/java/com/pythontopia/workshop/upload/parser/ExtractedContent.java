package com.pythontopia.workshop.upload.parser;

/**
 * 解析结果：规范化后的纯文本及其字符数 / 行数。行尾统一为 {@code \n} 以保证 char/line 计数稳定，
 * {@code charCount} 为规范化后文本长度，{@code lineCount} 为非空文本的逻辑行数（空文本为 0）。
 */
public record ExtractedContent(String text, int charCount, int lineCount) {

  public static ExtractedContent of(String rawText) {
    String normalized = normalizeNewlines(rawText);
    return new ExtractedContent(normalized, normalized.length(), lineCount(normalized));
  }

  private static String normalizeNewlines(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static int lineCount(String text) {
    if (text.isEmpty()) {
      return 0;
    }
    int newlines = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        newlines++;
      }
    }
    // 末行无换行符时补一行（"a\nb" → 2，"a\nb\n" → 2，"a" → 1）。
    return text.charAt(text.length() - 1) == '\n' ? newlines : newlines + 1;
  }
}
