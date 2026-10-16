package com.pythontopia.learner.quest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.quest.dto.QuestStatus;
import com.pythontopia.learner.quest.repository.QuestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 状态推进的并发守卫必须下推到 SQL：service 层「先读状态再判断」是无锁的，双击 / 客户端重试会让两个事务
 * 都通过前置校验，各自 UPDATE 成功，写出两行 COMPLETED、发两次事件、把掌握度记两遍。
 */
class QuestRepositoryStatusGuardTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final QuestRepository repository =
      new QuestRepository(jdbcTemplate, new ObjectMapper());

  @Test
  void updateStatusOnlyMovesQuestsThatAreStillActive() {
    repository.updateStatus(7L, 10L, QuestStatus.COMPLETED);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture(), eq("COMPLETED"), eq(10L), eq(7L));

    String sql = sqlCaptor.getValue().replaceAll("\\s+", " ").trim();
    assertThat(sql).contains("status IN ('OPEN', 'IN_PROGRESS')");
  }

  @Test
  void startOnlyMovesQuestsThatAreStillOpen() {
    repository.updateStatus(7L, 10L, QuestStatus.IN_PROGRESS);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture(), eq("IN_PROGRESS"), eq(10L), eq(7L));

    String sql = sqlCaptor.getValue().replaceAll("\\s+", " ").trim();
    assertThat(sql).contains("status = 'OPEN'").doesNotContain("status IN");
  }
}
