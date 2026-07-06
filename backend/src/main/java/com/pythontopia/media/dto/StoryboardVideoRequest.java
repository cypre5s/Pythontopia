package com.pythontopia.media.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 合成故事板短视频请求：前端把当前故事板的分镜（已在内存中）直接传来，后端按图序列 + TTS 合成 mp4。
 */
public record StoryboardVideoRequest(@NotEmpty @Size(max = 8) List<Scene> scenes) {

  public record Scene(
      String imagePrompt,
      String narration,
      String voice,
      Integer durationSeconds,
      List<String> kcCodes) {

    public int resolvedDuration() {
      return durationSeconds == null ? 20 : durationSeconds;
    }

    public List<String> resolvedKcCodes() {
      return kcCodes == null ? List.of() : kcCodes;
    }
  }
}
