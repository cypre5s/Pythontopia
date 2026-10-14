package com.pythontopia.classroom.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.classroom.domain.Classroom;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ClassroomRepositoryTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final ClassroomRepository repository = new ClassroomRepository(jdbcTemplate);

  @Test
  void findAllReturnsClassesInStableOrder() {
    List<Classroom> classrooms =
        List.of(new Classroom(9L, "演示班", "desc", 42L, 43L, "ABC123", "ACTIVE"));
    when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Classroom>>any()))
        .thenReturn(classrooms);

    assertThat(repository.findAll()).isEqualTo(classrooms);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).query(sql.capture(), ArgumentMatchers.<RowMapper<Classroom>>any());
    assertThat(sql.getValue()).contains("FROM classrooms", "ORDER BY id");
  }
}
