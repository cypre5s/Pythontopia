package com.pythontopia.tutor;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal, owner-scoped binary endpoint used by Graph only during Tutor card generation. */
@RestController
@RequestMapping("/api/internal/tutor/workflow/sessions/{sessionKey}/attachments")
public class TutorWorkflowInternalAttachmentController {

  private static final Pattern USER_SUBJECT = Pattern.compile("user:([1-9][0-9]{0,18})");
  private final TutorWorkflowAttachmentService service;
  private final String internalToken;

  public TutorWorkflowInternalAttachmentController(
      TutorWorkflowAttachmentService service, GraphServiceProperties properties) {
    this.service = service;
    this.internalToken = properties.ragAdminToken();
  }

  @GetMapping("/{attachmentId}")
  public ResponseEntity<byte[]> read(
      @PathVariable String sessionKey,
      @PathVariable String attachmentId,
      @RequestHeader(value = "X-Internal-Token", required = false) String providedToken,
      @RequestHeader(value = "X-Internal-Subject", required = false) String subject) {
    verifyInternalToken(providedToken);
    long userId = parseUserId(subject);
    TutorWorkflowStoredImage image = service.readForGraph(userId, sessionKey, attachmentId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(image.content());
  }

  private void verifyInternalToken(String providedToken) {
    if (internalToken == null || internalToken.isBlank()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "INTERNAL_AUTH_NOT_CONFIGURED",
          "Internal service authentication is not configured");
    }
    byte[] expected = internalToken.getBytes(StandardCharsets.UTF_8);
    byte[] provided = (providedToken == null ? "" : providedToken).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, provided)) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_TOKEN", "Invalid internal service token");
    }
  }

  private long parseUserId(String subject) {
    Matcher matcher = subject == null ? null : USER_SUBJECT.matcher(subject);
    if (matcher == null || !matcher.matches()) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_SUBJECT", "Invalid internal subject");
    }
    try {
      return Long.parseLong(matcher.group(1));
    } catch (NumberFormatException e) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_SUBJECT", "Invalid internal subject");
    }
  }
}
