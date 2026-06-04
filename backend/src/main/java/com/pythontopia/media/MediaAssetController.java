package com.pythontopia.media;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.media.domain.MediaAsset;
import com.pythontopia.media.dto.ImageGenerationRequest;
import com.pythontopia.media.dto.MediaAssetDto;
import com.pythontopia.media.dto.StoryboardVideoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;

/**
 * 多模态媒资端点：
 *
 * <ul>
 *   <li>{@code POST /api/media/image}（需登录）：按 prompt 生成图片，返回 {@link MediaAssetDto}。
 *       无讯飞 apikey 时返回 {@code degraded=true} 的本地占位资产，不阻塞验收。
 *   <li>{@code GET /api/media/assets/{id}}（匿名，供 {@code <img>}/{@code <video>} src）：按 id 流式
 *       分发已生成的媒资字节，支持 HTTP Range。
 * </ul>
 *
 * <p>资源服务端点随 {@code /api/media/assets/**} 在 {@code SecurityConfig} 放行匿名，因为
 * {@code <img src>}/{@code <video src>} 不会带 Authorization 头。
 */
@Validated
@RestController
@RequestMapping("/api/media")
public class MediaAssetController {

  private final XfyunImageService imageService;
  private final StoryboardCompositionService compositionService;
  private final MediaAssetRepository repository;
  private final MediaAssetStorage storage;

  public MediaAssetController(
      XfyunImageService imageService,
      StoryboardCompositionService compositionService,
      MediaAssetRepository repository,
      MediaAssetStorage storage) {
    this.imageService = imageService;
    this.compositionService = compositionService;
    this.repository = repository;
    this.storage = storage;
  }

  @PostMapping("/image")
  public MediaAssetDto generateImage(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody ImageGenerationRequest request) {
    MediaAsset asset =
        imageService.generate(
            user.userId(), request.prompt(), request.label(), request.resolvedKcCodes());
    return MediaAssetDto.from(asset);
  }

  @PostMapping("/storyboard/video")
  public MediaAssetDto composeStoryboardVideo(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody StoryboardVideoRequest request) {
    List<StoryboardCompositionService.SceneSpec> specs =
        request.scenes().stream()
            .map(
                s ->
                    new StoryboardCompositionService.SceneSpec(
                        s.imagePrompt(),
                        s.narration(),
                        s.voice(),
                        s.resolvedDuration(),
                        s.resolvedKcCodes()))
            .toList();
    MediaAsset asset = compositionService.compose(user.userId(), specs);
    return MediaAssetDto.from(asset);
  }

  @GetMapping("/assets/{id}")
  public ResponseEntity<Resource> serveAsset(
      @PathVariable @Positive long id,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
    Optional<MediaAsset> asset = repository.findById(id);
    if (asset.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    Optional<Path> file = storage.resolveExisting(asset.get().filePath());
    if (file.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    MediaType contentType =
        MediaType.parseMediaType(
            asset.get().mimeType() == null ? "application/octet-stream" : asset.get().mimeType());
    return ByteRangeResponder.serve(file.get(), contentType, rangeHeader);
  }
}
