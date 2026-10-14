package com.pythontopia.media;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.media.dto.AsrResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 兼容保留的语音听写端点：服务开关固定关闭，返回 unavailable 且不会读取或转发上传音频。
 */
@RestController
@RequestMapping("/api/media")
public class MediaAsrController {

  private static final long MAX_AUDIO_BYTES = 8L * 1024 * 1024;

  private final XfyunIatService iatService;

  public MediaAsrController(XfyunIatService iatService) {
    this.iatService = iatService;
  }

  @PostMapping(value = "/asr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AsrResponse transcribe(
      @CurrentUser CurrentUserPrincipal user, @RequestParam("audio") MultipartFile audio)
      throws IOException {
    if (!iatService.isEnabled()) {
      return AsrResponse.unavailable();
    }
    if (audio == null || audio.isEmpty()) {
      return AsrResponse.unavailable();
    }
    if (audio.getSize() > MAX_AUDIO_BYTES) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "ASR_AUDIO_TOO_LARGE",
          "音频文件不能超过 8MiB");
    }
    return iatService.transcribe(user.userId(), audio.getBytes(), audio.getContentType());
  }
}
