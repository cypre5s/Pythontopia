// PlayerStateMachine 扩展测试：并发转移 / 监听器边界 / 状态图全矩阵 / 自转 noop 监听器。
// 与原 PlayerStateMachine.spec.ts 互补：原 spec 覆盖单步 happy path 和 onChange 基本能力，
// 本 spec 聚焦"在监听器回调里再次 transition"、"listener 中 off 自己"、"非法→合法的恢复路径"。
import { describe, expect, it, vi } from 'vitest';
import { PlayerStateMachine } from '@/game/core/PlayerStateMachine';
import type {
  Direction,
  PlayerState,
  PlayerStateChangeEvent,
} from '@/game/core/PlayerStateMachine';

const ALL_STATES: PlayerState[] = [
  'idle',
  'walk',
  'run',
  'interact',
  'water',
  'mine',
  'talk',
];

// 与 PlayerStateMachine.ts 内部 TRANSITIONS 完全对齐（含自转）。
// 这里冗余写一份是为了把"业务期望矩阵"显式锁在测试里，被回归覆盖。
const EXPECTED_TRANSITIONS: Record<PlayerState, PlayerState[]> = {
  idle: ['idle', 'walk', 'run', 'interact', 'water', 'mine', 'talk'],
  walk: ['walk', 'run', 'idle', 'interact'],
  run: ['walk', 'run', 'idle', 'interact'],
  interact: ['interact', 'talk', 'water', 'mine', 'idle'],
  water: ['water', 'idle'],
  mine: ['mine', 'idle'],
  talk: ['talk', 'idle'],
};

describe('PlayerStateMachine 扩展 - 状态图全矩阵', () => {
  for (const from of ALL_STATES) {
    for (const to of ALL_STATES) {
      const allowed = EXPECTED_TRANSITIONS[from].includes(to);
      it(`${from} -> ${to} 应该${allowed ? '允许' : '拒绝'}`, () => {
        const sm = new PlayerStateMachine({ state: from });
        const ok = sm.transition(to);
        if (allowed) {
          expect(ok).toBe(true);
          expect(sm.state).toBe(to);
        } else {
          expect(ok).toBe(false);
          expect(sm.state).toBe(from);
        }
      });
    }
  }
});

describe('PlayerStateMachine 扩展 - 监听器边界', () => {
  it('监听器内部 off 自己时不会跳过同批次的后续监听器', () => {
    const sm = new PlayerStateMachine();
    const order: string[] = [];

    const offA = sm.onChange(() => {
      order.push('a');
      offA();
    });
    sm.onChange(() => order.push('b'));
    sm.onChange(() => order.push('c'));

    sm.transition('walk');

    // 关键期望：a 自摘后 b、c 仍然被通知（因为 transition 内部用 listeners.slice()）。
    expect(order).toEqual(['a', 'b', 'c']);

    sm.transition('run');
    // 第二轮 a 已经 off：只剩 b、c。
    expect(order).toEqual(['a', 'b', 'c', 'b', 'c']);
  });

  it('监听器内部 onChange 注册的新监听器在"当前批次"不会被触发，下一帧才生效', () => {
    const sm = new PlayerStateMachine();
    const order: string[] = [];

    sm.onChange(() => {
      order.push('outer');
      sm.onChange(() => order.push('inner'));
    });

    sm.transition('walk');
    expect(order).toEqual(['outer']);

    sm.transition('run');
    // 第二次 transition：outer 又触发并再注册一个 inner（数量增加），
    // 第一次注册的 inner 也会被通知。
    expect(order).toEqual(['outer', 'outer', 'inner']);
  });

  it('监听器抛错时仍按原实现冒泡：当前实现未 try/catch，故同批后续监听器中断', () => {
    const sm = new PlayerStateMachine();
    const calls: string[] = [];
    sm.onChange(() => calls.push('a'));
    sm.onChange(() => {
      calls.push('b');
      throw new Error('boom-listener');
    });
    sm.onChange(() => calls.push('c'));

    expect(() => sm.transition('walk')).toThrow('boom-listener');
    expect(calls).toEqual(['a', 'b']);
    expect(sm.state).toBe('walk');
  });

  it('监听器在回调里再次 transition：嵌套转移按"先完成本次再处理新一次"语义执行', () => {
    const sm = new PlayerStateMachine();
    const events: PlayerStateChangeEvent[] = [];
    let firstFire = true;
    sm.onChange((e) => {
      events.push(e);
      if (firstFire && e.to === 'walk') {
        firstFire = false;
        sm.transition('run');
      }
    });

    sm.transition('walk');

    // 期望：先收到 idle->walk，再因嵌套触发收到 walk->run，
    // 最终状态机停在 run。
    expect(events.map((e) => `${e.from}->${e.to}`)).toEqual([
      'idle->walk',
      'walk->run',
    ]);
    expect(sm.state).toBe('run');
  });

  it('同 state 的"自转 transition"在含 onChange 的情况下不会通知监听器', () => {
    const sm = new PlayerStateMachine({ state: 'water', direction: 'N' });
    const listener = vi.fn();
    sm.onChange(listener);

    const ok = sm.transition('water', { direction: 'E' });

    expect(ok).toBe(true);
    expect(listener).not.toHaveBeenCalled();
    // 同 state 的自转是 noop：direction 也不会被更新（早期 return 在 direction 赋值前）。
    expect(sm.direction).toBe('N');
  });

  it('off() 被调用两次只有第一次生效，第二次是 noop（不会误删别的监听器）', () => {
    const sm = new PlayerStateMachine();
    const a = vi.fn();
    const b = vi.fn();
    const offA = sm.onChange(a);
    sm.onChange(b);

    offA();
    offA();

    sm.transition('walk');

    expect(a).not.toHaveBeenCalled();
    expect(b).toHaveBeenCalledTimes(1);
  });
});

describe('PlayerStateMachine 扩展 - 非法转移恢复路径', () => {
  it('water 状态被非法 transition("run") 拒绝后，仍可经 idle 中转到 run', () => {
    const sm = new PlayerStateMachine({ state: 'water' });
    expect(sm.transition('run')).toBe(false);
    expect(sm.state).toBe('water');

    expect(sm.transition('idle')).toBe(true);
    expect(sm.transition('run', { direction: 'NE' })).toBe(true);
    expect(sm.state).toBe('run');
    expect(sm.direction).toBe('NE');
  });

  it('reset 后所有合法/非法限制按新状态重新计算', () => {
    const sm = new PlayerStateMachine({ state: 'walk' });
    expect(sm.transition('water')).toBe(false);
    sm.reset({ state: 'idle' });
    expect(sm.transition('water')).toBe(true);
    expect(sm.state).toBe('water');
  });

  it('face 不受当前 state 的转移规则约束，可任意切到 8 个方向', () => {
    const sm = new PlayerStateMachine({ state: 'water', direction: 'S' });
    const dirs: Direction[] = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
    for (const d of dirs) {
      sm.face(d);
      expect(sm.direction).toBe(d);
      expect(sm.state).toBe('water');
    }
  });
});

describe('PlayerStateMachine 扩展 - snapshot 与 reset', () => {
  it('snapshot 在多次 transition 中始终反映最新 state/direction', () => {
    const sm = new PlayerStateMachine();
    sm.transition('walk', { direction: 'E' });
    expect(sm.snapshot()).toEqual({ state: 'walk', direction: 'E' });
    sm.transition('run', { direction: 'NE' });
    expect(sm.snapshot()).toEqual({ state: 'run', direction: 'NE' });
  });

  it('reset() 不会触发 onChange 监听器（reset 是越过状态机的硬重置）', () => {
    const sm = new PlayerStateMachine({ state: 'walk', direction: 'E' });
    const listener = vi.fn();
    sm.onChange(listener);
    sm.reset({ state: 'mine', direction: 'NW' });
    expect(listener).not.toHaveBeenCalled();
    expect(sm.state).toBe('mine');
    expect(sm.direction).toBe('NW');
  });
});
