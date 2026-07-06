// 闭环动效适配器聚合实现（§3.7 + §4 F1）：把三个隔离后端（Motion DOM / Rive 吉祥物 /
// Phaser 画布内 tween）装配成业务层唯一依赖的 AnimAdapter。F2 的 LoopProgress 通过本工厂
// 注入；测试可注入假后端 + 假 reduce-motion 读取函数解耦。
//
// reduce-motion：每次调用实时读取（支持系统运行时切换 / WCAG 2.2 AA），为真时统一以
// instant:true 透传后端，使其退化为「瞬时落终值」。
import { createDomMotionBackend } from './domMotion';
import type {
  AnimAdapter,
  AnimAdapterDeps,
  AnimOptions,
  AnimStepOutcome,
  BackendOptions,
  CelebrateOptions,
} from './types';

const REDUCE_MOTION_QUERY = '(prefers-reduced-motion: reduce)';

function defaultPrefersReducedMotion(): boolean {
  try {
    return (
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia(REDUCE_MOTION_QUERY).matches
    );
  } catch {
    return false;
  }
}

export function createAnimAdapter(deps: AnimAdapterDeps = {}): AnimAdapter {
  const dom = deps.dom ?? createDomMotionBackend();
  const mascot = deps.mascot ?? null;
  const canvasFx = deps.canvasFx ?? null;
  const readReduceMotion = deps.prefersReducedMotion ?? defaultPrefersReducedMotion;

  return {
    get reduceMotion(): boolean {
      return readReduceMotion();
    },

    playStepIn(target: Element, opts?: AnimOptions): Promise<void> {
      return dom.stepIn(target, { ...opts, instant: readReduceMotion() });
    },

    playStepDone(target: Element, outcome: AnimStepOutcome, opts?: AnimOptions): Promise<void> {
      return dom.stepDone(target, outcome, { ...opts, instant: readReduceMotion() });
    },

    async celebrate(opts?: CelebrateOptions): Promise<void> {
      const instant = readReduceMotion();
      const scope = opts?.scope ?? 'both';
      const expression = opts?.expression ?? 'celebrate';
      const backendOpts: BackendOptions = { instant };
      if (opts?.durationMs !== undefined) {
        backendOpts.durationMs = opts.durationMs;
      }
      if (opts?.signal !== undefined) {
        backendOpts.signal = opts.signal;
      }

      const tasks: Array<Promise<void>> = [];

      if (mascot && (scope === 'both' || scope === 'mascot')) {
        if (expression === 'celebrate') {
          tasks.push(mascot.celebrate(backendOpts));
        } else {
          // critic 结果（approve/reject/thinking/idle）映射为表情切换，不放 confetti。
          mascot.setExpression(expression, backendOpts);
        }
      }

      if (canvasFx && (scope === 'both' || scope === 'canvas') && expression === 'celebrate') {
        tasks.push(canvasFx.celebrate(backendOpts));
      }

      await Promise.all(tasks);
    },

    dispose(): void {
      mascot?.dispose();
      canvasFx?.dispose();
    },
  };
}
