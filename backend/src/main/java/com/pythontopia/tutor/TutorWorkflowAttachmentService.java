package com.pythontopia.tutor;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.tutor.dto.TutorWorkflowAttachmentDto;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 短期、会话绑定的导学图片存储与 owner/session 作用域读取。 */
@Service
public class TutorWorkflowAttachmentService {

  static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
  private static final int MAX_SESSION_ATTACHMENTS = 10;
  private static final long MAX_SESSION_BYTES = 25L * 1024 * 1024;
  private static final int MAX_USER_ATTACHMENTS = 40;
  private static final long MAX_USER_BYTES = 100L * 1024 * 1024;
  private static final java.time.Duration ATTACHMENT_TTL = java.time.Duration.ofMinutes(30);

  private final TutorWorkflowRepository sessions;
  private final TutorWorkflowAttachmentRepository attachments;

  public TutorWorkflowAttachmentService(
      TutorWorkflowRepository sessions, TutorWorkflowAttachmentRepository attachments) {
    this.sessions = sessions;
    this.attachments = attachments;
  }

  @Transactional
  public TutorWorkflowAttachmentDto upload(long userId, String sessionKey, MultipartFile file) {
    TutorWorkflowSessionDto session = requireSession(userId, sessionKey);
    if (file == null || file.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TUTOR_IMAGE_EMPTY", "图片文件为空");
    }
    if (file.getSize() > MAX_IMAGE_BYTES) {
      throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "TUTOR_IMAGE_TOO_LARGE", "图片不能超过 5MiB");
    }
    String declaredType = normalizeContentType(file.getContentType());
    if (declaredType != null && !isSupportedContentType(declaredType)) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TUTOR_IMAGE_INVALID", "仅支持 PNG / JPG / GIF / WebP 图片");
    }
    attachments.lockOwnerUploads(userId);
    TutorWorkflowAttachmentUsage usage = attachments.activeUsage(userId, session.id());
    if (usage.sessionCount() >= MAX_SESSION_ATTACHMENTS
        || usage.sessionBytes() + file.getSize() > MAX_SESSION_BYTES
        || usage.userCount() >= MAX_USER_ATTACHMENTS
        || usage.userBytes() + file.getSize() > MAX_USER_BYTES) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "TUTOR_IMAGE_QUOTA_EXCEEDED", "图片附件数量或总容量已达上限");
    }

    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException e) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TUTOR_IMAGE_READ_FAILED", "读取图片失败");
    }
    if (content.length == 0) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "TUTOR_IMAGE_EMPTY", "图片文件为空");
    }
    if (content.length > MAX_IMAGE_BYTES) {
      throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "TUTOR_IMAGE_TOO_LARGE", "图片不能超过 5MiB");
    }

    String detectedType = detectImageType(content);
    if (detectedType == null || (declaredType != null && !detectedType.equals(declaredType))) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TUTOR_IMAGE_INVALID", "仅支持有效的 PNG / JPG / GIF / WebP 图片");
    }

    UUID id = UUID.randomUUID();
    attachments.insert(
        session.id(), id, detectedType, content, Instant.now().plus(ATTACHMENT_TTL));
    return new TutorWorkflowAttachmentDto(id.toString());
  }

  @Transactional(readOnly = true)
  public TutorWorkflowStoredImage readForGraph(long userId, String sessionKey, String attachmentId) {
    TutorWorkflowSessionDto session = requireSession(userId, sessionKey);
    UUID id;
    try {
      id = UUID.fromString(attachmentId);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "TUTOR_IMAGE_NOT_FOUND", "图片附件不存在或已过期");
    }
    return attachments
        .findActiveForOwnerSession(userId, session.id(), id)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "TUTOR_IMAGE_NOT_FOUND", "图片附件不存在或已过期"));
  }

  @Scheduled(fixedDelay = 300_000L, initialDelay = 30_000L)
  public void cleanupExpired() {
    attachments.deleteExpired();
  }

  private TutorWorkflowSessionDto requireSession(long userId, String sessionKey) {
    return sessions
        .findBySessionKey(userId, sessionKey)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "WORKFLOW_SESSION_NOT_FOUND", "导学会话不存在"));
  }

  private static String detectImageType(byte[] bytes) {
    if (startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) {
      return "image/png";
    }
    if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
      return "image/jpeg";
    }
    if (startsWith(bytes, "GIF87a".getBytes()) || startsWith(bytes, "GIF89a".getBytes())) {
      return "image/gif";
    }
    if (bytes.length >= 12
        && startsWith(bytes, "RIFF".getBytes())
        && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) {
      return "image/webp";
    }
    return null;
  }

  private static boolean startsWith(byte[] value, byte[] prefix) {
    if (value.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (value[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static String normalizeContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return null;
    }
    String normalized = contentType.toLowerCase(Locale.ROOT).trim();
    return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
  }

  private static boolean isSupportedContentType(String contentType) {
    return Set.of("image/png", "image/jpeg", "image/gif", "image/webp").contains(contentType);
  }
}
