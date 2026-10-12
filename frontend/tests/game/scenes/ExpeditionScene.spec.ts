import { afterEach, describe, expect, it, vi } from 'vitest';
import type { RogueMap } from '@/types/rogue';
import {
  EXPEDITION_SCENE_KEY,
  ExpeditionScene,
  __resetExpeditionForTests,
  ensureExpeditionSceneStarted,
  getExpeditionBridge,
  loadExpedition,
  stopExpeditionScene,
  type ExpeditionSceneContext,
} from '@/game/scenes/ExpeditionScene';

type Spy = ReturnType<typeof vi.fn>;

function chain(methods: readonly string[]): Record<string, Spy> {
  const obj: Record<string, Spy> = {};
  for (const m of methods) obj[m] = vi.fn(() => obj);
  obj.destroy = vi.fn();
  return obj;
}

const GRAPHICS_METHODS = [
  'clear',
  'fillStyle',
  'fillRect',
  'fillRoundedRect',
  'lineStyle',
  'lineBetween',
  'strokeRoundedRect',
  'setDepth',
  'setAlpha',
] as const;
const TEXT_METHODS = [
  'setOrigin',
  'setDepth',
  'setColor',
  'setText',
  'setPosition',
  'setAlpha',
  'setInteractive',
  'on',
  'destroy',
] as const;

interface FakeCtx extends ExpeditionSceneContext {
  time: { now: number };
  _spies: {
    graphics: Spy;
    text: Spy;
    removeAll: Spy;
    eventsOn: Spy;
    scaleOn: Spy;
  };
}

function makeCtx(width = 960, height = 640): FakeCtx {
  const graphics = vi.fn(() => chain(GRAPHICS_METHODS));
  const text = vi.fn(() => chain(TEXT_METHODS));
  const removeAll = vi.fn();
  const eventsOn = vi.fn();
  const scaleOn = vi.fn();
  const ctx = {
    add: {
      graphics,
      text: (x: number, y: number, str: string, style?: Record<string, unknown>) =>
        text(x, y, str, style),
    },
    tweens: { add: vi.fn(), killTweensOf: vi.fn() },
    scale: { width, height, on: scaleOn },
    time: { now: 0 },
    events: { on: eventsOn },
    children: { removeAll },
    _spies: { graphics, text, removeAll, eventsOn, scaleOn },
  };
  return ctx as unknown as FakeCtx;
}

const MAP: RogueMap = {
  layers: 3,
  nodes: [
    {
      id: 'L0-0',
      layer: 0,
      col: 0,
      kind: 'battle',
      kcCode: 'PY43-KC-001',
      challengeKind: 'dual_track',
      difficulty: 2,
      next: ['L1-0', 'L1-1'],
      state: 'cleared',
    },
    {
      id: 'L1-0',
      layer: 1,
      col: 0,
      kind: 'event',
      kcCode: 'PY43-KC-002',
      challengeKind: 'short_answer',
      difficulty: 2,
      next: ['L2-0'],
      state: 'available',
    },
    {
      id: 'L1-1',
      layer: 1,
      col: 1,
      kind: 'chest',
      kcCode: null,
      challengeKind: null,
      difficulty: 0,
      next: ['L2-0'],
      state: 'bypassed',
    },
    {
      id: 'L2-0',
      layer: 2,
      col: 0,
      kind: 'boss',
      kcCode: 'PY43-KC-007',
      challengeKind: 'multi_round',
      difficulty: 4,
      next: [],
      state: 'locked',
    },
  ],
};

afterEach(() => {
  __resetExpeditionForTests();
});

describe('ExpeditionScene · build', () => {
  it('renders the expedition tower from rogueRun nodes and state', () => {
    loadExpedition(MAP, { currentNodeId: 'L1-0', phase: 'map', hp: 4, maxHp: 5, score: 120 });
    const ctx = makeCtx();

    expect(() => ExpeditionScene.create.call(ctx)).not.toThrow();

    expect(ctx._spies.removeAll).toHaveBeenCalledWith(true);
    expect(ctx._spies.graphics).toHaveBeenCalled();
    expect(ctx._spies.graphics.mock.results[0]?.value.fillStyle).toHaveBeenCalledWith(0xf7f1dc, 1);
    expect(ctx._spies.text).toHaveBeenCalledWith(
      expect.any(Number),
      expect.any(Number),
      expect.stringContaining('无尽远征'),
      expect.any(Object),
    );
    expect(ctx._spies.text).toHaveBeenCalledWith(
      expect.any(Number),
      expect.any(Number),
      expect.stringContaining('BOSS'),
      expect.any(Object),
    );
    expect(ctx._spies.eventsOn).toHaveBeenCalledWith('shutdown', expect.any(Function));
    expect(ctx._spies.scaleOn).toHaveBeenCalledWith('resize', expect.any(Function));
  });

  it('rebuilds the tower when a new run state is loaded while mounted', () => {
    loadExpedition(MAP, { currentNodeId: 'L1-0', phase: 'map' });
    const ctx = makeCtx();
    ExpeditionScene.create.call(ctx);
    ctx._spies.removeAll.mockClear();

    loadExpedition(
      { ...MAP, nodes: MAP.nodes.map((n) => (n.id === 'L2-0' ? { ...n, state: 'cleared' } : n)) },
      { currentNodeId: 'L2-0', phase: 'cleared' },
    );

    expect(ctx._spies.removeAll).toHaveBeenCalledWith(true);
    expect(ctx._spies.text).toHaveBeenCalledWith(
      expect.any(Number),
      expect.any(Number),
      expect.stringContaining('通关'),
      expect.any(Object),
    );
  });
});

describe('ExpeditionScene · bridge', () => {
  it('routes pixel-node selection through the module bridge callback', () => {
    const onNodeClick = vi.fn();
    loadExpedition(MAP, { currentNodeId: 'L1-0', phase: 'map' }, { onNodeClick });
    const ctx = makeCtx();
    ExpeditionScene.create.call(ctx);

    getExpeditionBridge().selectNode('L1-0');

    expect(onNodeClick).toHaveBeenCalledWith('L1-0');
  });

  it('ignores locked pixel-node selection', () => {
    const onNodeClick = vi.fn();
    loadExpedition(MAP, { currentNodeId: 'L1-0', phase: 'map' }, { onNodeClick });
    ExpeditionScene.create.call(makeCtx());

    getExpeditionBridge().selectNode('L2-0');

    expect(onNodeClick).not.toHaveBeenCalled();
  });

  it('routes the current node from the map so an interrupted run can be resumed', () => {
    const onNodeClick = vi.fn();
    const currentNodeId = 'L1-0';
    loadExpedition(
      {
        ...MAP,
        nodes: MAP.nodes.map((node) => node.id === currentNodeId ? { ...node, state: 'current' } : node),
      },
      { currentNodeId, phase: 'map' },
      { onNodeClick },
    );
    const ctx = makeCtx();
    ExpeditionScene.create.call(ctx);

    getExpeditionBridge().selectNode(currentNodeId);

    expect(onNodeClick).toHaveBeenCalledWith(currentNodeId);
    const labelIndex = ctx._spies.text.mock.calls.findIndex(([, , label]) => label === '❓ 概念考点');
    expect(ctx._spies.text.mock.results[labelIndex]?.value.setInteractive).toHaveBeenCalled();
  });
});

describe('ExpeditionScene · singleton mounting', () => {
  it('registers + starts via the existing game scene manager without new Phaser.Game', () => {
    const add = vi.fn();
    const bringToTop = vi.fn();
    ensureExpeditionSceneStarted({ scene: { add, bringToTop, keys: {} } });
    expect(add).toHaveBeenCalledWith(EXPEDITION_SCENE_KEY, ExpeditionScene, true);
    expect(bringToTop).toHaveBeenCalledWith(EXPEDITION_SCENE_KEY);
  });

  it('starts an already registered scene and guards empty handles', () => {
    const add = vi.fn();
    const start = vi.fn();
    ensureExpeditionSceneStarted({
      scene: { add, start, keys: { [EXPEDITION_SCENE_KEY]: {} } },
    });
    expect(add).not.toHaveBeenCalled();
    expect(start).toHaveBeenCalledWith(EXPEDITION_SCENE_KEY);

    expect(() => ensureExpeditionSceneStarted(null)).not.toThrow();
    expect(() => stopExpeditionScene(null)).not.toThrow();

    const stop = vi.fn();
    stopExpeditionScene({ scene: { stop } });
    expect(stop).toHaveBeenCalledWith(EXPEDITION_SCENE_KEY);
  });
});
