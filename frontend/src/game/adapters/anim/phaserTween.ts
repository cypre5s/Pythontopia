// 画布内动效后端：闭环达成时在 Phaser 画布内（如玩家精灵）做一次缩放 pop 庆祝补间。
// 隔离原因：沿用 weather.ts / mood.ts 做法，对 Phaser 只做「结构化接口」依赖，不 import phaser，
// 业务层只通过 AnimAdapter / CanvasFxBackend 接口调用。真实 Phaser.Scene 在 Phase 2 接线时
// 由 WorldCanvas 以结构兼容方式传入。
//
// 设计取舍：
// - 无目标（画布未就绪）/ instant（reduce-motion 降级）→ fail-soft no-op，立即 resolve。
// - scene.tweens.add 抛错（场景重启 / shutdown 瞬态）→ fail-soft resolve，不外抛。
// - dispose 停止残留补间，幂等。
import type { BackendOptions, CanvasFxBackend } from './types';

const DEFAULT_CELEBRATE_MS = 600;
const CELEBRATE_POP = 1.18; // 庆祝放大倍数（yoyo 回弹）

/** 补间作用目标：Phaser GameObject 的最小结构（缩放 pop 用）。 */
export interface CanvasFxTarget {
  setScale?(scale: number): unknown;
  scaleX?: number;
  scaleY?: number;
}

/** Phaser tween 配置的最小结构（仅本后端用到的字段）。 */
export interface CanvasFxTweenConfig {
  targets: CanvasFxTarget;
  scaleX?: number;
  scaleY?: number;
  duration?: number;
  ease?: string;
  yoyo?: boolean;
  onComplete?: () => void;
}

/** Phaser tween 句柄的最小结构。 */
export interface CanvasFxTween {
  stop?(): void;
}

/** Phaser 场景的最小结构（仅 tweens.add）。 */
export interface CanvasFxScene {
  tweens: {
    add(config: CanvasFxTweenConfig): CanvasFxTween;
  };
}

export interface PhaserCanvasFxOptions {
  /** 返回庆祝补间作用目标（如玩家精灵 / confetti 容器）；返回 null 时安全 no-op。 */
  getTarget: () => CanvasFxTarget | null;
  /** 庆祝放大倍数（缺省 1.18）。 */
  pop?: number;
}

export function createPhaserCanvasFx(
  scene: CanvasFxScene,
  opts: PhaserCanvasFxOptions,
): CanvasFxBackend {
  const pop = opts.pop ?? CELEBRATE_POP;
  let activeTween: CanvasFxTween | null = null;

  return {
    celebrate(options?: BackendOptions): Promise<void> {
      const target = opts.getTarget();
      if (!target) {
        return Promise.resolve();
      }
      if (options?.instant) {
        try {
          target.setScale?.(1);
        } catch {
          // 目标已销毁，忽略。
        }
        return Promise.resolve();
      }
      return new Promise<void>((resolve) => {
        let settled = false;
        const done = (): void => {
          if (!settled) {
            settled = true;
            resolve();
          }
        };
        try {
          activeTween = scene.tweens.add({
            targets: target,
            scaleX: pop,
            scaleY: pop,
            duration: options?.durationMs ?? DEFAULT_CELEBRATE_MS,
            ease: 'Back.easeOut',
            yoyo: true,
            onComplete: done,
          });
        } catch {
          // 场景重启 / shutdown 瞬态：fail-soft resolve。
          done();
        }
      });
    },
    dispose(): void {
      if (!activeTween) {
        return;
      }
      try {
        activeTween.stop?.();
      } catch {
        // 补间已随场景销毁，忽略。
      }
      activeTween = null;
    },
  };
}
