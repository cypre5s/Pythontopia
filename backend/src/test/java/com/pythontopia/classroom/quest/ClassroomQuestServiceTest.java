package com.pythontopia.classroom.quest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.quest.repository.QuestRepository;
import com.pythontopia.learner.quest.repository.QuestRepository.QuestInsert;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class ClassroomQuestServiceTest {

  private final ClassroomQuestRepository repository = mock(ClassroomQuestRepository.class);
  private final ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
  private final QuestRepository questRepository = mock(QuestRepository.class);
  private final ClassroomGuard classroomGuard = mock(ClassroomGuard.class);
  private final CoursePackRepository coursePackRepository = mock(CoursePackRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ClassroomQuestService service =
      new ClassroomQuestService(
          repository,
          classroomRepository,
          questRepository,
          classroomGuard,
          coursePackRepository,
          objectMapper);

  private final CurrentUserPrincipal teacher = new CurrentUserPrincipal(42L, "t01", Role.TEACHER);

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
    verify(questRepository, times(3)).insert(any(QuestInsert.class));
    verify(repository).insert(any(ClassroomQuestInsert.class));
  }

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
    when(questRepository.insert(any(QuestInsert.class)))
        .thenReturn(101L)
        .thenThrow(new DuplicateKeyException("dup"));
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
        null);
  }

  private CourseKc kc(String code) {
    return new CourseKc(1L, 43L, code, code + " 名", 1L, 1, "Low", "");
  }
}
