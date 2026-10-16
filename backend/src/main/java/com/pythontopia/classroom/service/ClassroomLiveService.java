package com.pythontopia.classroom.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import com.pythontopia.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
  private static final int MAX_STREAMS_PER_TEACHER = 3;

  private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
  // shortcut: counts share this single-instance SSE fanout; coordinate across instances if clustered.
  private final Map<Long, Integer> streamsByTeacher = new ConcurrentHashMap<>();
  private final Map<SseEmitter, Long> teacherByEmitter = new ConcurrentHashMap<>();

  public SseEmitter subscribe(Long classroomId, Long teacherUserId) {
    acquireStream(teacherUserId);
    SseEmitter emitter = createEmitter();
    teacherByEmitter.put(emitter, teacherUserId);
    emitters.compute(
        classroomId,
        (ignored, existing) -> {
          CopyOnWriteArrayList<SseEmitter> list =
              existing == null ? new CopyOnWriteArrayList<>() : existing;
          list.add(emitter);
          return list;
        });

    Runnable cleanup = () -> cleanup(classroomId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());

    try {
      emitter.send(SseEmitter.event()
          .name("connected")
          .data(Map.of("classroomId", classroomId, "teacherUserId", teacherUserId)));
    } catch (IOException e) {
      log.warn("Failed to send SSE connected event", e);
      cleanup.run();
    }
    return emitter;
  }

  SseEmitter createEmitter() {
    return new SseEmitter(SSE_TIMEOUT_MS);
  }

  private void acquireStream(Long teacherUserId) {
    AtomicBoolean acquired = new AtomicBoolean();
    streamsByTeacher.compute(
        teacherUserId,
        (ignored, current) -> {
          int active = current == null ? 0 : current;
          if (active >= MAX_STREAMS_PER_TEACHER) return current;
          acquired.set(true);
          return active + 1;
        });
    if (!acquired.get()) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "CLASSROOM_LIVE_CONNECTION_LIMIT",
          "每位教师最多同时打开 3 个课堂直播连接");
    }
  }

  private void cleanup(Long classroomId, SseEmitter emitter) {
    emitters.computeIfPresent(
        classroomId,
        (ignored, list) -> {
          list.remove(emitter);
          return list.isEmpty() ? null : list;
        });
    Long teacherUserId = teacherByEmitter.remove(emitter);
    if (teacherUserId != null) {
      streamsByTeacher.computeIfPresent(
          teacherUserId,
          (ignored, count) -> count <= 1 ? null : count - 1);
    }
  }

  public void broadcast(Long classroomId, String eventName, Object data) {
    CopyOnWriteArrayList<SseEmitter> list = emitters.get(classroomId);
    if (list == null || list.isEmpty()) return;

    for (SseEmitter emitter : list) {
      try {
        emitter.send(SseEmitter.event().name(eventName).data(data));
      } catch (IOException e) {
        cleanup(classroomId, emitter);
      }
    }
  }
}
