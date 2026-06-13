/**
 * 集成对齐测试：把 **现网 C-mappers**（services/codeTheater/mappers）真实产出的
 * RenderCommand[] 喂进本 agent 的纯播放器/状态机，验证「事件→指令→舞台状态」整条缝
 * 此刻就能跑通（payload 主键已对齐：set_var→varName、speak→text/通道）。
 *
 * 仅 import types + mapEvent（只读复用，不修改 mappers）。Phase 2 编排者据此接线即可。
 */
import { describe, expect, it } from 'vitest';
import { mapEvent } from '@/services/codeTheater/mappers';
import type { MapContext, RenderCommand, TraceEvent } from '@/services/codeTheater/types';
import { computeStateAt } from '@/game/codeTheater/theaterState';
import { TimelinePlayer } from '@/game/codeTheater/timelinePlayer';

// total=0; for i in range(3): total=total+i; print(total)
const EVENTS: TraceEvent[] = [
  { step: 0, line: 1, kind: 'assign', varName: 'total', value: '0', scope: 'global' },
  { step: 1, line: 2, kind: 'loop_iter', loopId: 'for@2', loopType: 'for', iter: 0, varName: 'i', value: '0' },
  { step: 2, line: 3, kind: 'assign', varName: 'total', value: '0', scope: 'global' },
  { step: 3, line: 2, kind: 'loop_iter', loopId: 'for@2', loopType: 'for', iter: 1, varName: 'i', value: '1' },
  { step: 4, line: 3, kind: 'assign', varName: 'total', value: '1', scope: 'global' },
  { step: 5, line: 2, kind: 'loop_iter', loopId: 'for@2', loopType: 'for', iter: 2, varName: 'i', value: '2' },
  { step: 6, line: 3, kind: 'assign', varName: 'total', value: '3', scope: 'global' },
  { step: 7, line: 4, kind: 'output', text: '3' },
];

/** 模拟引擎：逐事件映射，并把 assign/loop 变量累积进只读 ctx（mapper 用于「旧→新」展示）。 */
function mapAll(events: readonly TraceEvent[]): RenderCommand[] {
  const variables: Record<string, string> = {};
  const out: RenderCommand[] = [];
  for (const event of events) {
    const ctx: MapContext = { variables: { ...variables }, callDepth: 0, defaultDurationMs: 400 };
    out.push(...mapEvent(event, ctx));
    if (event.kind === 'assign') variables[event.varName] = event.value;
    if (event.kind === 'loop_iter' && event.varName !== undefined && event.value !== undefined) {
      variables[event.varName] = event.value;
    }
  }
  return out;
}

describe('C-mappers → stage state (integration)', () => {
  it('folds real mapper output into the expected final stage', () => {
    const commands = mapAll(EVENTS);
    expect(commands.length).toBeGreaterThan(EVENTS.length); // loop_iter 产出 walk_loop + set_var

    const state = computeStateAt(commands, commands.length);
    expect(state.variables.total?.value).toBe('3');
    expect(state.variables.i?.value).toBe('2');
    expect(state.variables.i?.scope).toBe('local');
    expect(state.loops['for@2']?.laps).toBe(3);
    expect(state.noticeLines).toEqual(['3']);
    expect(state.currentLine).toBe(4);
  });

  it('plays real mapper output through the TimelinePlayer to the same end state', () => {
    const commands = mapAll(EVENTS);
    const player = new TimelinePlayer();
    player.load(commands, { autoplay: true });
    player.tick(1_000_000);
    expect(player.getStatus()).toBe('ended');
    expect(player.getAppliedCount()).toBe(commands.length);
    expect(player.getState().variables.total?.value).toBe('3');
    expect(player.getState().loops['for@2']?.laps).toBe(3);
  });

  it('renders input events (prompt/value) on the notice board via speak fallback', () => {
    const inputEvent: TraceEvent = {
      step: 0,
      line: 1,
      kind: 'input',
      prompt: '名字?',
      value: 'Ada',
    };
    const ctx: MapContext = { variables: {}, callDepth: 0, defaultDurationMs: 300 };
    const commands = mapEvent(inputEvent, ctx);
    const state = computeStateAt(commands, commands.length);
    expect(state.noticeLines[0]).toContain('名字?');
    expect(state.noticeLines[0]).toContain('Ada');
  });
});
