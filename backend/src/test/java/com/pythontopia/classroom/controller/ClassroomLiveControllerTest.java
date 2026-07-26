package com.pythontopia.classroom.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.domain.Classroom;
import com.pythontopia.classroom.repository.ClassroomRepository;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.classroom.service.ClassroomLiveService;
import com.pythontopia.common.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 回归（缺陷 8）：课堂直播 SSE 曾是 classroom 包内唯一只校验角色、不校验归属的端点。
 * classroomId 是顺序 BIGSERIAL，任何教师改一下 URL 数字就能订阅别班的实时事件流。
 * 这里用真实 {@link ClassroomGuard} 串起控制器，锁死「非归属教师订阅即 FORBIDDEN、
 * 且绝不建立 emitter」。
 */
class ClassroomLiveControllerTest {

  private final ClassroomLiveService liveService = mock(ClassroomLiveService.class);
  private final ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
  private final ClassroomGuard classroomGuard = new ClassroomGuard(classroomRepository);
  private final ClassroomLiveController controller =
      new ClassroomLiveController(liveService, classroomGuard);

  private static final long OWNER_ID = 42L;

  private void classroomOwnedByOwner() {
    when(classroomRepository.findById(9L))
        .thenReturn(
            Optional.of(new Classroom(9L, "演示班", "", OWNER_ID, 43L, "ABC123", "ACTIVE")));
  }

  @Test
  @DisplayName("非归属教师订阅直播 → FORBIDDEN，且不建立 SSE 连接")
  void otherTeacherCannotSubscribe() {
    classroomOwnedByOwner();
    CurrentUserPrincipal other = new CurrentUserPrincipal(99L, "t99", Role.TEACHER);

    assertThatThrownBy(() -> controller.subscribe(other, 9L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));

    verifyNoInteractions(liveService);
  }

  @Test
  @DisplayName("学生订阅直播 → FORBIDDEN，且在角色阶段即拦下（不读 classrooms 表）")
  void studentCannotSubscribe() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu01", Role.STUDENT);

    assertThatThrownBy(() -> controller.subscribe(student, 9L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));

    verifyNoInteractions(liveService);
    verifyNoInteractions(classroomRepository);
  }

  @Test
  @DisplayName("归属教师订阅成功")
  void owningTeacherSubscribes() {
    classroomOwnedByOwner();
    CurrentUserPrincipal owner = new CurrentUserPrincipal(OWNER_ID, "t42", Role.TEACHER);
    SseEmitter emitter = new SseEmitter();
    when(liveService.subscribe(9L, OWNER_ID)).thenReturn(emitter);

    assertThat(controller.subscribe(owner, 9L)).isSameAs(emitter);
    verify(liveService).subscribe(9L, OWNER_ID);
  }
}
