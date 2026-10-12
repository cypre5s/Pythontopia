package com.pythontopia.tutor;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.tutor.dto.TutorWorkflowCheckpointRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal checkpoint writer used by Graph after each completed AutoGen Tutor stage. */
@RestController
@RequestMapping("/api/internal/tutor/workflow/sessions/{sessionKey}/autogen-checkpoints")
public class TutorWorkflowInternalCheckpointController {

  private static final Pattern USER_SUBJECT = Pattern.compile("user:([1-9][0-9]{0,18})");
  private final TutorWorkflowService service;
  private final String internalToken;

  public TutorWorkflowInternalCheckpointController(
      TutorWorkflowService service, GraphServiceProperties properties) {
    this.service = service;
    this.internalToken = properties.ragAdminToken();
  }

  @PutMapping("/{runId}")
  public ResponseEntity<Void> save(
      @PathVariable String sessionKey,
      @PathVariable UUID runId,
      @Valid @RequestBody TutorWorkflowCheckpointRequest request,
      @RequestHeader(value = "X-Internal-Token", required = false) String providedToken,
      @RequestHeader(value = "X-Internal-Subject", required = false) String subject) {
    verifyInternalToken(providedToken);
    service.persistAutoGenCheckpoint(
        parseUserId(subject), sessionKey, runId, request.requestFingerprint(), request.checkpoint());
    return ResponseEntity.noContent().build();
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
