package com.pythontopia.submission.judge;

import com.pythontopia.common.error.BusinessException;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 判题任务有界线程池：限制并发 Python 子进程数（默认 core=2 / max=4），队列有界（缺省 100），
 * 满载时拒绝新任务并返回 503，避免请求线程绕过并发上限自行 fork Python 子进程。
 *
 * <p>每次判题提交一行代码，最多跑 {@link JudgeService#MAX_CASES} 个测试用例（串行），
 * 因此 pool size 不需很大；瓶颈在 CPU / 内存而非 IO。
 */
@Component
public class JudgeWorkerPool {

  private static final Logger log = LoggerFactory.getLogger(JudgeWorkerPool.class);

  static final int DEFAULT_CORE_SIZE = 2;
  static final int DEFAULT_MAX_SIZE = 4;
  static final int DEFAULT_QUEUE_CAPACITY = 100;
  private static final long KEEP_ALIVE_SECONDS = 60L;

  private final ThreadPoolExecutor executor;

  /** 默认配置（适合单机演示/生产）。 */
  public JudgeWorkerPool() {
    this(DEFAULT_CORE_SIZE, DEFAULT_MAX_SIZE, DEFAULT_QUEUE_CAPACITY);
  }

  JudgeWorkerPool(int core, int max, int queueCapacity) {
    this.executor =
        new ThreadPoolExecutor(
            core,
            max,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            namedDaemonThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());
    log.info(
        "JudgeWorkerPool started core={} max={} queue={}",
        core,
        max,
        queueCapacity);
  }

  /**
   * 在判题工作线程上执行任务（阻塞直到完成或异常）。
   *
   * @param task 判题任务
   * @param <T>  返回值类型
   * @return task 的返回值
   * @throws RuntimeException 如果任务执行失败
   */
  public <T> T submitAndWait(Callable<T> task) {
    Future<T> future = null;
    try {
      future = executor.submit(task);
      return future.get();
    } catch (RejectedExecutionException e) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "JUDGE_CAPACITY",
          "判题服务繁忙，请稍后重试");
    } catch (InterruptedException e) {
      if (future != null) {
        future.cancel(true);
      }
      Thread.currentThread().interrupt();
      throw new JudgeRejectedException("Judge interrupted", e);
    } catch (java.util.concurrent.ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      throw new JudgeRejectedException("Judge failed: " + cause.getMessage(), cause);
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private static ThreadFactory namedDaemonThreadFactory() {
    AtomicInteger counter = new AtomicInteger(1);
    return runnable -> {
      Thread thread = new Thread(runnable, "judge-worker-" + counter.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    };
  }

  /** 判题被拒绝/中断时的运行时异常。 */
  static final class JudgeRejectedException extends RuntimeException {
    JudgeRejectedException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
