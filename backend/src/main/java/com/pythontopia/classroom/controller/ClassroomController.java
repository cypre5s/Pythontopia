package com.pythontopia.classroom.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.dto.ClassroomDto;
import com.pythontopia.classroom.dto.ClassroomMemberDto;
import com.pythontopia.classroom.dto.CreateClassroomRequest;
import com.pythontopia.classroom.dto.JoinClassroomRequest;
import com.pythontopia.classroom.service.ClassroomService;
import com.pythontopia.common.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/classrooms")
public class ClassroomController {

  private final ClassroomService service;

  public ClassroomController(ClassroomService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ClassroomDto create(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody CreateClassroomRequest request) {
    return service.create(user, request);
  }

  @GetMapping("/mine")
  List<ClassroomDto> mine(@CurrentUser CurrentUserPrincipal user) {
    return service.mine(user);
  }

  @PostMapping("/join")
  ClassroomDto join(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody JoinClassroomRequest request) {
    return service.join(user, request.joinCode());
  }

  @GetMapping("/{id}/members")
  List<ClassroomMemberDto> members(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive Long id) {
    return service.members(user, id);
  }

  @DeleteMapping("/{id}/members/{studentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeMember(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long studentId) {
    service.removeMember(user, id, studentId);
  }
}
