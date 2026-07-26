package com.pythontopia.media;

import com.pythontopia.auth.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * <p>因此下发给客户端的直链一律带签名：{@code /api/media/assets/{id}?k=<sig>}，其中
 * {@code sig = HMAC-SHA256(jwtSecret, "media-asset:" + id)} 取前 16 字节的 base64url。
 * 服务端按常量时间比对，签名不符且不是资产 owner 时一律 404（不区分「不存在」与「无权」，
 * 不给存在性预言机）。
 *
 * <p>选型说明：相比「给 media_asset 加随机 UUID 列」，签名 URL 无需迁移、无需为已有行回填，
 * 且任何只拿到 {@code assetId} 的地方（作业 SSE result / 轮询响应）都能就地算出直链，
 * 不必额外回查资产表。代价是轮换 {@code PYT_JWT_SECRET} 会让旧直链失效——可接受，
 * 因为直链由每次 API 响应重新下发。
 */
@Component
public class MediaAssetUrls {

  static final String QUERY_PARAM = "k";

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String PAYLOAD_PREFIX = "media-asset:";
  /** 截断到 128 bit：足以抵抗离线伪造，同时让直链短一些。 */
  private static final int SIGNATURE_BYTES = 16;

  private final byte[] secret;

  public MediaAssetUrls(JwtProperties jwtProperties) {
    this.secret = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
  }

  /** 可直接用于 {@code <img>/<video>} src 的签名直链。 */
  public String urlFor(long assetId) {
    return "/api/media/assets/" + assetId + "?" + QUERY_PARAM + "=" + sign(assetId);
  }

  /** 单个资产的签名（base64url，无 padding）。 */
  public String sign(long assetId) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      byte[] full = mac.doFinal((PAYLOAD_PREFIX + assetId).getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(Arrays.copyOf(full, SIGNATURE_BYTES));
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalStateException("Unable to sign media asset url", ex);
    }
  }

  /** 常量时间校验；{@code null}/空签名直接判否。 */
  public boolean verify(long assetId, String signature) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        sign(assetId).getBytes(StandardCharsets.UTF_8),
        signature.getBytes(StandardCharsets.UTF_8));
  }
}
