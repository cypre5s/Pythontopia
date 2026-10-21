package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FeynmanSessionServiceSecurityTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final FeynmanSessionService service =
      new FeynmanSessionService(
          jdbc,
          new ObjectMapper(),
          mock(MisconceptionExtractor.class),
          mock(FeynmanGraphClient.class),
          mock(PublicProblemRepository.class),
          mock(ProfileRepository.class),
          mock(FeynmanBugBankRepository.class));

  @Test
  void loadSessionScopesLookupToAuthenticatedOwner() {
    FeynmanSessionService.SessionData expected =
        new FeynmanSessionService.SessionData(10L, 7L, "PY43-KC-016", 476L, "OPEN", "{}");
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(10L), eq(7L)))
        .thenReturn(expected);

    assertThat(service.loadSession(7L, 10L)).isSameAs(expected);
    verify(jdbc)
        .queryForObject(
            org.mockito.ArgumentMatchers.contains("user_id = ?"),
            any(RowMapper.class),
            eq(10L),
            eq(7L));
  }

  @Test
  void loadSessionHidesAnotherUsersSessionAsNotFound() {
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(10L), eq(8L)))
        .thenThrow(new EmptyResultDataAccessException(1));

    assertThatThrownBy(() -> service.loadSession(8L, 10L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(exception.code()).isEqualTo("FEYNMAN_SESSION_NOT_FOUND");
            });
  }
}
