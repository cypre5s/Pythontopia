package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtProperties;
import com.pythontopia.auth.security.Role;
import com.pythontopia.media.domain.MediaAsset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@code GET /api/media/assets/{id}} 的归属校验回归。
 *
 * <p>该端点在 {@code SecurityConfig} 里随 {@code /api/media/assets/**} 匿名放行（{@code <img>/<video>}
 * src 不带 Authorization 头），而 {@code media_asset.id} 是 BIGSERIAL 完全可预测——历史实现无任何
 * 授权判定，等于允许顺序枚举下载全体用户生成的图片/视频。这里锁死修复后的四条判定：
 *
 * <ol>
 *   <li>无签名匿名请求他人资产 → 404（且不泄露存在性）；
 *   <li>带合法签名 → 200；
 *   <li>带错误签名 → 404；
 *   <li>无签名但带 owner JWT → 200；非 owner JWT → 404。
 * </ol>
 */
class MediaAssetControllerAuthorizationTest {

  private static final long OWNER_ID = 42L;
  private static final long ASSET_ID = 7L;

  private MediaAssetRepository repository;
  private MediaAssetStorage storage;
  private MediaAssetUrls assetUrls;
  private MediaAssetController controller;
  private Path tempFile;

  @BeforeEach
  void setUp() throws IOException {
    repository = mock(MediaAssetRepository.class);
    storage = mock(MediaAssetStorage.class);
    assetUrls = new MediaAssetUrls(new JwtProperties("media-url-test-secret-at-least-32-chars", 60));
    controller =
        new MediaAssetController(
            mock(XfyunImageService.class),
            mock(StoryboardCompositionService.class),
            repository,
            storage,
            assetUrls);

    tempFile = Files.createTempFile("pyt-media-auth", ".png");
    Files.write(tempFile, new byte[] {1, 2, 3, 4});
    when(repository.findById(ASSET_ID)).thenReturn(Optional.of(asset(OWNER_ID)));
    when(storage.resolveExisting("owned.png")).thenReturn(Optional.of(tempFile));
  }

  @AfterEach
  void tearDown() throws IOException {
    SecurityContextHolder.clearContext();
    Files.deleteIfExists(tempFile);
  }

  @Test
  @DisplayName("匿名无签名请求他人资产返回 404（阻断按 id 枚举）")
  void anonymousWithoutSignatureIsNotFound() {
    anonymous();

    ResponseEntity<Resource> response = controller.serveAsset(ASSET_ID, null, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("匿名带合法签名可取流（<img>/<video> src 直连）")
  void anonymousWithValidSignatureIsServed() {
    anonymous();

    ResponseEntity<Resource> response =
        controller.serveAsset(ASSET_ID, assetUrls.sign(ASSET_ID), null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("签名不匹配（含他人资产的签名）返回 404")
  void anonymousWithForgedSignatureIsNotFound() {
    anonymous();

    assertThat(controller.serveAsset(ASSET_ID, "not-a-signature", null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller.serveAsset(ASSET_ID, assetUrls.sign(ASSET_ID + 1), null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("owner 带 JWT 无签名也能取流，非 owner 一律 404")
  void ownerJwtIsAllowedAndOtherUserIsNotFound() {
    authenticate(OWNER_ID);
    assertThat(controller.serveAsset(ASSET_ID, null, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    authenticate(OWNER_ID + 1);
    assertThat(controller.serveAsset(ASSET_ID, null, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("无 owner 的系统级资产（user_id IS NULL）匿名可读")
  void systemAssetWithoutOwnerIsPublic() {
    when(repository.findById(ASSET_ID)).thenReturn(Optional.of(asset(null)));
    anonymous();

    assertThat(controller.serveAsset(ASSET_ID, null, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("签名对每个 assetId 唯一且下发直链自带签名")
  void urlCarriesPerAssetSignature() {
    assertThat(assetUrls.urlFor(ASSET_ID))
        .isEqualTo("/api/media/assets/" + ASSET_ID + "?k=" + assetUrls.sign(ASSET_ID));
    assertThat(assetUrls.sign(ASSET_ID)).isNotEqualTo(assetUrls.sign(ASSET_ID + 1));
    assertThat(assetUrls.verify(ASSET_ID, assetUrls.sign(ASSET_ID))).isTrue();
    assertThat(assetUrls.verify(ASSET_ID, null)).isFalse();
    assertThat(assetUrls.verify(ASSET_ID, "")).isFalse();
  }

  private MediaAsset asset(Long userId) {
    return new MediaAsset(
        ASSET_ID,
        userId,
        MediaAssetKind.IMAGE,
        "for 循环流程图",
        "owned.png",
        "image/png",
        false,
        "PLACEHOLDER",
        List.of("PY43-KC-001"),
        Instant.now());
  }

  /** 模拟 permitAll 路径上的匿名请求：SecurityContext 里是 AnonymousAuthenticationToken。 */
  private void anonymous() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
  }

  private void authenticate(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CurrentUserPrincipal(userId, "stu-" + userId, Role.STUDENT),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
  }
}
