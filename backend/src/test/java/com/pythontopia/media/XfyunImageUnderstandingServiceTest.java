package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 看图答疑「图片理解」服务的网络无关降级路径单测：凭据缺失 / 非法 base64 / 超限 / 空图一律返回 null，
 * 由导师链路回退纯文本答疑（真实 WebSocket 调用不在单测覆盖，与同为 WS 的 XfyunTtsService 一致）。
 */
class XfyunImageUnderstandingServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private XfyunImageUnderstandingService service(ImageUnderstandingProperties props) {
    return new XfyunImageUnderstandingService(props, MAPPER);
  }

  private ImageUnderstandingProperties enabled(int maxImageBytes) {
    return new ImageUnderstandingProperties("app", "key", "secret", null, null, 0, 0, maxImageBytes);
  }

  @Test
  void propertiesDefaultsAndEnabledFlag() {
    ImageUnderstandingProperties p =
        new ImageUnderstandingProperties("app", "key", "secret", null, null, 0, 0, 0);
    assertThat(p.isEnabled()).isTrue();
    assertThat(p.baseUrl()).contains("v2.1/image");
    assertThat(p.domain()).isEqualTo("imagev3");
    assertThat(p.maxTokens()).isEqualTo(1024);
    assertThat(p.dailyLimitPerUser()).isEqualTo(50);
    assertThat(p.maxImageBytes()).isEqualTo(4 * 1024 * 1024);

    assertThat(new ImageUnderstandingProperties("", "", "", null, null, 0, 0, 0).isEnabled())
        .isFalse();
  }

  @Test
  void returnsNullWhenDisabled() {
    XfyunImageUnderstandingService svc =
        service(new ImageUnderstandingProperties(null, null, null, null, null, 0, 0, 0));
    assertThat(svc.isEnabled()).isFalse();
    assertThat(svc.understand(1L, "data:image/png;base64,aGVsbG8=", "描述")).isNull();
  }

  @Test
  void returnsNullOnInvalidBase64() {
    XfyunImageUnderstandingService svc = service(enabled(4 * 1024 * 1024));
    assertThat(svc.understand(1L, "data:image/png;base64,not*valid*base64", "描述")).isNull();
  }

  @Test
  void returnsNullWhenImageExceedsLimit() {
    // maxImageBytes=1：任何有效图片都超限，在发起 WebSocket 前即返回 null（不触网）。
    XfyunImageUnderstandingService svc = service(enabled(1));
    assertThat(svc.understand(1L, "aGVsbG8=", "描述")).isNull();
  }

  @Test
  void returnsNullForBlankOrNullImage() {
    XfyunImageUnderstandingService svc = service(enabled(4 * 1024 * 1024));
    assertThat(svc.understand(1L, "  ", "描述")).isNull();
    assertThat(svc.understand(1L, null, "描述")).isNull();
  }
}
