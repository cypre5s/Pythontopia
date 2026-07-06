package com.pythontopia.learner.path.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PathPlannerGraphRequest(
    @JsonProperty("user_id") Long userId,
    @JsonProperty("pack_id") Long packId,
    @JsonProperty("profile") ProfileSnapshot profile,
    @JsonProperty("kcs") List<KcSnapshot> kcs,
    @JsonProperty("recent_resources") List<ResourceUsage> recentResources,
    @JsonProperty("recent_submissions") List<ProblemSubmission> recentSubmissions,
    @JsonProperty("classroom_quests") List<ClassroomQuestSnapshot> classroomQuests,
    @JsonProperty("error_pattern_kcs") List<String> errorPatternKcs) {

  public record ProfileSnapshot(
      @JsonProperty("has_profile") boolean hasProfile,
      @JsonProperty("major") String major,
      @JsonProperty("python_self_rated_level") int pythonSelfRatedLevel,
      @JsonProperty("days_since_last_profile_update") int daysSinceLastProfileUpdate) {}

  public record KcSnapshot(
      @JsonProperty("kc_code") String kcCode,
      @JsonProperty("mastery_score") double masteryScore,
      @JsonProperty("confidence") double confidence,
      @JsonProperty("unlocked") boolean unlocked,
      @JsonProperty("chapter_id") Long chapterId,
      @JsonProperty("prerequisite_codes") List<String> prerequisiteCodes) {}

  public record ResourceUsage(
      @JsonProperty("kc_code") String kcCode,
      @JsonProperty("type") String type,
      @JsonProperty("created_at") String createdAt) {}

  public record ProblemSubmission(
      @JsonProperty("problem_id") Long problemId,
      @JsonProperty("kc_code") String kcCode,
      @JsonProperty("passed") boolean passed) {}

  public record ClassroomQuestSnapshot(
      @JsonProperty("classroom_quest_id") Long classroomQuestId,
      @JsonProperty("classroom_id") Long classroomId,
      @JsonProperty("kc_codes") List<String> kcCodes,
      @JsonProperty("node_type") String nodeType,
      @JsonProperty("title") String title,
      @JsonProperty("due_date") String dueDate) {}
}
