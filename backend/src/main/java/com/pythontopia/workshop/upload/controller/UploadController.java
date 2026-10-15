package com.pythontopia.workshop.upload.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.workshop.upload.dto.UploadDetailDto;
import com.pythontopia.workshop.upload.dto.UploadDto;
import com.pythontopia.workshop.upload.entity.UploadStatus;
import com.pythontopia.workshop.upload.retrieval.UploadCorpusIndexingService;
import com.pythontopia.workshop.upload.service.UploadService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 资源工坊文件上传 API（前缀 {@code /api}，JWT，仅 STUDENT）。学生上传个人文件解析为文本，供 A 通路
 * （前端取 {@code extractedText} 带标签注入答疑 context）与 B 通路（向量检索，见 retrieval 子包）使用。
 */
@RestController
@RequestMapping("/api/workshop/uploads")
public class UploadController {

  private static final Logger log = LoggerFactory.getLogger(UploadController.class);

  private final UploadService service;
  private final UploadCorpusIndexingService indexingService;

  public UploadController(UploadService service, UploadCorpusIndexingService indexingService) {
    this.service = service;
    this.indexingService = indexingService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public UploadDto upload(
      @CurrentUser CurrentUserPrincipal user, @RequestParam("file") MultipartFile file) {
    requireStudent(user);
    UploadDto dto = service.upload(user.userId(), file);
    // 解析成功才触发 B 通路分块+向量化（异步即发即忘、fail-soft，不影响上传响应；
    // service.upload 的 @Transactional 已提交，异步线程可见该行）。
    if (dto.status() == UploadStatus.READY && dto.id() != null) {
      try {
        indexingService.indexUploadAsync(dto.id());
      } catch (TaskRejectedException ex) {
        // shortcut: a full queue drops indexing; add durable retries if retrieval becomes mandatory.
        log.warn("upload corpus indexing skipped: executor queue is full");
      }
    }
    return dto;
  }

  @GetMapping
  public List<UploadDto> list(@CurrentUser CurrentUserPrincipal user) {
    requireStudent(user);
    return service.list(user.userId());
  }

  @GetMapping("/{id}")
  public UploadDetailDto detail(@CurrentUser CurrentUserPrincipal user, @PathVariable long id) {
    requireStudent(user);
    return service.detail(user.userId(), id);
  }

  @DeleteMapping("/{id}")
  public void delete(@CurrentUser CurrentUserPrincipal user, @PathVariable long id) {
    requireStudent(user);
    service.delete(user.userId(), id);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user == null || user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可上传文件");
    }
  }
}
