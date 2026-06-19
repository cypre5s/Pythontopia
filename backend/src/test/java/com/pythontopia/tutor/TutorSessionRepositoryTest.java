package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pythontopia.tutor.dto.TutorSessionDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TutorSessionRepositoryTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final TutorSessionRepository repository = new TutorSessionRepository(jdbcTemplate);

  @Test
  void findByConversationSelectsLatestTurnsThenReturnsChronologicalOrder() {
    repository.findByConversation(7L, "conv-1", 6);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .query(
            sqlCaptor.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<TutorSessionDto>>any(),
            eq(7L),
            eq("conv-1"),
            eq(6));

    String normalizedSql = sqlCaptor.getValue().replaceAll("\\s+", " ").trim();
    assertThat(normalizedSql)
        .contains("ORDER BY created_at DESC")
        .contains("ORDER BY created_at ASC");
    assertThat(normalizedSql.indexOf("ORDER BY created_at DESC"))
        .isLessThan(normalizedSql.lastIndexOf("ORDER BY created_at ASC"));
  }
}
