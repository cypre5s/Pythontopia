package com.pythontopia.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.auth.security.Role;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminStudentDetailDto(
    Account account,
    JsonNode profileDimensions,
    List<KcMastery> mastery,
    List<LearningActivity> activity,
    List<Submission> submissions,
    List<ClassroomMembership> classrooms) {

  public record Account(
      long id,
      String username,
      String displayName,
      Role role,
      String status,
      OffsetDateTime createdAt) {}

  public record KcMastery(
      String kcName,
      Integer chapterOrder,
      BigDecimal masteryScore,
      BigDecimal confidence,
      OffsetDateTime updatedAt) {}

  public record LearningActivity(long id, String eventType, String source, OffsetDateTime occurredAt) {}

  public record Submission(
      long id,
      String problemTitle,
      String status,
      String judgeVerdict,
      int passedCases,
      int totalCases,
      OffsetDateTime submittedAt) {}

  public record ClassroomMembership(
      long classroomId,
      String classroomName,
      String teacherName,
      String status,
      OffsetDateTime joinedAt) {}
}
