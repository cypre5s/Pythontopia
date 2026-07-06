package com.pythontopia.submission.judge;

public record SandboxRunResult(
    boolean timedOut,
    int exitCode,
    String stdout,
    String stderr,
    long durationMs,
    String errorKind) {

  public boolean isError() {
    return exitCode != 0 || timedOut || errorKind != null;
  }
}
