// 吉祥物动效后端：唯一允许 import `@rive-app/canvas`（Rive WASM 运行时）的隔离实现文件。
// 业务层只通过 AnimAdapter / MascotBackend 接口驱动表情，禁直接 import rive。
//
// 设计取舍：
// - 表情通过 Rive 状态机输入驱动：trigger 型用 fire()，boolean 型置 value=true（兼容两种工程做法）。
// - 资源未就绪 / 无对应输入 / stateMachineInputs 抛错 → 一律 fail-soft no-op，绝不打断闭环。
// - instant=true（prefers-reduced-motion 降级）→ 设状态后 pause()，不持续循环播放。
// - dispose 调 rive.cleanup() 释放 WASM 实例，幂等。
import { Rive, type RiveParameters, type StateMachineInput } from '@rive-app/canvas';
import type { BackendOptions, MascotBackend, MascotExpression } from './types';

const DEFAULT_STATE_MACHINE = 'State Machine 1';
const DEFAULT_CELEBRATE_MS = 900;

export interface RiveMascotOptions {
  /** .riv 资源路径（资产由 Docs stream 登记后注入）。 */
  src: string;
  /** 画板名（缺省用文件默认画板）。 */
  artboard?: string;
  /** 状态机名（缺省 'State Machine 1'）。 */
  stateMachine?: string;
  /** 表情 → 状态机输入名映射；缺省用表情同名（'idle'/'thinking'/'approve'/'reject'/'celebrate'）。 */
  triggers?: Partial<Record<MascotExpression, string>>;
}

function wait(ms: number, signal?: AbortSignal): Promise<void> {
  if (ms <= 0) {
    return Promise.resolve();
  }
  return new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener(
      'abort',
      () => {
        clearTimeout(timer);
        resolve();
      },
      { once: true },
    );
  });
}

export function createRiveMascot(canvas: HTMLCanvasElement, opts: RiveMascotOptions): MascotBackend {
  const stateMachine = opts.stateMachine ?? DEFAULT_STATE_MACHINE;
  const triggers = opts.triggers ?? {};

  let rive: Rive | null = null;
  let inputs: StateMachineInput[] = [];
  let inputsResolved = false;
  let disposed = false;

  function resolveInputs(): void {
    if (inputsResolved || !rive) {
      return;
    }
    try {
      inputs = rive.stateMachineInputs(stateMachine) ?? [];
      inputsResolved = true;
    } catch {
      // 状态机 / 文件尚未就绪：保持未解析，后续调用会重试。
    }
  }

  const params: RiveParameters = {
    canvas,
    src: opts.src,
    stateMachines: stateMachine,
    autoplay: true,
    onLoad: () => resolveInputs(),
  };
  if (opts.artboard !== undefined) {
    params.artboard = opts.artboard;
  }

  rive = new Rive(params);
  // mock / 已加载场景下 onLoad 可能早于 rive 赋值触发，这里补一次解析。
  resolveInputs();

  function findInput(name: string): StateMachineInput | null {
    resolveInputs();
    for (const input of inputs) {
      if (input.name === name) {
        return input;
      }
    }
    return null;
  }

  function setExpression(expression: MascotExpression, options?: BackendOptions): void {
    if (disposed || !rive) {
      return;
    }
    const inputName = triggers[expression] ?? expression;
    const input = findInput(inputName);
    if (!input) {
      // fail-soft：资源未就绪 / 该表情无对应输入，安静跳过。
      return;
    }
    try {
      input.fire(); // trigger 型生效；其它型 no-op
      if (typeof input.value === 'boolean') {
        input.value = true; // boolean 型生效
      }
      if (options?.instant) {
        rive.pause();
      } else {
        rive.play();
      }
    } catch {
      // 输入写入 / 播放控制偶发异常：fail-soft 跳过。
    }
  }

  return {
    setExpression,
    async celebrate(options?: BackendOptions): Promise<void> {
      setExpression('celebrate', options);
      if (options?.instant) {
        return;
      }
      await wait(options?.durationMs ?? DEFAULT_CELEBRATE_MS, options?.signal);
    },
    dispose(): void {
      if (disposed) {
        return;
      }
      disposed = true;
      try {
        rive?.cleanup();
      } catch {
        // 实例已被回收时再次 cleanup 会抛错，忽略。
      }
      rive = null;
    },
  };
}
