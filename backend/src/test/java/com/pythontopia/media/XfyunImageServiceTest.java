package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.media.domain.MediaAsset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * 验证无 apikey 时文生图降级路径：返回 degraded=true 的本地占位 PNG，字节落盘且记录 media_asset。
 */
class XfyunImageServiceTest {

  @Test
  void fallsBackToPlaceholderWhenDisabled(@TempDir Path tempDir) {
    ImageProperties disabled = new ImageProperties(null, null, null, null, 50);
    MediaAssetStorage storage = new MediaAssetStorage(tempDir.toString());
    MediaAssetRepository repository = Mockito.mock(MediaAssetRepository.class);

    when(repository.insert(
            any(), eq(MediaAssetKind.IMAGE), any(), any(), any(), anyBoolean(), any(), any(), any()))
        .thenAnswer(
            invocation ->
                new MediaAsset(
                    7L,
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    invocation.getArgument(3),
                    invocation.getArgument(4),
                    invocation.getArgument(5),
                    invocation.getArgument(6),
                    invocation.getArgument(7),
                    Instant.now()));

    XfyunImageService service =
        new XfyunImageService(
            disabled, new ObjectMapper(), new MediaPlaceholderFactory(), storage, repository);

    MediaAsset asset = service.generate(42L, "for 循环执行流程图解", "PY43-KC-030", List.of("PY43-KC-030"));

    assertThat(asset.degraded()).isTrue();
    assertThat(asset.source()).isEqualTo("PLACEHOLDER");
    assertThat(asset.mimeType()).isEqualTo("image/png");
    assertThat(asset.kind()).isEqualTo(MediaAssetKind.IMAGE);
    // 字节真的落盘了，serve 端点据此可解析。
    Path written = tempDir.resolve(asset.filePath());
    assertThat(Files.isRegularFile(written)).isTrue();
  }

  @Test
  void isDisabledWithoutCredentials() {
    assertThat(new ImageProperties("", "", "", null, 0).isEnabled()).isFalse();
    assertThat(new ImageProperties("app", "key", "secret", null, 0).isEnabled()).isTrue();
    // 默认值兜底
    assertThat(new ImageProperties("app", "key", "secret", null, 0).dailyLimitPerUser()).isEqualTo(50);
    assertThat(new ImageProperties("app", "key", "secret", null, 0).baseUrl()).contains("xf-yun.com");
  }
}
