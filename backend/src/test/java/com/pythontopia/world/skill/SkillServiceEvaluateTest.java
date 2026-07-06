package com.pythontopia.world.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.mastery.MasterySnapshot;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link SkillService#evaluate(JsonNode, Map)}. The recursive condition
 * evaluator is the only piece of skill-tree logic that does not need the database, so testing it in
 * isolation gives the fastest signal whenever the unlock JSON schema is touched (V104 +
 * follow-ups). All other branches (HTTP 404 / 409, repository fan-out) are already exercised by the
 * existing higher-level Spring tests.
 *
 * <p>Branch coverage targets:
 *
 * <ul>
 *   <li>truthy guards: {@code null}, missing node, JSON {@code null}, non-object, unknown
 *       {@code type}, empty {@code type}, explicit {@code ANY_TIME}
 *   <li>{@code KC_MASTERY}: blank kcCode, missing snapshot, null mastery score, threshold exact
 *       match / above / below, default threshold of {@code 1.0} when {@code min} is omitted
 *   <li>{@code ALL}: missing {@code conditions}, non-array {@code conditions}, empty array
 *       (vacuous truth), all-pass, one-fail short-circuit
 *   <li>{@code ANY}: missing {@code conditions} (false), empty array (false), all-fail,
 *       single-pass
 *   <li>nested composites: {@code ANY} inside {@code ALL}
 * </ul>
 */
class SkillServiceEvaluateTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private Map<String, MasterySnapshot> snapshots;

  @BeforeEach
  void resetSnapshots() {
    snapshots = new HashMap<>();
  }

  private JsonNode json(String raw) throws Exception {
    return mapper.readTree(raw);
  }

  private MasterySnapshot snapshot(String kcCode, BigDecimal score) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-22T10:00:00Z");
    return new MasterySnapshot(1L, 11L, kcCode, score, BigDecimal.valueOf(0.5), now, now);
  }

  @Test
  void nullConditionAlwaysAllowsUnlock() {
    assertThat(SkillService.evaluate(null, snapshots)).isTrue();
  }

  @Test
  void missingFieldViaGetReturnsJavaNullAndAllowsUnlock() throws Exception {
    JsonNode root = mapper.readTree("{\"other\":1}");

    assertThat(SkillService.evaluate(root.get("unlock_condition"), snapshots)).isTrue();
  }

  @Test
  void missingNodeViaPathAllowsUnlock() throws Exception {
    JsonNode root = mapper.readTree("{\"other\":1}");
    JsonNode missing = root.path("unlock_condition");

    assertThat(missing.isMissingNode()).isTrue();
    assertThat(SkillService.evaluate(missing, snapshots)).isTrue();
  }

  @Test
  void explicitJsonNullAllowsUnlock() throws Exception {
    JsonNode nullNode = mapper.readTree("null");

    assertThat(SkillService.evaluate(nullNode, snapshots)).isTrue();
  }

  @Test
  void primitiveNonObjectAllowsUnlock() throws Exception {
    assertThat(SkillService.evaluate(json("\"hello\""), snapshots)).isTrue();
    assertThat(SkillService.evaluate(json("42"), snapshots)).isTrue();
    assertThat(SkillService.evaluate(json("true"), snapshots)).isTrue();
  }

  @Test
  void emptyObjectAllowsUnlockBecauseTypeIsBlank() throws Exception {
    assertThat(SkillService.evaluate(json("{}"), snapshots)).isTrue();
  }

  @Test
  void anyTimeTypeAlwaysAllowsUnlock() throws Exception {
    assertThat(SkillService.evaluate(json("{\"type\":\"ANY_TIME\"}"), snapshots)).isTrue();
  }

  @Test
  void unknownTypeRejectsUnlock() throws Exception {
    assertThat(SkillService.evaluate(json("{\"type\":\"UNKNOWN_TYPE\"}"), snapshots)).isFalse();
    assertThat(SkillService.evaluate(json("{\"type\":\"ALWAYS_OPEN\"}"), snapshots)).isFalse();
  }

  @Test
  void kcMasteryAcceptsScoreAtExactThreshold() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("0.60")));
    JsonNode condition = json("{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.6}");

    assertThat(SkillService.evaluate(condition, snapshots)).isTrue();
  }

  @Test
  void kcMasteryAcceptsScoreAboveThreshold() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("0.99")));
    JsonNode condition = json("{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.5}");

    assertThat(SkillService.evaluate(condition, snapshots)).isTrue();
  }

  @Test
  void kcMasteryRejectsScoreBelowThreshold() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("0.59")));
    JsonNode condition = json("{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.6}");

    assertThat(SkillService.evaluate(condition, snapshots)).isFalse();
  }

  @Test
  void kcMasteryRejectsWhenSnapshotMissing() throws Exception {
    JsonNode condition = json("{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.6}");

    assertThat(SkillService.evaluate(condition, snapshots)).isFalse();
  }

  @Test
  void kcMasteryRejectsWhenMasteryScoreIsNull() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", null));
    JsonNode condition = json("{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.6}");

    assertThat(SkillService.evaluate(condition, snapshots)).isFalse();
  }

  @Test
  void kcMasteryWithBlankKcCodeRejects() throws Exception {
    snapshots.put("", snapshot("", new BigDecimal("1.0")));
    JsonNode condition = json("{\"type\":\"KC_MASTERY\",\"kcCode\":\"\",\"min\":0.1}");

    assertThat(SkillService.evaluate(condition, snapshots)).isFalse();
  }

  @Test
  void kcMasteryDefaultsThresholdToOnePointZeroWhenMinMissing() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("0.95")));
    JsonNode condition = json("{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\"}");

    assertThat(SkillService.evaluate(condition, snapshots)).isFalse();

    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("1.00")));
    assertThat(SkillService.evaluate(condition, snapshots)).isTrue();
  }

  @Test
  void allCompositePassesWhenEveryChildPasses() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("0.8")));
    snapshots.put("PY43-KC-002", snapshot("PY43-KC-002", new BigDecimal("0.7")));
    JsonNode condition =
        json(
            "{\"type\":\"ALL\",\"conditions\":["
                + "{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.6},"
                + "{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-002\",\"min\":0.6}"
                + "]}");

    assertThat(SkillService.evaluate(condition, snapshots)).isTrue();
  }

  @Test
  void allCompositeShortCircuitsOnFirstFailingChild() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("0.8")));
    snapshots.put("PY43-KC-002", snapshot("PY43-KC-002", new BigDecimal("0.10")));
    JsonNode condition =
        json(
            "{\"type\":\"ALL\",\"conditions\":["
                + "{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.6},"
                + "{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-002\",\"min\":0.6}"
                + "]}");

    assertThat(SkillService.evaluate(condition, snapshots)).isFalse();
  }

  @Test
  void allCompositeWithMissingArrayIsVacuousTrue() throws Exception {
    assertThat(SkillService.evaluate(json("{\"type\":\"ALL\"}"), snapshots)).isTrue();
  }

  @Test
  void allCompositeWithNonArrayConditionsFallsBackToTrue() throws Exception {
    assertThat(
            SkillService.evaluate(
                json("{\"type\":\"ALL\",\"conditions\":\"oops\"}"), snapshots))
        .isTrue();
  }

  @Test
  void allCompositeWithEmptyArrayIsVacuousTrue() throws Exception {
    assertThat(
            SkillService.evaluate(json("{\"type\":\"ALL\",\"conditions\":[]}"), snapshots))
        .isTrue();
  }

  @Test
  void anyCompositeReturnsFalseWhenConditionsMissing() throws Exception {
    assertThat(SkillService.evaluate(json("{\"type\":\"ANY\"}"), snapshots)).isFalse();
  }

  @Test
  void anyCompositeReturnsFalseWhenConditionsEmptyArray() throws Exception {
    assertThat(
            SkillService.evaluate(json("{\"type\":\"ANY\",\"conditions\":[]}"), snapshots))
        .isFalse();
  }

  @Test
  void anyCompositeReturnsFalseWhenEveryChildFails() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("0.10")));
    snapshots.put("PY43-KC-002", snapshot("PY43-KC-002", new BigDecimal("0.20")));
    JsonNode condition =
        json(
            "{\"type\":\"ANY\",\"conditions\":["
                + "{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.6},"
                + "{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-002\",\"min\":0.6}"
                + "]}");

    assertThat(SkillService.evaluate(condition, snapshots)).isFalse();
  }

  @Test
  void anyCompositeAcceptsSinglePassingChildEvenWhenOthersFail() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("0.10")));
    snapshots.put("PY43-KC-002", snapshot("PY43-KC-002", new BigDecimal("0.95")));
    JsonNode condition =
        json(
            "{\"type\":\"ANY\",\"conditions\":["
                + "{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.6},"
                + "{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-002\",\"min\":0.6}"
                + "]}");

    assertThat(SkillService.evaluate(condition, snapshots)).isTrue();
  }

  @Test
  void nestedAllInsideAnyShortCircuitsOnFirstPassingBranch() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("0.10")));
    snapshots.put("PY43-KC-002", snapshot("PY43-KC-002", new BigDecimal("0.95")));
    snapshots.put("PY43-KC-003", snapshot("PY43-KC-003", new BigDecimal("0.95")));
    JsonNode condition =
        json(
            "{\"type\":\"ANY\",\"conditions\":["
                + "{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.6},"
                + "{\"type\":\"ALL\",\"conditions\":["
                + "  {\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-002\",\"min\":0.6},"
                + "  {\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-003\",\"min\":0.6}"
                + "]}"
                + "]}");

    assertThat(SkillService.evaluate(condition, snapshots)).isTrue();
  }

  @Test
  void nestedAllInsideAnyFailsWhenEveryBranchFails() throws Exception {
    snapshots.put("PY43-KC-001", snapshot("PY43-KC-001", new BigDecimal("0.10")));
    snapshots.put("PY43-KC-002", snapshot("PY43-KC-002", new BigDecimal("0.95")));
    snapshots.put("PY43-KC-003", snapshot("PY43-KC-003", new BigDecimal("0.10")));
    JsonNode condition =
        json(
            "{\"type\":\"ANY\",\"conditions\":["
                + "{\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-001\",\"min\":0.6},"
                + "{\"type\":\"ALL\",\"conditions\":["
                + "  {\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-002\",\"min\":0.6},"
                + "  {\"type\":\"KC_MASTERY\",\"kcCode\":\"PY43-KC-003\",\"min\":0.6}"
                + "]}"
                + "]}");

    assertThat(SkillService.evaluate(condition, snapshots)).isFalse();
  }
}
