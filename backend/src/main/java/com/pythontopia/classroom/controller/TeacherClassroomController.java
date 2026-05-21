package com.pythontopia.classroom.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.dto.ClassroomDto;
import com.pythontopia.classroom.dto.ClassroomMemberDto;
import com.pythontopia.classroom.service.ClassroomService;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher-facing classroom administration endpoints.
 *
 * <p>All endpoints require teacher (or admin) role plus classroom ownership; both checks happen
 * in {@code ClassroomService}/{@code ClassroomGuard}.
 */
@Validated
@RestController
@RequestMapping("/api/teacher/classrooms")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherClassroomController {

  private final ClassroomService service;

  public TeacherClassroomController(ClassroomService service) {
    this.service = service;
  }

  @PutMapping("/{id}")
  public ClassroomDto update(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable @Positive Long id,
      @Valid @RequestBody UpdateClassroomRequest request) {
    return service.update(user, id, request.name(), request.description());
  }

  @PostMapping("/{id}/regenerate-code")
  public ClassroomDto regenerateCode(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long id) {
    return service.regenerateJoinCode(user, id);
  }

  @GetMapping("/{id}/members")
  public List<ClassroomMemberDto> members(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable @Positive Long id,
      @RequestParam(required = false) String status) {
    return service.membersByStatus(user, id, status);
  }

  public record UpdateClassroomRequest(
      @NotBlank @Size(max = 128) String name, @Size(max = 1024) String description) {}
}
