package com.pythontopia.learner.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 画像访谈单轮请求。{@code history} 为本轮之前的多轮对话（可空），用于让 ProfileAgent 具备多轮记忆，
 * 避免「单轮无上下文」导致重复提问。
 */
public record ProfileTurnRequest(
    @NotBlank @Size(max = 2000) String userMessage,
    @Valid @Size(max = 20) List<ProfileMessageDto> history) {

  public List<ProfileMessageDto> resolvedHistory() {
    return history == null ? List.of() : history;
  }
}
