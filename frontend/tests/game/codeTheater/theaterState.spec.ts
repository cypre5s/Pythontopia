import { describe, expect, it } from 'vitest';
import {
  applyCommand,
  computeStateAt,
  initialTheaterState,
} from '@/game/codeTheater/theaterState';
import { cmd, sumLoopCommands } from './_fixtures';

describe('theaterState · initial', () => {
  it('starts on no line with empty stage', () => {
    const s = initialTheaterState();
    expect(s.currentLine).toBe(-1);
    expect(s.variables).toEqual({});
    expect(s.variableOrder).toEqual([]);
    expect(s.loops).toEqual({});
    expect(s.callDepth).toBe(0);
    expect(s.noticeLines).toEqual([]);
    expect(s.stumbled).toBe(false);
    expect(s.lastStep).toBe(-1);
  });
});

describe('theaterState · applyCommand purity', () => {
  it('does not mutate the previous state', () => {
    const prev = initialTheaterState();
    const snapshot = JSON.parse(JSON.stringify(prev));
    const next = applyCommand(prev, cmd(0, 'set_var', { name: 'x', value: '1', line: 1 }));
    expect(prev).toEqual(snapshot); // 入参未变
    expect(next).not.toBe(prev); // 返回新对象
    expect(next.variables).not.toBe(prev.variables);
  });
});

describe('theaterState · per-action', () => {
  it('move_to_line + generic line advance the glowing line', () => {
    let s = applyCommand(initialTheaterState(), cmd(0, 'move_to_line', { line: 5 }));
    expect(s.currentLine).toBe(5);
    expect(s.lastAction).toBe('move_to_line');
    expect(s.lastStep).toBe(0);
    // 任意带 line 的 payload 也推进发光行。
    s = applyCommand(s, cmd(1, 'speak', { text: 'hi', line: 9 }));
    expect(s.currentLine).toBe(9);
  });

  it('set_var creates a flippable board cell and records order (varName, the C-mappers key)', () => {
    let s = applyCommand(initialTheaterState(), cmd(0, 'set_var', { varName: 'x', value: '1', scope: 'global', line: 1 }));
    expect(s.variables.x).toMatchObject({ name: 'x', value: '1', scope: 'global', updatedAtStep: 0 });
    expect(s.variableOrder).toEqual(['x']);
    expect(s.changedVar).toBe('x');

    s = applyCommand(s, cmd(1, 'set_var', { varName: 'y', value: 'true', scope: 'local', line: 2 }));
    expect(s.variableOrder).toEqual(['x', 'y']);
    expect(s.variables.y?.scope).toBe('local');

    // 重绑定不重复入序，但更新值与 step。
    s = applyCommand(s, cmd(2, 'set_var', { varName: 'x', value: '42', line: 1 }));
    expect(s.variableOrder).toEqual(['x', 'y']);
    expect(s.variables.x).toMatchObject({ value: '42', updatedAtStep: 2 });
    expect(s.changedVar).toBe('x');
  });

  it('set_var also accepts the `name` alias (self-made fixtures / other producers)', () => {
    const s = applyCommand(initialTheaterState(), cmd(0, 'set_var', { name: 'z', value: '7', line: 1 }));
    expect(s.variables.z).toMatchObject({ name: 'z', value: '7' });
  });

  it('changedVar resets each step (only the just-set var flips)', () => {
    let s = applyCommand(initialTheaterState(), cmd(0, 'set_var', { varName: 'x', value: '1' }));
    expect(s.changedVar).toBe('x');
    s = applyCommand(s, cmd(1, 'move_to_line', { line: 2 }));
    expect(s.changedVar).toBeNull();
  });

  it('walk_loop counts laps and honors authoritative iter; nested loops are independent', () => {
    let s = initialTheaterState();
    s = applyCommand(s, cmd(0, 'walk_loop', { loopId: 'for@1', loopType: 'for', varName: 'i', value: '0' }));
    s = applyCommand(s, cmd(1, 'walk_loop', { loopId: 'for@1', varName: 'i', value: '1' }));
    expect(s.loops['for@1']).toMatchObject({ laps: 2, loopType: 'for', varName: 'i', value: '1' });

    // iter 优先：直接定位到第 (iter+1) 圈。
    s = applyCommand(s, cmd(2, 'walk_loop', { loopId: 'for@1', iter: 9 }));
    expect(s.loops['for@1']?.laps).toBe(10);

    // 嵌套 while 互不干扰。
    s = applyCommand(s, cmd(3, 'walk_loop', { loopId: 'while@3', loopType: 'while' }));
    expect(s.loops['while@3']).toMatchObject({ laps: 1, loopType: 'while' });
    expect(s.loops['for@1']?.laps).toBe(10);
  });

  it('take_branch records the choice and moves the puppet to the taken line', () => {
    const s = applyCommand(
      initialTheaterState(),
      cmd(0, 'take_branch', { cond: 'x > 0', result: true, lineTaken: 7 }),
    );
    expect(s.lastBranch).toMatchObject({ cond: 'x > 0', result: true, lineTaken: 7, atStep: 0 });
    expect(s.currentLine).toBe(7);
  });

  it('enter_call / exit_call track stack depth and frames', () => {
    let s = applyCommand(initialTheaterState(), cmd(0, 'enter_call', { funcName: 'foo' }));
    expect(s.callDepth).toBe(1);
    expect(s.callStack).toEqual(['foo']);
    s = applyCommand(s, cmd(1, 'enter_call', { funcName: 'bar', depth: 2 }));
    expect(s.callDepth).toBe(2);
    expect(s.callStack).toEqual(['foo', 'bar']);
    s = applyCommand(s, cmd(2, 'exit_call', { funcName: 'bar', value: '5' }));
    expect(s.callDepth).toBe(1);
    expect(s.callStack).toEqual(['foo']);
  });

  it('list_push / list_pop / list_index manage boxes', () => {
    let s = initialTheaterState();
    s = applyCommand(s, cmd(0, 'list_push', { target: 'xs', value: 'a' }));
    s = applyCommand(s, cmd(1, 'list_push', { target: 'xs', value: 'b' }));
    s = applyCommand(s, cmd(2, 'list_push', { target: 'xs', value: 'c' }));
    expect(s.lists.xs?.map((b) => b.value)).toEqual(['a', 'b', 'c']);

    s = applyCommand(s, cmd(3, 'list_index', { target: 'xs', position: 1 }));
    expect(s.listFocus).toEqual({ target: 'xs', position: 1 });

    s = applyCommand(s, cmd(4, 'list_pop', { target: 'xs', position: 0 }));
    expect(s.lists.xs?.map((b) => b.value)).toEqual(['b', 'c']);
    expect(s.listFocus).toBeNull(); // 每步重置

    s = applyCommand(s, cmd(5, 'list_pop', { target: 'xs' }));
    expect(s.lists.xs?.map((b) => b.value)).toEqual(['b']);
  });

  it('dict_set writes key→value cells', () => {
    let s = applyCommand(initialTheaterState(), cmd(0, 'dict_set', { target: 'm', key: 'a', value: '1' }));
    s = applyCommand(s, cmd(1, 'dict_set', { target: 'm', key: 'b', value: '2' }));
    s = applyCommand(s, cmd(2, 'dict_set', { target: 'm', key: 'a', value: '9' }));
    expect(s.dicts.m).toEqual({ a: '9', b: '2' });
  });

  it('speak appends to the notice board', () => {
    let s = applyCommand(initialTheaterState(), cmd(0, 'speak', { text: 'hello' }));
    s = applyCommand(s, cmd(1, 'speak', { text: 'world' }));
    expect(s.noticeLines).toEqual(['hello', 'world']);
  });

  it('stumble trips the puppet on its line', () => {
    const s = applyCommand(
      initialTheaterState(),
      cmd(0, 'stumble', { excType: 'ZeroDivisionError', message: 'division by zero', line: 4 }),
    );
    expect(s.stumbled).toBe(true);
    expect(s.stumble).toEqual({ excType: 'ZeroDivisionError', message: 'division by zero', line: 4 });
    expect(s.currentLine).toBe(4);
  });

  it('world_effect (stub) records a label without throwing', () => {
    const s = applyCommand(initialTheaterState(), cmd(0, 'world_effect', { effect: 'gate_open' }));
    expect(s.lastWorldEffect).toBe('gate_open');
  });

  it('unknown action is a safe no-op beyond generic line/step', () => {
    const weird = { step: 0, action: 'totally_new_action', payload: { line: 3 }, durationMs: 10 } as never;
    const s = applyCommand(initialTheaterState(), weird);
    expect(s.currentLine).toBe(3);
    expect(s.lastStep).toBe(0);
  });
});

describe('theaterState · computeStateAt fold', () => {
  it('folds the whole sum-loop program to the final stage', () => {
    const commands = sumLoopCommands();
    const s = computeStateAt(commands, commands.length);
    expect(s.variables.total?.value).toBe('3');
    expect(s.loops['for@2']?.laps).toBe(3);
    expect(s.noticeLines).toEqual(['3']);
    expect(s.currentLine).toBe(4);
    expect(s.lastStep).toBe(8);
  });

  it('supports partial replay and clamps out-of-range counts', () => {
    const commands = sumLoopCommands();
    const mid = computeStateAt(commands, 3); // 应用 step 0..2
    expect(mid.variables.total?.value).toBe('0');
    expect(mid.loops['for@2']?.laps).toBe(1);
    expect(mid.lastStep).toBe(2);

    expect(computeStateAt(commands, 0)).toEqual(initialTheaterState());
    expect(computeStateAt(commands, 999).lastStep).toBe(8);
    expect(computeStateAt(commands, -5)).toEqual(initialTheaterState());
  });
});
