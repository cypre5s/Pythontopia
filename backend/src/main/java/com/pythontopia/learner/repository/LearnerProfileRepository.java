package com.pythontopia.learner.repository;

import com.pythontopia.learner.dto.LearnerProfileDto;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LearnerProfileRepository {

  private final JdbcTemplate jdbcTemplate;

  public LearnerProfileRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<LearnerProfileDto> findByUserId(Long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, pack_id, major, learning_goal, cognitive_style, narrative_summary
            FROM learner_profile
            WHERE user_id = ?
            """,
            (rs, rowNum) ->
                new LearnerProfileDto(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getLong("pack_id"),
                    rs.getString("major"),
                    rs.getString("learning_goal"),
                    rs.getString("cognitive_style"),
                    rs.getString("narrative_summary")),
            userId)
        .stream()
        .findFirst();
  }
}
