package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class ExpeditionRunServiceTest {

  private static final String RUN_ID = "123e4567-e89b-12d3-a456-426614174000";

  @Mock JdbcTemplate jdbcTemplate;

  @Test
  void aNewRunGetsAServerIssuedIdAndDoesNotPersistClientScore() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), eq(7L)))
        .thenReturn(List.of());
    when(jdbcTemplate.update(contains("INSERT INTO expedition_run"), any(Object[].class)))
        .thenReturn(1);
    ExpeditionRunService service = new ExpeditionRunService(jdbcTemplate, new ObjectMapper());

    ExpeditionRunDto saved = service.start(7L, null, false, frontierPicks());

    assertThat(saved.runId()).isNotBlank();
    assertThat(saved.layers()).isEqualTo(10);
    assertThat(saved.hp()).isEqualTo(5);
    assertThat(saved.momentum()).isZero();
    assertThat(saved.relics()).isEmpty();
    assertThat(saved.history()).isEmpty();
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbcTemplate).update(sql.capture(), args.capture());
    assertThat(sql.getValue()).contains("VALUES (?, ?::uuid").contains(", 0,");
    assertThat(args.getValue()).contains(saved.runId());
    assertThat(args.getValue()).doesNotContain(999_999);
  }

  @Test
  void anActiveRunCannotBeReplacedByAClientSuppliedId() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), eq(7L)))
        .thenReturn(List.of(new ExpeditionRunService.ExistingRun(RUN_ID, "ACTIVE")));
    ExpeditionRunService service = new ExpeditionRunService(jdbcTemplate, new ObjectMapper());

    assertThatThrownBy(() -> service.save(7L, runWithId("123e4567-e89b-12d3-a456-426614174001")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("远征存档已变化");
  }

  @Test
  void activeRunCanOnlyBeUpdatedByItsServerIssuedId() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), eq(7L)))
        .thenReturn(List.of(new ExpeditionRunService.ExistingRun(RUN_ID, "ACTIVE")));
    when(jdbcTemplate.update(contains("INSERT INTO expedition_run"), any(Object[].class)))
        .thenReturn(1);
    ExpeditionRunService service = new ExpeditionRunService(jdbcTemplate, new ObjectMapper());

    ExpeditionRunDto saved = service.save(7L, runWithId(RUN_ID));

    assertThat(saved.runId()).isEqualTo(RUN_ID);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
    assertThat(sql.getValue()).contains("expedition_verified_attempt");
  }

  @Test
  void activeSnapshotCannotRewriteServerIssuedMapConfiguration() {
    Map<String, Object> node =
        Map.of(
            "id", "L0-0",
            "layer", 0,
            "col", 0,
            "kind", "battle",
            "kcCode", "PY43-KC-001",
            "challengeKind", "dual_track",
            "difficulty", 2,
            "next", List.of("L1-0"),
            "state", "available");
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), eq(7L)))
        .thenReturn(
            List.of(
                new ExpeditionRunService.ExistingRun(
                    RUN_ID, "ACTIVE", 1, List.of(node), 10, false)));
    ExpeditionRunDto altered =
        new ExpeditionRunDto(
            "map",
            1,
            List.of(
                Map.of(
                    "id",
                    "L0-0",
                    "layer",
                    0,
                    "col",
                    0,
                    "kind",
                    "battle",
                    "kcCode",
                    "PY43-KC-002",
                    "challengeKind",
                    "dual_track",
                    "difficulty",
                    2,
                    "next",
                    List.of("L1-0"),
                    "state",
                    "available")),
            10,
            null,
            5,
            5,
            0,
            0,
            0,
            List.of(),
            false,
            List.of(),
            RUN_ID);
    ExpeditionRunService service = new ExpeditionRunService(jdbcTemplate, new ObjectMapper());

    assertThatThrownBy(() -> service.save(7L, altered))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("地图");
  }

  @Test
  void activeSnapshotCanUpdateProgressWithoutChangingItsMap() {
    Map<String, Object> canonicalNode =
        Map.of(
            "id", "L0-0",
            "layer", 0,
            "col", 0,
            "kind", "battle",
            "kcCode", "PY43-KC-001",
            "challengeKind", "dual_track",
            "difficulty", 2,
            "next", List.of("L1-0"),
            "state", "available");
    Map<String, Object> progressedNode = new java.util.HashMap<>(canonicalNode);
    progressedNode.put("state", "cleared");
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), eq(7L)))
        .thenReturn(
            List.of(
                new ExpeditionRunService.ExistingRun(
                    RUN_ID, "ACTIVE", 1, List.of(canonicalNode), 10, false)));
    when(jdbcTemplate.update(contains("INSERT INTO expedition_run"), any(Object[].class)))
        .thenReturn(1);
    ExpeditionRunService service = new ExpeditionRunService(jdbcTemplate, new ObjectMapper());
    ExpeditionRunDto update =
        new ExpeditionRunDto(
            "map",
            1,
            List.of(progressedNode),
            10,
            "L0-0",
            4,
            5,
            1,
            1,
            0,
            List.of(),
            false,
            List.of(),
            RUN_ID);

    ExpeditionRunDto saved = service.save(7L, update);

    assertThat(saved.runId()).isEqualTo(RUN_ID);
    assertThat(saved.hp()).isEqualTo(4);
    assertThat(saved.nodes().getFirst().get("state")).isEqualTo("cleared");
  }

  @Test
  void aSettledRunRequiresANewServerIssuedId() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), eq(7L)))
        .thenReturn(List.of(new ExpeditionRunService.ExistingRun(RUN_ID, "SETTLED")));
    when(jdbcTemplate.update(contains("INSERT INTO expedition_run"), any(Object[].class)))
        .thenReturn(1);
    ExpeditionRunService service = new ExpeditionRunService(jdbcTemplate, new ObjectMapper());

    ExpeditionRunDto saved = service.start(7L, null, false, frontierPicks());

    assertThat(saved.runId()).isNotEqualTo(RUN_ID);
  }

  @Test
  void oversizedNestedSnapshotIsRejectedBeforeDatabaseAccess() {
    ExpeditionRunService service = new ExpeditionRunService(jdbcTemplate, new ObjectMapper());
    ExpeditionRunDto oversized =
        new ExpeditionRunDto(
            "map",
            null,
            List.of(),
            10,
            null,
            5,
            5,
            0,
            0,
            0,
            List.of(),
            false,
            List.of(java.util.Map.of("text", "x".repeat(70_000))),
            null);

    assertThatThrownBy(() -> service.save(7L, oversized))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("快照过大");
    verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void clientCannotPersistTerminalOrIdleRunPhases() {
    ExpeditionRunService service = new ExpeditionRunService(jdbcTemplate, new ObjectMapper());

    for (String phase : List.of("idle", "cleared", "over")) {
      assertThatThrownBy(() -> service.save(7L, runWithPhase(phase)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("阶段无效");
    }

    verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void snapshotPutCannotCreateANewRun() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), eq(7L)))
        .thenReturn(List.of());
    ExpeditionRunService service = new ExpeditionRunService(jdbcTemplate, new ObjectMapper());

    assertThatThrownBy(() -> service.save(7L, runWithoutId()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("只能通过服务端开局");

    verify(jdbcTemplate, org.mockito.Mockito.never())
        .update(contains("INSERT INTO expedition_run"), any(Object[].class));
  }

  private static ExpeditionRunDto runWithoutId() {
    ExpeditionRunDto run =
        new ExpeditionRunDto(
            "map", null, List.of(), 0, null, 5, 5, 0, 0, 999_999, List.of(), false, List.of(),
            null);
    return run;
  }

  private static ExpeditionRunDto runWithId(String runId) {
    return new ExpeditionRunDto(
        "map", null, List.of(), 0, null, 5, 5, 0, 0, 999_999, List.of(), false, List.of(), runId);
  }

  private static ExpeditionRunDto runWithPhase(String phase) {
    return new ExpeditionRunDto(
        phase, null, List.of(), 0, null, 5, 5, 0, 0, 0, List.of(), false, List.of(), null);
  }

  private static List<com.pythontopia.world.boss.dto.FrontierPickDto> frontierPicks() {
    return List.of(new com.pythontopia.world.boss.dto.FrontierPickDto("PY43-KC-001", 1, 2, "weak"));
  }
}
