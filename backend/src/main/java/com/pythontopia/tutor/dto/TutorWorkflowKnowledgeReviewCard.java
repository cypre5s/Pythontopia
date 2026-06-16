package com.pythontopia.tutor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * 知识点复盘卡（KNOWLEDGE_REVIEW，graph {@code card_key=knowledge_review}）的 Java 契约投影。
 *
 * <p>字段 snake_case 对齐 graph {@code schemas/tutor_workflow.py KnowledgeReviewCard}。运行期卡片
 * payload 在 {@link TutorWorkflowCardDto} 中以原始 {@code JsonNode} 透传（graph 侧已 pydantic 强校验，
 * 本仓「graph 零回调 Java」），本 DTO 仅作跨端契约的 Java 侧权威强类型（snake_case ↔ camelCase 映射），
 * 供需要强类型消费 / 单测断言时反序列化，不改变 opaque 中继与投影路径。
 *
 * <p>{@code reviewContent} 由 LLM 产出并须引用课件页码；{@code relatedKcs} / {@code coursewareRefs}
 * 由 graph 据题目 KC 与 RAG 证据确定性回填（防伪造页码）。
 */
public record TutorWorkflowKnowledgeReviewCard(
    @JsonProperty("card_id") String cardId,
    @JsonProperty("review_content") String reviewContent,
    @JsonProperty("related_kcs") List<String> relatedKcs,
    @JsonProperty("courseware_refs") List<CoursewareRef> coursewareRefs,
    @JsonProperty("practice_suggestions") List<String> practiceSuggestions,
    @JsonProperty("mastery_snapshot") Map<String, Object> masterySnapshot,
    @JsonProperty("kc_selection_basis") String kcSelectionBasis,
    @JsonProperty("teaching_goal") String teachingGoal,
    @JsonProperty("checkpoint_prompt") String checkpointPrompt,
    @JsonProperty("mentor_role") String mentorRole,
    @JsonProperty("reflection_prompt") String reflectionPrompt) {

  /** 课件引用（据 RAG 证据回填，防伪造页码）。 */
  public record CoursewareRef(
      @JsonProperty("page_no") int pageNo,
      @JsonProperty("page_id") Long pageId,
      @JsonProperty("chapter_id") Long chapterId,
      String snippet) {}
}
