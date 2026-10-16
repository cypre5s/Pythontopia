package com.pythontopia.classroom.quest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.domain.Classroom;
import com.pythontopia.classroom.dto.ClassroomMemberDto;
import com.pythontopia.classroom.quest.dto.ClassroomQuestCreateRequest;
import com.pythontopia.classroom.quest.dto.ClassroomQuestDto;
import com.pythontopia.classroom.quest.dto.ClassroomQuestPublishResponse;
import com.pythontopia.classroom.quest.repository.ClassroomQuestRepository;
import com.pythontopia.classroom.quest.repository.ClassroomQuestRepository.ClassroomQuestInsert;
import com.pythontopia.classroom.quest.service.ClassroomQuestService;
import com.pythontopia.classroom.repository.ClassroomRepository;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.quest.repository.QuestRepository.QuestInsert;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class ClassroomQuestServiceTest {

  private final ClassroomQuestRepository repository = mock(ClassroomQuestRepository.class);
  private final ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
  private final ClassroomGuard classroomGuard = mock(ClassroomGuard.class);
  private final CoursePackRepository coursePackRepository = mock(CoursePackRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final StudentWriteThrottle studentWriteThrottle = mock(StudentWriteThrottle.class);
  private final ClassroomQuestService service =
      new ClassroomQuestService(
          repository,
          classroomRepository,
          classroomGuard,
          coursePackRepository,
          objectMapper,
          studentWriteThrottle);

  private final CurrentUserPrincipal teacher = new CurrentUserPrincipal(42L, "t01", Role.TEACHER);

  @BeforeEach
  void allowNormalWrites() {
    when(studentWriteThrottle.tryAcquire(anyLong(), anyInt())).thenReturn(true);
    when(studentWriteThrottle.tryAcquireClassroomQuestFanOut(anyLong(), anyInt())).thenReturn(true);
  }

  @Test
  void rejectsTeacherQuestPublicationWhenAccountBudgetIsExhausted() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "desc", 42L, 43L, "ABC123", "ACTIVE"));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of(kc("PY43-KC-001")));
    when(studentWriteThrottle.tryAcquireClassroomQuestFanOut(teacher.userId(), 3))
        .thenReturn(false);
    when(repository.insert(any(ClassroomQuestInsert.class))).thenReturn(77L);
    when(classroomRepository.findMembers(9L))
        .thenReturn(
            List.of(
                new ClassroomMemberDto(11L, "stu01", "Stu 01", "ACTIVE"),
                new ClassroomMemberDto(12L, "stu02", "Stu 02", "ACTIVE")));
    when(repository.findById(77L)).thenReturn(Optional.of(sampleQuest(77L, "ACTIVE")));

    assertThatThrownBy(
            () ->
                service.publish(
                    teacher,
                    9L,
                    new ClassroomQuestCreateRequest(
                        "Task",
                        null,
                        List.of("PY43-KC-001"),
                        null,
                        "HANDOUT",
                        LocalDate.now(ZoneOffset.UTC).plusDays(1))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(error.code()).isEqualTo("CLASSROOM_WRITE_RATE_LIMITED");
            });

    verify(repository, never()).insert(any(ClassroomQuestInsert.class));
    verify(studentWriteThrottle).tryAcquireClassroomQuestFanOut(teacher.userId(), 3);
    verify(classroomRepository).findMembers(9L);
  }

  @Test
  void rejectsTeacherQuestArchivalWhenAccountBudgetIsExhausted() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "desc", 42L, 43L, "ABC123", "ACTIVE"));
    when(repository.findById(77L))
        .thenReturn(
            Optional.of(sampleQuest(77L, "ACTIVE")), Optional.of(sampleQuest(77L, "ARCHIVED")));
    when(studentWriteThrottle.tryAcquire(teacher.userId(), 1)).thenReturn(false);

    assertThatThrownBy(() -> service.archive(teacher, 9L, 77L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(error.code()).isEqualTo("CLASSROOM_WRITE_RATE_LIMITED");
            });

    verify(studentWriteThrottle).tryAcquire(teacher.userId(), 1);
    verify(repository, never()).archive(77L);
    verify(repository, never()).skipDistributedQuests(77L);
  }

  @Test
  void publishWritesClassroomQuestAndFanOutsToStudents() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "desc", 42L, 43L, "ABC123", "ACTIVE"));
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(List.of(kc("PY43-KC-001"), kc("PY43-KC-002")));
    when(coursePackRepository.findProblem(eq(43L), eq(12L)))
        .thenReturn(Optional.of(new com.pythontopia.coursepack.domain.CourseProblem(
            12L, 43L, "PY43-KC-001", "题目", "stmt", "in", "out", "Low", null)));
    when(repository.insert(any(ClassroomQuestInsert.class))).thenReturn(77L);
    when(classroomRepository.findMembers(9L))
        .thenReturn(
            List.of(
                new ClassroomMemberDto(11L, "stu01", "Stu 01", "ACTIVE"),
                new ClassroomMemberDto(12L, "stu02", "Stu 02", "ACTIVE"),
                new ClassroomMemberDto(13L, "stu03", "Stu 03", "ACTIVE")));
    when(repository.findById(77L))
        .thenReturn(Optional.of(sampleQuest(77L, "ACTIVE")));
    when(studentWriteThrottle.tryAcquireClassroomQuestFanOut(teacher.userId(), 4)).thenReturn(true);
    when(repository.insertStudentQuestIfAbsent(any(QuestInsert.class)))
        .thenReturn(Optional.of(101L), Optional.of(102L), Optional.of(103L));

    ClassroomQuestCreateRequest request =
        new ClassroomQuestCreateRequest(
            "完成 for 循环练习",
            "对应 KC for-loop",
            List.of("PY43-KC-001"),
            12L,
            null,
            LocalDate.now(ZoneOffset.UTC).plusDays(2));
    ClassroomQuestPublishResponse response = service.publish(teacher, 9L, request);

    assertThat(response.totalStudents()).isEqualTo(3);
    assertThat(response.createdQuests()).isEqualTo(3);
    assertThat(response.skippedDuplicates()).isEqualTo(0);
    ArgumentCaptor<QuestInsert> questInsertCaptor = ArgumentCaptor.forClass(QuestInsert.class);
    verify(repository, times(3)).insertStudentQuestIfAbsent(questInsertCaptor.capture());
    assertThat(questInsertCaptor.getAllValues())
        .allSatisfy(
            insert -> {
              assertThat(insert.requiredEvidenceType()).isEqualTo("submission");
              assertThat(insert.targetProblemIds()).containsExactly(12L);
              assertThat(insert.deepLinkRoute()).isEqualTo("/training");
              assertThat(insert.deepLinkParams().toString()).contains("\"problemId\":12");
            });
    verify(repository).insert(any(ClassroomQuestInsert.class));
    verify(studentWriteThrottle).tryAcquireClassroomQuestFanOut(teacher.userId(), 4);
  }

  @Test
  void rejectsClassroomQuestFanOutAboveSynchronousLimitBeforeAnyWrite() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "desc", 42L, 43L, "ABC123", "ACTIVE"));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of(kc("PY43-KC-001")));
    when(classroomRepository.findMembers(9L))
        .thenReturn(
            Collections.nCopies(
                StudentWriteThrottle.MAX_CLASSROOM_QUEST_FANOUT_WRITES_PER_MINUTE,
                new ClassroomMemberDto(11L, "stu01", "Stu 01", "ACTIVE")));

    assertThatThrownBy(
            () ->
                service.publish(
                    teacher,
                    9L,
                    new ClassroomQuestCreateRequest(
                        "Task",
                        null,
                        List.of("PY43-KC-001"),
                        null,
                        "HANDOUT",
                        LocalDate.now(ZoneOffset.UTC).plusDays(1))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(error.code()).isEqualTo("CLASSROOM_QUEST_FANOUT_TOO_LARGE");
            });

    verify(repository, never()).insert(any(ClassroomQuestInsert.class));
    verify(studentWriteThrottle, never())
        .tryAcquireClassroomQuestFanOut(anyLong(), anyInt());
  }

  /**
   * 重复分发必须走「ON CONFLICT DO NOTHING」的真幂等：PostgreSQL 里唯一键冲突会把整个
   * @Transactional 事务标记为 aborted，靠 catch DuplicateKeyException 续跑会让后续 insert 全部失败。
   */
  @Test
  void duplicateFanOutIsCountedAndDoesNotFailTransaction() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "desc", 42L, 43L, "ABC123", "ACTIVE"));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of(kc("PY43-KC-001")));
    when(coursePackRepository.findProblem(eq(43L), eq(12L)))
        .thenReturn(Optional.of(new com.pythontopia.coursepack.domain.CourseProblem(
            12L, 43L, "PY43-KC-001", "题目", "stmt", "in", "out", "Low", null)));
    when(repository.insert(any(ClassroomQuestInsert.class))).thenReturn(78L);
    when(classroomRepository.findMembers(9L))
        .thenReturn(
            List.of(
                new ClassroomMemberDto(11L, "stu01", "Stu 01", "ACTIVE"),
                new ClassroomMemberDto(12L, "stu02", "Stu 02", "ACTIVE")));
    when(repository.insertStudentQuestIfAbsent(any(QuestInsert.class)))
        .thenReturn(Optional.of(101L), Optional.empty());
    when(studentWriteThrottle.tryAcquireClassroomQuestFanOut(teacher.userId(), 3)).thenReturn(true);
    when(repository.findById(78L)).thenReturn(Optional.of(sampleQuest(78L, "ACTIVE")));

    ClassroomQuestPublishResponse response =
        service.publish(
            teacher,
            9L,
            new ClassroomQuestCreateRequest(
                "Title",
                null,
                List.of("PY43-KC-001"),
                12L,
                null,
                LocalDate.now(ZoneOffset.UTC).plusDays(1)));
    assertThat(response.createdQuests()).isEqualTo(1);
    assertThat(response.skippedDuplicates()).isEqualTo(1);
    verify(studentWriteThrottle).tryAcquireClassroomQuestFanOut(teacher.userId(), 3);
  }

  @Test
  void rejectRequestWithoutAnchor() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "desc", 42L, 43L, "ABC123", "ACTIVE"));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of(kc("PY43-KC-001")));
    ClassroomQuestCreateRequest request =
        new ClassroomQuestCreateRequest(
            "无锚点", null, List.of("PY43-KC-001"), null, null, LocalDate.now(ZoneOffset.UTC).plusDays(1));
    assertThatThrownBy(() -> service.publish(teacher, 9L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).code())
        .isEqualTo("CLASSROOM_QUEST_ANCHOR_REQUIRED");
  }

  @Test
  void rejectUnknownKc() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "desc", 42L, 43L, "ABC123", "ACTIVE"));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of(kc("PY43-KC-001")));
    ClassroomQuestCreateRequest request =
        new ClassroomQuestCreateRequest(
            "未知 KC", null, List.of("PY43-KC-999"), null, "HANDOUT", LocalDate.now(ZoneOffset.UTC).plusDays(1));
    assertThatThrownBy(() -> service.publish(teacher, 9L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).code())
        .isEqualTo("CLASSROOM_QUEST_KC_UNKNOWN");
  }

  @Test
  void archivedQuestUpdatesStatus() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "desc", 42L, 43L, "ABC123", "ACTIVE"));
    when(repository.findById(77L))
        .thenReturn(Optional.of(sampleQuest(77L, "ACTIVE")), Optional.of(sampleQuest(77L, "ARCHIVED")));
    when(repository.archive(77L)).thenReturn(1);
    ClassroomQuestDto archived = service.archive(teacher, 9L, 77L);
    assertThat(archived.status()).isEqualTo("ARCHIVED");
    verify(repository).archive(77L);
  }

  /**
   * 回归（缺陷 3）：只把 classroom_quest 置为 ARCHIVED 不够。学生侧「今日任务」走 learner 的
   * {@code QuestRepository.findTodayQuests}，只看 {@code quest.status + due_date}、不 join
   * classroom_quest，所以归档后学生仍能看到并完成它、继续加 mastery。归档必须同事务撤回副本。
   */
  @Test
  void archiveAlsoWithdrawsDistributedStudentQuests() {
    when(classroomGuard.requireOwner(teacher, 9L))
        .thenReturn(new Classroom(9L, "DemoClass", "desc", 42L, 43L, "ABC123", "ACTIVE"));
    when(repository.findById(77L))
        .thenReturn(
            Optional.of(sampleQuest(77L, "ACTIVE")), Optional.of(sampleQuest(77L, "ARCHIVED")));
    when(repository.archive(77L)).thenReturn(1);
    when(repository.skipDistributedQuests(77L)).thenReturn(3);

    service.archive(teacher, 9L, 77L);

    verify(repository).skipDistributedQuests(77L);
  }

  /**
   * 回归（缺陷 4）：任务发布之后才入班的学生永远拿不到 quest 副本 —— 公告板用 LEFT JOIN quest，
   * 所以他看得到任务卡但 studentQuestId 为 null，点「提交完成」只会得到「请刷新公告板」，
   * 而刷新永远无效。入班后必须补发所有仍在进行中的班级任务副本。
   */
  @Test
  void backfillForStudentCreatesCopiesOfActiveClassroomQuests() {
    when(repository.listActiveForClassroom(eq(9L), any(LocalDate.class)))
        .thenReturn(List.of(sampleQuest(77L, "ACTIVE"), sampleQuest(78L, "ACTIVE")));
    when(repository.insertStudentQuestIfAbsent(any(QuestInsert.class)))
        .thenReturn(Optional.of(501L), Optional.of(502L));

    int created = service.backfillForStudent(9L, 55L);

    assertThat(created).isEqualTo(2);
    ArgumentCaptor<QuestInsert> captor = ArgumentCaptor.forClass(QuestInsert.class);
    verify(repository, times(2)).insertStudentQuestIfAbsent(captor.capture());
    assertThat(captor.getAllValues())
        .allSatisfy(
            insert -> {
              assertThat(insert.userId()).isEqualTo(55L);
              assertThat(insert.source().name()).isEqualTo("CLASSROOM");
              // 补发的副本与发布时的投影完全一致（标题前缀 / 深链 / 证据要求）。
              assertThat(insert.title()).startsWith("班级任务 · ");
              assertThat(insert.deepLinkRoute()).isEqualTo("/training");
              assertThat(insert.requiredEvidenceType()).isEqualTo("submission");
              assertThat(insert.targetProblemIds()).containsExactly(12L);
            });
    assertThat(captor.getAllValues())
        .extracting(QuestInsert::sourceRefId)
        .containsExactly(77L, 78L);
  }

  /** 已有副本时补发是 no-op（ON CONFLICT DO NOTHING 返回 empty），重复入班不产生重复任务。 */
  @Test
  void backfillForStudentIsIdempotent() {
    when(repository.listActiveForClassroom(eq(9L), any(LocalDate.class)))
        .thenReturn(List.of(sampleQuest(77L, "ACTIVE")));
    when(repository.insertStudentQuestIfAbsent(any(QuestInsert.class)))
        .thenReturn(Optional.empty());

    assertThat(service.backfillForStudent(9L, 55L)).isZero();
  }

  private ClassroomQuestDto sampleQuest(Long id, String status) {
    return new ClassroomQuestDto(
        id,
        9L,
        "title",
        "desc",
        List.of("PY43-KC-001"),
        com.pythontopia.learner.quest.dto.QuestNodeType.SOLVE_PROBLEM,
        objectMapper.createObjectNode(),
        LocalDate.now(ZoneOffset.UTC).plusDays(1),
        42L,
        OffsetDateTime.now(ZoneOffset.UTC),
        status,
        12L,
        null,
        null,
        null,
        null,
        List.of(),
        null,
        null);
  }

  private CourseKc kc(String code) {
    return new CourseKc(1L, 43L, code, code + " 名", 1L, 1, "Low", "");
  }
}
