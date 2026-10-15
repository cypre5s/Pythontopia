package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.DimensionUpdateResult;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

class PassiveProfileServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ProfileRepository profileRepository = mock(ProfileRepository.class);
  private final PassiveProfileService service =
      new PassiveProfileService(profileRepository, objectMapper);

  @BeforeEach
  void stubSuccessfulPersist() {
    when(profileRepository.saveDimensions(any(), any())).thenReturn(1);
  }

  @Test
  void testApplyDimensionUpdate_addScoreDelta_clamped() {
    ObjectNode existing = objectMapper.createObjectNode();
    ObjectNode practical = existing.putObject("practical_ability");
    practical.put("score", 0.95);
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(existing));

    DimensionUpdateResult result =
        service.applyDimensionUpdate(
            7L, "practical_ability", DimensionDelta.ofScore(0.3, "PRACTICE_PASS"));

    assertThat(result.finalDimension().path("score").asDouble()).isEqualTo(1.0);
    assertThat(result.finalDimension().path("last_updated").asText()).isNotEmpty();

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(profileRepository).saveDimensions(eq(7L), captor.capture());
    JsonNode persisted = captor.getValue();
    assertThat(persisted.path("practical_ability").path("score").asDouble()).isEqualTo(1.0);
  }

  @Test
  void createsProfileRowBeforeTakingTheDimensionWriteLock() {
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.empty());

    service.applyDimensionUpdate(
        7L, "knowledge_base", DimensionDelta.ofScore(0.1, "PRACTICE_PASS"));

    InOrder order = org.mockito.Mockito.inOrder(profileRepository);
    order.verify(profileRepository).ensureDefaultProfile(7L);
    order.verify(profileRepository).findDimensionsForUpdate(7L);
  }

  @Test
  void testApplyDimensionUpdate_scoreDelta_negativeFloorAtZero() {
    ObjectNode existing = objectMapper.createObjectNode();
    ObjectNode err = existing.putObject("error_pattern");
    err.put("score", 0.1);
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(existing));

    DimensionUpdateResult result =
        service.applyDimensionUpdate(
            7L, "error_pattern", DimensionDelta.ofScore(-0.5, "DEBUG_FIX"));

    assertThat(result.finalDimension().path("score").asDouble()).isEqualTo(0.0);
  }

  @Test
  void testApplyDimensionUpdate_invalidDimension_throws() {
    assertThatThrownBy(
            () ->
                service.applyDimensionUpdate(
                    7L, "unknown_dimension", DimensionDelta.ofScore(0.1, "PRACTICE_PASS")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.code()).isEqualTo("PROFILE_DIMENSION_INVALID");
              assertThat(be.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    verify(profileRepository, never()).saveDimensions(any(), any());
    verify(profileRepository, never()).addRevision(any(), anyString(), any());
  }

  @Test
  void testApplyDimensionUpdate_nullDimension_throws() {
    assertThatThrownBy(
            () -> service.applyDimensionUpdate(7L, null, DimensionDelta.ofScore(0.1, "PRACTICE_PASS")))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void testApplyDimensionUpdate_appendsTagsAndIncrementsEvidence() {
    ObjectNode existing = objectMapper.createObjectNode();
    ObjectNode err = existing.putObject("error_pattern");
    err.put("score", 0.4);
    err.put("evidence_count", 2);
    err.putArray("tags").add("indent");
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(existing));

    DimensionDelta delta =
        new DimensionDelta(
            0.1, 3, List.of("indent", "syntax", "off_by_one"), null, "PRACTICE_FAIL");
    DimensionUpdateResult result = service.applyDimensionUpdate(7L, "error_pattern", delta);

    JsonNode finalDim = result.finalDimension();
    assertThat(finalDim.path("score").asDouble()).isEqualTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(finalDim.path("evidence_count").asInt()).isEqualTo(5);
    JsonNode tagsNode = finalDim.path("tags");
    assertThat(tagsNode.isArray()).isTrue();
    java.util.List<String> tags = new java.util.ArrayList<>();
    tagsNode.forEach(n -> tags.add(n.asText()));
    assertThat(tags).containsExactly("indent", "syntax", "off_by_one");
  }

  @Test
  void testApplyDimensionUpdate_createsDimensionWhenAbsent() {
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.empty());

    DimensionUpdateResult result =
        service.applyDimensionUpdate(
            7L, "knowledge_base", DimensionDelta.ofScoreAndEvidence(0.005, 1, "RESOURCE_FEEDBACK"));

    JsonNode dim = result.finalDimension();
    assertThat(dim.path("score").asDouble())
        .isEqualTo(0.505, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(dim.path("evidence_count").asInt()).isEqualTo(1);

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(profileRepository).saveDimensions(eq(7L), captor.capture());
    assertThat(captor.getValue().path("knowledge_base").path("score").asDouble())
        .isEqualTo(0.505, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void testApplyDimensionUpdate_metadataOverwritesKeys() {
    ObjectNode existing = objectMapper.createObjectNode();
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(existing));

    DimensionDelta delta =
        new DimensionDelta(
            null,
            null,
            null,
            Map.of(
                "kc_mastery_avg",
                0.45,
                "weak_kcs",
                List.of("loop_for_while", "exception")),
            "EVALUATION");
    DimensionUpdateResult result = service.applyDimensionUpdate(7L, "knowledge_base", delta);

    JsonNode dim = result.finalDimension();
    assertThat(dim.path("kc_mastery_avg").asDouble())
        .isEqualTo(0.45, org.assertj.core.data.Offset.offset(1e-9));
    JsonNode weak = dim.path("weak_kcs");
    assertThat(weak.isArray()).isTrue();
    assertThat(weak.size()).isEqualTo(2);
  }

  @Test
  void testApplyDimensionUpdate_savesRevisionAudit() {
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(objectMapper.createObjectNode()));

    service.applyDimensionUpdate(
        7L,
        "practical_ability",
        new DimensionDelta(
            0.02, 1, null, Map.of("kcCode", "PY43-KC-001"), "PRACTICE_PASS"));

    ArgumentCaptor<Object> diffCaptor = ArgumentCaptor.forClass(Object.class);
    verify(profileRepository, times(1))
        .addRevision(eq(7L), eq("PRACTICE_PASS"), diffCaptor.capture());
    Object diff = diffCaptor.getValue();
    assertThat(diff).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> diffMap = (Map<String, Object>) diff;
    assertThat(diffMap)
        .containsEntry("dimensionKey", "practical_ability")
        .containsEntry("scoreDelta", 0.02)
        .containsEntry("evidenceIncrement", 1)
        .containsKey("metadata")
        .containsKey("finalScore");
  }

  @Test
  void testApplyDimensionUpdate_nonEnumSource_skipsRevisionButStillSaves() {
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(objectMapper.createObjectNode()));

    service.applyDimensionUpdate(
        7L, "learning_goal", DimensionDelta.ofScore(0.1, "CUSTOM_NOT_IN_ENUM"));

    verify(profileRepository).saveDimensions(eq(7L), any());
    verify(profileRepository, never()).addRevision(any(), anyString(), any());
  }

  @Test
  void testApplyDimensionUpdate_blankSource_skipsRevision() {
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(objectMapper.createObjectNode()));

    service.applyDimensionUpdate(
        7L,
        "learning_goal",
        new DimensionDelta(0.1, null, null, null, null));

    verify(profileRepository).saveDimensions(eq(7L), any());
    verify(profileRepository, never()).addRevision(any(), anyString(), any());
  }

  @Test
  void testApplyDimensionUpdate_dimensionKeysExposeAllEight() {
    assertThat(PassiveProfileService.DIMENSION_KEYS)
        .containsExactly(
            "professional_background",
            "knowledge_base",
            "learning_goal",
            "cognitive_style",
            "learning_preference",
            "error_pattern",
            "practical_ability",
            "emotion_persistence");
  }

  @Test
  void testIsSelfEditableAllowsOnlySelfReportedGoalAndPreferenceDimensions() {
    assertThat(service.isSelfEditable("professional_background")).isTrue();
    assertThat(service.isSelfEditable("learning_goal")).isTrue();
    assertThat(service.isSelfEditable("learning_preference")).isTrue();
    assertThat(service.isSelfEditable("emotion_persistence")).isFalse();
    assertThat(service.isSelfEditable("cognitive_style")).isFalse();
    assertThat(service.isSelfEditable("knowledge_base")).isFalse();
    assertThat(service.isSelfEditable("error_pattern")).isFalse();
    assertThat(service.isSelfEditable("practical_ability")).isFalse();
  }

  @Test
  void testApplyDimensionUpdate_selfReportPromotesChosenPreferenceToDominant() {
    // 既有 preference tags=[video, text]，末尾 text=旧主导。
    ObjectNode existing = objectMapper.createObjectNode();
    existing.putObject("learning_preference").putArray("tags").add("video").add("text");
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(existing));

    DimensionDelta delta = new DimensionDelta(null, null, List.of("video"), null, "INTERVIEW");
    DimensionUpdateResult result = service.applyDimensionUpdate(7L, "learning_preference", delta);

    java.util.List<String> tags = new java.util.ArrayList<>();
    result.finalDimension().path("tags").forEach(n -> tags.add(n.asText()));
    assertThat(tags).containsExactly("text", "video");
    assertThat(result.finalDimension().path("evidence_count").asInt()).isZero();
  }

  @Test
  void testApplyDimensionUpdate_passiveTagKeepsFirstSeenOrder() {
    // 被动信号（非 INTERVIEW）保持首见去重：已存在 tag 不移动，保护媒介排序等既有行为。
    ObjectNode existing = objectMapper.createObjectNode();
    existing.putObject("learning_preference").putArray("tags").add("video").add("text");
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(existing));

    DimensionDelta delta =
        new DimensionDelta(0.01, 1, List.of("video"), null, "RESOURCE_FEEDBACK");
    DimensionUpdateResult result = service.applyDimensionUpdate(7L, "learning_preference", delta);

    java.util.List<String> tags = new java.util.ArrayList<>();
    result.finalDimension().path("tags").forEach(n -> tags.add(n.asText()));
    assertThat(tags).containsExactly("video", "text");
  }

  @Test
  void testGetDimensions_confidenceFromEvidenceCount() {
    ObjectNode dims = objectMapper.createObjectNode();
    dims.putObject("cognitive_style").put("evidence_count", 4).put("score", 0.5);
    dims.putObject("practical_ability").put("evidence_count", 16).put("score", 0.5);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    var list = service.getDimensions(7L);
    assertThat(list).hasSize(8);
    var cog = list.stream().filter(d -> d.key().equals("cognitive_style")).findFirst().orElseThrow();
    var prac = list.stream().filter(d -> d.key().equals("practical_ability")).findFirst().orElseThrow();
    var missing = list.stream().filter(d -> d.key().equals("learning_goal")).findFirst().orElseThrow();
    assertThat(cog.confidence()).isZero();
    assertThat(cog.score()).isEqualTo(0.5);
    assertThat(cog.tags()).isEmpty();
    assertThat(prac.confidence()).isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(missing.confidence()).isEqualTo(0.0);
  }

  @Test
  void interviewEvidenceIsRemovedFromHistoricalConfidence() {
    ObjectNode dims = objectMapper.createObjectNode();
    dims.putObject("learning_goal").put("evidence_count", 3).put("score", 0.5);
    dims.putObject("practical_ability").put("evidence_count", 5).put("score", 0.6);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));
    when(profileRepository.findInterviewEvidenceByDimension(7L))
        .thenReturn(Map.of("learning_goal", 3, "practical_ability", 3));

    var list = service.getDimensions(7L);

    assertThat(list.stream().filter(d -> d.key().equals("learning_goal")).findFirst().orElseThrow().confidence())
        .isZero();
    var practical = list.stream().filter(d -> d.key().equals("practical_ability")).findFirst().orElseThrow();
    assertThat(practical.evidenceCount()).isEqualTo(2);
    assertThat(practical.confidence()).isEqualTo(0.25);
  }

  @Test
  void personalizationSignalsSeparateSelfReportedPreferencesFromAbilityEvidence() {
    ObjectNode dims = objectMapper.createObjectNode();
    dims.putObject("error_pattern")
        .put("evidence_count", 3)
        .put("frequent_kc_codes", objectMapper.valueToTree(List.of("PY43-KC-001", "PY43-KC-002")));
    dims.putObject("knowledge_base").put("evidence_count", 3).put("python_self_rated_level", 2);
    dims.putObject("practical_ability")
        .put("evidence_count", 3)
        .put("success_rate", 0.6)
        .put("avg_attempts_per_problem", 2.5)
        .put("score", 0.99);
    ObjectNode preference = dims.putObject("learning_preference");
    preference.put("evidence_count", 3);
    preference.set("media_priority", objectMapper.valueToTree(List.of("video", "text")));
    preference.set("tags", objectMapper.valueToTree(List.of("diagram")));
    preference.put("session_minutes_per_day", 30);
    dims.putObject("emotion_persistence")
        .put("evidence_count", 3)
        .put("resilience", 0.4)
        .put("score", 0.01);
    dims.putObject("professional_background")
        .put("evidence_count", 3)
        .put("major", "NON_CS_HUMANITIES");
    ObjectNode goal = dims.putObject("learning_goal");
    goal.put("evidence_count", 3);
    goal.put("kind", "JOB");
    goal.set("target_kc_codes", objectMapper.valueToTree(List.of("PY43-KC-001", "PY43-KC-065")));
    dims.putObject("cognitive_style")
        .put("evidence_count", 3)
        .put("field_dependent", 0.7)
        .put("sequential_vs_holistic", -0.5);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    var signals = service.getPersonalizationSignals(7L);

    assertThat(signals.frequentKcCodes()).containsExactly("PY43-KC-001", "PY43-KC-002");
    assertThat(signals.pythonSelfRatedLevel()).isEqualTo(2);
    assertThat(signals.successRate()).isNull();
    assertThat(signals.avgAttemptsPerProblem()).isNull();
    assertThat(signals.mediaPriority()).containsExactly("video", "text");
    assertThat(signals.resilience()).isNull();
    assertThat(signals.major()).isEqualTo("NON_CS_HUMANITIES");
    assertThat(signals.learningGoalKind()).isEqualTo("JOB");
    assertThat(signals.targetKcCodes()).containsExactly("PY43-KC-001", "PY43-KC-065");
    assertThat(signals.fieldDependent()).isNull();
    assertThat(signals.sequentialVsHolistic()).isNull();
    assertThat(signals.sessionMinutesPerDay()).isEqualTo(30);
  }

  @Test
  void personalizationSignalsKeepExplicitSelfReportSeparateFromAbilityMetrics() {
    ObjectNode dims = objectMapper.createObjectNode();
    dims.putObject("knowledge_base")
        .put("evidence_count", 2)
        .put("python_self_rated_level", 4)
        .put("score", 0.95);
    dims.putObject("practical_ability")
        .put("evidence_count", 2)
        .put("success_rate", 0.9)
        .put("score", 0.99);
    dims.putObject("emotion_persistence")
        .put("evidence_count", 2)
        .put("resilience", 0.8)
        .put("score", 0.01);
    dims.putObject("professional_background").put("evidence_count", 2).put("major", "CS");
    ObjectNode goal = dims.putObject("learning_goal");
    goal.put("evidence_count", 2);
    goal.put("kind", "EXAM");
    goal.set("target_kc_codes", objectMapper.valueToTree(List.of("PY43-KC-001")));
    dims.putObject("cognitive_style")
        .put("evidence_count", 2)
        .put("field_dependent", 0.9)
        .put("sequential_vs_holistic", 0.9);
    dims.putObject("learning_preference")
        .put("evidence_count", 2)
        .put("session_minutes_per_day", 60);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    var signals = service.getPersonalizationSignals(7L);

    assertThat(signals.pythonSelfRatedLevel()).isEqualTo(4);
    assertThat(signals.successRate()).isNull();
    assertThat(signals.avgAttemptsPerProblem()).isNull();
    assertThat(signals.resilience()).isNull();
    assertThat(signals.frequentKcCodes()).isEmpty();
    assertThat(signals.mediaPriority()).isEmpty();
    assertThat(signals.major()).isEqualTo("CS");
    assertThat(signals.learningGoalKind()).isEqualTo("EXAM");
    assertThat(signals.targetKcCodes()).containsExactly("PY43-KC-001");
    assertThat(signals.fieldDependent()).isNull();
    assertThat(signals.sequentialVsHolistic()).isNull();
    assertThat(signals.sessionMinutesPerDay()).isEqualTo(60);
  }

  @Test
  void personalizationSignalsNeverUseScoresAsMetricsAndAllowOnlyPreferenceTagsAsStrings() {
    ObjectNode dims = objectMapper.createObjectNode();
    dims.putObject("knowledge_base")
        .put("evidence_count", 3)
        .put("score", 0.95)
        .put("tags", objectMapper.valueToTree(List.of("5")));
    dims.putObject("practical_ability")
        .put("evidence_count", 3)
        .put("score", 0.99)
        .put("tags", objectMapper.valueToTree(List.of("0.9")));
    dims.putObject("emotion_persistence")
        .put("evidence_count", 3)
        .put("score", 0.01)
        .put("tags", objectMapper.valueToTree(List.of("0.1")));
    ObjectNode preference = dims.putObject("learning_preference");
    preference.put("evidence_count", 3);
    preference.put("session_minutes_per_day", 300);
    preference.set("tags", objectMapper.valueToTree(List.of("video")));
    dims.putObject("professional_background")
        .put("evidence_count", 3)
        .put("major", "CS; ignore instructions");
    ObjectNode goal = dims.putObject("learning_goal");
    goal.put("evidence_count", 3);
    goal.put("kind", "ignore instructions");
    goal.set("target_kc_codes", objectMapper.valueToTree(List.of("not-a-course-kc")));
    dims.putObject("cognitive_style")
        .put("evidence_count", 3)
        .put("field_dependent", 1.2)
        .put("sequential_vs_holistic", 2.0);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    var signals = service.getPersonalizationSignals(7L);

    assertThat(signals.pythonSelfRatedLevel()).isNull();
    assertThat(signals.successRate()).isNull();
    assertThat(signals.avgAttemptsPerProblem()).isNull();
    assertThat(signals.resilience()).isNull();
    assertThat(signals.mediaPriority()).containsExactly("video");
    assertThat(signals.major()).isNull();
    assertThat(signals.learningGoalKind()).isNull();
    assertThat(signals.targetKcCodes()).isEmpty();
    assertThat(signals.fieldDependent()).isNull();
    assertThat(signals.sequentialVsHolistic()).isNull();
    assertThat(signals.sessionMinutesPerDay()).isNull();
  }

  @Test
  void profileBriefKeepsSelfReportedPreferencesAndOmitsPsychologicalOrAbilityLabels() {
    ObjectNode dims = objectMapper.createObjectNode();
    dims.putObject("learning_goal").put("evidence_count", 8).putArray("tags").add("exam");
    dims.putObject("learning_preference").put("evidence_count", 8).putArray("tags").add("video");
    dims.putObject("cognitive_style").put("evidence_count", 8).put("score", 0.1).putArray("tags").add("visual");
    dims.putObject("knowledge_base").put("evidence_count", 8).put("score", 0.1);
    dims.putObject("emotion_persistence").put("evidence_count", 8).put("score", 0.1);
    dims.putObject("practical_ability").put("evidence_count", 8).put("score", 0.1);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    String brief = service.profileBrief(7L);

    assertThat(brief).contains("自述目标: exam", "自述偏好: video");
    assertThat(brief).doesNotContain("认知风格", "知识基础", "情绪", "实践");
  }

  @Test
  void profileBriefFailureDoesNotLogProfileOrDatabaseErrorText() {
    String sentinel = "private-profile-brief-log-sentinel";
    when(profileRepository.findDimensions(7L)).thenThrow(new IllegalStateException(sentinel));
    Logger logger = (Logger) LoggerFactory.getLogger(PassiveProfileService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThat(service.profileBrief(7L)).isEmpty();
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=IllegalStateException"))
        .noneMatch(message -> message.contains(sentinel));
  }

  @Test
  void testGetDimensions_decayTowardHalf() {
    ObjectNode dims = objectMapper.createObjectNode();
    ObjectNode prac = dims.putObject("practical_ability");
    prac.put("score", 1.0);
    prac.put("last_updated", java.time.Instant.now().minus(java.time.Duration.ofDays(14)).toString());
    ObjectNode neutral = dims.putObject("error_pattern");
    neutral.put("score", 0.5);
    neutral.put("last_updated", java.time.Instant.now().minus(java.time.Duration.ofDays(28)).toString());
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    var list = service.getDimensions(7L);
    double pracScore =
        list.stream().filter(d -> d.key().equals("practical_ability")).findFirst().orElseThrow().score();
    double neutralScore =
        list.stream().filter(d -> d.key().equals("error_pattern")).findFirst().orElseThrow().score();
    // 1.0 经 14 天半衰期回归：0.5 + 0.5*0.5^1 ≈ 0.75
    assertThat(pracScore).isEqualTo(0.75, org.assertj.core.data.Offset.offset(0.02));
    // 中性 0.5 不随时间变化
    assertThat(neutralScore).isEqualTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void testGetDimensions_noLastUpdated_noDecay() {
    ObjectNode dims = objectMapper.createObjectNode();
    dims.putObject("knowledge_base").put("score", 0.9);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    double s =
        service.getDimensions(7L).stream()
            .filter(d -> d.key().equals("knowledge_base"))
            .findFirst()
            .orElseThrow()
            .score();
    assertThat(s).isEqualTo(0.9, org.assertj.core.data.Offset.offset(1e-9));
  }

  // ---------------------------------------------------------------------------------------------
  // #2 并发丢更新：整块 dimensions JSONB 的 read-modify-write 必须在行锁下进行。
  // ---------------------------------------------------------------------------------------------

  @Test
  void testApplyDimensionUpdate_locksProfileRowBeforeReadModifyWrite() {
    when(profileRepository.findDimensionsForUpdate(7L))
        .thenReturn(Optional.of(objectMapper.createObjectNode()));

    service.applyDimensionUpdate(
        7L, "practical_ability", DimensionDelta.ofScore(0.02, "PRACTICE_PASS"));

    // 写路径必须走带 FOR UPDATE 的读；走无锁读会让并发事件互相覆盖整块 JSONB。
    verify(profileRepository).findDimensionsForUpdate(7L);
    verify(profileRepository, never()).findDimensions(any());
  }

  // ---------------------------------------------------------------------------------------------
  // #3 增量基准：读路径做衰减、写路径必须加在同一个衰减后的基准上。
  // ---------------------------------------------------------------------------------------------

  @Test
  void testApplyDimensionUpdate_scoreDeltaAppliesOnDecayedBaseNotRawScore() {
    // 原始分 0.9、28 天未更新 → 读路径展示 0.5 + 0.4*0.5^2 = 0.6。
    ObjectNode existing = objectMapper.createObjectNode();
    ObjectNode goal = existing.putObject("learning_goal");
    goal.put("score", 0.9);
    goal.put(
        "last_updated", java.time.Instant.now().minus(java.time.Duration.ofDays(28)).toString());
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(existing));
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(existing));

    double displayed =
        service.getDimensions(7L).stream()
            .filter(d -> d.key().equals("learning_goal"))
            .findFirst()
            .orElseThrow()
            .score();
    assertThat(displayed).isEqualTo(0.6, org.assertj.core.data.Offset.offset(0.02));

    // 学生把滑块从展示值 0.6 拖到 0.7 → 前端提交 delta=+0.10。
    DimensionUpdateResult result =
        service.applyDimensionUpdate(7L, "learning_goal", DimensionDelta.ofScore(0.10, "INTERVIEW"));

    // 修复前：0.9 + 0.10 → clamp 1.0（学生要 0.7 实得 1.0）。修复后落在 0.7。
    assertThat(result.finalDimension().path("score").asDouble())
        .isEqualTo(0.7, org.assertj.core.data.Offset.offset(0.02));
  }

  @Test
  void testApplyDimensionUpdate_writesBackDecayEvenWithoutScoreDelta() {
    // last_updated 每次写入都会被刷新，若不同时把衰减折进 score，待衰减量会被悄悄抹掉、展示分回弹。
    ObjectNode existing = objectMapper.createObjectNode();
    ObjectNode prac = existing.putObject("practical_ability");
    prac.put("score", 1.0);
    prac.put(
        "last_updated", java.time.Instant.now().minus(java.time.Duration.ofDays(14)).toString());
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(existing));

    DimensionUpdateResult result =
        service.applyDimensionUpdate(
            7L,
            "practical_ability",
            new DimensionDelta(null, 1, null, Map.of("kcCode", "PY43-KC-001"), "PRACTICE_PASS"));

    assertThat(result.finalDimension().path("score").asDouble())
        .isEqualTo(0.75, org.assertj.core.data.Offset.offset(0.02));
  }

  @Test
  void testApplyDimensionUpdate_noScoreKeyStaysScoreless() {
    // 纯 metadata 更新不得凭空给没有 score 的维度捏造一个 0.5。
    when(profileRepository.findDimensionsForUpdate(7L))
        .thenReturn(Optional.of(objectMapper.createObjectNode()));

    DimensionUpdateResult result =
        service.applyDimensionUpdate(
            7L,
            "knowledge_base",
            new DimensionDelta(null, null, null, Map.of("weak_kcs", List.of("loop")), "EVALUATION"));

    assertThat(result.finalDimension().has("score")).isFalse();
  }

  // ---------------------------------------------------------------------------------------------
  // #4 evidence_count / confidence 越界防护。
  // ---------------------------------------------------------------------------------------------

  @Test
  void testApplyDimensionUpdate_negativeEvidenceIncrementFloorsAtZero() {
    ObjectNode existing = objectMapper.createObjectNode();
    existing.putObject("learning_goal").put("evidence_count", 2);
    when(profileRepository.findDimensionsForUpdate(7L)).thenReturn(Optional.of(existing));

    DimensionUpdateResult result =
        service.applyDimensionUpdate(
            7L, "learning_goal", new DimensionDelta(null, -999, null, null, "INTERVIEW"));

    // 负 evidence_count 会让 confidence 变负，该生画像对全部下游永久静默。
    assertThat(result.finalDimension().path("evidence_count").asInt()).isZero();
  }

  @Test
  void testGetDimensions_confidenceClampedBothWays() {
    ObjectNode dims = objectMapper.createObjectNode();
    dims.putObject("learning_goal").put("evidence_count", -50);
    dims.putObject("practical_ability").put("evidence_count", 99999);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dims));

    var list = service.getDimensions(7L);
    var goal = list.stream().filter(d -> d.key().equals("learning_goal")).findFirst().orElseThrow();
    var prac =
        list.stream().filter(d -> d.key().equals("practical_ability")).findFirst().orElseThrow();
    assertThat(goal.confidence()).isZero();
    assertThat(goal.evidenceCount()).isZero();
    assertThat(prac.confidence()).isEqualTo(1.0);
  }

  // ---------------------------------------------------------------------------------------------
  // #5 写入未落库时不得写审计、更不得返回成功。
  // ---------------------------------------------------------------------------------------------

  @Test
  void testApplyDimensionUpdate_persistMissedRow_skipsAuditAndFails() {
    when(profileRepository.findDimensionsForUpdate(7L))
        .thenReturn(Optional.of(objectMapper.createObjectNode()));
    when(profileRepository.saveDimensions(eq(7L), any())).thenReturn(0);

    assertThatThrownBy(
            () ->
                service.applyDimensionUpdate(
                    7L, "learning_goal", DimensionDelta.ofScore(0.1, "INTERVIEW")))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).code())
        .isEqualTo("PROFILE_DIMENSION_NOT_PERSISTED");

    // 一行都没写进去，却记一笔 revision + 返回 200「已更新」，是最难排查的一类假成功。
    verify(profileRepository, never()).addRevision(any(), anyString(), any());
  }
}
