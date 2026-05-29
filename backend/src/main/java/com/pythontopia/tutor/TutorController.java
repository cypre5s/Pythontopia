package com.pythontopia.tutor;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.tutor.dto.TutorAskRequest;
import com.pythontopia.tutor.dto.TutorSessionDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/tutor")
public class TutorController {

  private final TutorService service;

  public TutorController(TutorService service) {
    this.service = service;
  }

  @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter ask(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody TutorAskRequest request) {
    requireStudent(user);
    return service.ask(user.userId(), request);
  }

  @GetMapping("/sessions")
  public List<TutorSessionDto> listSessions(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(defaultValue = "20") int limit) {
    return service.listSessions(user.userId(), limit);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用导师问答");
    }
  }
}
