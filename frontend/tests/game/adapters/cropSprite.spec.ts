import { describe, expect, it } from 'vitest';
import { createCropSprite } from '@game/adapters/cropSprite';

interface MockRect {
  fillColor: number;
  fillAlpha: number;
  setStrokeStyle: (w: number, c: number) => MockRect;
  setInteractive: () => MockRect;
  on: () => MockRect;
  setDepth: () => MockRect;
  setAlpha: (a: number) => MockRect;
  x: number;
  y: number;
}

interface MockText {
  content: string;
  setOrigin: () => MockText;
  setDepth: () => MockText;
  setText: (v: string) => MockText;
}

function createScene(): { scene: unknown; rects: MockRect[]; texts: MockText[] } {
  const rects: MockRect[] = [];
  const texts: MockText[] = [];
  const add = {
    rectangle(x: number, y: number, _w: number, _h: number, color: number, alpha?: number) {
      const r: MockRect = {
        x,
        y,
        fillColor: color,
        fillAlpha: alpha ?? 1,
        setStrokeStyle() { return r; },
        setInteractive() { return r; },
        on() { return r; },
        setDepth() { return r; },
        setAlpha(a: number) { r.fillAlpha = a; return r; },
      };
      rects.push(r);
      return r;
    },
    text(_x: number, _y: number, value: string) {
      const t: MockText = {
        content: value,
        setOrigin() { return t; },
        setDepth() { return t; },
        setText(v: string) { t.content = v; return t; },
      };
      texts.push(t);
      return t;
    },
    image() {
      const img = { setOrigin: () => img, setDepth: () => img, setScale: () => img, setAlpha: () => img, x: 0, y: 0 };
      return img;
    },
    tileSprite() {
      return this.image();
    },
    grid() {
      return this.rectangle(0, 0, 0, 0, 0);
    },
  };
  const scene = {
    add,
    input: { keyboard: null },
    time: { now: 0 },
    cameras: { main: { startFollow() {}, setBounds() {} } },
  };
  return { scene, rects, texts };
}

describe('cropSprite adapter', () => {
  it('renders a SEED tile with seed symbol', () => {
    const { scene, texts } = createScene();
    const handle = createCropSprite(scene as never, {
      tileX: 0,
      tileY: 0,
      tileSize: 32,
      status: 'SEED',
      kcCode: 'PY43-KC-001',
    });
    expect(handle.status).toBe('SEED');
    expect(texts[0]?.content).toBe('·');
  });

  it('updates from SEED to SPROUT and changes label', () => {
    const { scene, texts } = createScene();
    const handle = createCropSprite(scene as never, {
      tileX: 0,
      tileY: 0,
      tileSize: 32,
      status: 'SEED',
      kcCode: 'PY43-KC-001',
    });
    handle.setStatus('SPROUT');
    expect(handle.status).toBe('SPROUT');
    expect(texts[0]?.content).toBe('∴');
  });
});
