/**
 * 调试剧场 · 回放端口（集成点抽象）。
 *
 * {@link CodeTheaterReplay.vue} 依赖的「舞台播放接口」。C-stage-render
 * （frontend/src/game/codeTheater/** + CodeTheaterScene.ts）将在 Phase 2 提供真实适配器实现本接口；
 * 在此之前组件以可选注入方式编程，缺省走纯 DOM 静态高亮回退。
 *
 * 设计：仅以 §5 契约类型（Trace / step / line）表达，不把任何 Phaser 对象泄漏进本逻辑层。
 */

import type { FocusedTraceSlice } from './diagnosticFocus';

/** 舞台回放端口：把「截到出错前后的轨迹切片」演给学生看。 */
export interface CodeTheaterReplayPort {
  /** 载入已截到「出错前后」的轨迹切片。 */
  load(slice: FocusedTraceSlice): void | Promise<void>;
  /** 从头 / 当前位置播放。 */
  play(): void;
  /** 暂停。 */
  pause(): void;
  /** 跳到指定 step（对齐 TraceEvent.step）。 */
  seekToStep(step: number): void;
  /** 高亮诊断焦点行（让出错行发光 / 傀儡在此绊倒）。 */
  highlightLine(line: number): void;
  /** 释放资源（可选）。 */
  dispose?(): void;
}

/** 无副作用占位端口：舞台未就绪时组件可安全调用，便于 Phase 2 前先跑通逻辑与测试。 */
export function createNoopReplayPort(): CodeTheaterReplayPort {
  return {
    load: () => undefined,
    play: () => undefined,
    pause: () => undefined,
    seekToStep: () => undefined,
    highlightLine: () => undefined,
    dispose: () => undefined,
  };
}
