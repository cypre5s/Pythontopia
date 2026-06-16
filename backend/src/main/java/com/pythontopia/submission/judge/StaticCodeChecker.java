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
    selfCheck();
  }

  /**
   * 启动自检：用最小合法代码 {@code pass} 跑一次 AST 白名单。若解释器缺标准库（如 {@code ast}）或无法启动，
   * 记 {@code log.error} 提示运维（演示阻断级，根因：运行镜像曾装 python3-minimal），
   * 但<strong>绝不</strong> fail-fast 阻断应用启动。
   */
  private void selfCheck() {
    CheckResult probe;
    try {
      probe = runAstWhitelist("pass");
    } catch (RuntimeException ex) {
      log.error(
          "StaticCodeChecker 启动自检异常（已忽略，不阻断启动）：python={} err={}",
          pythonExecutable,
          ex.getMessage());
      return;
    }
    if (probe.systemError()) {
      log.error(
          "StaticCodeChecker 启动自检失败：python 解释器 '{}' 无法运行 AST 白名单（{}）；"
              + "运行镜像很可能装了 python3-minimal（缺 ast 等标准库），将导致全部提交被误判。"
              + "请改装完整 python3。",
          pythonExecutable,
          probe.failureReason());
    } else if (!probe.passed()) {
      log.warn(
          "StaticCodeChecker 启动自检：AST 白名单对 `pass` 返回非通过（{}），请核对 ast_whitelist.py。",
          probe.failureReason());
    } else {
      log.info("StaticCodeChecker 启动自检通过：AST 白名单可用（python={}）。", pythonExecutable);
    }
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
      return rejected("EMPTY_CODE");
    }
    if (code.length() > MAX_CODE_LENGTH) {
      return rejected("CODE_TOO_LONG");
    }
    for (RegexRule rule : RULES) {
      Matcher matcher = rule.pattern().matcher(code);
      if (matcher.find()) {
        return rejected(rule.label() + ":" + matcher.group());
      }
    }
    CheckResult astResult = runAstWhitelist(code);
    if (!astResult.passed()) {
      return astResult;
    }
    return accepted();
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
          return accepted();
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
        return rejected("AST_CHECK_TIMEOUT");
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
          .trim();
      if (process.exitValue() != 0) {
        // 解释器自身缺模块 / 脚本崩溃（Python traceback）≠ 学生代码违规：归为基础设施错误，
        // 不把环境问题判成学生的 CE（任务 A 根因：python3-minimal 缺 ast，首行 import ast 即 ModuleNotFoundError）。
        // 学生违规走 ast_whitelist.py 的干净输出（banned_* / syntax_error），不含 traceback。
        if (looksLikeInterpreterFailure(output)) {
          log.warn(
              "AST whitelist interpreter failure (treated as SYSTEM_ERROR, not student CE): {}",
              firstLine(output));
          return infraError("SYSTEM_ERROR:ast_whitelist_interpreter");
        }
        return rejected(output.isEmpty() ? "AST_REJECTED" : output);
      }
      return accepted();
    } catch (IOException exception) {
      // 子进程无法启动（fork/exec 失败、解释器缺失）属基础设施错误，而非学生代码问题。
      log.warn(
          "AST whitelist could not launch (treated as SYSTEM_ERROR): {}", exception.getMessage());
      return infraError("SYSTEM_ERROR:ast_whitelist_launch_failed");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      log.warn(
          "AST whitelist interrupted (treated as SYSTEM_ERROR): {}", exception.getMessage());
      return infraError("SYSTEM_ERROR:ast_whitelist_interrupted");
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

  /**
   * 判断 AST 子进程的合并输出是否为「解释器自身故障」而非学生代码违规。学生违规由 ast_whitelist.py
   * 打印干净标签（如 {@code banned_import:socket}、{@code syntax_error:...}）且不含 Python traceback；
   * 一旦出现模块缺失 / 导入失败 / traceback，即判定为基础设施错误。
   */
  private static boolean looksLikeInterpreterFailure(String output) {
    if (output == null || output.isBlank()) {
      return false;
    }
    return output.contains("ModuleNotFoundError")
        || output.contains("No module named")
        || output.contains("ImportError")
        || output.contains("Traceback (most recent call last)");
  }

  private static String firstLine(String output) {
    if (output == null) {
      return "";
    }
    int idx = output.indexOf('\n');
    return idx >= 0 ? output.substring(0, idx) : output;
  }

  private static CheckResult accepted() {
    return new CheckResult(true, null, false);
  }

  private static CheckResult rejected(String reason) {
    return new CheckResult(false, reason, false);
  }

  /** 基础设施错误（解释器缺模块/无法启动等）：{@code passed=false} 仍 fail-closed 拦截，但带 SYSTEM_ERROR 信号。 */
  private static CheckResult infraError(String reason) {
    return new CheckResult(false, reason, true);
  }

  private record RegexRule(Pattern pattern, String label) {}

  /**
   * 静态检查结果。
   *
   * @param passed 是否通过（true=放行进入沙箱判题）
   * @param failureReason 失败原因标签（{@code passed=false} 时有意义）
   * @param systemError 是否为基础设施错误（解释器缺模块/无法启动等）。<strong>跨包契约</strong>：
   *     调用方（{@code JudgeService}）应在 {@code systemError()} 为 true 时判为 {@code Verdict.SYSTEM_ERROR}
   *     （配 {@code SubmissionStatus.ERROR}），而非把环境问题当成学生代码的 CE。
   */
  public record CheckResult(boolean passed, String failureReason, boolean systemError) {}
}
