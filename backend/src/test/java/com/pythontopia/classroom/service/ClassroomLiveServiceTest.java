package com.pythontopia.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.common.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ClassroomLiveServiceTest {

  @Test
  void limitsConcurrentStreamsPerTeacherAndReleasesTheSlotOnCompletion() {
    TestService service = new TestService();
    service.subscribe(1L, 7L);
    service.subscribe(2L, 7L);
    service.subscribe(3L, 7L);

    assertThatThrownBy(() -> service.subscribe(4L, 7L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    assertThat(service.subscribe(4L, 8L)).isNotNull();

    service.created.getFirst().disconnect();

    assertThat(service.subscribe(4L, 7L)).isNotNull();
    service.created.forEach(RecordingEmitter::disconnect);
  }

  private static final class TestService extends ClassroomLiveService {
    private final List<RecordingEmitter> created = new ArrayList<>();

    @Override
    SseEmitter createEmitter() {
      RecordingEmitter emitter = new RecordingEmitter();
      created.add(emitter);
      return emitter;
    }
  }

  private static final class RecordingEmitter extends SseEmitter {
    private Runnable completion;

    @Override
    public void onCompletion(Runnable callback) {
      completion = callback;
    }

    private void disconnect() {
      completion.run();
    }
  }
}
