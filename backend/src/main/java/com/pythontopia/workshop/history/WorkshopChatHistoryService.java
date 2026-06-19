package com.pythontopia.workshop.history;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkshopChatHistoryService {

  private final WorkshopChatHistoryRepository repository;

  public WorkshopChatHistoryService(WorkshopChatHistoryRepository repository) {
    this.repository = repository;
  }

  public WorkshopChatHistoryRecordResponse recordTurn(
      Long userId, WorkshopChatHistoryRecordRequest request) {
    Long id = repository.insert(userId, request);
    return new WorkshopChatHistoryRecordResponse(id);
  }

  public List<WorkshopChatHistoryTurnDto> listTurns(Long userId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 50));
    return repository.findByUser(userId, safeLimit);
  }
}
