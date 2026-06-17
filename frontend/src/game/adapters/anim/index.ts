// 闭环动效适配层公共出口（barrel）。F2 / Phase 2 接线统一从 `@game/adapters/anim` 引入：
//   import { createAnimAdapter, createRiveMascot, createPhaserCanvasFx } from '@game/adapters/anim'
// 业务层只依赖此处导出的「接口 + 工厂」，三方库（motion / @rive-app/canvas / phaser）全部隔离在内部实现文件。
export type {
  AnimAdapter,
  AnimAdapterDeps,
  AnimOptions,
  AnimStepOutcome,
  BackendOptions,
  CanvasFxBackend,
  CelebrateOptions,
  CelebrateScope,
  DomMotionBackend,
  MascotBackend,
  MascotExpression,
} from './types';
export { createAnimAdapter } from './AnimAdapter';
export { createDomMotionBackend } from './domMotion';
export { createRiveMascot, type RiveMascotOptions } from './riveMascot';
export {
  createPhaserCanvasFx,
  type CanvasFxScene,
  type CanvasFxTarget,
  type CanvasFxTween,
  type CanvasFxTweenConfig,
  type PhaserCanvasFxOptions,
} from './phaserTween';
