package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

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
}
