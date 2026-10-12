package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.auth.security.JwtProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MediaAssetUrlsTest {

  private final MediaAssetUrls assetUrls =
      new MediaAssetUrls(new JwtProperties("media-url-test-secret-at-least-32-chars", 60));

  @Test
  void expiryIsSignedAndExpiredCapabilitiesAreRejected() {
    long assetId = 7L;
    long expiresAt = Instant.now().plusSeconds(3600).getEpochSecond();
    String signature = assetUrls.sign(assetId, expiresAt);

    assertThat(assetUrls.verify(assetId, expiresAt, signature)).isTrue();
    assertThat(assetUrls.verify(assetId, expiresAt + 1, signature)).isFalse();

    long expiredAt = Instant.now().minusSeconds(1).getEpochSecond();
    assertThat(assetUrls.verify(assetId, expiredAt, assetUrls.sign(assetId, expiredAt))).isFalse();
  }
}
