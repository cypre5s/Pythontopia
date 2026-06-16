package com.pythontopia.tutor.dto;

/** 导学工作流会话投影（id 为内部主键；sessionKey 是 graph 侧 session_id）。 */
public record TutorWorkflowSessionDto(
    Long id, String sessionKey, Long problemId, String phase, int seq, String status) {}
