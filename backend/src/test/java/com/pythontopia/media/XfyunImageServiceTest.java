package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

/**
 * 验证无 apikey 时文生图 fail closed：不伪造本地图片、不写 media_asset。
 */
class XfyunImageServiceTest {

  @Test
  void failsClosedWhenDisabled(@TempDir Path tempDir) {
    ImageProperties disabled = new ImageProperties(null, null, null, null, 50);
    MediaAssetStorage storage = new MediaAssetStorage(tempDir.toString());
    MediaAssetRepository repository = Mockito.mock(MediaAssetRepository.class);

    XfyunImageService service =
        new XfyunImageService(disabled, new ObjectMapper(), storage, repository);

    for (int attempt = 0; attempt < 51; attempt++) {
      assertThatThrownBy(
              () ->
                  service.generate(
                      42L, "for 循环执行流程图解", "PY43-KC-030", List.of("PY43-KC-030")))
          .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
          .extracting(ex -> ((com.pythontopia.common.error.BusinessException) ex).code())
          .isEqualTo("IMAGE_PROVIDER_UNAVAILABLE");
    }
    verifyNoInteractions(repository);
  }

  @Test
  void imageGenerationStaysDisabledEvenWhenLegacyCredentialsArePresent() {
    assertThat(new ImageProperties("", "", "", null, 0).isEnabled()).isFalse();
    assertThat(new ImageProperties("app", "key", "secret", null, 0).isEnabled()).isFalse();
    // 默认值兜底
    assertThat(new ImageProperties("app", "key", "secret", null, 0).dailyLimitPerUser()).isEqualTo(50);
    assertThat(new ImageProperties("app", "key", "secret", null, 0).baseUrl()).contains("xf-yun.com");
  }

  @Test
  void providerErrorMessageIsNotWrittenToLogs(@TempDir Path tempDir) throws IOException {
    String privateProviderMessage = "PRIVATE_PROVIDER_RESPONSE_81a4";
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] body =
        ("{\"header\":{\"code\":13000,\"message\":\"" + privateProviderMessage + "\"}}")
            .getBytes(StandardCharsets.UTF_8);
    server.createContext(
        "/v2.1/tti",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    ImageProperties properties = Mockito.mock(ImageProperties.class);
    when(properties.isEnabled()).thenReturn(true);
    when(properties.appId()).thenReturn("test-app");
    when(properties.apiKey()).thenReturn("test-key");
    when(properties.apiSecret()).thenReturn("test-secret");
    when(properties.baseUrl())
        .thenReturn("http://127.0.0.1:" + server.getAddress().getPort() + "/v2.1/tti");
    when(properties.dailyLimitPerUser()).thenReturn(50);
    Logger logger = (Logger) LoggerFactory.getLogger(XfyunImageService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(logger.getLoggerContext());
    appender.start();
    logger.addAppender(appender);
    try {
      XfyunImageService service =
          new XfyunImageService(
              properties,
              new ObjectMapper(),
              new MediaAssetStorage(tempDir.toString()),
              Mockito.mock(MediaAssetRepository.class));

      assertThatThrownBy(
              () -> service.generate(42L, "test prompt", "test", List.of("PY43-KC-001")))
          .isInstanceOf(com.pythontopia.common.error.BusinessException.class);

      String loggedMessages =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .collect(Collectors.joining("\n"));
      assertThat(loggedMessages)
          .contains("Xfyun image generation failed")
          .doesNotContain(privateProviderMessage);
    } finally {
      logger.detachAppender(appender);
      server.stop(0);
    }
  }
}
