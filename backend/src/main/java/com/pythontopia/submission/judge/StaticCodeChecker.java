package com.pythontopia.submission.judge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Python 提交静态安全检查：两阶段过滤，阻止危险内置函数、系统模块导入与已知绕过技巧。
 *
 * <p>Phase 1：正则黑名单（快速拦截 16+ 类危险 token）。
 * Phase 2：AST 白名单（可选，由调用方在沙箱内跑 {@code ast_check.py}）。
 */
@Component
public class StaticCodeChecker {

  private static final Logger log = LoggerFactory.getLogger(StaticCodeChecker.class);
  private static final int MAX_CODE_LENGTH = 8192;
  private static final int AST_CHECK_TIMEOUT_SECONDS = 3;

  private final String pythonExecutable;

  public StaticCodeChecker(
      @Value("${pythontopia.judge.python:python3}") String pythonExecutable) {
    this.pythonExecutable = pythonExecutable;
  }

  // ---- Phase 1 正则黑名单（已补 known bypass patterns） ----

  private static final List<RegexRule> RULES =
      List.of(
          // 危险模块导入 (含别名/相对 import)
          rule("import\\s+os\\b", "banned_import:os"),
          rule("from\\s+os\\b", "banned_import:os"),
          rule("import\\s+subprocess\\b", "banned_import:subprocess"),
          rule("from\\s+subprocess\\b", "banned_import:subprocess"),
          rule("import\\s+socket\\b", "banned_import:socket"),
          rule("from\\s+socket\\b", "banned_import:socket"),
          rule("import\\s+threading\\b", "banned_import:threading"),
          rule("import\\s+multiprocessing\\b", "banned_import:multiprocessing"),
          rule("import\\s+ctypes\\b", "banned_import:ctypes"),
          rule("import\\s+pathlib\\b", "banned_import:pathlib"),
          rule("import\\s+shutil\\b", "banned_import:shutil"),
          rule("import\\s+sys\\b", "banned_import:sys"),
          rule("from\\s+sys\\b", "banned_import:sys"),
          rule("import\\s+signal\\b", "banned_import:signal"),
          rule("import\\s+fcntl\\b", "banned_import:fcntl"),
          rule("import\\s+resource\\b", "banned_import:resource"),
          // 危险内置函数
          rule("\\b__import__\\s*\\(", "banned_call:__import__"),
          rule("\\bopen\\s*\\(", "banned_call:open"),
          rule("\\beval\\s*\\(", "banned_call:eval"),
          rule("\\bexec\\s*\\(", "banned_call:exec"),
          rule("\\bcompile\\s*\\(", "banned_call:compile"),
          // 绕过技巧：getattr / setattr / delattr
          rule("\\bgetattr\\s*\\(", "banned_call:getattr"),
          rule("\\bsetattr\\s*\\(", "banned_call:setattr"),
          rule("\\bdelattr\\s*\\(", "banned_call:delattr"),
          // 绕过技巧：globals / locals / vars
          rule("\\bglobals\\s*\\(", "banned_call:globals"),
          rule("\\blocals\\s*\\(", "banned_call:locals"),
          // 绕过技巧：breakpoint (Python 3.7+)
          rule("\\bbreakpoint\\s*\\(", "banned_call:breakpoint"),
          // 绕过技巧：内置函数通过索引访问 __builtins__
          rule("__builtins__", "banned_access:__builtins__"),
          rule("__subclasses__", "banned_access:__subclasses__"),
          rule("__bases__", "banned_access:__bases__"),
          rule("__mro__", "banned_access:__mro__"),
          rule("__class__", "banned_access:__class__"),
          rule("__globals__", "banned_access:__globals__"),
          rule("__code__", "banned_access:__code__"),
          rule("__closure__", "banned_access:__closure__"),
          // 十六进制/unicode 编码绕过 eval/exec
          rule("\\\\x[0-9a-fA-F]{2}", "banned_escape:hex"),
          rule("\\\\u[0-9a-fA-F]{4}", "banned_escape:unicode"),
          rule("\\\\U[0-9a-fA-F]{8}", "banned_escape:unicode"),
          // os.system 等调用（即使 os 已通过别名导入）
          rule("os\\.(system|popen|execv|spawn|kill|remove|rmdir|unlink|chmod|chown|listdir|walk|environ)", "banned_os_call"),
          rule("subprocess\\.(call|run|Popen|check_call|check_output)", "banned_subprocess_call"));

  public CheckResult check(String code) {
    if (code == null) {
      return new CheckResult(false, "EMPTY_CODE");
    }
    if (code.length() > MAX_CODE_LENGTH) {
      return new CheckResult(false, "CODE_TOO_LONG");
    }
    for (RegexRule rule : RULES) {
      Matcher matcher = rule.pattern().matcher(code);
      if (matcher.find()) {
        return new CheckResult(false, rule.label() + ":" + matcher.group());
      }
    }
    CheckResult astResult = runAstWhitelist(code);
    if (!astResult.passed()) {
      return astResult;
    }
    return new CheckResult(true, null);
  }

  /** Phase 2：AST 白名单（python3 ast_whitelist.py），拦截正则难覆盖的绕过写法。 */
  private CheckResult runAstWhitelist(String code) {
    Path scriptFile = null;
    try {
      scriptFile = Files.createTempFile("pythontopia-ast-", ".py");
      try (InputStream in =
          getClass().getClassLoader().getResourceAsStream("judge/ast_whitelist.py")) {
        if (in == null) {
          log.warn("ast_whitelist.py missing from classpath; skipping AST phase");
          return new CheckResult(true, null);
        }
        Files.write(scriptFile, in.readAllBytes());
      }
      Process process =
          new ProcessBuilder(
                  List.of(pythonExecutable, scriptFile.toAbsolutePath().toString()))
              .redirectErrorStream(true)
              .start();
      process.getOutputStream().write(code.getBytes(StandardCharsets.UTF_8));
      process.getOutputStream().close();
      boolean finished = process.waitFor(AST_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return new CheckResult(false, "AST_CHECK_TIMEOUT");
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
          .trim();
      if (process.exitValue() != 0) {
        return new CheckResult(false, output.isEmpty() ? "AST_REJECTED" : output);
      }
      return new CheckResult(true, null);
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.warn("AST whitelist check failed: {}", exception.getMessage());
      return new CheckResult(false, "AST_CHECK_ERROR");
    } finally {
      if (scriptFile != null) {
        try {
          Files.deleteIfExists(scriptFile);
        } catch (IOException ignored) {
          // best effort cleanup
        }
      }
    }
  }

  private static RegexRule rule(String regex, String label) {
    return new RegexRule(Pattern.compile(regex, Pattern.MULTILINE), label);
  }

  private record RegexRule(Pattern pattern, String label) {}

  public record CheckResult(boolean passed, String failureReason) {}
}
