package com.pythontopia.submission.judge;

public record SandboxRunResult(
    boolean timedOut,
    int exitCode,
    String stdout,
    String stderr,
    long durationMs,
    Integer memoryKb,
    String errorKind) {

  public SandboxRunResult(
      boolean timedOut, int exitCode, String stdout, String stderr, long durationMs, String errorKind) {
    this(timedOut, exitCode, stdout, stderr, durationMs, null, errorKind);
  }

  public boolean isError() {
    return exitCode != 0 || timedOut || errorKind != null;
  }
}
