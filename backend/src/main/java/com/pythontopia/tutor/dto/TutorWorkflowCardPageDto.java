package com.pythontopia.tutor.dto;

import java.util.List;

/** One bounded page of a session's persisted Tutor card history. */
public record TutorWorkflowCardPageDto(
    List<TutorWorkflowCardDto> cards, TutorWorkflowCardCursorDto nextCursor) {}
