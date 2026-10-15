package com.pythontopia.workshop.history;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WorkshopChatHistoryService {

  private static final int MAX_RESPONSE_PAYLOAD_BYTES = 64 * 1024;

  private final WorkshopChatHistoryRepository repository;
  private final StudentWriteThrottle throttle;

  public WorkshopChatHistoryService(
      WorkshopChatHistoryRepository repository, StudentWriteThrottle throttle) {
    this.repository = repository;
    this.throttle = throttle;
  }

  public WorkshopChatHistoryRecordResponse recordTurn(
      Long userId, WorkshopChatHistoryRecordRequest request) {
    if (request.responsePayload() != null
        && request.responsePayload().toString().getBytes(StandardCharsets.UTF_8).length
            > MAX_RESPONSE_PAYLOAD_BYTES) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "WORKSHOP_HISTORY_PAYLOAD_TOO_LARGE",
          "聊天历史 payload 不能超过 64 KiB");
    }
    if (!throttle.tryAcquire(userId, 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "STUDENT_WRITE_RATE_LIMITED", "每个账号每分钟最多写入 100 条学习记录");
    }
    Long id = repository.insert(userId, request);
    return new WorkshopChatHistoryRecordResponse(id);
  }

  public List<WorkshopChatHistoryTurnDto> listTurns(Long userId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 50));
    return repository.findByUser(userId, safeLimit);
  }
}
