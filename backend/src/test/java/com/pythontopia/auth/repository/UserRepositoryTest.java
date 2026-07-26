package com.pythontopia.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** 会话吊销开关的 SQL 契约：必须是参数化的原子自增，而不是先读后写。 */
class UserRepositoryTest {

  @Test
  @DisplayName("bumpTokenVersion 以参数化 SQL 原子自增指定用户的会话代次")
  void bumpTokenVersionIncrementsAtomically() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.update(anyString(), eq(9L))).thenReturn(1);
    UserRepository repository = new UserRepository(jdbcTemplate);

    assertThat(repository.bumpTokenVersion(9L)).isEqualTo(1);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sql.capture(), eq(9L));
    assertThat(sql.getValue())
        .contains("token_version = token_version + 1")
        .contains("WHERE id = ?");
  }
}
