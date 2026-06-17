// DOM 过渡后端：闭环步骤卡片的入场 / 收束动效，唯一允许 import `motion` 的隔离实现文件。
// 业务层（LoopProgress.vue 等）只通过 AnimAdapter / DomMotionBackend 接口调用，禁直接 import motion。
//
// 设计取舍：
// - instant=true（prefers-reduced-motion 降级）→ 不创建 motion 动画，直接落终值，零 rAF / 零补间。
// - motion 抛错 / 动画被取消 → fail-soft：落终值并 resolve，绝不把动画异常外抛打断闭环渲染。
// - signal 中断（路由切换 / 快速重渲染）→ controls.complete() 跳到终值。
import { animate, type AnimationOptions, type DOMKeyframesDefinition, type Easing } from 'motion';
import type { AnimStepOutcome, BackendOptions, DomMotionBackend } from './types';

const DEFAULT_STEP_IN_MS = 240;
const DEFAULT_STEP_DONE_MS = 220;
const DEFAULT_FAIL_MS = 360;

const STEP_IN_OFFSET_PX = 6; // 入场上移幅度
const DONE_POP = 1.06; // 成功收束的轻微放大回弹
const FAIL_SHAKE_PX = 4; // 失败抖动幅度

interface FinalStyles {
  opacity?: string;
  transform?: string;
}

function applyFinal(target: Element, styles: FinalStyles): void {
  const el = target as HTMLElement;
  if (!el || !el.style) {
    return;
  }
  if (styles.opacity !== undefined) {
    el.style.opacity = styles.opacity;
  }
  if (styles.transform !== undefined) {
    el.style.transform = styles.transform;
  }
}

async function run(
  target: Element,
  keyframes: DOMKeyframesDefinition,
  ease: Easing,
  finalStyles: FinalStyles,
  opts: BackendOptions,
  defaultMs: number,
): Promise<void> {
  if (opts.instant) {
    applyFinal(target, finalStyles);
    return;
  }
  const ms = opts.durationMs ?? defaultMs;
  try {
    const options: AnimationOptions = { duration: ms / 1000, ease };
    const controls = animate(target, keyframes, options);
    if (opts.signal) {
      if (opts.signal.aborted) {
        safeComplete(controls);
      } else {
        opts.signal.addEventListener('abort', () => safeComplete(controls), { once: true });
      }
    }
    await controls.finished;
  } catch {
    // 动画被取消 / motion 库异常：落终值，保证步骤卡片仍处于正确视觉终态。
    applyFinal(target, finalStyles);
  }
}

function safeComplete(controls: { complete: () => void }): void {
  try {
    controls.complete();
  } catch {
    // complete 在动画已结束时可能抛错，忽略。
  }
}

export function createDomMotionBackend(): DomMotionBackend {
  return {
    stepIn(target: Element, opts: BackendOptions): Promise<void> {
      return run(
        target,
        { opacity: [0, 1], y: [STEP_IN_OFFSET_PX, 0] },
        'easeOut',
        { opacity: '1', transform: 'none' },
        opts,
        DEFAULT_STEP_IN_MS,
      );
    },
    stepDone(target: Element, outcome: AnimStepOutcome, opts: BackendOptions): Promise<void> {
      if (outcome === 'failed') {
        return run(
          target,
          { x: [0, -FAIL_SHAKE_PX, FAIL_SHAKE_PX, -FAIL_SHAKE_PX * 0.6, FAIL_SHAKE_PX * 0.6, 0] },
          'easeInOut',
          { transform: 'none' },
          opts,
          DEFAULT_FAIL_MS,
        );
      }
      return run(
        target,
        { opacity: [1, 1], scale: [1, DONE_POP, 1] },
        'easeOut',
        { opacity: '1', transform: 'none' },
        opts,
        DEFAULT_STEP_DONE_MS,
      );
    },
  };
}
