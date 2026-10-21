package com.pythontopia.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtProperties;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.media.domain.MediaAsset;
import com.pythontopia.media.domain.MediaJob;
import com.pythontopia.media.dto.StoryboardVideoRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * {@link MediaJobService} 单测：覆盖①幂等命中（同 fingerprint 已 SUCCESS → cached）②状态流转
 * PENDING→RUNNING→SUCCESS ③ffmpeg 不可用→FAILED(VIDEO_COMPOSE_UNAVAILABLE)。
 *
 * <p>用同步执行器（{@code Runnable::run}）让 worker 在 {@code submit} 内联跑完，便于断言状态流转；
 * 协作方 {@link XfyunImageService}/{@link XfyunTtsService} 经 {@link StoryboardCompositionService} 隔离，
 * 直接 mock 合成服务即可。
 */
class MediaJobServiceTest {

  private static final long USER_ID = 7L;

  private MediaJobRepository jobRepository;
  private MediaAssetRepository assetRepository;
  private StoryboardCompositionService compositionService;
  private MediaJobStreamRegistry streamRegistry;
  private ObjectMapper objectMapper;
  private MediaJobService service;

  /** 同步执行器：worker 立即在调用线程跑完，便于断言 PENDING→RUNNING→SUCCESS/FAILED。 */
  private static final Executor SYNCHRONOUS = Runnable::run;

  @BeforeEach
  void setUp() {
    jobRepository = mock(MediaJobRepository.class);
    assetRepository = mock(MediaAssetRepository.class);
    compositionService = mock(StoryboardCompositionService.class);
    objectMapper = new ObjectMapper();
    MediaComposeProperties properties = new MediaComposeProperties(2, 2, 2, 64, 300_000L);
    streamRegistry = spy(new MediaJobStreamRegistry(objectMapper, properties));
    service =
        new MediaJobService(
            jobRepository,
            assetRepository,
            compositionService,
            streamRegistry,
            objectMapper,
            new MediaAssetUrls(new JwtProperties("media-job-test-secret-at-least-32-chars", 60)),
            new StudentWriteThrottle(),
            properties,
            SYNCHRONOUS);
  }

  private StoryboardVideoRequest sampleRequest() {
    return new StoryboardVideoRequest(
        List.of(
            new StoryboardVideoRequest.Scene(
                "像素教学插画：input 函数", "我们先看 input 怎么读取键盘输入", "x4_yezi", 20, List.of("PY43-KC-001"))));
  }

  private MediaJob job(long id, MediaJobStatus status, Long assetId, String errorCode) {
    return new MediaJob(
        id,
        USER_ID,
        "STORYBOARD_VIDEO",
        status,
        "fingerprint-" + id,
        "{}",
        "{}",
        assetId,
        errorCode,
        Instant.now(),
        Instant.now());
  }

  private MediaAsset videoAsset(long id) {
    return new MediaAsset(
        id,
        USER_ID,
        MediaAssetKind.VIDEO,
        "storyboard composition",
        id + ".mp4",
        "video/mp4",
        false,
        "FFMPEG",
        List.of("PY43-KC-001"),
        Instant.now());
  }

  @Test
  void idempotentHitReturnsCachedWithoutComposing() {
    MediaJob cached = job(10L, MediaJobStatus.SUCCESS, 777L, null);
    when(compositionService.isImageGenerationEnabled()).thenReturn(true);
    when(jobRepository.findCachedSuccess(eq(USER_ID), Mockito.anyString()))
        .thenReturn(Optional.of(cached));
    when(assetRepository.findById(777L)).thenReturn(Optional.of(videoAsset(777L)));

    MediaJobService.SubmitResult result = service.submit(USER_ID, sampleRequest());

    assertThat(result.cached()).isTrue();
    assertThat(result.status()).isEqualTo(MediaJobStatus.SUCCESS);
    assertThat(result.assetId()).isEqualTo(777L);
    assertThat(result.jobId()).isEqualTo(10L);

    // 命中缓存：不入库新作业、不触发任何合成。
    verify(jobRepository, never()).insert(any(), any(), any(), any());
    verify(compositionService, never())
        .compose(any(), anyList(), any(StoryboardCompositionService.ProgressSink.class));

    // 指纹是规范化 spec 的 sha-256（64 位 hex）。
    ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
    verify(jobRepository).findCachedSuccess(eq(USER_ID), fingerprint.capture());
    assertThat(fingerprint.getValue()).matches("[0-9a-f]{64}");
  }

  @Test
  void mediaJobPlanDoesNotAdvertiseDisabledVoiceSynthesis() {
    assertThat(streamRegistry.planNodes())
        .extracting(node -> node.get("role_key"))
        .doesNotContain("narrator");
  }

  @Test
  void disabledImageProviderRejectsBeforePersistingOrQueueing() {
    when(jobRepository.findCachedSuccess(eq(USER_ID), Mockito.anyString()))
        .thenReturn(Optional.empty());
    when(compositionService.isImageGenerationEnabled()).thenReturn(false);

    assertThatThrownBy(() -> service.submit(USER_ID, sampleRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(error.code()).isEqualTo("VIDEO_COMPOSE_UNAVAILABLE");
            });

    verify(jobRepository, never()).insert(any(), any(), any(), any());
    verify(compositionService, never())
        .compose(any(), anyList(), any(StoryboardCompositionService.ProgressSink.class));
  }

  @Test
  void limitsNewCompositionJobsPerAccountBeforePersistence() {
    when(jobRepository.findCachedSuccess(eq(USER_ID), Mockito.anyString()))
        .thenReturn(Optional.empty());
    when(jobRepository.insert(eq(USER_ID), eq("STORYBOARD_VIDEO"), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(
            job(61L, MediaJobStatus.PENDING, null, null),
            job(62L, MediaJobStatus.PENDING, null, null),
            job(63L, MediaJobStatus.PENDING, null, null),
            job(64L, MediaJobStatus.PENDING, null, null));
    when(compositionService.isImageGenerationEnabled()).thenReturn(true);
    service =
        new MediaJobService(
            jobRepository,
            assetRepository,
            compositionService,
            streamRegistry,
            objectMapper,
            new MediaAssetUrls(new JwtProperties("media-job-test-secret-at-least-32-chars", 60)),
            new StudentWriteThrottle(),
            new MediaComposeProperties(2, 2, 2, 64, 300_000L),
            command -> {});

    service.submit(USER_ID, sampleRequest());
    service.submit(USER_ID, sampleRequest());
    service.submit(USER_ID, sampleRequest());

    assertThatThrownBy(() -> service.submit(USER_ID, sampleRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
              assertThat(error.code()).isEqualTo("MEDIA_COMPOSE_RATE_LIMITED");
            });
    verify(jobRepository, times(3))
        .insert(eq(USER_ID), eq("STORYBOARD_VIDEO"), Mockito.anyString(), Mockito.anyString());
  }

  @Test
  void disabledImageProviderRejectsCachedVideoInsteadOfReturningIt() {
    when(compositionService.isImageGenerationEnabled()).thenReturn(false);

    assertThatThrownBy(() -> service.submit(USER_ID, sampleRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("VIDEO_COMPOSE_UNAVAILABLE"));

    verify(jobRepository, never()).findCachedSuccess(eq(USER_ID), Mockito.anyString());
    verify(assetRepository, never()).findById(Mockito.anyLong());
  }

  @Test
  void statusFlowsPendingThenRunningThenSuccess() {
    when(jobRepository.findCachedSuccess(eq(USER_ID), Mockito.anyString()))
        .thenReturn(Optional.empty());
    when(jobRepository.insert(eq(USER_ID), eq("STORYBOARD_VIDEO"), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(job(42L, MediaJobStatus.PENDING, null, null));
    when(compositionService.isImageGenerationEnabled()).thenReturn(true);
    when(compositionService.isAvailable()).thenReturn(true);
    when(compositionService.compose(
            eq(USER_ID), anyList(), any(StoryboardCompositionService.ProgressSink.class)))
        .thenReturn(videoAsset(555L));

    MediaJobService.SubmitResult result = service.submit(USER_ID, sampleRequest());

    assertThat(result.cached()).isFalse();
    assertThat(result.status()).isEqualTo(MediaJobStatus.PENDING);
    assertThat(result.jobId()).isEqualTo(42L);

    // worker 同步跑完：先置 RUNNING，再置 SUCCESS + asset_id。
    InOrder inOrder = Mockito.inOrder(jobRepository);
    inOrder.verify(jobRepository).updateStatus(42L, MediaJobStatus.RUNNING, null, null);
    inOrder.verify(jobRepository).updateStatus(42L, MediaJobStatus.SUCCESS, 555L, null);
  }

  @Test
  void ffmpegUnavailableMarksJobFailed() {
    when(jobRepository.findCachedSuccess(eq(USER_ID), Mockito.anyString()))
        .thenReturn(Optional.empty());
    when(jobRepository.insert(eq(USER_ID), eq("STORYBOARD_VIDEO"), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(job(43L, MediaJobStatus.PENDING, null, null));
    when(compositionService.isImageGenerationEnabled()).thenReturn(true);
    when(compositionService.isAvailable()).thenReturn(false);

    MediaJobService.SubmitResult result = service.submit(USER_ID, sampleRequest());

    assertThat(result.status()).isEqualTo(MediaJobStatus.PENDING);

    // ffmpeg 不可用：worker 不进入合成，直接置 FAILED + VIDEO_COMPOSE_UNAVAILABLE。
    verify(jobRepository).updateStatus(43L, MediaJobStatus.RUNNING, null, null);
    verify(jobRepository).updateStatus(43L, MediaJobStatus.FAILED, null, "VIDEO_COMPOSE_UNAVAILABLE");
    verify(compositionService, never())
        .compose(any(), anyList(), any(StoryboardCompositionService.ProgressSink.class));
  }

  @Test
  void rejectedQueueMarksJobFailedAndReturnsServiceUnavailable() {
    when(jobRepository.findCachedSuccess(eq(USER_ID), Mockito.anyString()))
        .thenReturn(Optional.empty());
    when(
            jobRepository.insert(
                eq(USER_ID),
                eq("STORYBOARD_VIDEO"),
                Mockito.anyString(),
                Mockito.anyString()))
        .thenReturn(job(50L, MediaJobStatus.PENDING, null, null));
    when(compositionService.isImageGenerationEnabled()).thenReturn(true);
    service =
        new MediaJobService(
            jobRepository,
            assetRepository,
            compositionService,
            streamRegistry,
            objectMapper,
            new MediaAssetUrls(new JwtProperties("media-job-test-secret-at-least-32-chars", 60)),
            new StudentWriteThrottle(),
            new MediaComposeProperties(2, 2, 2, 64, 300_000L),
            command -> {
              throw new RejectedExecutionException("queue full");
            });

    assertThatThrownBy(() -> service.submit(USER_ID, sampleRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(error.code()).isEqualTo("VIDEO_COMPOSE_QUEUE_FULL");
            });

    verify(jobRepository)
        .updateStatus(50L, MediaJobStatus.FAILED, null, "VIDEO_COMPOSE_QUEUE_FULL");
    verify(compositionService, never())
        .compose(any(), anyList(), any(StoryboardCompositionService.ProgressSink.class));
  }

  @Test
  void composeBusinessFailurePropagatesErrorCode() {
    when(jobRepository.findCachedSuccess(eq(USER_ID), Mockito.anyString()))
        .thenReturn(Optional.empty());
    String sentinel = "private-storyboard-input-error-sentinel";
    when(jobRepository.insert(eq(USER_ID), eq("STORYBOARD_VIDEO"), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(job(44L, MediaJobStatus.PENDING, null, null))
        .thenReturn(job(45L, MediaJobStatus.PENDING, null, null));
    when(compositionService.isImageGenerationEnabled()).thenReturn(true);
    when(compositionService.isAvailable()).thenReturn(true);
    when(compositionService.compose(
            eq(USER_ID), anyList(), any(StoryboardCompositionService.ProgressSink.class)))
        .thenThrow(new BusinessException(HttpStatus.BAD_GATEWAY, "VIDEO_COMPOSE_FAILED", sentinel))
        .thenThrow(new IllegalStateException(sentinel));

    Logger logger = (Logger) LoggerFactory.getLogger(MediaJobService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      service.submit(USER_ID, sampleRequest());
      service.submit(USER_ID, sampleRequest());
    } finally {
      logger.detachAppender(appender);
    }

    verify(jobRepository).updateStatus(44L, MediaJobStatus.FAILED, null, "VIDEO_COMPOSE_FAILED");
    verify(jobRepository).updateStatus(45L, MediaJobStatus.FAILED, null, "VIDEO_COMPOSE_FAILED");
    ArgumentCaptor<Object> errorPayload = ArgumentCaptor.forClass(Object.class);
    verify(streamRegistry, Mockito.times(2))
        .broadcast(Mockito.anyLong(), eq("error"), errorPayload.capture());
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=BusinessException"))
        .anySatisfy(message -> assertThat(message).contains("errorType=IllegalStateException"))
        .noneMatch(message -> message.contains(sentinel));
    assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    assertThat(errorPayload.getAllValues().toString()).contains("合成失败").doesNotContain(sentinel);
  }

  @Test
  void malformedProgressDoesNotLogStoredStreamContent() {
    String sentinel = "private-media-progress-log-sentinel";
    Instant now = Instant.now();
    when(jobRepository.findById(46L))
        .thenReturn(
            Optional.of(
                new MediaJob(
                    46L,
                    USER_ID,
                    "STORYBOARD_VIDEO",
                    MediaJobStatus.PENDING,
                    "fingerprint-46",
                    "{}",
                    sentinel,
                    null,
                    null,
                    now,
                    now)));
    Logger logger = (Logger) LoggerFactory.getLogger(MediaJobService.class);
    Level previousLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      service.stream(46L, USER_ID);
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType="))
        .noneMatch(message -> message.contains(sentinel));
    assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
  }

  @Test
  void getJobHidesOwnerlessAndForeignJobs() {
    Instant now = Instant.now();
    MediaJob ownerless =
        new MediaJob(45L, null, "STORYBOARD_VIDEO", MediaJobStatus.PENDING, "fp", "{}", "{}", null, null, now, now);
    MediaJob foreign =
        new MediaJob(46L, USER_ID + 1, "STORYBOARD_VIDEO", MediaJobStatus.PENDING, "fp", "{}", "{}", null, null, now, now);
    MediaJob owned =
        new MediaJob(48L, USER_ID, "STORYBOARD_VIDEO", MediaJobStatus.PENDING, "fp", "{}", "{}", null, null, now, now);
    MediaJobService controllerService = mock(MediaJobService.class);
    when(controllerService.getJob(45L)).thenReturn(Optional.of(ownerless));
    when(controllerService.getJob(46L)).thenReturn(Optional.of(foreign));
    when(controllerService.getJob(48L)).thenReturn(Optional.of(owned));
    MediaJobController controller =
        new MediaJobController(
            controllerService,
            objectMapper,
            new MediaAssetUrls(new JwtProperties("media-job-test-secret-at-least-32-chars", 60)));
    CurrentUserPrincipal caller = new CurrentUserPrincipal(USER_ID, "student", Role.STUDENT);

    assertThatThrownBy(() -> controller.getJob(caller, 45L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.code()).isEqualTo("MEDIA_JOB_NOT_FOUND");
            });
    assertThatThrownBy(() -> controller.getJob(caller, 46L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.code()).isEqualTo("MEDIA_JOB_NOT_FOUND");
            });
    assertThat(controller.getJob(caller, 48L)).isNotNull();
  }

  @Test
  void streamHidesOwnerlessJob() {
    Instant now = Instant.now();
    MediaJob ownerless =
        new MediaJob(47L, null, "STORYBOARD_VIDEO", MediaJobStatus.PENDING, "fp", "{}", "{}", null, null, now, now);
    when(jobRepository.findById(47L)).thenReturn(Optional.of(ownerless));

    assertThatThrownBy(() -> service.stream(47L, USER_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.code()).isEqualTo("MEDIA_JOB_NOT_FOUND");
            });

    MediaJob foreign =
        new MediaJob(49L, USER_ID + 1, "STORYBOARD_VIDEO", MediaJobStatus.PENDING, "fp", "{}", "{}", null, null, now, now);
    when(jobRepository.findById(49L)).thenReturn(Optional.of(foreign));
    assertThatThrownBy(() -> service.stream(49L, USER_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.code()).isEqualTo("MEDIA_JOB_NOT_FOUND");
            });
  }

  @Test
  void getJobHidesOwnerlessAndForeignJobs() {
    Instant now = Instant.now();
    MediaJob ownerless =
        new MediaJob(45L, null, "STORYBOARD_VIDEO", MediaJobStatus.PENDING, "fp", "{}", "{}", null, null, now, now);
    MediaJob foreign =
        new MediaJob(46L, USER_ID + 1, "STORYBOARD_VIDEO", MediaJobStatus.PENDING, "fp", "{}", "{}", null, null, now, now);
    MediaJob owned =
        new MediaJob(48L, USER_ID, "STORYBOARD_VIDEO", MediaJobStatus.PENDING, "fp", "{}", "{}", null, null, now, now);
    MediaJobService controllerService = mock(MediaJobService.class);
    when(controllerService.getJob(45L)).thenReturn(Optional.of(ownerless));
    when(controllerService.getJob(46L)).thenReturn(Optional.of(foreign));
    when(controllerService.getJob(48L)).thenReturn(Optional.of(owned));
    MediaJobController controller =
        new MediaJobController(
            controllerService,
            objectMapper,
            new MediaAssetUrls(new JwtProperties("media-job-test-secret-at-least-32-chars", 60)));
    CurrentUserPrincipal caller = new CurrentUserPrincipal(USER_ID, "student", Role.STUDENT);

    assertThatThrownBy(() -> controller.getJob(caller, 45L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.code()).isEqualTo("MEDIA_JOB_NOT_FOUND");
            });
    assertThatThrownBy(() -> controller.getJob(caller, 46L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.code()).isEqualTo("MEDIA_JOB_NOT_FOUND");
            });
    assertThat(controller.getJob(caller, 48L)).isNotNull();
  }

  @Test
  void streamHidesOwnerlessJob() {
    Instant now = Instant.now();
    MediaJob ownerless =
        new MediaJob(47L, null, "STORYBOARD_VIDEO", MediaJobStatus.PENDING, "fp", "{}", "{}", null, null, now, now);
    when(jobRepository.findById(47L)).thenReturn(Optional.of(ownerless));

    assertThatThrownBy(() -> service.stream(47L, USER_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.code()).isEqualTo("MEDIA_JOB_NOT_FOUND");
            });

    MediaJob foreign =
        new MediaJob(49L, USER_ID + 1, "STORYBOARD_VIDEO", MediaJobStatus.PENDING, "fp", "{}", "{}", null, null, now, now);
    when(jobRepository.findById(49L)).thenReturn(Optional.of(foreign));
    assertThatThrownBy(() -> service.stream(49L, USER_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.code()).isEqualTo("MEDIA_JOB_NOT_FOUND");
            });
  }
}
