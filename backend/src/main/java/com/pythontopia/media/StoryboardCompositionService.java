package com.pythontopia.media;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.media.domain.MediaAsset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 把一份故事板分镜（图序列 + 每镜 TTS 旁白）经 ffmpeg 程序化合成为真实 mp4 短视频讲解。
 *
 * <p>讯飞未提供公开「文生视频」接口，故采用「文生图 + TTS 配音 → ffmpeg 拼接」路线。每个分镜：
 * {@link XfyunImageService} 生成真实插画（无凭据时 fail closed）+ {@link XfyunTtsService} 合成旁白，
 * 再用 ffmpeg 把图按旁白时长定格成片段并 concat 为整片。
 *
 * <p><b>降级</b>：未安装 ffmpeg 时 {@link #isAvailable()} 为 false，{@link #compose} 抛
 * {@code VIDEO_COMPOSE_UNAVAILABLE}，前端据此回退到「实时剧场」（图 + TTS + 动效）播放，不阻塞主流程。
 */
@Service
public class StoryboardCompositionService {

  private static final Logger log = LoggerFactory.getLogger(StoryboardCompositionService.class);
  private static final int PER_CMD_TIMEOUT_SECONDS = 60;
  private static final int MAX_SCENES = 8;

  private final String ffmpegPath;
  private final XfyunImageService imageService;
  private final XfyunTtsService ttsService;
  private final MediaAssetStorage storage;
  private final MediaAssetRepository repository;

  private volatile Boolean availableCache;

  public StoryboardCompositionService(
      @Value("${pythontopia.media.ffmpegPath:ffmpeg}") String ffmpegPath,
      XfyunImageService imageService,
      XfyunTtsService ttsService,
      MediaAssetStorage storage,
      MediaAssetRepository repository) {
    this.ffmpegPath = ffmpegPath;
    this.imageService = imageService;
    this.ttsService = ttsService;
    this.storage = storage;
    this.repository = repository;
  }

  /** 单个分镜的合成输入。 */
  public record SceneSpec(
      String imagePrompt, String narration, String voice, int durationSeconds, List<String> kcCodes) {}

  /**
   * 合成进度回调 + 讯飞路数闸门。同步端点（{@code /api/media/storyboard/video}）用 {@link #NOOP}，
   * 行为与重构前完全一致；异步 job worker 注入实现：{@link #onProgress} 回写 {@code progress_json}
   * 并向 SSE 订阅者推 trace（{@code roleKey}=illustrator/narrator/editor），
   * {@code acquire*Slot}/{@code release*Slot} 接到全局 Semaphore 限讯飞并发（acquire 在 TTS/TTI 调用前）。
   */
  public interface ProgressSink {

    /** 一个合成阶段开始。 */
    void onProgress(
        String phase, int sceneIndex, int sceneTotal, String roleKey, String message, int percent);

    /** 文生图（插画师）调用前获取一路讯飞 TTI 配额；NOOP 不限流。 */
    default void acquireImageSlot() throws InterruptedException {}

    /** 释放一路讯飞 TTI 配额（务必在 finally 调用）。 */
    default void releaseImageSlot() {}

    /** 语音合成（配音师）调用前获取一路讯飞 TTS 配额；NOOP 不限流。 */
    default void acquireTtsSlot() throws InterruptedException {}

    /** 释放一路讯飞 TTS 配额（务必在 finally 调用）。 */
    default void releaseTtsSlot() {}

    /** 无操作 sink：保持同步端点旧行为（无进度上报、无并发闸门）。 */
    ProgressSink NOOP = (phase, sceneIndex, sceneTotal, roleKey, message, percent) -> {};
  }

  /** ffmpeg 是否可用（缓存探测结果）。 */
  public boolean isAvailable() {
    Boolean cached = availableCache;
    if (cached != null) {
      return cached;
    }
    boolean ok;
    try {
      Process process = new ProcessBuilder(ffmpegPath, "-version").redirectErrorStream(true).start();
      ok = process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
      if (!ok) {
        process.destroyForcibly();
      }
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      ok = false;
    }
    availableCache = ok;
    return ok;
  }

  /** 同步合成（{@code /api/media/storyboard/video}）：无进度上报、无并发闸门，行为与重构前一致。 */
  public MediaAsset compose(Long userId, List<SceneSpec> scenes) {
    return compose(userId, scenes, ProgressSink.NOOP);
  }

  /**
   * 带进度回调 + 讯飞路数闸门的合成（异步 job worker 用）。逐镜：插画师出图（{@code acquireImageSlot}）
   * → 配音师配音（{@code acquireTtsSlot}）→ 定格片段 → 剪辑师 concat 成片，每阶段经 {@code sink} 上报。
   */
  public MediaAsset compose(Long userId, List<SceneSpec> scenes, ProgressSink sink) {
    if (scenes == null || scenes.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "STORYBOARD_EMPTY", "分镜为空，无法合成视频");
    }
    if (scenes.size() > MAX_SCENES) {
      scenes = scenes.subList(0, MAX_SCENES);
    }
    if (!isAvailable()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "VIDEO_COMPOSE_UNAVAILABLE",
          "服务端未安装 ffmpeg，已降级为实时剧场播放");
    }

    int total = scenes.size();
    long units = 2L * total + 1; // 每镜 2 步（图/音）+ 1 步成片，用于进度百分比。
    Path work = null;
    boolean degraded = false;
    List<String> kcAll = new ArrayList<>();
    try {
      work = Files.createTempDirectory("pyt-storyboard-");
      List<Path> clips = new ArrayList<>();
      for (int i = 0; i < scenes.size(); i++) {
        SceneSpec scene = scenes.get(i);
        kcAll.addAll(scene.kcCodes() == null ? List.of() : scene.kcCodes());

        sink.onProgress(
            "illustrate", i, total, "illustrator", "插画师绘制第 " + (i + 1) + " 镜",
            percentOf(2L * i, units));
        MediaAsset image;
        sink.acquireImageSlot();
        try {
          image = imageService.generate(userId, resolvePrompt(scene), labelOf(scene), scene.kcCodes());
        } finally {
          sink.releaseImageSlot();
        }
        degraded = degraded || image.degraded();
        Path imagePath =
            storage
                .resolveExisting(image.filePath())
                .orElseThrow(
                    () ->
                        new BusinessException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "IMAGE_MISSING",
                            "分镜插画落盘失败"));

        sink.onProgress(
            "narrate", i, total, "narrator", "配音师朗读第 " + (i + 1) + " 镜",
            percentOf(2L * i + 1, units));
        Path audioPath = synthAudio(userId, scene, work, i, sink);
        if (audioPath == null) {
          degraded = true; // 没有真实 TTS 音轨即视为降级（静默片段）。
        }
        clips.add(buildClip(work, i, imagePath, audioPath, scene.durationSeconds()));
      }

      sink.onProgress("edit", total, total, "editor", "剪辑师拼接成片", percentOf(2L * total, units));
      Path finalMp4 = concat(work, clips);
      byte[] bytes = Files.readAllBytes(finalMp4);
      String file = storage.write("mp4", bytes);
      return repository.insert(
          userId,
          MediaAssetKind.VIDEO,
          "storyboard composition",
          file,
          "video/mp4",
          degraded,
          "FFMPEG",
          kcAll,
          null);
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.error("storyboard composition failed", ex);
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "VIDEO_COMPOSE_FAILED", "短视频合成失败：" + ex.getMessage());
    } finally {
      cleanup(work);
    }
  }

  private Path synthAudio(Long userId, SceneSpec scene, Path work, int index, ProgressSink sink)
      throws InterruptedException {
    String narration = scene.narration() == null ? "" : scene.narration().trim();
    if (narration.isEmpty()) {
      return null;
    }
    sink.acquireTtsSlot();
    try {
      byte[] audio =
          ttsService.synthesize(
              userId, narration, scene.voice() == null ? "x4_yezi" : scene.voice());
      Path audioPath = work.resolve("scene_" + index + ".mp3");
      Files.write(audioPath, audio);
      return audioPath;
    } catch (Exception ex) {
      // TTS 未配置/失败 → 该片段无音轨，按时长定格（不阻塞合成）。
      log.debug("scene {} tts unavailable, silent clip: {}", index, ex.getMessage());
      return null;
    } finally {
      sink.releaseTtsSlot();
    }
  }

  private static int percentOf(long done, long total) {
    if (total <= 0) {
      return 0;
    }
    long pct = Math.round(done * 100.0 / total);
    return (int) Math.max(0, Math.min(100, pct));
  }

  private Path buildClip(Path work, int index, Path image, Path audio, int durationSeconds)
      throws IOException, InterruptedException {
    Path clip = work.resolve("clip_" + index + ".mp4");
    int dur = Math.max(3, Math.min(durationSeconds, 90));
    List<String> cmd = new ArrayList<>();
    cmd.add(ffmpegPath);
    cmd.add("-y");
    cmd.add("-loop");
    cmd.add("1");
    cmd.add("-i");
    cmd.add(image.toString());
    if (audio != null) {
      cmd.add("-i");
      cmd.add(audio.toString());
    }
    cmd.add("-c:v");
    cmd.add("libx264");
    cmd.add("-tune");
    cmd.add("stillimage");
    cmd.add("-pix_fmt");
    cmd.add("yuv420p");
    cmd.add("-vf");
    cmd.add("scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2");
    if (audio != null) {
      cmd.add("-c:a");
      cmd.add("aac");
      cmd.add("-b:a");
      cmd.add("128k");
      cmd.add("-shortest");
    } else {
      cmd.add("-t");
      cmd.add(String.valueOf(dur));
    }
    cmd.add(clip.toString());
    run(cmd);
    return clip;
  }

  private Path concat(Path work, List<Path> clips) throws IOException, InterruptedException {
    if (clips.size() == 1) {
      return clips.get(0);
    }
    Path list = work.resolve("concat.txt");
    StringBuilder sb = new StringBuilder();
    for (Path clip : clips) {
      sb.append("file '").append(clip.toAbsolutePath()).append("'\n");
    }
    Files.writeString(list, sb.toString());
    Path out = work.resolve("final.mp4");
    run(
        List.of(
            ffmpegPath, "-y", "-f", "concat", "-safe", "0", "-i", list.toString(),
            "-c", "copy", out.toString()));
    return out;
  }

  private void run(List<String> cmd) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    boolean done = process.waitFor(PER_CMD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!done) {
      process.destroyForcibly();
      throw new IOException("ffmpeg timed out: " + String.join(" ", cmd));
    }
    if (process.exitValue() != 0) {
      String tail = new String(process.getInputStream().readAllBytes()).strip();
      throw new IOException("ffmpeg exit " + process.exitValue() + ": " + tail);
    }
  }

  private String resolvePrompt(SceneSpec scene) {
    String prompt = scene.imagePrompt() == null ? "" : scene.imagePrompt().trim();
    return prompt.isEmpty() ? "像素教学插画，Python 知识点示意，暖色调，无文字" : prompt;
  }

  private String labelOf(SceneSpec scene) {
    if (scene.kcCodes() != null && !scene.kcCodes().isEmpty()) {
      return scene.kcCodes().get(0);
    }
    return "";
  }

  private void cleanup(Path work) {
    if (work == null) {
      return;
    }
    try (var paths = Files.walk(work)) {
      paths
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                  // 临时目录尽力清理，失败忽略
                }
              });
    } catch (IOException ignored) {
      // 临时目录尽力清理，失败忽略
    }
  }
}
