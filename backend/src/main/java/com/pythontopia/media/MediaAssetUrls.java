package com.pythontopia.media;

import com.pythontopia.auth.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 媒资直链的签名器（capability URL）。
 *
 * <p>{@code <img src>} / {@code <video src>} 不会带 {@code Authorization} 头，所以
 * {@code GET /api/media/assets/{id}} 必须保持匿名可达；但 {@code media_asset.id} 是
 * {@code BIGSERIAL}（完全可预测），裸 id 直链等于让任何人顺序枚举下载全体用户生成的图片/视频。
 *
 * <p>因此下发给客户端的直链一律带签名和到期秒数：{@code /api/media/assets/{id}?e=<epoch>&k=<sig>}，
 * HMAC-SHA256 覆盖资产 id 与到期秒数，取前 16 字节 base64url。新直链有效 24 小时；服务端按常量时间
 * 比对，签名不符、过期且不是资产 owner 时一律 404（不区分「不存在」与「无权」，不给存在性预言机）。
 *
 * <p>迁移兼容：旧的 {@code ?k=<sig>} 只在 {@code createdAt + 24h} 前接受；过期后客户端从既有 API
 * 重新获取 DTO 或作业状态即可得到新链接。资产 owner 的 JWT 直连方式保持不变。
 *
 * <p>相比给 {@code media_asset} 加随机 UUID 列，签名 URL 无需迁移或回填，且作业 SSE / 轮询响应可由
 * {@code assetId} 就地算出直链。轮换 {@code PYT_JWT_SECRET} 会让已下发直链失效，重新取 URL 即可。
 */
@Component
public class MediaAssetUrls {

  static final String QUERY_PARAM = "k";
  static final String EXPIRES_PARAM = "e";
  static final Duration SIGNED_URL_TTL = Duration.ofHours(24);

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String PAYLOAD_PREFIX = "media-asset:";
  /** 截断到 128 bit：足以抵抗离线伪造，同时让直链短一些。 */
  private static final int SIGNATURE_BYTES = 16;

  private final byte[] secret;
  private final Clock clock;

  public MediaAssetUrls(JwtProperties jwtProperties) {
    this.secret = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
    this.clock = Clock.systemUTC();
  }

  /** 可直接用于 {@code <img>/<video>} src 的签名直链。 */
  public String urlFor(long assetId) {
    long expiresAtEpochSecond = Instant.now(clock).plus(SIGNED_URL_TTL).getEpochSecond();
    return "/api/media/assets/"
        + assetId
        + "?"
        + EXPIRES_PARAM
        + "="
        + expiresAtEpochSecond
        + "&"
        + QUERY_PARAM
        + "="
        + sign(assetId, expiresAtEpochSecond);
  }

  /** 新格式签名：资产 id 与到期秒数均被 HMAC 覆盖。 */
  String sign(long assetId, long expiresAtEpochSecond) {
    return hmac(PAYLOAD_PREFIX + assetId + ":expires:" + expiresAtEpochSecond);
  }

  /** 旧格式签名，仅供有界兼容校验和回归测试使用。 */
  String sign(long assetId) {
    return hmac(PAYLOAD_PREFIX + assetId);
  }

  private String hmac(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      byte[] full = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(Arrays.copyOf(full, SIGNATURE_BYTES));
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalStateException("Unable to sign media asset url", ex);
    }
  }

  /** 校验新格式签名，并拒绝缺失或已过期的到期秒数。 */
  public boolean verify(long assetId, Long expiresAtEpochSecond, String signature) {
    if (expiresAtEpochSecond == null
        || expiresAtEpochSecond <= Instant.now(clock).getEpochSecond()
        || signature == null
        || signature.isBlank()) {
      return false;
    }
    return matches(sign(assetId, expiresAtEpochSecond), signature);
  }

  /** 校验旧格式签名，但只在资产创建后的 24 小时迁移窗口中接受。 */
  boolean verifyLegacy(long assetId, String signature, Instant createdAt) {
    if (signature == null
        || signature.isBlank()
        || createdAt == null
        || !Instant.now(clock).isBefore(createdAt.plus(SIGNED_URL_TTL))) {
      return false;
    }
    return matches(sign(assetId), signature);
  }

  private static boolean matches(String expected, String signature) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
  }
}
