package com.pythontopia.mainline;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 主线域可调参数（全部带<b>合理默认值</b>，无需在共享 application.yml 配置即可运行）。
 *
 * <p>R-15 计划相关键：
 *
 * <ul>
 *   <li>{@code pythontopia.mainline.pregen-concurrency}（默认 2，§11.2）：backend→graph 章测预生成信号量并发上限。
 *   <li>{@code pythontopia.mainline.video-delta-max-sec}（默认 120，§9）：单次 video-progress 计入上限（超出钳制）。
 *   <li>{@code pythontopia.mainline.video-writes-per-min}（默认 2，§9）：每 stage 每分钟有效 video-progress 次数上限。
 *   <li>{@code pythontopia.mainline.test-generate-max-retries}（默认 2，§4.1）：graph 生成失败重试上限，超限走 golden 兜底。
 * </ul>
 *
 * <p>主代理集成时如需覆盖，请在共享 application.yml 增 {@code pythontopia.mainline.*}（见本类交付报告）。
 */
@ConfigurationProperties(prefix = "pythontopia.mainline")
public record MainlineProperties(
    Integer pregenConcurrency,
    Integer videoDeltaMaxSec,
    Integer videoWritesPerMin,
    Integer testGenerateMaxRetries) {

  public MainlineProperties {
    if (pregenConcurrency == null || pregenConcurrency < 1) {
      pregenConcurrency = 2;
    }
    if (videoDeltaMaxSec == null || videoDeltaMaxSec < 1) {
      videoDeltaMaxSec = 120;
    }
    if (videoWritesPerMin == null || videoWritesPerMin < 1) {
      videoWritesPerMin = 2;
    }
    if (testGenerateMaxRetries == null || testGenerateMaxRetries < 0) {
      testGenerateMaxRetries = 2;
    }
  }
}
