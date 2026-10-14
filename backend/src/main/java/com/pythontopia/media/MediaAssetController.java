package com.pythontopia.media;

import com.pythontopia.auth.domain.UserAccount;
import com.pythontopia.auth.repository.UserRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多模态媒资端点：
 *
 * <ul>
 *   <li>{@code POST /api/media/image}（需登录）：保留的兼容路由；图片生成 provider 固定关闭并返回错误。
 *   <li>{@code GET /api/media/assets/{id}?k=<sig>}（匿名，供 {@code <img>}/{@code <video>} src）：
 *       按 id 流式分发已生成的媒资字节，支持 HTTP Range。
 * </ul>
 *
 * <p>资源服务端点随 {@code /api/media/assets/**} 在 {@code SecurityConfig} 放行匿名，因为
 * {@code <img src>}/{@code <video src>} 不会带 Authorization 头。<strong>但匿名 ≠ 无授权</strong>：
 * {@code media_asset.id} 是 BIGSERIAL 可预测，裸 id 直链等于允许枚举下载全体用户的生成物，因此
 * 取流必须满足下列任一条件，否则一律 404（不区分「不存在」与「无权」）：
 *
 * <ol>
 *   <li>query 参数 {@code k} 是该 id 的合法签名（见 {@link MediaAssetUrls}）——所有 API 下发的
 *       直链都自带该签名；
 *   <li>请求带有效 JWT 且调用者就是资产 owner（原生客户端可直接带 Authorization 头）；
 *   <li>资产无 owner（{@code user_id IS NULL} 的系统级资产）。
 * </ol>
 */
@Validated
@RestController
@RequestMapping("/api/media")
public class MediaAssetController {

  private final XfyunImageService imageService;
  private final StoryboardCompositionService compositionService;
  private final MediaAssetRepository repository;
  private final MediaAssetStorage storage;
  private final MediaAssetUrls assetUrls;
  private final UserRepository userRepository;

  public MediaAssetController(
      XfyunImageService imageService,
      StoryboardCompositionService compositionService,
      MediaAssetRepository repository,
      MediaAssetStorage storage,
      MediaAssetUrls assetUrls,
      UserRepository userRepository) {
    this.imageService = imageService;
    this.compositionService = compositionService;
    this.repository = repository;
    this.storage = storage;
    this.assetUrls = assetUrls;
    this.userRepository = userRepository;
  }

  @PostMapping("/image")
  public MediaAssetDto generateImage(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody ImageGenerationRequest request) {
    MediaAsset asset =
        imageService.generate(
            user.userId(), request.prompt(), request.label(), request.resolvedKcCodes());
    return MediaAssetDto.from(asset, assetUrls);
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
    return MediaAssetDto.from(asset, assetUrls);
  }

  @GetMapping("/assets/{id}")
  public ResponseEntity<Resource> serveAsset(
      @PathVariable @Positive long id,
      @RequestParam(name = MediaAssetUrls.QUERY_PARAM, required = false) String signature,
      @RequestParam(name = MediaAssetUrls.EXPIRES_PARAM, required = false) Long expiresAtEpochSecond,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
    Optional<MediaAsset> asset = repository.findById(id);
    if (asset.isEmpty() || !isAuthorized(asset.get(), signature, expiresAtEpochSecond)) {
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

  private boolean isAuthorized(MediaAsset asset, String signature, Long expiresAtEpochSecond) {
    if (asset.userId() == null) {
      // 无 owner 的系统级资产：任何人可读，不含用户生成内容。
      return true;
    }
    if (userRepository.findById(asset.userId()).filter(UserAccount::isActive).isEmpty()) {
      return false;
    }
    if (assetUrls.verify(asset.id(), expiresAtEpochSecond, signature)
        || (expiresAtEpochSecond == null
            && assetUrls.verifyLegacy(asset.id(), signature, asset.createdAt()))) {
      return true;
    }
    Long callerId = currentUserId();
    return callerId != null && callerId.equals(asset.userId());
  }

  /**
   * 取当前登录者 id；匿名请求返回 {@code null}。
   *
   * <p>这里不用 {@code @CurrentUser} 参数解析器：本端点在 {@code SecurityConfig} 里 permitAll，
   * 匿名访问时 {@code SecurityContext} 持有的是 {@code AnonymousAuthenticationToken}（principal 为
   * {@code String}），直接绑定到 {@code CurrentUserPrincipal} 形参会抛类型错误。
   */
  private static Long currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Object principal = authentication == null ? null : authentication.getPrincipal();
    return principal instanceof CurrentUserPrincipal current ? current.userId() : null;
  }
}
