package com.pythontopia.learner.event;

import com.pythontopia.common.jooq.JsonbHelper;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class LearningEventLogRepository {

  private final DSLContext dsl;

  public LearningEventLogRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public void insert(Long userId, String eventType, Map<String, Object> payload, String source) {
    dsl.insertInto(DSL.table("learning_event_log"))
        .set(DSL.field("user_id"), userId)
        .set(DSL.field("event_type"), eventType)
        .set(DSL.field("payload", JSONB.class), JsonbHelper.toJooqJsonb(payload))
        .set(DSL.field("source"), source)
        .execute();
  }

  public List<Map<String, Object>> findByUser(Long userId, int limit) {
    return dsl.select()
        .from(DSL.table("learning_event_log"))
        .where(DSL.field("user_id").eq(userId))
        .orderBy(DSL.field("created_at").desc())
        .limit(limit)
        .fetchMaps();
  }
}
