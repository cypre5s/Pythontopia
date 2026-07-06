package com.pythontopia.feynman;

import java.util.List;

/** 费曼 AI 学弟要镜像的真实误解，每条都必须有 event_id 证据来源。 */
public record Misconception(String tag, List<Long> evidenceEventIds, boolean resolved) {
  public Misconception {
    evidenceEventIds = evidenceEventIds == null ? List.of() : List.copyOf(evidenceEventIds);
  }
}
