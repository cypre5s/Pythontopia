// 与后端 `PlayerStatePatch.direction` 的 `@Pattern` 约束对齐。前端 8 方向输入经
// `frontend/src/game/adapters/input.ts:toServerDirection` 在写后端前降级为 4 向，
// 但此 type 仍保留 8 方向以兼容接收 / 测试 / 未来直传 8 向场景。
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
