package com.pythontopia.workshop.upload.service;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.workshop.upload.dto.UploadDetailDto;
import com.pythontopia.workshop.upload.dto.UploadDto;
import com.pythontopia.workshop.upload.entity.UploadStatus;
import com.pythontopia.workshop.upload.entity.WorkshopUpload;
import com.pythontopia.workshop.upload.parser.ExtractedContent;
import com.pythontopia.workshop.upload.parser.UploadParseException;
import com.pythontopia.workshop.upload.parser.UploadTextExtractor;
import com.pythontopia.workshop.upload.repository.UploadRepository;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 资源工坊文件上传服务：白名单校验（415）、大小限制（≤5MB 否则 413）、有界同步解析（PDFBox/POI/UTF-8），
 * 解析失败 fail-soft 落 {@code status=FAILED + error} 而非抛 500。上传内容是「用户提供内容」，仅经
 * {@code extractedText} 带标签注入答疑 context，绝不当课件 evidence。
 */
@Service
public class UploadService {

  private static final Logger log = LoggerFactory.getLogger(UploadService.class);

  /** 单文件大小上限 5 MB（契约 §3.4）。 */
  static final long MAX_BYTES = 5L * 1024 * 1024;
  // shortcut: fixed per-user storage limits; move to deployment properties if storage budgets differ.
  private static final int MAX_FILES_PER_USER = 20;
  private static final long MAX_TOTAL_BYTES_PER_USER = 50L * 1024 * 1024;

  /** filename / error 列宽（V162）：超长截断，避免插入失败。 */
  private static final int MAX_FILENAME_LENGTH = 255;
  private static final int MAX_ERROR_LENGTH = 512;

  private final UploadRepository repository;
  private final UploadTextExtractor extractor;

  public UploadService(UploadRepository repository, UploadTextExtractor extractor) {
    this.repository = repository;
    this.extractor = extractor;
  }

  @Transactional
  public UploadDto upload(long userId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "UPLOAD_EMPTY", "上传文件为空");
    }
    String filename = sanitizeFilename(file.getOriginalFilename());
    String ext = extension(filename);
    if (!UploadTextExtractor.ALLOWED_EXTENSIONS.contains(ext)) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "UPLOAD_EXT_NOT_ALLOWED",
          "不支持的文件类型：" + (ext.isEmpty() ? "(无扩展名)" : ext) + "，仅支持 .py/.txt/.md/.csv/.pdf/.docx");
    }
    if (file.getSize() > MAX_BYTES) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "文件超过 5MB 上限");
    }
    if (!repository.canAcceptUpload(
        userId, file.getSize(), MAX_FILES_PER_USER, MAX_TOTAL_BYTES_PER_USER)) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "UPLOAD_QUOTA_EXCEEDED",
          "个人上传额度已用完，请删除旧文件后再上传");
    }

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "UPLOAD_READ_FAILED", "读取上传文件失败");
    }
    if (bytes.length > MAX_BYTES) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "文件超过 5MB 上限");
    }

    if (!repository.canAcceptUpload(
        userId, bytes.length, MAX_FILES_PER_USER, MAX_TOTAL_BYTES_PER_USER)) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "UPLOAD_QUOTA_EXCEEDED",
          "个人上传额度已用完，请删除旧文件后再上传");
    }

    UploadStatus status;
    String error;
    String text;
    int charCount;
    int lineCount;
    try {
      ExtractedContent content = extractor.extract(ext, bytes);
      text = content.text();
      charCount = content.charCount();
      lineCount = content.lineCount();
      status = UploadStatus.READY;
      error = null;
    } catch (UploadParseException e) {
      // 解析失败是预期可恢复态：落 FAILED + error，返回 200 让前端 pill 显示错误并支持重试。
      log.info("upload parse failed ext={}", ext);
      text = "";
      charCount = 0;
      lineCount = 0;
      status = UploadStatus.FAILED;
      error = truncate(e.getMessage(), MAX_ERROR_LENGTH);
    }

    WorkshopUpload saved =
        repository.insert(
            userId, filename, ext, bytes.length, text, charCount, lineCount, status, error);
    return UploadDto.from(saved);
  }

  @Transactional(readOnly = true)
  public List<UploadDto> list(long userId) {
    return repository.listByUser(userId).stream().map(UploadDto::from).toList();
  }

  @Transactional(readOnly = true)
  public UploadDetailDto detail(long userId, long id) {
    return repository
        .findByIdForUser(userId, id)
        .map(UploadDetailDto::from)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "未找到该上传文件"));
  }

  @Transactional
  public void delete(long userId, long id) {
    int affected = repository.deleteByIdForUser(userId, id);
    if (affected == 0) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "未找到该上传文件");
    }
  }

  /**
   * A 通路服务方法：取本人某上传的解析文本（仅 {@link UploadStatus#READY} 才有文本），供服务端消费者
   * 带标签注入 context 使用。前端默认走 {@code GET /uploads/{id}} 取 {@code extractedText}，本方法为
   * Phase 2 服务端接线（如需后端直接注入）预留，不改动 tutor 端点。
   */
  @Transactional(readOnly = true)
  public Optional<String> extractedText(long userId, long id) {
    return repository
        .findByIdForUser(userId, id)
        .filter(upload -> upload.status() == UploadStatus.READY)
        .map(WorkshopUpload::extractedText);
  }

  private static String sanitizeFilename(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "UPLOAD_NO_FILENAME", "上传缺少文件名");
    }
    String name = raw.trim();
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (slash >= 0) {
      name = name.substring(slash + 1);
    }
    if (name.isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "UPLOAD_NO_FILENAME", "上传缺少文件名");
    }
    return truncate(name, MAX_FILENAME_LENGTH);
  }

  /** 取规范化小写扩展名（含点）；无扩展名返回空串（→ 415）。 */
  private static String extension(String filename) {
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return "";
    }
    return filename.substring(dot).toLowerCase(Locale.ROOT);
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  private static BusinessException quotaExceeded() {
    return new BusinessException(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "UPLOAD_QUOTA_EXCEEDED",
        "个人上传额度已用完，请删除旧文件后再上传");
  }
}
