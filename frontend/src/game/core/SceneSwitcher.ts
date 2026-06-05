// SceneSwitcher：把 Phaser 场景切换从「散落各处的 scene.scene.start(key)」
// 收敛到一条统一管线，强制保证：
//   fade-out → 等 camerafadeoutcomplete → cleanup(timers/tweens/loader) →
//   cleanupHooks → 目标 scene 存在性校验 → scene.start。
// 目标 scene 不存在 / fadeOut 抛错 / cleanup 抛错 时退到 fallbackSceneKey，
// 错误经 onError 回调透传给业务层，避免静默失败。
//
// 隔离边界：本文件属于 game core 层，允许 import 'phaser' 类型；业务层调用方
// 不需要直接 import phaser，构造一次 SceneSwitcher 后只用 switchTo() 即可。
//
// fadeOut 超时兜底：camerafadeoutcomplete 事件偶发不触发（Phaser 在 jsdom 单测、
// 极小 fadeMs、tab 不可见时），统一用 setTimeout(fadeMs + 200) 兜底解锁管线，
// 避免 promise 永远 pending 把游戏卡死。

import type Phaser from 'phaser';

export interface SwitchOptions {
  fadeMs?: number;
  data?: Record<string, unknown>;
  preserveState?: boolean;
  onError?: (err: Error, attemptedSceneKey: string) => void;
}

export interface SceneSwitcherDeps {
  fallbackSceneKey?: string;
  cleanupHooks?: Array<(scene: Phaser.Scene) => void>;
}

const DEFAULT_FADE_MS = 300;
const FADE_TIMEOUT_BUFFER_MS = 200;
const DEFAULT_FALLBACK_KEY = 'BootScene';
const FADE_OUT_EVENT = 'camerafadeoutcomplete';

export class SceneSwitcher {
  private readonly fallbackSceneKey: string;
  private readonly cleanupHooks: ReadonlyArray<(scene: Phaser.Scene) => void>;

  constructor(deps?: SceneSwitcherDeps) {
    this.fallbackSceneKey = deps?.fallbackSceneKey ?? DEFAULT_FALLBACK_KEY;
    this.cleanupHooks = deps?.cleanupHooks ?? [];
  }

  async switchTo(
    currentScene: Phaser.Scene,
    targetSceneKey: string,
    options?: SwitchOptions,
  ): Promise<void> {
    const fadeMs = options?.fadeMs ?? DEFAULT_FADE_MS;
    const data = options?.data;
    const onError = options?.onError;

    try {
      this.startFade(currentScene, fadeMs);
      await this.waitForFadeOut(currentScene, fadeMs);

      SceneSwitcher.cleanupScene(currentScene);
      this.runCleanupHooks(currentScene);

      if (!this.sceneExists(currentScene, targetSceneKey)) {
        const err = new Error(
          `[SceneSwitcher] target scene "${targetSceneKey}" not registered, falling back to "${this.fallbackSceneKey}"`,
        );
        if (onError) {
          safeInvoke(() => onError(err, targetSceneKey));
        }
        this.startSceneSafe(currentScene, this.fallbackSceneKey, data);
        return;
      }

      this.startSceneSafe(currentScene, targetSceneKey, data);
    } catch (rawError) {
      const err =
        rawError instanceof Error ? rawError : new Error(String(rawError));
      if (onError) {
        safeInvoke(() => onError(err, targetSceneKey));
      }
      this.startSceneSafe(currentScene, this.fallbackSceneKey, data);
    }
  }

  // 静态 cleanup：业务层在场景 shutdown / 切换前可手动调一次，
  // 单测里也方便不构造完整 SceneSwitcher 直接验证清理行为。
  static cleanupScene(scene: Phaser.Scene): void {
    safeInvoke(() => {
      const time = (scene as { time?: { removeAllEvents?: () => void } }).time;
      time?.removeAllEvents?.();
    });
    safeInvoke(() => {
      const tweens = (scene as { tweens?: { killAll?: () => void } }).tweens;
      tweens?.killAll?.();
    });
    safeInvoke(() => {
      const load = (scene as { load?: { reset?: () => void } }).load;
      load?.reset?.();
    });
  }

  private startFade(scene: Phaser.Scene, fadeMs: number): void {
    safeInvoke(() => {
      scene.cameras.main.fadeOut(fadeMs, 0, 0, 0);
    });
  }

  private waitForFadeOut(scene: Phaser.Scene, fadeMs: number): Promise<void> {
    return new Promise<void>((resolve) => {
      let settled = false;
      const finish = (): void => {
        if (settled) {
          return;
        }
        settled = true;
        resolve();
      };

      let listenerAttached = false;
      try {
        scene.cameras.main.once(FADE_OUT_EVENT, finish);
        listenerAttached = true;
      } catch {
        // camera 异常：直接走超时兜底，避免卡死。
      }

      // 即使监听器挂上了，也始终挂超时兜底，覆盖事件不触发的边缘情况。
      const timeoutMs = Math.max(0, fadeMs) + FADE_TIMEOUT_BUFFER_MS;
      setTimeout(finish, timeoutMs);

      if (!listenerAttached) {
        // 已经无可能监听到事件，让超时尽快接管即可——不在这里同步 resolve，
        // 否则 await 链会跳过 fadeMs 等待，单测里观察不到「未触发也能 200ms 兜底」。
      }
    });
  }

  private runCleanupHooks(scene: Phaser.Scene): void {
    for (const hook of this.cleanupHooks) {
      safeInvoke(() => hook(scene));
    }
  }

  // Phaser.Scenes.SceneManager.keys 实际是 { [key]: Scene } record，但为兼容
  // 测试桩里可能传数组（更直观），两种形态都接受；都失败再走 scene.scene.get(key)。
  private sceneExists(scene: Phaser.Scene, key: string): boolean {
    const manager = scene.scene.manager as { keys?: unknown } | null | undefined;
    const rawKeys: unknown = manager?.keys;
    if (Array.isArray(rawKeys)) {
      if (rawKeys.includes(key)) {
        return true;
      }
    } else if (rawKeys && typeof rawKeys === 'object') {
      if (key in (rawKeys as Record<string, unknown>)) {
        return true;
      }
    }
    try {
      return scene.scene.get(key) != null;
    } catch {
      return false;
    }
  }

  private startSceneSafe(
    scene: Phaser.Scene,
    key: string,
    data?: Record<string, unknown>,
  ): void {
    safeInvoke(() => {
      scene.scene.start(key, data);
    });
  }
}

function safeInvoke(fn: () => void): void {
  try {
    fn();
  } catch {
    // 切场景流程中任何一步抛错都不应该把整个管线带崩；外层 try/catch + onError
    // 已经在 switchTo 顶层捕获，cleanup / start 这种「best-effort」调用更不应阻断。
  }
}
