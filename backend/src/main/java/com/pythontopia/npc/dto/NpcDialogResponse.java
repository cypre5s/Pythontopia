package com.pythontopia.npc.dto;

import java.util.List;

public record NpcDialogResponse(String replyMd, List<Long> citedEventIds, boolean grounded) {
  public NpcDialogResponse {
    citedEventIds = citedEventIds == null ? List.of() : List.copyOf(citedEventIds);
  }
}
