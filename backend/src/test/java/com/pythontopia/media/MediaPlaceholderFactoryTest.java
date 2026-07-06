package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MediaPlaceholderFactoryTest {

  private final MediaPlaceholderFactory factory = new MediaPlaceholderFactory();

  @Test
  void rendersDecodable512PngWithCjkPromptAndLabel() throws Exception {
    byte[] png = factory.renderPng("第三章 循环结构的执行流程图解", "PY43-KC-030");

    assertThat(png).isNotEmpty();
    BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
    assertThat(img).isNotNull();
    assertThat(img.getWidth()).isEqualTo(512);
    assertThat(img.getHeight()).isEqualTo(512);
  }

  @Test
  void isDeterministicForSamePrompt() {
    byte[] first = factory.renderPng("for 循环", "PY43-KC-030");
    byte[] second = factory.renderPng("for 循环", "PY43-KC-030");
    assertThat(first).isEqualTo(second);
  }

  @Test
  void differsAcrossPrompts() {
    byte[] a = factory.renderPng("列表推导式", "PY43-KC-001");
    byte[] b = factory.renderPng("字典与集合", "PY43-KC-002");
    assertThat(a).isNotEqualTo(b);
  }
}
