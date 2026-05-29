package com.pythontopia.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TtsRequest(
    @NotBlank @Size(max = 200) String text,
    String voice) {

  public String resolvedVoice() {
    return voice != null && !voice.isBlank() ? voice : "x4_yezi";
  }
}
