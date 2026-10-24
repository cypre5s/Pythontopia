package com.pythontopia.admin;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/student-imports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentImportController {

  private final AdminStudentImportService service;

  public AdminStudentImportController(AdminStudentImportService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AdminStudentImportResultDto importStudents(
      @CurrentUser CurrentUserPrincipal admin, @RequestParam("file") MultipartFile file) {
    return service.importRoster(admin, file);
  }
}
