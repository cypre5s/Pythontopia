package com.pythontopia.mainline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MainlineStageProgressRepositoryTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final MainlineStageProgressRepository repository =
      new MainlineStageProgressRepository(jdbcTemplate);

  @Test
  void mediaEventsAreClaimedByOneConditionalUpdatePerStage() {
    assertThat(repository.claimVideoConsumed(7L, 10L)).isFalse();
    assertThat(repository.claimCoursewareConsumed(7L, 10L)).isFalse();

    verify(jdbcTemplate)
        .update(contains("video_consumed_at IS NULL"), eq(7L), eq(10L));
    verify(jdbcTemplate)
        .update(contains("courseware_consumed_at IS NULL"), eq(7L), eq(10L));
  }
}
