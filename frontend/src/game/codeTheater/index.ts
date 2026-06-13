/**
 * 代码剧场（机关广场）渲染层公共出口。
 *
 * - 纯逻辑（可无头复用，无 Phaser）：TimelinePlayer（时间轴/播放控制）、theaterState（状态机）、
 *   stageLayout（几何）。Vue 组件 / debug-theater 可直接 `new TimelinePlayer()` 做逻辑回放。
 * - 渲染原语（Phaser 隔离）：stagePrimitives，仅 CodeTheaterScene 使用。
 * - 场景挂载/控制：见 game/scenes/CodeTheaterScene.ts 的 getCodeTheaterPlayer / loadCodeTheater /
 *   ensureCodeTheaterSceneStarted（经 phaserGame.ts 单例挂载，不在此重复导出以保持单一职责）。
 */
export {
  TimelinePlayer,
  MIN_RATE,
  MAX_RATE,
  DEFAULT_DURATION_MS,
  FAST_FORWARD_RATE,
} from './timelinePlayer';
export type {
  PlaybackStatus,
  TimelineFrame,
  TimelineListener,
  LoadOptions,
} from './timelinePlayer';

export {
  initialTheaterState,
  applyCommand,
  computeStateAt,
} from './theaterState';
export type {
  TheaterState,
  VariableCell,
  LoopRuntimeState,
  BranchRuntimeState,
  ListBox,
  StumbleState,
} from './theaterState';

export {
  computeLadderLayout,
  ladderLineY,
  puppetPositionForLine,
  computeLoopTrackLayout,
  loopRunnerPosition,
  computeForkLayout,
  forkBranchPosition,
  variableBoardSlot,
  computeNoticeBoardLayout,
} from './stageLayout';
export type {
  StageDimensions,
  LadderLayout,
  LadderRung,
  LoopTrackLayout,
  ForkLayout,
  NoticeBoardLayout,
} from './stageLayout';

export {
  createScriptLadder,
  createPuppet,
  createVariableBoards,
  createLoopTrack,
  createBranchFork,
  createNoticeBoard,
  formatStumble,
} from './stagePrimitives';
export type {
  StageContext,
  StageAddLike,
  StageTweensLike,
  GraphicsLike,
  TextLike,
  ScriptLadderHandle,
  PuppetHandle,
  VariableBoardsHandle,
  LoopTrackHandle,
  BranchForkHandle,
  NoticeBoardHandle,
} from './stagePrimitives';
