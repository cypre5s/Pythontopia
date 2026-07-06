package com.pythontopia.classroom.quest.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.quest.dto.ClassroomQuestCreateRequest;
import com.pythontopia.classroom.quest.dto.ClassroomQuestDto;
import com.pythontopia.classroom.quest.dto.ClassroomQuestPublishResponse;
import com.pythontopia.classroom.quest.service.ClassroomQuestService;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/teacher/classrooms/{classroomId}/quests")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherClassroomQuestController {

  private final ClassroomQuestService service;

  public TeacherClassroomQuestController(ClassroomQuestService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ClassroomQuestPublishResponse publish(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable @Positive Long classroomId,
      @Valid @RequestBody ClassroomQuestCreateRequest request) {
    return service.publish(user, classroomId, request);
  }

  @GetMapping
  public List<ClassroomQuestDto> list(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long classroomId) {
    return service.listAllForClassroom(user, classroomId);
  }

  @PostMapping("/{questId}/archive")
  public ClassroomQuestDto archive(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable @Positive Long classroomId,
      @PathVariable @Positive Long questId) {
    return service.archive(user, classroomId, questId);
  }
}
