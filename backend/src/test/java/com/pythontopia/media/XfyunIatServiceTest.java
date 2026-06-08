package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.media.dto.AsrResponse;
import org.junit.jupiter.api.Test;

/**
 * 语音听写服务的网络无关降级路径单测：凭据缺失 / 空音频一律返回 degraded（不触网、不调 ffmpeg），
 * 由前端提示改用键盘输入（真实 WebSocket + ffmpeg 转码不在单测覆盖，与同为 WS 的 TTS / 图片理解一致）。
 */
class XfyunIatServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private XfyunIatService service(AsrProperties props) {
    return new XfyunIatService(props, MAPPER, "ffmpeg");
  }

  @Test
  void propertiesDefaultsAndEnabledFlag() {
    AsrProperties p = new AsrProperties("app", "key", "secret", null, 0);
    assertThat(p.isEnabled()).isTrue();
    assertThat(p.baseUrl()).contains("iat-api.xfyun.cn");
    assertThat(p.dailyLimitPerUser()).isEqualTo(100);

    assertThat(new AsrProperties("", "", "", null, 0).isEnabled()).isFalse();
    assertThat(new AsrProperties(null, null, null, null, 0).isEnabled()).isFalse();
  }

  @Test
  void returnsDegradedWhenDisabled() {
    XfyunIatService svc = service(new AsrProperties(null, null, null, null, 0));
    assertThat(svc.isEnabled()).isFalse();
    AsrResponse res = svc.transcribe(1L, new byte[] {1, 2, 3, 4}, "audio/webm");
    assertThat(res.degraded()).isTrue();
    assertThat(res.text()).isEmpty();
  }

  @Test
  void returnsDegradedForEmptyOrNullAudio() {
    XfyunIatService svc = service(new AsrProperties("app", "key", "secret", null, 0));
    assertThat(svc.transcribe(1L, new byte[0], "audio/webm").degraded()).isTrue();
    assertThat(svc.transcribe(1L, null, "audio/webm").degraded()).isTrue();
  }
}
