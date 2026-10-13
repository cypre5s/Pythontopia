package com.pythontopia.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.media.domain.MediaJob;
import com.pythontopia.media.dto.JobStatusResponse;
import com.pythontopia.media.dto.JobSubmitResponse;
import com.pythontopia.media.dto.StoryboardVideoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 媒体异步合成作业端点（Track B，均需登录，落在 {@code SecurityConfig} 的 {@code anyRequest().authenticated()}）：
 *
 * <ul>
 *   <li>{@code POST /api/media/storyboard/video/jobs}：提交合成作业，返回 {@code 202}
 *       {@code {jobId,status,cached,assetId?}}（命中幂等缓存时 {@code cached=true,status=SUCCESS,assetId} 直给）。
 *   <li>{@code GET /api/media/jobs/{id}}：轮询作业状态 {@code {jobId,status,progress,assetId?,url?,errorCode?}}。
 *   <li>{@code GET /api/media/jobs/{id}/stream}：SSE 进度流（{@code plan}/{@code trace}/{@code result}/{@code error}）。
 *       前端用 {@code fetch}+JWT（非 EventSource）订阅。
 * </ul>
 *
 * <p>成片读取走匿名但带签名的 {@code GET /api/media/assets/{assetId}?k=<sig>}（{@code <video src>}
 * 不带 Authorization，签名见 {@link MediaAssetUrls}）；客户端必须原样使用响应里的 {@code url}。
 */
@Validated
@RestController
@RequestMapping("/api/media")
public class MediaJobController {

  private final MediaJobService service;
  private final ObjectMapper objectMapper;
  private final MediaAssetUrls assetUrls;

  public MediaJobController(
      MediaJobService service, ObjectMapper objectMapper, MediaAssetUrls assetUrls) {
    this.service = service;
    this.objectMapper = objectMapper;
    this.assetUrls = assetUrls;
  }

  @PostMapping("/storyboard/video/jobs")
  public ResponseEntity<JobSubmitResponse> submitJob(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody StoryboardVideoRequest request) {
    MediaJobService.SubmitResult result = service.submit(user.userId(), request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobSubmitResponse.from(result));
  }

  @GetMapping("/jobs/{id}")
  public JobStatusResponse getJob(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive long id) {
    MediaJob job = service.getJob(id).filter(found -> ownedBy(found, user)).orElseThrow(this::notFound);
    return JobStatusResponse.from(job, objectMapper, assetUrls);
  }

  @GetMapping(value = "/jobs/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamJob(
      @CurrentUser CurrentUserPrincipal user, @PathVariable @Positive long id) {
    return service.stream(id, user.userId());
  }

  private boolean ownedBy(MediaJob job, CurrentUserPrincipal user) {
    return job.userId() != null && job.userId().equals(user.userId());
  }

  private BusinessException notFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "MEDIA_JOB_NOT_FOUND", "合成作业不存在");
  }
}
