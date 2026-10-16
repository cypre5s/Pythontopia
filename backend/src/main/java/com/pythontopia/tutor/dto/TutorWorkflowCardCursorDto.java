package com.pythontopia.tutor.dto;

/** Stable cursor for loading cards older than a sequence and row ID pair. */
public record TutorWorkflowCardCursorDto(int seq, long id) {}
