package com.pythontopia.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStudentDetailService {

  private final AdminStudentDetailRepository repository;
  private final ProfileRepository profileRepository;
  private final ObjectMapper objectMapper;

  public AdminStudentDetailService(
      AdminStudentDetailRepository repository,
      ProfileRepository profileRepository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.profileRepository = profileRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public AdminStudentDetailDto get(CurrentUserPrincipal admin, long userId) {
    if (admin == null || admin.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "仅管理员可查看学生详情");
    }
    AdminStudentDetailDto.Account account =
        repository
            .findStudent(userId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "学生账号不存在"));
    return new AdminStudentDetailDto(
        account,
        profileRepository.findDimensions(userId).orElseGet(objectMapper::createObjectNode),
        repository.findMastery(userId),
        repository.findActivity(userId),
        repository.findSubmissions(userId),
        repository.findClassrooms(userId));
  }
}
