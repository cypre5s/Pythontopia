package com.pythontopia.media;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.media.domain.MediaAsset;
import com.pythontopia.media.domain.MediaJob;
import com.pythontopia.media.dto.StoryboardVideoRequest;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 媒体异步合成作业（Track B）服务层：把故事板短视频合成从 HTTP 请求线程解耦成可排队/可去重/可上报进度
 * 的后台作业，支撑高并发提交。
 *
 * <ul>
 *   <li><b>幂等去重</b>：{@code fingerprint = sha256(规范化 spec)}；命中同用户同指纹已成功且成片仍在的作业，
 *       直接复用、不重复合成。
 *   <li><b>并发护栏</b>：合成 worker 走专用有界线程池（{@code mediaComposeExecutor}）；讯飞「文生图/语音合成」
 *       各自全局 {@link Semaphore} 限路数，调用前 acquire，避免突发提交击穿上游配额。
 *   <li><b>进度上报</b>：worker 逐镜把 {@code progress_json} 回写库 + 向 SSE 订阅者广播 {@code trace}；
 *       终态广播 {@code result}/{@code error}。
 * </ul>
 *
 * <p>红线：合成失败必须进入 FAILED 并广播结构化错误；不生成占位图片或伪造成片。
 */
@Service
public class MediaJobService {

  private static final Logger log = LoggerFactory.getLogger(MediaJobService.class);
  private static final String KIND_STORYBOARD_VIDEO = "STORYBOARD_VIDEO";

  private final MediaJobRepository jobRepository;
  private final MediaAssetRepository assetRepository;
  private final StoryboardCompositionService compositionService;
  private final MediaJobStreamRegistry streamRegistry;
  private final ObjectMapper objectMapper;
  private final MediaAssetUrls assetUrls;
  private final Executor composeExecutor;
  private final Semaphore imageSemaphore;
  private final Semaphore ttsSemaphore;

  public MediaJobService(
      MediaJobRepository jobRepository,
      MediaAssetRepository assetRepository,
      StoryboardCompositionService compositionService,
      MediaJobStreamRegistry streamRegistry,
      ObjectMapper objectMapper,
      MediaAssetUrls assetUrls,
      MediaComposeProperties properties,
      @Qualifier("mediaComposeExecutor") Executor composeExecutor) {
    this.jobRepository = jobRepository;
    this.assetRepository = assetRepository;
    this.compositionService = compositionService;
    this.streamRegistry = streamRegistry;
    this.objectMapper = objectMapper;
    this.assetUrls = assetUrls;
    this.composeExecutor = composeExecutor;
    this.imageSemaphore = new Semaphore(properties.ttiConcurrency(), true);
    this.ttsSemaphore = new Semaphore(properties.ttsConcurrency(), true);
  }

  /** {@code POST /api/media/storyboard/video/jobs} 的结果。 */
  public record SubmitResult(long jobId, MediaJobStatus status, boolean cached, Long assetId) {}

  /**
   * 提交合成作业：算指纹 → 命中已成功成片直接 {@code cached} 返回；否则入库 {@code PENDING} 并提交线程池异步合成。
   */
  public SubmitResult submit(Long userId, StoryboardVideoRequest request) {
    String fingerprint = fingerprint(request);

    if (!compositionService.isImageGenerationEnabled()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "VIDEO_COMPOSE_UNAVAILABLE",
          "故事板图片生成服务未启用，无法提交视频合成任务");
    }

    Optional<MediaJob> cached = jobRepository.findCachedSuccess(userId, fingerprint);
    if (cached.isPresent()) {
      MediaJob hit = cached.get();
      if (hit.assetId() != null && assetRepository.findById(hit.assetId()).isPresent()) {
        return new SubmitResult(hit.id(), MediaJobStatus.SUCCESS, true, hit.assetId());
      }
    }

    String specJson = writeJson(request);
    MediaJob job = jobRepository.insert(userId, KIND_STORYBOARD_VIDEO, fingerprint, specJson);
    List<StoryboardCompositionService.SceneSpec> scenes = toSceneSpecs(request);
    long jobId = job.id();
    composeExecutor.execute(() -> runJob(jobId, userId, scenes));
    return new SubmitResult(jobId, MediaJobStatus.PENDING, false, null);
  }

  public Optional<MediaJob> getJob(long id) {
    return jobRepository.findById(id);
  }

  /**
   * 订阅作业进度 SSE。先下发 {@code plan}，再按库中当前状态回放：终态直给 {@code result}/{@code error} 并收尾，
   * 进行中回放最近一帧 {@code trace} 后挂起等待 worker 广播。鉴权：非本人作业按不存在处理。
   */
  public SseEmitter stream(long jobId, Long userId) {
    MediaJob job =
        jobRepository
            .findById(jobId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "MEDIA_JOB_NOT_FOUND", "合成作业不存在"));
    if (job.userId() == null || !job.userId().equals(userId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "MEDIA_JOB_NOT_FOUND", "合成作业不存在");
    }

    SseEmitter emitter = streamRegistry.subscribe(jobId);
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("nodes", streamRegistry.planNodes());
    streamRegistry.send(emitter, "plan", plan);

    switch (job.status()) {
      case SUCCESS -> {
        if (job.assetId() != null) {
          streamRegistry.send(emitter, "result", resultPayload(jobId, job.assetId()));
        }
        streamRegistry.completeQuietly(emitter);
      }
      case FAILED -> {
        String code = job.errorCode() == null ? "VIDEO_COMPOSE_FAILED" : job.errorCode();
        streamRegistry.send(emitter, "error", errorPayload(code, "合成失败"));
        streamRegistry.completeQuietly(emitter);
      }
      default -> {
        Map<String, Object> progress = parseProgress(job.progressJson());
        if (!progress.isEmpty()) {
          streamRegistry.send(emitter, "trace", replayTrace(progress));
        }
      }
    }
    return emitter;
  }

  /** 合成 worker：消费 job，逐镜出图→配音→片段→concat 成片，落 media_asset，回写状态与进度。 */
  private void runJob(long jobId, Long userId, List<StoryboardCompositionService.SceneSpec> scenes) {
    long startNanos = System.nanoTime();
    try {
      jobRepository.updateStatus(jobId, MediaJobStatus.RUNNING, null, null);
      if (!compositionService.isAvailable()) {
        throw new BusinessException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "VIDEO_COMPOSE_UNAVAILABLE",
            "服务端未安装 ffmpeg，已降级为实时剧场播放");
      }
      MediaAsset asset =
          compositionService.compose(userId, scenes, new JobProgressSink(jobId, startNanos));
      jobRepository.updateStatus(jobId, MediaJobStatus.SUCCESS, asset.id(), null);
      streamRegistry.broadcast(jobId, "result", resultPayload(jobId, asset.id()));
    } catch (BusinessException ex) {
      log.warn("media job {} failed: {} {}", jobId, ex.code(), ex.getMessage());
      jobRepository.updateStatus(jobId, MediaJobStatus.FAILED, null, ex.code());
      streamRegistry.broadcast(jobId, "error", errorPayload(ex.code(), ex.getMessage()));
    } catch (Exception ex) {
      log.error("media job {} failed unexpectedly", jobId, ex);
      jobRepository.updateStatus(jobId, MediaJobStatus.FAILED, null, "VIDEO_COMPOSE_FAILED");
      streamRegistry.broadcast(jobId, "error", errorPayload("VIDEO_COMPOSE_FAILED", ex.getMessage()));
    } finally {
      streamRegistry.complete(jobId);
    }
  }

  /** 进度回调实现：回写 {@code progress_json} + 广播 {@code trace}，并把讯飞调用接到全局 Semaphore 闸门。 */
  private final class JobProgressSink implements StoryboardCompositionService.ProgressSink {

    private final long jobId;
    private final long startNanos;

    private JobProgressSink(long jobId, long startNanos) {
      this.jobId = jobId;
      this.startNanos = startNanos;
    }

    @Override
    public void onProgress(
        String phase, int sceneIndex, int sceneTotal, String roleKey, String message, int percent) {
      Map<String, Object> progress = new LinkedHashMap<>();
      progress.put("phase", phase);
      progress.put("sceneIndex", sceneIndex);
      progress.put("sceneTotal", sceneTotal);
      progress.put("roleKey", roleKey);
      progress.put("message", message);
      progress.put("percent", percent);
      jobRepository.updateProgress(jobId, writeJson(progress));

      Map<String, Object> trace = new LinkedHashMap<>();
      trace.put("node", roleKey);
      trace.put("phase", phase);
      trace.put("status", "running");
      trace.put("elapsed_ms", (System.nanoTime() - startNanos) / 1_000_000L);
      trace.put("role_key", roleKey);
      trace.put("message", message);
      trace.put("percent", percent);
      streamRegistry.broadcast(jobId, "trace", trace);
    }

    @Override
    public void acquireImageSlot() throws InterruptedException {
      imageSemaphore.acquire();
    }

    @Override
    public void releaseImageSlot() {
      imageSemaphore.release();
    }

    @Override
    public void acquireTtsSlot() throws InterruptedException {
      ttsSemaphore.acquire();
    }

    @Override
    public void releaseTtsSlot() {
      ttsSemaphore.release();
    }
  }

  private Map<String, Object> resultPayload(long jobId, long assetId) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("jobId", jobId);
    result.put("assetId", assetId);
    result.put("url", assetUrls.urlFor(assetId));
    return result;
  }

  private Map<String, Object> errorPayload(String code, String message) {
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("code", code);
    error.put("message", message);
    return error;
  }

  private Map<String, Object> replayTrace(Map<String, Object> progress) {
    Object roleKey = progress.get("roleKey");
    Map<String, Object> trace = new LinkedHashMap<>();
    trace.put("node", roleKey);
    trace.put("phase", progress.get("phase"));
    trace.put("status", "running");
    trace.put("elapsed_ms", 0L);
    trace.put("role_key", roleKey);
    trace.put("message", progress.get("message"));
    trace.put("percent", progress.getOrDefault("percent", 0));
    return trace;
  }

  private Map<String, Object> parseProgress(String json) {
    if (json == null || json.isBlank() || "{}".equals(json.trim())) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception ex) {
      log.debug("failed to parse media job progress json, replaying nothing: {}", ex.toString());
      return Map.of();
    }
  }

  private List<StoryboardCompositionService.SceneSpec> toSceneSpecs(StoryboardVideoRequest request) {
    return request.scenes().stream()
        .map(
            scene ->
                new StoryboardCompositionService.SceneSpec(
                    scene.imagePrompt(),
                    scene.narration(),
                    scene.voice(),
                    scene.resolvedDuration(),
                    scene.resolvedKcCodes()))
        .toList();
  }

  /**
   * 规范化 spec 的 sha-256（hex，64 字符）。逐镜归一化（去空白、空值兜底、TreeMap 键排序）后序列化，
   * 保证同一分镜规格指纹稳定、可幂等去重。
   */
  private String fingerprint(StoryboardVideoRequest request) {
    try {
      List<Map<String, Object>> normalized = new ArrayList<>();
      for (StoryboardVideoRequest.Scene scene : request.scenes()) {
        Map<String, Object> entry = new java.util.TreeMap<>();
        entry.put("imagePrompt", scene.imagePrompt() == null ? "" : scene.imagePrompt().trim());
        entry.put("narration", scene.narration() == null ? "" : scene.narration().trim());
        entry.put("voice", scene.voice() == null ? "" : scene.voice().trim());
        entry.put("durationSeconds", scene.resolvedDuration());
        entry.put("kcCodes", scene.resolvedKcCodes());
        normalized.add(entry);
      }
      byte[] canonical = objectMapper.writeValueAsBytes(normalized);
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
      return HexFormat.of().formatHex(digest);
    } catch (Exception ex) {
      throw new BusinessException(
          HttpStatus.INTERNAL_SERVER_ERROR, "FINGERPRINT_FAILED", "无法计算合成指纹");
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new BusinessException(
          HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_JOB_SERIALIZE_FAILED", "无法序列化合成规格");
    }
  }
}
