// R-13：与后端 `PlayerStatePatch.direction` 的 `@Pattern` 约束严格对齐。
// V113 已经把 DB 列扩到 VARCHAR(16) 以支持 8 方向；前端 8 方向输入由
// `frontend/src/game/adapters/input.ts:toServerDirection` 在写后端前做 8→4 降级，
// 但 type 这里仍允许 8 方向以兼容上层接收 / 测试 / 未来直传 8 向场景。
export type Direction =
  | 'UP'
  | 'DOWN'
  | 'LEFT'
  | 'RIGHT'
  | 'UP_LEFT'
  | 'UP_RIGHT'
  | 'DOWN_LEFT'
  | 'DOWN_RIGHT';

/** 4 方向子集，给只接受 cardinal 的旧 API 使用 */
export type CardinalDirection = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT';
