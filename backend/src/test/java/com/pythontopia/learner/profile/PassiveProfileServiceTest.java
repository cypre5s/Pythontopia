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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class PassiveProfileServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ProfileRepository profileRepository = mock(ProfileRepository.class);
  private final PassiveProfileService service =
      new PassiveProfileService(profileRepository, objectMapper);

  @Test
  void testApplyDimensionUpdate_addScoreDelta_clamped() {
    ObjectNode existing = objectMapper.createObjectNode();
    ObjectNode practical = existing.putObject("practical_ability");
    practical.put("score", 0.95);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(existing));

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
  void testApplyDimensionUpdate_scoreDelta_negativeFloorAtZero() {
    ObjectNode existing = objectMapper.createObjectNode();
    ObjectNode err = existing.putObject("error_pattern");
    err.put("score", 0.1);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(existing));

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
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(existing));

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
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.empty());

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
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(existing));

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
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(objectMapper.createObjectNode()));

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
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(objectMapper.createObjectNode()));

    service.applyDimensionUpdate(
        7L, "learning_goal", DimensionDelta.ofScore(0.1, "CUSTOM_NOT_IN_ENUM"));

    verify(profileRepository).saveDimensions(eq(7L), any());
    verify(profileRepository, never()).addRevision(any(), anyString(), any());
  }

  @Test
  void testApplyDimensionUpdate_blankSource_skipsRevision() {
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(objectMapper.createObjectNode()));

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
  void testIsSelfEditable_onlyFourSubjectiveDimensions() {
    assertThat(service.isSelfEditable("professional_background")).isTrue();
    assertThat(service.isSelfEditable("learning_goal")).isTrue();
    assertThat(service.isSelfEditable("learning_preference")).isTrue();
    assertThat(service.isSelfEditable("emotion_persistence")).isTrue();
    assertThat(service.isSelfEditable("knowledge_base")).isFalse();
    assertThat(service.isSelfEditable("cognitive_style")).isFalse();
    assertThat(service.isSelfEditable("error_pattern")).isFalse();
    assertThat(service.isSelfEditable("practical_ability")).isFalse();
  }
}
