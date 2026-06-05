package com.pythontopia.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 语音合成请求。{@code voice} 为讯飞发音人 vcn，缺省 {@code x4_yezi}。 */
public record TtsRequest(
    @NotBlank @Size(max = 200) String text,
    String voice) {

  public String resolvedVoice() {
    return voice != null && !voice.isBlank() ? voice : "x4_yezi";
  }
}
