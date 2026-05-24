// NpcFramework 扩展测试：聚焦原 spec 未覆盖的边界。
// - 多 NPC 同时巡逻：互不影响（独立状态），dialog 隔离
// - register 同 id 二次注册：覆盖旧 instance
// - faceTowards 8 扇区全覆盖
// - speed=0 / 极大 deltaMs 不会越过 waypoint
// - patrol 单点闭环（waypoint % 1 → 0）
// - dwell 在 deltaMs 大于 dwellMs 时被吃干净，但同帧不前进
import { describe, expect, it } from 'vitest';
import type Phaser from 'phaser';
import { NpcFramework } from '@/game/core/NpcFramework';
import type { NpcDefinition } from '@/game/core/NpcFramework';

function makeScene(): Phaser.Scene {
  return {} as unknown as Phaser.Scene;
}

function baseDef(overrides: Partial<NpcDefinition> & Pick<NpcDefinition, 'id'>): NpcDefinition {
  return {
    displayName: `NPC ${overrides.id}`,
    spawnX: 0,
    spawnY: 0,
    ...overrides,
  };
}

describe('NpcFramework 扩展 - 多 NPC 互斥', () => {
  it('两个 NPC 同时巡逻：独立推进位置，不互相覆盖', () => {
    const f = new NpcFramework(makeScene());
    const a = f.register(
      baseDef({
        id: 'a',
        spawnX: 0,
        spawnY: 0,
        patrol: [{ x: 100, y: 0 }],
        speed: 50,
      }),
    );
    const b = f.register(
      baseDef({
        id: 'b',
        spawnX: 200,
        spawnY: 200,
        patrol: [{ x: 200, y: 100 }],
        speed: 100,
      }),
    );

    f.update(1000);

    expect(a.x).toBeCloseTo(50);
    expect(a.y).toBe(0);
    expect(a.direction).toBe('E');
    expect(b.x).toBe(200);
    expect(b.y).toBe(100);
    expect(b.direction).toBe('N');
  });

  it('startDialog 仅暂停目标 NPC，其它 NPC 继续巡逻', () => {
    const f = new NpcFramework(makeScene());
    const a = f.register(
      baseDef({
        id: 'a',
        spawnX: 0,
        spawnY: 0,
        patrol: [{ x: 100, y: 0 }],
        speed: 50,
      }),
    );
    const b = f.register(
      baseDef({
        id: 'b',
        spawnX: 0,
        spawnY: 0,
        patrol: [{ x: 100, y: 0 }],
        speed: 50,
      }),
    );

    f.startDialog('a');
    f.update(1000);

    expect(a.x).toBe(0);
    expect(a.inDialog).toBe(true);
    expect(b.x).toBeCloseTo(50);
    expect(b.inDialog).toBe(false);
  });

  it('clear() 后再 register 不会"复活"旧 NPC（旧 instance 引用被丢弃）', () => {
    const f = new NpcFramework(makeScene());
    const oldA = f.register(baseDef({ id: 'a', spawnX: 99, spawnY: 99 }));
    f.clear();
    expect(f.get('a')).toBeUndefined();

    const newA = f.register(baseDef({ id: 'a', spawnX: 1, spawnY: 1 }));
    expect(newA).not.toBe(oldA);
    expect(f.get('a')?.x).toBe(1);
    expect(f.get('a')?.y).toBe(1);
  });

  it('register 同 id 两次：后注册的覆盖前注册的（spawn 重置）', () => {
    const f = new NpcFramework(makeScene());
    const first = f.register(baseDef({ id: 'a', spawnX: 10, spawnY: 10 }));
    first.x = 50;
    first.y = 50;
    const second = f.register(baseDef({ id: 'a', spawnX: 0, spawnY: 0 }));
    expect(second.x).toBe(0);
    expect(second.y).toBe(0);
    expect(f.get('a')?.x).toBe(0);
    expect(f.get('a')?.y).toBe(0);
    expect(f.getAll()).toHaveLength(1);
  });
});

describe('NpcFramework 扩展 - faceTowards 8 扇区', () => {
  it.each([
    { name: 'E (+x)', dx: 100, dy: 0, expected: 'E' as const },
    { name: 'SE (+x+y)', dx: 100, dy: 100, expected: 'SE' as const },
    { name: 'S (+y)', dx: 0, dy: 100, expected: 'S' as const },
    { name: 'SW (-x+y)', dx: -100, dy: 100, expected: 'SW' as const },
    { name: 'W (-x)', dx: -100, dy: 0, expected: 'W' as const },
    { name: 'NW (-x-y)', dx: -100, dy: -100, expected: 'NW' as const },
    { name: 'N (-y)', dx: 0, dy: -100, expected: 'N' as const },
    { name: 'NE (+x-y)', dx: 100, dy: -100, expected: 'NE' as const },
  ])('扇区 $name 朝向应推导为 $expected', ({ dx, dy, expected }) => {
    const f = new NpcFramework(makeScene());
    const npc = f.register(
      baseDef({ id: 'a', spawnX: 0, spawnY: 0, spawnDirection: 'S' }),
    );
    expect(f.faceTowards('a', dx, dy)).toBe(true);
    expect(npc.direction).toBe(expected);
  });

  it('faceTowards 在巡逻过程中可以临时打断方向（不影响下次 update 重算）', () => {
    const f = new NpcFramework(makeScene());
    const npc = f.register(
      baseDef({
        id: 'a',
        spawnX: 0,
        spawnY: 0,
        patrol: [{ x: 100, y: 0 }],
        speed: 50,
      }),
    );
    f.update(100);
    expect(npc.direction).toBe('E');

    f.faceTowards('a', npc.x, npc.y - 100);
    expect(npc.direction).toBe('N');

    // 再 update 又被 patrol 推导回 E。
    f.update(100);
    expect(npc.direction).toBe('E');
  });
});

describe('NpcFramework 扩展 - patrol / speed 边界', () => {
  it('speed=0 时 NPC 不会移动（但仍记录朝向）', () => {
    const f = new NpcFramework(makeScene());
    const npc = f.register(
      baseDef({
        id: 'a',
        spawnX: 0,
        spawnY: 0,
        patrol: [{ x: 100, y: 100 }],
        speed: 0,
      }),
    );
    f.update(1000);
    f.update(1000);
    expect(npc.x).toBe(0);
    expect(npc.y).toBe(0);
    expect(npc.direction).toBe('SE');
  });

  it('极大 deltaMs（step > dist）一帧 snap 到 waypoint 且不会越过', () => {
    const f = new NpcFramework(makeScene());
    const npc = f.register(
      baseDef({
        id: 'a',
        spawnX: 0,
        spawnY: 0,
        patrol: [
          { x: 10, y: 0 },
          { x: 20, y: 0 },
        ],
        speed: 1000,
      }),
    );
    // step = 1000 * 1000 / 1000 = 1000，远远超过 dist=10。
    f.update(1000);
    expect(npc.x).toBe(10);
    expect(npc.currentWaypoint).toBe(1);
  });

  it('patrol 单 waypoint 闭环：到达后 currentWaypoint 取模为 0，下一帧继续走（已经在原地直接 snap）', () => {
    const f = new NpcFramework(makeScene());
    const npc = f.register(
      baseDef({
        id: 'a',
        spawnX: 0,
        spawnY: 0,
        patrol: [{ x: 50, y: 0, dwellMs: 100 }],
        speed: 100,
      }),
    );
    // 走到 waypoint[0]。
    f.update(500);
    expect(npc.x).toBe(50);
    expect(npc.currentWaypoint).toBe(0);

    // dwell 100ms。
    f.update(100);
    expect(npc.x).toBe(50);

    // dwell 已耗尽。下一帧检测到 spawn≈waypoint[0]，再次 snap + dwell。
    f.update(50);
    expect(npc.x).toBe(50);
    expect(npc.currentWaypoint).toBe(0);
  });

  it('dwellMs 未指定时默认 0：到达 waypoint 立刻进入下一个的移动（同帧仍只 snap 不前进）', () => {
    const f = new NpcFramework(makeScene());
    const npc = f.register(
      baseDef({
        id: 'a',
        spawnX: 0,
        spawnY: 0,
        patrol: [
          { x: 10, y: 0 },
          { x: 30, y: 0 },
        ],
        speed: 100,
      }),
    );
    f.update(100);
    expect(npc.x).toBe(10);
    expect(npc.currentWaypoint).toBe(1);
    // 同帧已经 snap，未消耗剩余 deltaMs（per-frame 简化语义）。
    f.update(100);
    expect(npc.x).toBe(20);
    expect(npc.currentWaypoint).toBe(1);
  });

  it('dwell 期间 inDialog 切换不会累计或扣减 dwellRemainingMs', () => {
    const f = new NpcFramework(makeScene());
    const npc = f.register(
      baseDef({
        id: 'a',
        spawnX: 0,
        spawnY: 0,
        patrol: [
          { x: 10, y: 0, dwellMs: 500 },
          { x: 20, y: 0 },
        ],
        speed: 100,
      }),
    );
    f.update(100);
    expect(npc.x).toBe(10);

    f.startDialog('a');
    f.update(2000);
    expect(npc.x).toBe(10);

    f.endDialog('a');
    // dwell 还剩 500ms（未在对话中消耗）。
    f.update(400);
    expect(npc.x).toBe(10);
    f.update(150);
    expect(npc.x).toBe(10);
    // 再过 100ms 才开始走第二段。
    f.update(100);
    expect(npc.x).toBe(20);
  });

  it('update 接受小数 deltaMs（亚毫秒），按比例推进', () => {
    const f = new NpcFramework(makeScene());
    const npc = f.register(
      baseDef({
        id: 'a',
        spawnX: 0,
        spawnY: 0,
        patrol: [{ x: 1000, y: 0 }],
        speed: 100,
      }),
    );
    f.update(0.5);
    expect(npc.x).toBeCloseTo(0.05);
  });

  it('endDialog 在 inDialog=false 时也返回 true（idempotent）', () => {
    const f = new NpcFramework(makeScene());
    const npc = f.register(baseDef({ id: 'a' }));
    expect(npc.inDialog).toBe(false);
    expect(f.endDialog('a')).toBe(true);
    expect(npc.inDialog).toBe(false);
  });

  it('clear 后 update 不抛错，且不影响新注册的 NPC', () => {
    const f = new NpcFramework(makeScene());
    f.register(baseDef({ id: 'a', patrol: [{ x: 50, y: 0 }], speed: 50 }));
    f.clear();
    expect(() => f.update(1000)).not.toThrow();

    const fresh = f.register(
      baseDef({ id: 'b', spawnX: 0, spawnY: 0, patrol: [{ x: 50, y: 0 }], speed: 50 }),
    );
    f.update(100);
    expect(fresh.x).toBeCloseTo(5);
  });
});
