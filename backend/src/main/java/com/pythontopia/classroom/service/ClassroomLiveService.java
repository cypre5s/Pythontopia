package com.pythontopia.classroom.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 教师班级视图的 SSE 实时推送：教师通过 GET /api/classroom/{id}/live 订阅 SSE 流，
 * 学生事件经 {@link #broadcast} 推送。
 */
@Service
public class ClassroomLiveService {

  private static final Logger log = LoggerFactory.getLogger(ClassroomLiveService.class);
  private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000;

  private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

  public SseEmitter subscribe(Long classroomId, Long teacherUserId) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
    emitters.computeIfAbsent(classroomId, k -> new CopyOnWriteArrayList<>()).add(emitter);

    Runnable cleanup = () -> {
      CopyOnWriteArrayList<SseEmitter> list = emitters.get(classroomId);
      if (list != null) {
        list.remove(emitter);
        if (list.isEmpty()) emitters.remove(classroomId);
      }
    };
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());

    try {
      emitter.send(SseEmitter.event()
          .name("connected")
          .data(Map.of("classroomId", classroomId, "teacherUserId", teacherUserId)));
    } catch (IOException e) {
      log.warn("Failed to send SSE connected event", e);
    }
    return emitter;
  }

  public void broadcast(Long classroomId, String eventName, Object data) {
    CopyOnWriteArrayList<SseEmitter> list = emitters.get(classroomId);
    if (list == null || list.isEmpty()) return;

    for (SseEmitter emitter : list) {
      try {
        emitter.send(SseEmitter.event().name(eventName).data(data));
      } catch (IOException e) {
        list.remove(emitter);
      }
    }
  }
}
