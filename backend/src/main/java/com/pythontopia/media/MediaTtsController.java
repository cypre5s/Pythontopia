package com.pythontopia.media;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.media.dto.TtsRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaTtsController {

  private final XfyunTtsService ttsService;

  public MediaTtsController(XfyunTtsService ttsService) {
    this.ttsService = ttsService;
  }

  @PostMapping("/tts")
  public ResponseEntity<byte[]> synthesize(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody TtsRequest request) {
    byte[] audio = ttsService.synthesize(user.userId(), request.text(), request.resolvedVoice());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"tts.mp3\"")
        .body(audio);
  }
}
