/**
 * 调试剧场 · debug 纯逻辑层公共出口。
 * C-debug-theater 归属域（见 docs/plans/2026-06-13-parallel-build-contracts.md §2 / §5）。
 */

export {
  locateDiagnosticFocus,
  sliceTraceAroundFocus,
} from './diagnosticFocus';
export type {
  DiagnosticFocus,
  DiagnosticFocusKind,
  LocateFocusOptions,
  FocusedTraceSlice,
  SliceOptions,
} from './diagnosticFocus';

export {
  buildGuideLadder,
  redactSolutionLeak,
} from './guideLadder';
export type {
  DebugLadderStage,
  DebugHintLayerInput,
  DebugGuideStep,
  BuildGuideLadderInput,
} from './guideLadder';

export { createNoopReplayPort } from './replayPort';
export type { CodeTheaterReplayPort } from './replayPort';
