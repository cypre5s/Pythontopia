package com.pythontopia.media.dto;

/**
 * 语音听写（ASR）结果。{@code degraded=true} 表示服务端 ASR 未启用 / 不可用（无讯飞凭据、无 ffmpeg 转码、
 * 或转码失败），此时 {@code text} 为空，前端据此提示用户改用键盘输入，绝不阻塞答疑。
 */
public record AsrResponse(String text, boolean degraded) {

  /** 服务端 ASR 不可用时的空降级结果（无凭据 / 无 ffmpeg / 转码失败）。 */
  public static AsrResponse unavailable() {
    return new AsrResponse("", true);
  }

  public static AsrResponse of(String text) {
    return new AsrResponse(text == null ? "" : text, false);
  }
}
