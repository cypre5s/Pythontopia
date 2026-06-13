import { describe, expect, it } from 'vitest';

import {
  DURATION_MULTIPLIERS,
  assignMapper,
  branchMapper,
  callMapper,
  command,
  dictOpMapper,
  durationFor,
  exceptionMapper,
  fallbackMapper,
  inputMapper,
  lineMapper,
  listOpMapper,
  loopIterMapper,
  mapEvent,
  mapperRegistry,
  outputMapper,
  returnMapper,
} from '@/services/codeTheater/mappers';
import type {
  AssignEvent,
  BranchEvent,
  CallEvent,
  DictOpEvent,
  ExceptionEvent,
  InputEvent,
  LineEvent,
  ListOpEvent,
  LoopIterEvent,
  MapContext,
  OutputEvent,
  ReturnEvent,
  TraceEvent,
  TraceEventKind,
  TraceMapper,
} from '@/services/codeTheater/types';

/** 默认基准时长选 600：所有动作系数乘出来都是整数，断言无浮点噪声。 */
const BASE = 600;

function makeCtx(overrides: Partial<MapContext> = {}): MapContext {
  return {
    variables: {},
    callDepth: 0,
    defaultDurationMs: BASE,
    ...overrides,
  };
}

/** 取唯一一条命令并断言长度为 1，避免 noUncheckedIndexedAccess 的 undefined。 */
function only(commands: ReturnType<typeof mapEvent>) {
  expect(commands).toHaveLength(1);
  const [cmd] = commands;
  if (!cmd) throw new Error('expected exactly one command');
  return cmd;
}

describe('fallbackMapper / lineMapper → move_to_line', () => {
  const lineEvent: LineEvent = { step: 0, line: 7, kind: 'line' };

  it('line 事件回退为单条 move_to_line，payload 带 line+kind', () => {
    const cmd = only(fallbackMapper(lineEvent, makeCtx()));
    expect(cmd).toEqual({
      step: 0,
      action: 'move_to_line',
      payload: { line: 7, kind: 'line' },
      durationMs: BASE,
    });
  });

  it('lineMapper 与 fallbackMapper 行为一致', () => {
    expect(lineMapper(lineEvent, makeCtx())).toEqual(fallbackMapper(lineEvent, makeCtx()));
  });

  it('对任意 kind 都能兜底（payload.kind 透传原始 kind）', () => {
    const assignEvent: AssignEvent = {
      step: 4,
      line: 9,
      kind: 'assign',
      varName: 'x',
      value: '1',
      scope: 'global',
    };
    const cmd = only(fallbackMapper(assignEvent, makeCtx()));
    expect(cmd.action).toBe('move_to_line');
    expect(cmd.payload).toEqual({ line: 9, kind: 'assign' });
  });
});

describe('assignMapper → set_var（牌面翻新）', () => {
  const event: AssignEvent = {
    step: 3,
    line: 5,
    kind: 'assign',
    varName: 'total',
    value: '42',
    scope: 'global',
  };

  it('无历史值时只翻出新值', () => {
    const cmd = only(assignMapper(event, makeCtx()));
    expect(cmd).toEqual({
      step: 3,
      action: 'set_var',
      payload: { line: 5, varName: 'total', value: '42', scope: 'global' },
      durationMs: BASE,
    });
  });

  it('上下文已有该变量时，payload 带 previousValue（旧→新）', () => {
    const cmd = only(assignMapper(event, makeCtx({ variables: { total: '7' } })));
    expect(cmd.payload).toEqual({
      line: 5,
      varName: 'total',
      value: '42',
      scope: 'global',
      previousValue: '7',
    });
  });

  it('错误 kind 进入时安全回退 move_to_line', () => {
    const line: LineEvent = { step: 1, line: 2, kind: 'line' };
    expect(only(assignMapper(line, makeCtx())).action).toBe('move_to_line');
  });
});

describe('loopIterMapper → walk_loop（绕跑道一圈、计数 +1、循环变量值）', () => {
  it('for 循环：walk_loop（lap=iter+1）+ 循环变量 set_var', () => {
    const event: LoopIterEvent = {
      step: 10,
      line: 2,
      kind: 'loop_iter',
      loopId: 'for@2',
      loopType: 'for',
      iter: 2,
      varName: 'i',
      value: '2',
    };
    const commands = loopIterMapper(event, makeCtx());
    expect(commands).toHaveLength(2);

    const [lap, setVar] = commands;
    expect(lap).toEqual({
      step: 10,
      action: 'walk_loop',
      payload: {
        line: 2,
        loopId: 'for@2',
        loopType: 'for',
        iter: 2,
        lap: 3,
        varName: 'i',
        value: '2',
      },
      durationMs: Math.round(BASE * 1.25),
    });
    expect(setVar).toEqual({
      step: 10,
      action: 'set_var',
      payload: { line: 2, varName: 'i', value: '2', scope: 'local' },
      durationMs: BASE,
    });
  });

  it('while 循环（无循环变量）：只产出单条 walk_loop', () => {
    const event: LoopIterEvent = {
      step: 0,
      line: 4,
      kind: 'loop_iter',
      loopId: 'while@4',
      loopType: 'while',
      iter: 0,
    };
    const cmd = only(loopIterMapper(event, makeCtx()));
    expect(cmd.action).toBe('walk_loop');
    expect(cmd.payload).toEqual({
      line: 4,
      loopId: 'while@4',
      loopType: 'while',
      iter: 0,
      lap: 1,
    });
  });
});

describe('branchMapper → take_branch（按 result 选路）', () => {
  it('true 分支携带 cond/result/lineTaken', () => {
    const event: BranchEvent = {
      step: 6,
      line: 3,
      kind: 'branch',
      cond: 'x > 0',
      result: true,
      lineTaken: 4,
    };
    const cmd = only(branchMapper(event, makeCtx()));
    expect(cmd).toEqual({
      step: 6,
      action: 'take_branch',
      payload: { line: 3, cond: 'x > 0', result: true, lineTaken: 4 },
      durationMs: BASE,
    });
  });

  it('false 分支 result 透传为 false', () => {
    const event: BranchEvent = {
      step: 7,
      line: 3,
      kind: 'branch',
      cond: 'x > 0',
      result: false,
      lineTaken: 6,
    };
    expect(only(branchMapper(event, makeCtx())).payload.result).toBe(false);
  });
});

describe('callMapper → enter_call（走进侧屋、栈 +1、实参）', () => {
  it('实参拍平为一行文本并保留个数', () => {
    const event: CallEvent = {
      step: 8,
      line: 12,
      kind: 'call',
      funcName: 'add',
      args: ['1', '2'],
      depth: 1,
    };
    const cmd = only(callMapper(event, makeCtx()));
    expect(cmd).toEqual({
      step: 8,
      action: 'enter_call',
      payload: { line: 12, funcName: 'add', args: '1, 2', argCount: 2, depth: 1 },
      durationMs: Math.round(BASE * 1.2),
    });
  });

  it('无参调用 args 为空串、argCount=0', () => {
    const event: CallEvent = {
      step: 9,
      line: 1,
      kind: 'call',
      funcName: 'main',
      args: [],
      depth: 1,
    };
    const cmd = only(callMapper(event, makeCtx()));
    expect(cmd.payload.args).toBe('');
    expect(cmd.payload.argCount).toBe(0);
  });
});

describe('returnMapper → exit_call（带返回值走出侧屋、栈 -1）', () => {
  it('携带 funcName/value/depth', () => {
    const event: ReturnEvent = {
      step: 11,
      line: 13,
      kind: 'return',
      funcName: 'add',
      value: '3',
      depth: 0,
    };
    const cmd = only(returnMapper(event, makeCtx()));
    expect(cmd).toEqual({
      step: 11,
      action: 'exit_call',
      payload: { line: 13, funcName: 'add', value: '3', depth: 0 },
      durationMs: Math.round(BASE * 1.2),
    });
  });
});

describe('listOpMapper → list_push / list_pop / list_index', () => {
  it.each<[ListOpEvent['op'], 'list_push' | 'list_pop' | 'list_index']>([
    ['append', 'list_push'],
    ['insert', 'list_push'],
    ['pop', 'list_pop'],
    ['remove', 'list_pop'],
    ['index', 'list_index'],
    ['set', 'list_index'],
  ])('op=%s → action=%s', (op, action) => {
    const event: ListOpEvent = {
      step: 1,
      line: 5,
      kind: 'list_op',
      op,
      target: 'nums',
      lengthAfter: 3,
    };
    expect(only(listOpMapper(event, makeCtx())).action).toBe(action);
  });

  it('append：payload 带 value，无 position', () => {
    const event: ListOpEvent = {
      step: 2,
      line: 5,
      kind: 'list_op',
      op: 'append',
      target: 'nums',
      value: '7',
      lengthAfter: 3,
    };
    const cmd = only(listOpMapper(event, makeCtx()));
    expect(cmd).toEqual({
      step: 2,
      action: 'list_push',
      payload: { line: 5, op: 'append', target: 'nums', lengthAfter: 3, value: '7' },
      durationMs: BASE,
    });
    expect(cmd.payload).not.toHaveProperty('position');
  });

  it('set：走到第 i 箱并带新值（position+value 同时存在）', () => {
    const event: ListOpEvent = {
      step: 3,
      line: 6,
      kind: 'list_op',
      op: 'set',
      target: 'nums',
      position: 0,
      value: '99',
      lengthAfter: 3,
    };
    const cmd = only(listOpMapper(event, makeCtx()));
    expect(cmd.action).toBe('list_index');
    expect(cmd.payload).toEqual({
      line: 6,
      op: 'set',
      target: 'nums',
      lengthAfter: 3,
      position: 0,
      value: '99',
    });
  });

  it('index：position=0 也会写入 payload（不被当成缺省吞掉）', () => {
    const event: ListOpEvent = {
      step: 4,
      line: 6,
      kind: 'list_op',
      op: 'index',
      target: 'nums',
      position: 0,
      value: '5',
      lengthAfter: 3,
    };
    expect(only(listOpMapper(event, makeCtx())).payload.position).toBe(0);
  });
});

describe('dictOpMapper → dict_set（get/set/del 经 payload.op 区分）', () => {
  it('set：写 key→value', () => {
    const event: DictOpEvent = {
      step: 1,
      line: 8,
      kind: 'dict_op',
      op: 'set',
      target: 'scores',
      key: 'alice',
      value: '90',
    };
    const cmd = only(dictOpMapper(event, makeCtx()));
    expect(cmd).toEqual({
      step: 1,
      action: 'dict_set',
      payload: { line: 8, op: 'set', target: 'scores', key: 'alice', value: '90' },
      durationMs: BASE,
    });
  });

  it('del：无 value 字段', () => {
    const event: DictOpEvent = {
      step: 2,
      line: 9,
      kind: 'dict_op',
      op: 'del',
      target: 'scores',
      key: 'alice',
    };
    const cmd = only(dictOpMapper(event, makeCtx()));
    expect(cmd.action).toBe('dict_set');
    expect(cmd.payload).toEqual({ line: 9, op: 'del', target: 'scores', key: 'alice' });
    expect(cmd.payload).not.toHaveProperty('value');
  });
});

describe('output / input → speak', () => {
  it('output：channel=output，携带 text，时长放大', () => {
    const event: OutputEvent = { step: 5, line: 1, kind: 'output', text: 'hello' };
    const cmd = only(outputMapper(event, makeCtx()));
    expect(cmd).toEqual({
      step: 5,
      action: 'speak',
      payload: { line: 1, channel: 'output', text: 'hello' },
      durationMs: Math.round(BASE * 1.5),
    });
  });

  it('input：channel=input，携带 prompt 与读到的 value', () => {
    const event: InputEvent = {
      step: 6,
      line: 2,
      kind: 'input',
      prompt: '姓名?',
      value: 'Bob',
    };
    const cmd = only(inputMapper(event, makeCtx()));
    expect(cmd.action).toBe('speak');
    expect(cmd.payload).toEqual({ line: 2, channel: 'input', prompt: '姓名?', value: 'Bob' });
  });
});

describe('exceptionMapper → stumble（绊倒冒火花）', () => {
  it('携带 excType/message，时长放大', () => {
    const event: ExceptionEvent = {
      step: 9,
      line: 4,
      kind: 'exception',
      excType: 'ZeroDivisionError',
      message: 'division by zero',
    };
    const cmd = only(exceptionMapper(event, makeCtx()));
    expect(cmd).toEqual({
      step: 9,
      action: 'stumble',
      payload: { line: 4, excType: 'ZeroDivisionError', message: 'division by zero' },
      durationMs: Math.round(BASE * 1.4),
    });
  });
});

describe('mapperRegistry / mapEvent 派发', () => {
  const allKinds: TraceEventKind[] = [
    'line',
    'assign',
    'loop_iter',
    'branch',
    'call',
    'return',
    'list_op',
    'dict_op',
    'output',
    'input',
    'exception',
  ];

  it('注册表覆盖全部 11 个 kind', () => {
    for (const kind of allKinds) {
      expect(mapperRegistry[kind]).toBeTypeOf('function');
    }
    expect(Object.keys(mapperRegistry).sort()).toEqual([...allKinds].sort());
  });

  it('mapEvent 按 kind 派发到对应 mapper', () => {
    const event: AssignEvent = {
      step: 0,
      line: 1,
      kind: 'assign',
      varName: 'x',
      value: '1',
      scope: 'global',
    };
    expect(mapEvent(event, makeCtx())).toEqual(assignMapper(event, makeCtx()));
  });

  it('注册表缺该 kind 时回退 move_to_line（未覆盖 kind 契约）', () => {
    const event: AssignEvent = {
      step: 2,
      line: 3,
      kind: 'assign',
      varName: 'x',
      value: '1',
      scope: 'global',
    };
    const cmd = only(mapEvent(event, makeCtx(), {}));
    expect(cmd.action).toBe('move_to_line');
    expect(cmd.payload).toEqual({ line: 3, kind: 'assign' });
  });
});

describe('时长微调（durationFor / DURATION_MULTIPLIERS）', () => {
  it('未列出的动作系数为 1（等于 defaultDurationMs 起步）', () => {
    expect(durationFor(makeCtx(), 'move_to_line')).toBe(BASE);
    expect(durationFor(makeCtx(), 'set_var')).toBe(BASE);
    expect(durationFor(makeCtx(), 'take_branch')).toBe(BASE);
    expect(durationFor(makeCtx(), 'list_push')).toBe(BASE);
    expect(durationFor(makeCtx(), 'dict_set')).toBe(BASE);
  });

  it('已调优动作系数稳定（防漂移哨兵）', () => {
    expect(DURATION_MULTIPLIERS).toEqual({
      walk_loop: 1.25,
      enter_call: 1.2,
      exit_call: 1.2,
      speak: 1.5,
      stumble: 1.4,
    });
  });

  it('command 工厂用动作系数算时长', () => {
    const cmd = command(0, 'speak', { line: 1 }, makeCtx());
    expect(cmd.durationMs).toBe(Math.round(BASE * 1.5));
  });

  it('时长随 defaultDurationMs 线性缩放并取整', () => {
    expect(durationFor(makeCtx({ defaultDurationMs: 500 }), 'walk_loop')).toBe(625);
    expect(durationFor(makeCtx({ defaultDurationMs: 333 }), 'enter_call')).toBe(400); // round(399.6)
  });
});

describe('纯函数性质（同输入同输出、不改外部状态）', () => {
  const event: AssignEvent = {
    step: 1,
    line: 1,
    kind: 'assign',
    varName: 'x',
    value: '5',
    scope: 'local',
  };

  it('多次调用结果深相等', () => {
    const ctx = makeCtx({ variables: { x: '4' } });
    const first = assignMapper(event, ctx);
    const second = assignMapper(event, ctx);
    expect(first).toEqual(second);
  });

  it('不修改传入的 ctx / event（冻结后仍不抛错）', () => {
    const frozenVars = Object.freeze({ x: '4' });
    const ctx = Object.freeze(makeCtx({ variables: frozenVars }));
    const frozenEvent = Object.freeze(event);
    expect(() => assignMapper(frozenEvent, ctx)).not.toThrow();
    expect(ctx.variables).toEqual({ x: '4' });
  });

  it('每次返回全新对象（互不共享引用）', () => {
    const ctx = makeCtx();
    const a = assignMapper(event, ctx);
    const b = assignMapper(event, ctx);
    expect(a).not.toBe(b);
    expect(a[0]).not.toBe(b[0]);
  });
});

describe('每个 mapper 对错配 kind 安全回退 move_to_line（误注册保护）', () => {
  // 喂一个 assign 事件给「非 assign」的 mapper，应触发各自的防御兜底分支。
  const wrong: AssignEvent = {
    step: 0,
    line: 1,
    kind: 'assign',
    varName: 'x',
    value: '1',
    scope: 'global',
  };

  it.each<[string, TraceMapper]>([
    ['loopIterMapper', loopIterMapper],
    ['branchMapper', branchMapper],
    ['callMapper', callMapper],
    ['returnMapper', returnMapper],
    ['listOpMapper', listOpMapper],
    ['dictOpMapper', dictOpMapper],
    ['outputMapper', outputMapper],
    ['inputMapper', inputMapper],
    ['exceptionMapper', exceptionMapper],
  ])('%s 收到错配事件 → move_to_line', (_name, mapper) => {
    const cmd = only(mapper(wrong, makeCtx()));
    expect(cmd.action).toBe('move_to_line');
    expect(cmd.payload).toEqual({ line: 1, kind: 'assign' });
  });
});

describe('step 对齐（每条 RenderCommand.step === event.step）', () => {
  it('所有 mapper 产出的命令 step 都等于事件 step', () => {
    const events: TraceEvent[] = [
      { step: 100, line: 1, kind: 'line' },
      { step: 101, line: 1, kind: 'assign', varName: 'x', value: '1', scope: 'global' },
      {
        step: 102,
        line: 1,
        kind: 'loop_iter',
        loopId: 'for@1',
        loopType: 'for',
        iter: 0,
        varName: 'i',
        value: '0',
      },
      { step: 103, line: 1, kind: 'branch', cond: 'x', result: true, lineTaken: 2 },
      { step: 104, line: 1, kind: 'call', funcName: 'f', args: [], depth: 1 },
      { step: 105, line: 1, kind: 'return', funcName: 'f', value: '1', depth: 0 },
      { step: 106, line: 1, kind: 'list_op', op: 'append', target: 'a', lengthAfter: 1 },
      { step: 107, line: 1, kind: 'dict_op', op: 'set', target: 'd', key: 'k', value: 'v' },
      { step: 108, line: 1, kind: 'output', text: 'x' },
      { step: 109, line: 1, kind: 'input', prompt: 'p', value: 'v' },
      { step: 110, line: 1, kind: 'exception', excType: 'E', message: 'm' },
    ];
    for (const event of events) {
      const commands = mapEvent(event, makeCtx());
      expect(commands.length).toBeGreaterThan(0);
      for (const cmd of commands) {
        expect(cmd.step).toBe(event.step);
      }
    }
  });
});
