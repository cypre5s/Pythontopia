package com.pythontopia.coursepack.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseResourceAvailabilityRepository {

  private final JdbcTemplate jdbcTemplate;

  public CourseResourceAvailabilityRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean isEnabled(String resourceType, long resourceId) {
    Boolean disabled =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM course_resource_state"
                + " WHERE pack_id = 43 AND resource_type = ? AND resource_id = ? AND NOT enabled)",
            Boolean.class,
            resourceType,
            resourceId);
    return !Boolean.TRUE.equals(disabled);
  }
}
