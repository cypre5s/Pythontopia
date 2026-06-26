package com.pythontopia.workshop.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkshopChatHistoryServiceTest {

  private final WorkshopChatHistoryRepository repository = mock(WorkshopChatHistoryRepository.class);
  private final WorkshopChatHistoryService service = new WorkshopChatHistoryService(repository);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void recordTurnStoresPlanHistoryForCurrentUser() throws Exception {
    JsonNode payload = objectMapper.readTree("{\"planSteps\":[{\"title\":\"先看循环概念\"}]}");
    WorkshopChatHistoryRecordRequest request =
        new WorkshopChatHistoryRecordRequest(
            "conv-plan",
            "我想学循环，从哪开始",
            "PLAN",
            "plan",
            "规划学习",
            "计划如下",
            payload);
    when(repository.insert(7L, request)).thenReturn(42L);

    WorkshopChatHistoryRecordResponse response = service.recordTurn(7L, request);

    verify(repository).insert(7L, request);
    assertThat(response.id()).isEqualTo(42L);
  }

  @Test
  void recordRequestAllowsBrowseHistoryKindButRejectsUnknownKind() throws Exception {
    JsonNode payload = objectMapper.readTree("{\"browseRun\":{\"id\":88}}");
    WorkshopChatHistoryRecordRequest browse =
        new WorkshopChatHistoryRecordRequest(
            "conv-browse",
            "查 Python 官网 sorted 稳定性",
            "BROWSE",
            "browse",
            "联网查证",
            "Python sorted 是稳定排序。",
            payload);
    WorkshopChatHistoryRecordRequest invalid =
        new WorkshopChatHistoryRecordRequest(
            "conv-browse",
            "查 Python 官网 sorted 稳定性",
            "BROWSE",
            "browser",
            "联网查证",
            "Python sorted 是稳定排序。",
            payload);

    assertThat(validator.validate(browse)).isEmpty();
    assertThat(validator.validate(invalid)).isNotEmpty();
  }

  @Test
  void listTurnsReturnsTutorAndWorkshopTurnsTogether() {
    WorkshopChatHistoryTurnDto planTurn =
        new WorkshopChatHistoryTurnDto(
            "workshop-42",
            "conv-plan",
            "我想学循环，从哪开始",
            "PLAN",
            "plan",
            "规划学习",
            "计划如下",
            objectMapper.createObjectNode(),
            List.of(),
            null,
            Instant.parse("2026-06-19T10:00:00Z"));
    WorkshopChatHistoryTurnDto askTurn =
        new WorkshopChatHistoryTurnDto(
            "tutor-99",
            "conv-plan",
            "什么是 while",
            "ASK",
            "answer",
            "问答",
            "while 是条件循环。",
            objectMapper.createObjectNode(),
            List.of(1001),
            "PASSED",
            Instant.parse("2026-06-19T10:01:00Z"));
    when(repository.findByUser(7L, 50)).thenReturn(List.of(askTurn, planTurn));

    List<WorkshopChatHistoryTurnDto> turns = service.listTurns(7L, 80);

    verify(repository).findByUser(7L, 50);
    assertThat(turns).containsExactly(askTurn, planTurn);
  }
}
