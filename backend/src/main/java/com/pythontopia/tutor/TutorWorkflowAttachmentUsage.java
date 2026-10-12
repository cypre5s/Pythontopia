package com.pythontopia.tutor;

/** Aggregate of non-expired private Tutor attachments for a session and its owner. */
record TutorWorkflowAttachmentUsage(
    long sessionCount, long sessionBytes, long userCount, long userBytes) {}
