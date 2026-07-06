import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import MiniMap from '@/components/hud/MiniMap.vue';

interface FillRectCall {
  x: number;
  y: number;
  w: number;
  h: number;
  fillStyle: string;
}

interface MockCanvasContext {
  fillRect: ReturnType<typeof vi.fn>;
  calls: FillRectCall[];
}

let mockCtx: MockCanvasContext;
let getContextSpy: ReturnType<typeof vi.spyOn>;

function buildMockContext(): MockCanvasContext {
  let currentFillStyle = '';
  const calls: FillRectCall[] = [];
  const fillRect = vi.fn((x: number, y: number, w: number, h: number) => {
    calls.push({ x, y, w, h, fillStyle: currentFillStyle });
  });
  const ctx = {
    fillRect,
    calls,
    get fillStyle(): string {
      return currentFillStyle;
    },
    set fillStyle(value: string) {
      currentFillStyle = value;
    },
  };
  return ctx as unknown as MockCanvasContext;
}

const sampleTilemap = {
  width: 4,
  height: 4,
  ground: [
    [1, 1, 1, 1],
    [1, 1, 1, 1],
    [1, 1, 1, 1],
    [1, 1, 1, 1],
  ],
  collision: [
    [false, false, false, false],
    [false, true, true, false],
    [false, true, true, false],
    [false, false, false, false],
  ],
};

const PLAYER_COLOR = '#e63946';
const NPC_COLOR = '#ffd166';

beforeEach(() => {
  mockCtx = buildMockContext();
  getContextSpy = vi
    .spyOn(HTMLCanvasElement.prototype, 'getContext')
    .mockImplementation(() => mockCtx as unknown as CanvasRenderingContext2D);
});

afterEach(() => {
  getContextSpy.mockRestore();
  vi.restoreAllMocks();
});

describe('MiniMap', () => {
  it('mounts canvas element when tilemap is provided', () => {
    const wrapper = mount(MiniMap, {
      props: {
        tilemap: sampleTilemap,
        playerX: 0,
        playerY: 0,
      },
    });
    const canvas = wrapper.find('[data-testid="mini-map-canvas"]');
    expect(canvas.exists()).toBe(true);
    expect(canvas.element.tagName).toBe('CANVAS');
    expect(wrapper.find('[data-testid="mini-map-empty"]').exists()).toBe(false);
  });

  it('applies width and height props to canvas element attributes', () => {
    const wrapper = mount(MiniMap, {
      props: {
        width: 160,
        height: 120,
        tilemap: sampleTilemap,
        playerX: 0,
        playerY: 0,
      },
    });
    const canvas = wrapper.find<HTMLCanvasElement>(
      '[data-testid="mini-map-canvas"]',
    );
    expect(canvas.attributes('width')).toBe('160');
    expect(canvas.attributes('height')).toBe('120');
  });

  it('draws the player marker at the scaled tile position with red color', () => {
    const width = 200;
    const height = 200;
    const playerX = 2;
    const playerY = 1;
    mount(MiniMap, {
      props: {
        width,
        height,
        tilemap: sampleTilemap,
        playerX,
        playerY,
      },
    });

    const scaleX = width / sampleTilemap.width;
    const scaleY = height / sampleTilemap.height;
    const expectedX = Math.floor(playerX * scaleX);
    const expectedY = Math.floor(playerY * scaleY);
    const playerCall = mockCtx.calls.find(
      (call) =>
        call.fillStyle === PLAYER_COLOR &&
        call.x === expectedX &&
        call.y === expectedY,
    );
    expect(playerCall).toBeDefined();
    expect(playerCall?.w).toBe(3);
    expect(playerCall?.h).toBe(3);
  });

  it('renders one yellow rect per NPC marker', () => {
    const width = 200;
    const height = 200;
    const npcs = [
      { id: 'mayor', x: 1, y: 1 },
      { id: 'farmer', x: 3, y: 2 },
      { id: 'teacher', x: 0, y: 3 },
    ];
    mount(MiniMap, {
      props: {
        width,
        height,
        tilemap: sampleTilemap,
        playerX: 0,
        playerY: 0,
        npcs,
      },
    });

    const npcCalls = mockCtx.calls.filter(
      (call) => call.fillStyle === NPC_COLOR,
    );
    expect(npcCalls).toHaveLength(npcs.length);

    const scaleX = width / sampleTilemap.width;
    const scaleY = height / sampleTilemap.height;
    for (const npc of npcs) {
      const ex = Math.floor(npc.x * scaleX);
      const ey = Math.floor(npc.y * scaleY);
      const match = npcCalls.find((call) => call.x === ex && call.y === ey);
      expect(match, `expected NPC ${npc.id} drawn at (${ex},${ey})`).toBeDefined();
    }
  });

  it('shows fallback placeholder text when tilemap prop is missing', () => {
    const wrapper = mount(MiniMap, {
      props: {
        playerX: 0,
        playerY: 0,
      },
    });
    expect(wrapper.find('[data-testid="mini-map-canvas"]').exists()).toBe(false);
    const empty = wrapper.find('[data-testid="mini-map-empty"]');
    expect(empty.exists()).toBe(true);
    expect(empty.text()).toContain('无地图数据');
  });

  it('re-draws the canvas when playerX changes (watcher path)', async () => {
    const width = 200;
    const height = 200;
    const wrapper = mount(MiniMap, {
      props: {
        width,
        height,
        tilemap: sampleTilemap,
        playerX: 0,
        playerY: 0,
      },
    });

    const initialPlayerCalls = mockCtx.calls.filter(
      (call) => call.fillStyle === PLAYER_COLOR,
    ).length;
    expect(initialPlayerCalls).toBeGreaterThan(0);

    await wrapper.setProps({ playerX: 3 });
    await flushPromises();

    const scaleX = width / sampleTilemap.width;
    const expectedX = Math.floor(3 * scaleX);
    const playerCalls = mockCtx.calls.filter(
      (call) => call.fillStyle === PLAYER_COLOR,
    );
    expect(playerCalls.length).toBeGreaterThan(initialPlayerCalls);
    const lastPlayerCall = playerCalls[playerCalls.length - 1];
    expect(lastPlayerCall?.x).toBe(expectedX);
  });

  // R-13 增强：地图名 + 出口方向显示，不破坏既有 6 个用例。
  it('R-13: renders map name label when mapName prop is provided', () => {
    const wrapper = mount(MiniMap, {
      props: {
        tilemap: sampleTilemap,
        playerX: 0,
        playerY: 0,
        mapName: 'Pythontopia 小镇',
      },
    });
    const title = wrapper.find('[data-testid="mini-map-title"]');
    expect(title.exists()).toBe(true);
    expect(title.text()).toBe('Pythontopia 小镇');
  });

  it('R-13: omits map name label when mapName is empty (back-compat)', () => {
    const wrapper = mount(MiniMap, {
      props: {
        tilemap: sampleTilemap,
        playerX: 0,
        playerY: 0,
      },
    });
    expect(wrapper.find('[data-testid="mini-map-title"]').exists()).toBe(false);
  });

  it('R-13: renders one exit arrow per direction with arrow + tooltip + aria-label', () => {
    const wrapper = mount(MiniMap, {
      props: {
        tilemap: sampleTilemap,
        playerX: 0,
        playerY: 0,
        mapName: 'town_hub',
        exits: [
          { side: 'south', toMap: 'mine_south', label: 'Bug 矿洞' },
        ],
      },
    });
    const south = wrapper.find('[data-testid="mini-map-exit-south"]');
    expect(south.exists()).toBe(true);
    expect(wrapper.find('[data-testid="mini-map-exit-north"]').exists()).toBe(false);
    expect(south.text()).toBe('▼');
    expect(south.attributes('title')).toContain('Bug 矿洞');
    expect(south.attributes('aria-label')).toContain('south');
  });
});
