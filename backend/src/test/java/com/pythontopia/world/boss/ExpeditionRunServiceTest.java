package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import java.util.List;
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

    ExpeditionRunDto saved = service.save(7L, runWithoutId());

    assertThat(saved.runId()).isNotBlank();
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
  void aSettledRunRequiresANewServerIssuedId() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), eq(7L)))
        .thenReturn(List.of(new ExpeditionRunService.ExistingRun(RUN_ID, "SETTLED")));
    when(jdbcTemplate.update(contains("INSERT INTO expedition_run"), any(Object[].class)))
        .thenReturn(1);
    ExpeditionRunService service = new ExpeditionRunService(jdbcTemplate, new ObjectMapper());

    ExpeditionRunDto saved = service.save(7L, runWithoutId());

    assertThat(saved.runId()).isNotEqualTo(RUN_ID);
  }

  private static ExpeditionRunDto runWithoutId() {
    ExpeditionRunDto run = new ExpeditionRunDto(
        "map", null, List.of(), 0, null, 5, 5, 0, 0, 999_999, List.of(), false, List.of(), null);
    return run;
  }

  private static ExpeditionRunDto runWithId(String runId) {
    return new ExpeditionRunDto(
        "map", null, List.of(), 0, null, 5, 5, 0, 0, 999_999, List.of(), false, List.of(), runId);
  }
}
