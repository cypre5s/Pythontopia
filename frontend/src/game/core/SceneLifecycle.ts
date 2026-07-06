// Phaser scene 在 shutdown/destroy 时若不显式清理 tween / timer / input
// listener，切场景或 HMR 会留下"幽灵 tween 与 ticker"，长时间运行触发内存累积。
// SceneLifecycle 把清理逻辑收敛到一个 helper：在 scene.events 的 shutdown 与 destroy
// 上挂钩，shutdown 时统一调用 tweens.killAll / time.removeAllEvents /
// input.removeAllListeners；destroy 时再保险一次。
//
// 关于 textures：Phaser 的 TextureManager 是 Game 级别的全局缓存，BootScene 预加载的
// texture 必须保留供后续 scene 复用，所以本 helper 故意 **不** 删除任何 texture，只清
// scene 自己注册的运行时资源。
import type Phaser from 'phaser';

export interface SceneLifecycleOptions {
  onShutdown?: () => void;
  logger?: (msg: string) => void;
}

interface SceneLifecycleHost {
  events?: {
    on(event: string, fn: () => void, context?: unknown): unknown;
    off?(event: string, fn: () => void, context?: unknown, once?: boolean): unknown;
  };
  tweens?: { killAll(): unknown };
  time?: { removeAllEvents(): unknown };
  input?: { removeAllListeners(): unknown };
}

const SHUTDOWN_EVENT = 'shutdown';
const DESTROY_EVENT = 'destroy';

export function attachLifecycle(
  scene: Phaser.Scene,
  opts?: SceneLifecycleOptions,
): () => void {
  const host = scene as unknown as SceneLifecycleHost;
  const log = opts?.logger;

  const cleanupRuntime = (phase: string): void => {
    try {
      host.tweens?.killAll();
    } catch (err) {
      log?.(`[SceneLifecycle] ${phase}: tweens.killAll failed: ${describeError(err)}`);
    }
    try {
      host.time?.removeAllEvents();
    } catch (err) {
      log?.(`[SceneLifecycle] ${phase}: time.removeAllEvents failed: ${describeError(err)}`);
    }
    try {
      host.input?.removeAllListeners();
    } catch (err) {
      log?.(`[SceneLifecycle] ${phase}: input.removeAllListeners failed: ${describeError(err)}`);
    }
  };

  const shutdownHandler = (): void => {
    cleanupRuntime(SHUTDOWN_EVENT);
    if (opts?.onShutdown) {
      try {
        opts.onShutdown();
      } catch (err) {
        log?.(`[SceneLifecycle] onShutdown threw: ${describeError(err)}`);
      }
    }
    log?.('[SceneLifecycle] shutdown cleanup done');
  };

  const destroyHandler = (): void => {
    cleanupRuntime(DESTROY_EVENT);
    log?.('[SceneLifecycle] destroy cleanup done');
  };

  host.events?.on(SHUTDOWN_EVENT, shutdownHandler);
  host.events?.on(DESTROY_EVENT, destroyHandler);

  let detached = false;
  return (): void => {
    if (detached) {
      return;
    }
    detached = true;
    host.events?.off?.(SHUTDOWN_EVENT, shutdownHandler);
    host.events?.off?.(DESTROY_EVENT, destroyHandler);
  };
}

function describeError(err: unknown): string {
  if (err instanceof Error) {
    return err.message;
  }
  return String(err);
}
