package com.pythontopia.submission.judge;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StaticCodeChecker {

  private static final List<Pattern> BANNED_PATTERNS =
      List.of(
          Pattern.compile("(?m)^\\s*import\\s+os\\b"),
          Pattern.compile("(?m)^\\s*from\\s+os\\b"),
          Pattern.compile("(?m)^\\s*import\\s+subprocess\\b"),
          Pattern.compile("(?m)^\\s*from\\s+subprocess\\b"),
          Pattern.compile("(?m)^\\s*import\\s+socket\\b"),
          Pattern.compile("(?m)^\\s*from\\s+socket\\b"),
          Pattern.compile("(?m)^\\s*import\\s+threading\\b"),
          Pattern.compile("(?m)^\\s*import\\s+multiprocessing\\b"),
          Pattern.compile("(?m)^\\s*import\\s+ctypes\\b"),
          Pattern.compile("(?m)^\\s*import\\s+pathlib\\b"),
          Pattern.compile("(?m)^\\s*import\\s+shutil\\b"),
          Pattern.compile("\\b__import__\\s*\\("),
          Pattern.compile("\\bopen\\s*\\("),
          Pattern.compile("\\beval\\s*\\("),
          Pattern.compile("\\bexec\\s*\\("),
          Pattern.compile("\\bcompile\\s*\\("));

  public CheckResult check(String code) {
    if (code == null) {
      return new CheckResult(false, "EMPTY_CODE");
    }
    if (code.length() > 8192) {
      return new CheckResult(false, "CODE_TOO_LONG");
    }
    for (Pattern pattern : BANNED_PATTERNS) {
      Matcher matcher = pattern.matcher(code);
      if (matcher.find()) {
        return new CheckResult(false, "banned_token:" + matcher.group());
      }
    }
    return new CheckResult(true, null);
  }

  public record CheckResult(boolean passed, String failureReason) {}
}
