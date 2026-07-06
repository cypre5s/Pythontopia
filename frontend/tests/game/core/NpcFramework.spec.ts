// W2 NpcFramework 单测：覆盖注册、巡逻、dwell、自动朝向、对话暂停、faceTowards
// 与 clear。本框架对 Phaser.Scene 只做引用保存，不调用 scene API，所以测试用
// 最小空对象桩即可（与 SceneSwitcher.spec.ts 同套路）。
import { describe, expect, it } from 'vitest';
import type Phaser from 'phaser';
import { NpcFramework, defaultPortraitKey } from '@/game/core/NpcFramework';
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

describe('NpcFramework', () => {
  describe('register', () => {
    it('返回带初始位置 / 显式 spawnDirection / inDialog=false 的 instance', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(
        baseDef({
          id: 'sara',
          displayName: 'Sara 老师',
          spawnX: 100,
          spawnY: 200,
          spawnDirection: 'NE',
        }),
      );

      expect(npc.id).toBe('sara');
      expect(npc.x).toBe(100);
      expect(npc.y).toBe(200);
      expect(npc.direction).toBe('NE');
      expect(npc.currentWaypoint).toBe(0);
      expect(npc.inDialog).toBe(false);
      expect(npc.def.displayName).toBe('Sara 老师');
    });

    it('未传 spawnDirection 时朝向默认为 S', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(baseDef({ id: 'a' }));
      expect(npc.direction).toBe('S');
    });

    it('def 上的 questPublisher / dialogAnchor 标记会原样透传到 instance.def', () => {
      const f = new NpcFramework(makeScene());
      const def: NpcDefinition = {
        id: 'merchant',
        displayName: '商人',
        spawnX: 0,
        spawnY: 0,
        questPublisher: true,
        dialogAnchor: { x: 16, y: 32 },
      };
      const npc = f.register(def);
      expect(npc.def.questPublisher).toBe(true);
      expect(npc.def.dialogAnchor).toEqual({ x: 16, y: 32 });
    });
  });

  describe('get / getAll', () => {
    it('get 返回已注册 instance, 未注册 id 返回 undefined', () => {
      const f = new NpcFramework(makeScene());
      expect(f.get('missing')).toBeUndefined();

      const npc = f.register(baseDef({ id: 'a' }));
      expect(f.get('a')).toBe(npc);
      expect(f.get('b')).toBeUndefined();
    });

    it('getAll 返回所有已注册 NPC 数组', () => {
      const f = new NpcFramework(makeScene());
      expect(f.getAll()).toEqual([]);

      f.register(baseDef({ id: 'a' }));
      f.register(baseDef({ id: 'b', spawnX: 5, spawnY: 5 }));

      const all = f.getAll();
      expect(all).toHaveLength(2);
      expect(all.map((n) => n.id).sort()).toEqual(['a', 'b']);
    });
  });

  describe('巡逻', () => {
    it('patrol 省略或为空数组时 NPC 站桩, update 不改变位置', () => {
      const f = new NpcFramework(makeScene());
      const stationer = f.register(baseDef({ id: 'a', spawnX: 50, spawnY: 50 }));
      const emptyPatrol = f.register(
        baseDef({ id: 'b', spawnX: 10, spawnY: 20, patrol: [], speed: 200 }),
      );

      f.update(1000);
      f.update(1000);

      expect(stationer.x).toBe(50);
      expect(stationer.y).toBe(50);
      expect(emptyPatrol.x).toBe(10);
      expect(emptyPatrol.y).toBe(20);
    });

    it('patrol 单 waypoint, NPC 向其移动, 朝向自动从 S 变 E, 到达后停在 waypoint', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(
        baseDef({
          id: 'a',
          spawnX: 0,
          spawnY: 0,
          patrol: [{ x: 50, y: 0 }],
          speed: 100,
        }),
      );
      expect(npc.direction).toBe('S');

      // 第一帧 step=10, 仅前进 10px。
      f.update(100);
      expect(npc.x).toBeCloseTo(10);
      expect(npc.y).toBe(0);
      expect(npc.direction).toBe('E');

      // 一次大 deltaMs 直接到达。
      f.update(1000);
      expect(npc.x).toBe(50);
      expect(npc.y).toBe(0);
      expect(npc.direction).toBe('E');
    });

    it('patrol 双 waypoint 环形循环: 0 → 1 → 0 → 1 …', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(
        baseDef({
          id: 'a',
          spawnX: 0,
          spawnY: 0,
          patrol: [
            { x: 10, y: 0 },
            { x: 10, y: 10 },
          ],
          speed: 100,
        }),
      );

      // 到 waypoint[0] (10, 0)
      f.update(100);
      expect(npc.x).toBe(10);
      expect(npc.y).toBe(0);
      expect(npc.currentWaypoint).toBe(1);

      // 到 waypoint[1] (10, 10)，dx=0 dy=10 → S
      f.update(100);
      expect(npc.x).toBe(10);
      expect(npc.y).toBe(10);
      expect(npc.currentWaypoint).toBe(0);
      expect(npc.direction).toBe('S');

      // 回到 waypoint[0] (10, 0)，dx=0 dy=-10 → N
      f.update(100);
      expect(npc.x).toBe(10);
      expect(npc.y).toBe(0);
      expect(npc.currentWaypoint).toBe(1);
      expect(npc.direction).toBe('N');
    });

    it('dwellMs 生效: 到达 waypoint 后停留指定毫秒数, 期间位置不变', () => {
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

      // 到达 waypoint[0]，开始 dwell。
      f.update(100);
      expect(npc.x).toBe(10);
      expect(npc.currentWaypoint).toBe(1);

      // dwell 中：累计 400ms < 500ms，NPC 不动。
      f.update(200);
      expect(npc.x).toBe(10);
      f.update(200);
      expect(npc.x).toBe(10);

      // dwell 刚清零的这一帧也不发生移动（per-frame 简化语义）。
      f.update(200);
      expect(npc.x).toBe(10);

      // dwell 已耗尽，下一帧才开始走 waypoint[1]。
      f.update(100);
      expect(npc.x).toBe(20);
      expect(npc.currentWaypoint).toBe(0);
    });

    it('speed 默认为 32 px/s', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(
        baseDef({
          id: 'a',
          spawnX: 0,
          spawnY: 0,
          patrol: [{ x: 1000, y: 0 }],
        }),
      );
      f.update(1000);
      expect(npc.x).toBeCloseTo(32);
    });

    it('speed 自定义生效, 1 秒推进 speed 像素', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(
        baseDef({
          id: 'a',
          spawnX: 0,
          spawnY: 0,
          patrol: [{ x: 1000, y: 0 }],
          speed: 250,
        }),
      );
      f.update(100);
      expect(npc.x).toBeCloseTo(25);
      f.update(900);
      expect(npc.x).toBeCloseTo(250);
    });

    it('inDialog=true 时暂停巡逻, endDialog 后继续移动', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(
        baseDef({
          id: 'a',
          spawnX: 0,
          spawnY: 0,
          patrol: [{ x: 100, y: 0 }],
          speed: 100,
        }),
      );

      f.startDialog('a');
      expect(npc.inDialog).toBe(true);
      f.update(1000);
      expect(npc.x).toBe(0);

      f.endDialog('a');
      expect(npc.inDialog).toBe(false);
      f.update(100);
      expect(npc.x).toBeCloseTo(10);
    });

    it('spawn 已在第一个 waypoint 上时, 第一帧直接推进到 waypoint[1]', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(
        baseDef({
          id: 'a',
          spawnX: 10,
          spawnY: 0,
          patrol: [
            { x: 10, y: 0, dwellMs: 200 },
            { x: 30, y: 0 },
          ],
          speed: 100,
        }),
      );

      // 第一帧检测到 dist≈0，直接 snap + 切到 waypoint[1] 并启动 dwell。
      f.update(50);
      expect(npc.x).toBe(10);
      expect(npc.y).toBe(0);
      expect(npc.currentWaypoint).toBe(1);

      // dwell 200ms 内不动。
      f.update(150);
      expect(npc.x).toBe(10);

      // dwell 耗尽 + 一帧推进后开始走 waypoint[1]。
      f.update(200);
      f.update(100);
      expect(npc.x).toBeCloseTo(20);
    });

    it('update 忽略 deltaMs <= 0 或非有限数, 不修改 NPC', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(
        baseDef({
          id: 'a',
          spawnX: 0,
          spawnY: 0,
          patrol: [{ x: 100, y: 0 }],
          speed: 100,
        }),
      );

      f.update(0);
      f.update(-50);
      f.update(Number.NaN);
      f.update(Number.POSITIVE_INFINITY);

      expect(npc.x).toBe(0);
      expect(npc.currentWaypoint).toBe(0);
    });
  });

  describe('wander 自由游走（R-14 需求8b）', () => {
    function makeSeqRng(seq: number[]): () => number {
      let i = 0;
      return () => {
        const v = seq[i % seq.length] ?? 0;
        i += 1;
        return v;
      };
    }

    it('无 patrol + wander 配置：朝随机选点移动（注入 rng 确定化）', () => {
      // rng 序列 [0, 0.5]：angle=0(向东) + r=0.5×40=20 → 目标 (120,100)。
      const f = new NpcFramework(makeScene(), { rng: makeSeqRng([0, 0.5]) });
      const npc = f.register(
        baseDef({ id: 'w', spawnX: 100, spawnY: 100, wander: { radiusPx: 40 }, speed: 100 }),
      );
      // step=100×0.1=10 → 向东推进 10px（未到达，目标保留）。
      f.update(100);
      expect(npc.x).toBeCloseTo(110);
      expect(npc.y).toBeCloseTo(100);
      expect(npc.direction).toBe('E');
    });

    it('碰撞避障：下一步落点被挡 → 放弃目标原地等待，不穿墙', () => {
      // 目标 (140,100) 本身可达，但 x∈[105,135] 是墙带，向东第一步 110 会撞墙。
      const f = new NpcFramework(makeScene(), { rng: makeSeqRng([0, 1]) });
      f.setCollisionChecker((x) => x >= 105 && x <= 135);
      const npc = f.register(
        baseDef({ id: 'w', spawnX: 100, spawnY: 100, wander: { radiusPx: 40 }, speed: 100 }),
      );
      f.update(100);
      expect(npc.x).toBe(100);
      expect(npc.y).toBe(100);
    });

    it('patrol 优先于 wander：两者都配时只走 patrol', () => {
      const f = new NpcFramework(makeScene(), { rng: () => 0.5 });
      const npc = f.register(
        baseDef({
          id: 'p',
          spawnX: 0,
          spawnY: 0,
          patrol: [{ x: 50, y: 0 }],
          wander: { radiusPx: 40 },
          speed: 100,
        }),
      );
      f.update(100);
      expect(npc.x).toBeCloseTo(10);
      expect(npc.y).toBe(0);
    });

    it('inDialog 时 wander 暂停', () => {
      const f = new NpcFramework(makeScene(), { rng: makeSeqRng([0, 0.5]) });
      const npc = f.register(
        baseDef({ id: 'w', spawnX: 100, spawnY: 100, wander: { radiusPx: 40 }, speed: 100 }),
      );
      f.startDialog('w');
      f.update(100);
      expect(npc.x).toBe(100);
      expect(npc.y).toBe(100);
    });
  });

  describe('startDialog / endDialog', () => {
    it('未注册 id 返回 false 且不影响已有 NPC', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(baseDef({ id: 'a' }));
      expect(f.startDialog('ghost')).toBe(false);
      expect(f.endDialog('ghost')).toBe(false);
      expect(npc.inDialog).toBe(false);
    });

    it('已注册 id 返回 true 并切换 inDialog 状态', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(baseDef({ id: 'a' }));
      expect(npc.inDialog).toBe(false);
      expect(f.startDialog('a')).toBe(true);
      expect(npc.inDialog).toBe(true);
      expect(f.endDialog('a')).toBe(true);
      expect(npc.inDialog).toBe(false);
    });
  });

  describe('faceTowards', () => {
    it('根据目标方向更新 NPC.direction (E / N / SW 各扇区)', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(
        baseDef({ id: 'a', spawnX: 100, spawnY: 100, spawnDirection: 'S' }),
      );

      // 东
      expect(f.faceTowards('a', 200, 100)).toBe(true);
      expect(npc.direction).toBe('E');

      // 北
      f.faceTowards('a', 100, 50);
      expect(npc.direction).toBe('N');

      // 西南（屏幕 Y 向下，所以 dy>0 是南）
      f.faceTowards('a', 50, 150);
      expect(npc.direction).toBe('SW');
    });

    it('未注册 id 返回 false', () => {
      const f = new NpcFramework(makeScene());
      expect(f.faceTowards('ghost', 0, 0)).toBe(false);
    });

    it('目标与 NPC 几乎重合时不改朝向 (避免 atan2(0,0) 退化)', () => {
      const f = new NpcFramework(makeScene());
      const npc = f.register(
        baseDef({ id: 'a', spawnX: 100, spawnY: 100, spawnDirection: 'NW' }),
      );
      f.faceTowards('a', 100, 100);
      expect(npc.direction).toBe('NW');
    });
  });

  describe('clear', () => {
    it('清空所有已注册 NPC, getAll/get 返回空', () => {
      const f = new NpcFramework(makeScene());
      f.register(baseDef({ id: 'a' }));
      f.register(baseDef({ id: 'b' }));
      expect(f.getAll()).toHaveLength(2);

      f.clear();
      expect(f.getAll()).toHaveLength(0);
      expect(f.get('a')).toBeUndefined();
      expect(f.get('b')).toBeUndefined();
    });
  });
});

describe('defaultPortraitKey', () => {
  it('返回 portrait_<id> 命名, 对齐 frontend/public/assets/pixel/portraits/ 文件名', () => {
    expect(defaultPortraitKey('mayor_py')).toBe('portrait_mayor_py');
    expect(defaultPortraitKey('teacher')).toBe('portrait_teacher');
    expect(defaultPortraitKey('student_anxious')).toBe('portrait_student_anxious');
  });
});
