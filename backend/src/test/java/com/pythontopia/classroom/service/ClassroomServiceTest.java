package com.pythontopia.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.domain.Classroom;
import com.pythontopia.classroom.dto.ClassroomDto;
import com.pythontopia.classroom.dto.CreateClassroomRequest;
import com.pythontopia.classroom.quest.service.ClassroomQuestService;
import com.pythontopia.classroom.repository.ClassroomRepository;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 班级链路第 2 步（学生加入）的服务层红线：
 *
 * <ul>
 *   <li>缺陷 2：被移出的学生必须能重新加入（removeMember 是软删，addMember 必须是 upsert）；
 *   <li>缺陷 4：入班成功后必须补发该班仍在进行中的班级任务副本；
 *   <li>缺陷 8：教师端写操作复用 ClassroomGuard，与看板 / 任务 / 补救四条入口同口径（含 ADMIN）。
 * </ul>
 */
class ClassroomServiceTest {

  private final ClassroomRepository repository = mock(ClassroomRepository.class);
  private final ClassroomGuard classroomGuard = mock(ClassroomGuard.class);
  private final ClassroomQuestService questService = mock(ClassroomQuestService.class);
  private final ClassroomService service =
      new ClassroomService(repository, classroomGuard, questService);

  private final CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu01", Role.STUDENT);
  private final CurrentUserPrincipal admin = new CurrentUserPrincipal(1L, "root", Role.ADMIN);

  private static Classroom classroom(String status) {
    return new Classroom(9L, "演示班", "desc", 42L, 43L, "ABC123", status);
  }

  @Test
  @DisplayName("首次加入：写成员行 + 补发进行中的班级任务副本")
  void joinCreatesMembershipAndBackfillsQuests() {
    when(repository.findByJoinCode("ABC123")).thenReturn(Optional.of(classroom("ACTIVE")));
    when(repository.findMemberStatus(9L, 7L)).thenReturn(Optional.empty());

    ClassroomDto dto = service.join(student, "ABC123");

    assertThat(dto.classroomId()).isEqualTo(9L);
    // 学生视角不下发加入码。
    assertThat(dto.joinCode()).isNull();
    verify(repository).addMember(9L, 7L);
    // 缺陷 4：不补发的话，发布之后才入班的学生只能看到点不动的任务卡。
    verify(questService).backfillForStudent(9L, 7L);
  }

  @Test
  @DisplayName("管理员进入教师屋可创建班级")
  void adminCanCreateClassroom() {
    when(repository.create(eq("管理班"), eq("演示"), eq(admin.userId()), anyString()))
        .thenReturn(new Classroom(99L, "管理班", "演示", admin.userId(), 43L, "ZXCVBN", "ACTIVE"));

    ClassroomDto dto = service.create(admin, new CreateClassroomRequest("管理班", "演示"));

    assertThat(dto.classroomId()).isEqualTo(99L);
    assertThat(dto.joinCode()).isEqualTo("ZXCVBN");
  }

  @Test
  @DisplayName("管理员班级总览包含可管理班级的加入码")
  void adminClassroomListIncludesManageableClassrooms() {
    when(repository.findAll()).thenReturn(List.of(classroom("ACTIVE")));

    List<ClassroomDto> rows = service.mine(admin);

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().classroomId()).isEqualTo(9L);
    assertThat(rows.getFirst().joinCode()).isEqualTo("ABC123");
    verify(repository).findAll();
    verify(repository, never()).findByStudent(admin.userId());
  }

  /**
   * 回归（缺陷 2）：{@code removeMember} 是软删（status='REMOVED'），而 {@code classroom_members} 上有
   * UNIQUE(classroom_id, student_id)。旧实现裸 INSERT， 被移出的学生二次加入必撞唯一键 → 409「已加入该班级」，但
   * /api/classrooms/mine 又看不到 （只查 ACTIVE），学生和教师都没有自助恢复手段。
   */
  @Test
  @DisplayName("被移出的学生可以重新加入：不报 409，走 upsert 复活成员行")
  void removedStudentCanRejoin() {
    when(repository.findByJoinCode("ABC123")).thenReturn(Optional.of(classroom("ACTIVE")));
    when(repository.findMemberStatus(9L, 7L)).thenReturn(Optional.of("REMOVED"));

    ClassroomDto dto = service.join(student, "ABC123");

    assertThat(dto.classroomId()).isEqualTo(9L);
    verify(repository).addMember(9L, 7L);
    verify(questService).backfillForStudent(9L, 7L);
  }

  @Test
  @DisplayName("已在班（ACTIVE）才回 409，且不重复写成员行 / 不重复补发")
  void alreadyActiveMemberIsRejectedWithConflict() {
    when(repository.findByJoinCode("ABC123")).thenReturn(Optional.of(classroom("ACTIVE")));
    when(repository.findMemberStatus(9L, 7L)).thenReturn(Optional.of("ACTIVE"));

    assertThatThrownBy(() -> service.join(student, "ABC123"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.code()).isEqualTo("CLASSROOM_ALREADY_JOINED");
            });

    verify(repository, never()).addMember(9L, 7L);
    verifyNoInteractions(questService);
  }

  @Test
  @DisplayName("错误加入码计入限额，避免同一学生枚举有效码")
  void invalidJoinCodesAreRateLimitedBeforeLookup() {
    ClassroomService limitedService =
        new ClassroomService(repository, classroomGuard, questService);
    CurrentUserPrincipal attacker = new CurrentUserPrincipal(77L, "stu77", Role.STUDENT);

    for (int attempt = 0; attempt < 30; attempt++) {
      assertThatThrownBy(() -> limitedService.join(attacker, "BAD123"))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    assertThatThrownBy(() -> limitedService.join(attacker, "ABC123"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(ex.code()).isEqualTo("JOIN_CODE_RATE_LIMITED");
            });
    verify(repository, never()).findByJoinCode("ABC123");
  }

  @Test
  @DisplayName("成功加入以及随后重复加入的尝试都计入限额")
  void successfulJoinAttemptsAreRateLimited() {
    ClassroomService limitedService =
        new ClassroomService(repository, classroomGuard, questService);
    CurrentUserPrincipal attacker = new CurrentUserPrincipal(78L, "stu78", Role.STUDENT);
    when(repository.findByJoinCode("ABC123")).thenReturn(Optional.of(classroom("ACTIVE")));
    when(repository.findMemberStatus(9L, 78L))
        .thenReturn(Optional.empty(), Optional.of("ACTIVE"));

    assertThat(limitedService.join(attacker, "ABC123").classroomId()).isEqualTo(9L);

    for (int attempt = 1; attempt < 30; attempt++) {
      assertThatThrownBy(() -> limitedService.join(attacker, "ABC123"))
          .isInstanceOfSatisfying(
              BusinessException.class,
              ex -> assertThat(ex.code()).isEqualTo("CLASSROOM_ALREADY_JOINED"));
    }

    assertThatThrownBy(() -> limitedService.join(attacker, "ABC123"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(ex.code()).isEqualTo("JOIN_CODE_RATE_LIMITED");
            });
    verify(repository, times(30)).findByJoinCode("ABC123");
  }

  @Test
  @DisplayName("已归档的班级不接受加入")
  void archivedClassroomRejectsJoin() {
    when(repository.findByJoinCode("ABC123")).thenReturn(Optional.of(classroom("ARCHIVED")));

    assertThatThrownBy(() -> service.join(student, "ABC123"))
        .isInstanceOfSatisfying(
            BusinessException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));
    verifyNoInteractions(questService);
  }

  @Test
  @DisplayName("教师不能用加入码入班（join 仅限学生）")
  void teacherCannotJoin() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(42L, "t01", Role.TEACHER);

    assertThatThrownBy(() -> service.join(teacher, "ABC123"))
        .isInstanceOfSatisfying(
            BusinessException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));
    verifyNoInteractions(repository);
  }

  /**
   * 回归（缺陷 8）：教师端写操作原本自己做 {@code requireRole(user, TEACHER)}，与类级注解 / ClassroomGuard 放行 ADMIN
   * 的口径相反——同一个 ADMIN 能读看板、能发任务，却在改名 / 重置加入码 / 成员管理上被 403。现在统一走 guard。
   */
  @Test
  @DisplayName("ADMIN 走同一道 guard：改名 / 重置加入码 / 成员列表不再自相矛盾地 403")
  void adminUsesTheSameGuardAsDashboard() {
    CurrentUserPrincipal admin = new CurrentUserPrincipal(1L, "root", Role.ADMIN);
    when(classroomGuard.requireOwner(admin, 9L)).thenReturn(classroom("ACTIVE"));
    when(repository.findById(9L)).thenReturn(Optional.of(classroom("ACTIVE")));

    service.update(admin, 9L, "新名", "新说明");
    service.members(admin, 9L);
    service.removeMember(admin, 9L, 7L);

    verify(repository).updateBasics(9L, "新名", "新说明");
    verify(repository).findMembers(9L);
    verify(repository).removeMember(9L, 7L);
  }

  @Test
  @DisplayName("非归属教师被 guard 拒绝时，写操作一律不落库")
  void nonOwnerTeacherCannotMutate() {
    CurrentUserPrincipal other = new CurrentUserPrincipal(99L, "t99", Role.TEACHER);
    when(classroomGuard.requireOwner(other, 9L))
        .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "班级不属于当前教师"));

    assertThatThrownBy(() -> service.update(other, 9L, "改名", ""))
        .isInstanceOfSatisfying(
            BusinessException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> service.removeMember(other, 9L, 7L))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.regenerateJoinCode(other, 9L))
        .isInstanceOf(BusinessException.class);

    verifyNoInteractions(repository);
  }
}
