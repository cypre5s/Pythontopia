package com.pythontopia.workshop.upload.retrieval;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.workshop.upload.retrieval.dto.RetrieveRequest;
import com.pythontopia.workshop.upload.retrieval.dto.RetrieveResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人语料检索端点（文件解析 B 通路·向量检索）。独立于 parse 通路的上传 CRUD 控制器，仅暴露
 * {@code POST /api/workshop/uploads/retrieve}。仅学生可用，且只检索<strong>本人</strong>上传（以登录态 userId
 * scoped，杜绝越权读他人语料）。
 */
@RestController
@RequestMapping("/api/workshop/uploads")
public class UploadRetrievalController {

  private final UploadRetrievalService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public UploadRetrievalController(
      UploadRetrievalService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @PostMapping("/retrieve")
  public RetrieveResponse retrieve(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody RetrieveRequest request) {
    requireStudent(user);
    if (!studentWriteThrottle.tryAcquireRetrievalRequest(user.userId())) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "RETRIEVAL_RATE_LIMITED",
          "每个账号每分钟最多发起 30 次课件或个人语料检索");
    }
    return new RetrieveResponse(
        service.retrieve(user.userId(), request.query(), request.uploadIds(), request.topK()));
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可检索个人语料");
    }
  }
}
