package com.pythontopia.learner.event;

import java.util.Set;

/** The only learning event types a browser client may report directly. */
public final class ClientLearningEventPolicy {

  private static final Set<String> ALLOWED_EVENT_TYPES = Set.of("quiz_finished", "answer_feedback");

  private ClientLearningEventPolicy() {}

  public static boolean allows(String eventType) {
    return eventType != null && ALLOWED_EVENT_TYPES.contains(eventType);
  }
}
