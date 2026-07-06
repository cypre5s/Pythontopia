package com.pythontopia.classroom.quest.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.quest.dto.ClassroomQuestDto;
import com.pythontopia.classroom.quest.service.ClassroomQuestService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classroom/quests")
public class ClassroomQuestController {

  private final ClassroomQuestService service;

  public ClassroomQuestController(ClassroomQuestService service) {
    this.service = service;
  }

  @GetMapping("/mine")
  public List<ClassroomQuestDto> mine(@CurrentUser CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可查看个人班级任务");
    }
    return service.listForStudent(user.userId());
  }
}
