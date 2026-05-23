// W2 集成测试：验证 WorldScene 暴露的辅助构造器能正确派生 NpcDefinition /
// ITrigger，以及把它们注册到 InteractableSystem / NpcFramework 后能联动触发
// dialog store。完整 update() / Phaser scene lifecycle 集成由 Playwright 截图
// 跑端到端覆盖，本 spec 只用 jsdom + 最小桩验证关键集成点。
import type Phaser from 'phaser';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  BULLETIN_BOARD_TRIGGER_ID,
  NPC_TRIGGER_ID_PREFIX,
  buildBulletinBoardTrigger,
  buildNpcDefinitionFromSpawn,
  buildNpcHintText,
  buildNpcTrigger,
  directionDelta,
  toCardinal,
} from '@/game/scenes/WorldScene';
import { InteractableSystem, type HintRenderer } from '@/game/core/InteractableSystem';
import { NpcFramework } from '@/game/core/NpcFramework';
import { useDialogStore } from '@/stores/dialog';
import type { NpcSpawn } from '@/game/adapters/tilemap';

const TILE_SIZE = 32;

function sampleNpc(overrides: Partial<NpcSpawn> = {}): NpcSpawn {
  return {
    npcKey: 'mayor_py',
    label: '镇长 Py',
    tileX: 25,
    tileY: 10,
    sprite: 'player_sara',
    ...overrides,
  };
}

function makeScene(): Phaser.Scene {
  return {} as unknown as Phaser.Scene;
}

function makeHintRenderer(): {
  renderer: HintRenderer;
  show: ReturnType<typeof vi.fn>;
  hide: ReturnType<typeof vi.fn>;
} {
  const show = vi.fn();
  const hide = vi.fn();
  return { renderer: { show, hide }, show, hide };
}

describe('WorldScene.buildNpcHintText', () => {
  it('returns "按 E 与 {label} 对话" using the provided NPC label', () => {
    expect(buildNpcHintText('镇长 Py')).toBe('按 E 与 镇长 Py 对话');
    expect(buildNpcHintText('练习教练 Q')).toBe('按 E 与 练习教练 Q 对话');
  });
});

describe('WorldScene.buildNpcDefinitionFromSpawn', () => {
  it('maps NpcSpawn to NpcDefinition with id/displayName/portraitKey/spawn coords', () => {
    const npc = sampleNpc({ tileX: 25, tileY: 10 });
    const def = buildNpcDefinitionFromSpawn(npc, TILE_SIZE);

    expect(def.id).toBe('mayor_py');
    expect(def.displayName).toBe('镇长 Py');
    // portraitKey 走 defaultPortraitKey(id) -> portrait_<id>
    expect(def.portraitKey).toBe('portrait_mayor_py');
    // tile (25,10) 中心像素：25*32+16=816, 10*32+16=336
    expect(def.spawnX).toBe(25 * TILE_SIZE + TILE_SIZE / 2);
    expect(def.spawnY).toBe(10 * TILE_SIZE + TILE_SIZE / 2);
  });

  it('falls back to npcKey when label is empty', () => {
    const def = buildNpcDefinitionFromSpawn(sampleNpc({ label: '' }), TILE_SIZE);
    expect(def.displayName).toBe('mayor_py');
  });
});

describe('WorldScene.buildNpcTrigger', () => {
  it('produces a circle trigger with namespaced id and localized hint', () => {
    const npc = sampleNpc();
    const trigger = buildNpcTrigger(npc, TILE_SIZE, vi.fn());

    expect(trigger.id).toBe(`${NPC_TRIGGER_ID_PREFIX}mayor_py`);
    expect(trigger.hint).toBe('按 E 与 镇长 Py 对话');
    expect(trigger.shape.type).toBe('circle');
    if (trigger.shape.type === 'circle') {
      expect(trigger.shape.x).toBe(25 * TILE_SIZE + TILE_SIZE / 2);
      expect(trigger.shape.y).toBe(10 * TILE_SIZE + TILE_SIZE / 2);
      expect(trigger.shape.radius).toBeCloseTo(TILE_SIZE * 1.4);
    }
  });

  it('invokes the provided onActivate callback when activated by InteractableSystem', () => {
    const onActivate = vi.fn();
    const npc = sampleNpc({ tileX: 5, tileY: 5 });
    const trigger = buildNpcTrigger(npc, TILE_SIZE, onActivate);

    const sys = new InteractableSystem(makeScene());
    sys.register(trigger);
    // tile (5,5) 中心像素 = 176, 176
    sys.update(176, 176, true);

    expect(onActivate).toHaveBeenCalledTimes(1);
    const ctx = onActivate.mock.calls[0]?.[0] as { trigger?: { id: string } } | undefined;
    // Trigger 自身被透传到 ctx.trigger（InteractableSystem 契约）。
    expect(ctx?.trigger?.id).toBe(trigger.id);
  });
});

describe('WorldScene.buildBulletinBoardTrigger', () => {
  it('builds a circle trigger at the configured tile center with the board hint', () => {
    const trigger = buildBulletinBoardTrigger({
      tileX: 27,
      tileY: 20,
      tileSize: TILE_SIZE,
      onActivate: vi.fn(),
    });

    expect(trigger.id).toBe(BULLETIN_BOARD_TRIGGER_ID);
    expect(trigger.hint).toBe('按 E 查看公告板');
    expect(trigger.shape.type).toBe('circle');
    if (trigger.shape.type === 'circle') {
      expect(trigger.shape.x).toBe(27 * TILE_SIZE + TILE_SIZE / 2);
      expect(trigger.shape.y).toBe(20 * TILE_SIZE + TILE_SIZE / 2);
      expect(trigger.shape.radius).toBeCloseTo(TILE_SIZE * 1.4);
    }
  });

  it('triggers onActivate via InteractableSystem when player stands on the board', () => {
    const onActivate = vi.fn();
    const trigger = buildBulletinBoardTrigger({
      tileX: 10,
      tileY: 10,
      tileSize: TILE_SIZE,
      onActivate,
    });

    const sys = new InteractableSystem(makeScene());
    sys.register(trigger);
    const cx = 10 * TILE_SIZE + TILE_SIZE / 2;
    const cy = 10 * TILE_SIZE + TILE_SIZE / 2;
    sys.update(cx, cy, true);

    expect(onActivate).toHaveBeenCalledTimes(1);
  });
});

describe('WorldScene integration: NPC triggers + dialog store + NpcFramework', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('walking into NPC range shows the localized hint via hintRenderer', () => {
    const npc = sampleNpc({ tileX: 5, tileY: 5 });
    const { renderer, show, hide } = makeHintRenderer();
    const sys = new InteractableSystem(makeScene(), { hintRenderer: renderer });
    sys.register(buildNpcTrigger(npc, TILE_SIZE, vi.fn()));

    // Far away first：show 不应被触发；hide 可能被 refreshHint() 调
    // （active 集合为空时统一收尾），这里只断言 show 没被触发即可。
    sys.update(0, 0, false);
    expect(show).not.toHaveBeenCalled();

    // 走到 NPC 中心 -> show 被调用且文本含 NPC 名。
    const cx = 5 * TILE_SIZE + TILE_SIZE / 2;
    const cy = 5 * TILE_SIZE + TILE_SIZE / 2;
    sys.update(cx, cy, false);
    expect(show).toHaveBeenCalledWith('按 E 与 镇长 Py 对话');

    // 走远 -> hide 被调用。
    sys.update(2000, 2000, false);
    expect(hide).toHaveBeenCalled();
  });

  it('activating an NPC trigger opens the dialog store via openWithTree path', async () => {
    const dialog = useDialogStore();
    // 把 fetch mock 成立刻成功，避免真实网络；store openWithTree 内部 await 两次
    // fetch（meta + dialog）后才把 tree 写入。
    vi.spyOn(global, 'fetch').mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/meta.json')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({ key: 'mayor_py', name: '镇长 Py', portrait: 'portrait_mayor_py.png' }),
            { status: 200 },
          ),
        );
      }
      if (url.endsWith('/dialog.json')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              npcKey: 'mayor_py',
              rootNodeId: 'greet',
              nodes: {
                greet: { id: 'greet', text: '你好', conditions: [], choices: [] },
              },
            }),
            { status: 200 },
          ),
        );
      }
      return Promise.reject(new Error(`unexpected fetch: ${url}`));
    });

    const npc = sampleNpc({ tileX: 8, tileY: 8 });
    const sys = new InteractableSystem(makeScene());
    let activated = false;
    sys.register(
      buildNpcTrigger(npc, TILE_SIZE, () => {
        activated = true;
        // 模拟 WorldScene.create 里的 onActivate 闭包：调 dialog store。
        void dialog.openWithTree(npc.npcKey);
      }),
    );

    const cx = 8 * TILE_SIZE + TILE_SIZE / 2;
    const cy = 8 * TILE_SIZE + TILE_SIZE / 2;
    sys.update(cx, cy, true);

    expect(activated).toBe(true);
    // openWithTree 立即把 visible 翻为 true、npcKey 写入；fetch 是 await 链路，
    // store 在 then 后才填充 tree。等一次微任务 flush 让 try/catch 跑完。
    expect(dialog.visible).toBe(true);
    expect(dialog.npcKey).toBe('mayor_py');

    await vi.waitFor(() => {
      expect(dialog.loading).toBe(false);
    });
    expect(dialog.tree?.npcKey).toBe('mayor_py');
    expect(dialog.npcName).toBe('镇长 Py');
  });

  it('NpcFramework receives the parsed npc and supports startDialog/faceTowards', () => {
    const npc = sampleNpc({ tileX: 5, tileY: 5 });
    const framework = new NpcFramework(makeScene());
    const instance = framework.register(buildNpcDefinitionFromSpawn(npc, TILE_SIZE));
    // spawn 时朝向默认 S。
    expect(instance.direction).toBe('S');

    expect(framework.startDialog(npc.npcKey)).toBe(true);
    expect(instance.inDialog).toBe(true);

    // faceTowards：玩家位于 NPC 左侧 -> NPC 应朝 W。
    const npcCenterX = 5 * TILE_SIZE + TILE_SIZE / 2;
    const npcCenterY = 5 * TILE_SIZE + TILE_SIZE / 2;
    framework.faceTowards(npc.npcKey, npcCenterX - 100, npcCenterY);
    expect(instance.direction).toBe('W');

    expect(framework.endDialog(npc.npcKey)).toBe(true);
    expect(instance.inDialog).toBe(false);
  });

  it('clearing InteractableSystem hides the hint and removes all triggers', () => {
    const { renderer, hide } = makeHintRenderer();
    const sys = new InteractableSystem(makeScene(), { hintRenderer: renderer });
    sys.register(buildNpcTrigger(sampleNpc({ tileX: 1, tileY: 1 }), TILE_SIZE, vi.fn()));
    sys.register(
      buildBulletinBoardTrigger({
        tileX: 1,
        tileY: 1,
        tileSize: TILE_SIZE,
        onActivate: vi.fn(),
      }),
    );

    const cx = TILE_SIZE + TILE_SIZE / 2;
    const cy = TILE_SIZE + TILE_SIZE / 2;
    sys.update(cx, cy, false);
    expect(sys.getActive()).toHaveLength(2);

    sys.clear();
    expect(sys.getActive()).toHaveLength(0);
    expect(hide).toHaveBeenCalled();
  });
});

describe('WorldScene.directionDelta / toCardinal (existing helpers; sanity)', () => {
  it('directionDelta covers 4-card + diagonals', () => {
    expect(directionDelta('UP')).toEqual({ dx: 0, dy: -1 });
    expect(directionDelta('UP_RIGHT')).toEqual({ dx: 1, dy: -1 });
    expect(directionDelta('DOWN_LEFT')).toEqual({ dx: -1, dy: 1 });
    expect(directionDelta('')).toEqual({ dx: 0, dy: 0 });
  });

  it('toCardinal degrades diagonals to LEFT/RIGHT priority then UP/DOWN', () => {
    expect(toCardinal('UP_RIGHT', 'DOWN')).toBe('RIGHT');
    expect(toCardinal('UP_LEFT', 'DOWN')).toBe('LEFT');
    expect(toCardinal('UP', 'DOWN')).toBe('UP');
    expect(toCardinal('NONE' as unknown as string, 'DOWN')).toBe('DOWN');
  });
});
