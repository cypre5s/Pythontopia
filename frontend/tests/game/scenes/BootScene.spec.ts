import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BootScene } from '@/game/scenes/BootScene';

type AnyFn = (...args: unknown[]) => unknown;

interface LoaderSpy {
  on: ReturnType<typeof vi.fn>;
  once: ReturnType<typeof vi.fn>;
  image: ReturnType<typeof vi.fn>;
  spritesheet: ReturnType<typeof vi.fn>;
  audio: ReturnType<typeof vi.fn>;
  json: ReturnType<typeof vi.fn>;
  tilemapTiledJSON: ReturnType<typeof vi.fn>;
  progressHandlers: Array<(value: number) => void>;
  onceHandlers: Map<string, AnyFn>;
  audioUrls: string[];
  imageUrls: string[];
  spritesheetKeys: string[];
}

function makeLoaderSpy(): LoaderSpy {
  const audioUrls: string[] = [];
  const imageUrls: string[] = [];
  const spritesheetKeys: string[] = [];
  const progressHandlers: Array<(value: number) => void> = [];
  const onceHandlers = new Map<string, AnyFn>();
  return {
    on: vi.fn((event: string, cb: (value: number) => void) => {
      if (event === 'progress') {
        progressHandlers.push(cb);
      }
    }),
    once: vi.fn((event: string, cb: AnyFn) => {
      onceHandlers.set(event, cb);
    }),
    image: vi.fn((_key: string, url: string) => {
      imageUrls.push(url);
    }),
    spritesheet: vi.fn((key: string, url: string) => {
      spritesheetKeys.push(key);
      imageUrls.push(url);
    }),
    audio: vi.fn((_key: string, url: string | string[]) => {
      if (Array.isArray(url)) {
        audioUrls.push(...url);
      } else {
        audioUrls.push(url);
      }
    }),
    json: vi.fn(),
    tilemapTiledJSON: vi.fn(),
    progressHandlers,
    onceHandlers,
    audioUrls,
    imageUrls,
    spritesheetKeys,
  };
}

function expectPublicImageAsset(url: string): void {
  const filePath = join(process.cwd(), 'public', url);
  expect(existsSync(filePath), `${url} should exist`).toBe(true);
  if (url.endsWith('.png')) {
    const signature = readFileSync(filePath).subarray(0, 8).toString('hex');
    expect(signature, `${url} should be a PNG`).toBe('89504e470d0a1a0a');
  } else if (url.endsWith('.svg')) {
    const head = readFileSync(filePath, 'utf-8').trimStart().slice(0, 5);
    expect(head, `${url} should be an SVG`).toBe('<svg ');
  }
}

interface GraphicsSpy {
  fillStyle: ReturnType<typeof vi.fn>;
  fillRect: ReturnType<typeof vi.fn>;
  clear: ReturnType<typeof vi.fn>;
  setDepth: ReturnType<typeof vi.fn>;
}

function makeGraphics(): GraphicsSpy {
  const g: GraphicsSpy = {
    fillStyle: vi.fn(() => g),
    fillRect: vi.fn(() => g),
    clear: vi.fn(() => g),
    setDepth: vi.fn(() => g),
  };
  return g;
}

interface TextSpy {
  setOrigin: ReturnType<typeof vi.fn>;
  setDepth: ReturnType<typeof vi.fn>;
  setText: ReturnType<typeof vi.fn>;
  destroy: ReturnType<typeof vi.fn>;
}

function makeText(): TextSpy {
  const t: TextSpy = {
    setOrigin: vi.fn(() => t),
    setDepth: vi.fn(() => t),
    setText: vi.fn(() => t),
    destroy: vi.fn(),
  };
  return t;
}

describe('BootScene asset manifest', () => {
  it('loads only existing public audio assets', () => {
    const loader = makeLoaderSpy();

    BootScene.preload.call({ load: loader });

    expect(loader.audioUrls.length).toBeGreaterThan(0);
    for (const url of loader.audioUrls) {
      expect(existsSync(join(process.cwd(), 'public', url))).toBe(true);
    }
  });

  it('loads only existing public image assets with valid file signatures', () => {
    const loader = makeLoaderSpy();

    BootScene.preload.call({ load: loader });

    expect(loader.imageUrls.length).toBeGreaterThan(0);
    for (const url of loader.imageUrls) {
      expectPublicImageAsset(url);
    }
  });

  it('preloads dedicated low obstacle fence spritesheet for jumpable path props', () => {
    const loader = makeLoaderSpy();

    BootScene.preload.call({ load: loader });

    expect(loader.spritesheetKeys).toContain('low_fence_16');
    expect(loader.imageUrls).toContain('/assets/pixel/props/fantastic-fence/fence_00.png');
  });
});

describe('BootScene loading UX', () => {
  let dispatchedEvents: Array<{ type: string; detail: unknown }>;
  let origDispatch: typeof window.dispatchEvent;

  beforeEach(() => {
    dispatchedEvents = [];
    origDispatch = window.dispatchEvent.bind(window);
    window.dispatchEvent = vi.fn((event: Event) => {
      const detail =
        event instanceof CustomEvent ? (event as CustomEvent<unknown>).detail : undefined;
      dispatchedEvents.push({ type: event.type, detail });
      return true;
    }) as typeof window.dispatchEvent;
  });

  afterEach(() => {
    window.dispatchEvent = origDispatch;
    vi.useRealTimers();
  });

  it('draws a progress bar via add.graphics and updates it when loader fires progress', () => {
    vi.useFakeTimers();
    const loader = makeLoaderSpy();
    const bgGraphics = makeGraphics();
    const fillGraphics = makeGraphics();
    const graphicsResults = [bgGraphics, fillGraphics];
    let graphicsCallCount = 0;
    const text = makeText();

    const add = {
      graphics: vi.fn(() => {
        const g = graphicsResults[graphicsCallCount] ?? fillGraphics;
        graphicsCallCount += 1;
        return g;
      }),
      text: vi.fn(() => text),
    };

    BootScene.preload.call({
      load: loader,
      add,
      scale: { width: 960, height: 640 },
    });

    expect(add.graphics).toHaveBeenCalledTimes(2);
    expect(bgGraphics.fillRect).toHaveBeenCalledTimes(1);
    expect(loader.progressHandlers.length).toBe(1);

    const handler = loader.progressHandlers[0];
    expect(handler).toBeTypeOf('function');
    handler?.(0.5);

    expect(fillGraphics.clear).toHaveBeenCalledTimes(1);
    expect(fillGraphics.fillStyle).toHaveBeenCalledWith(0xffd166, 1);
    const fillCalls = fillGraphics.fillRect.mock.calls;
    expect(fillCalls.length).toBe(1);
    const firstCall = fillCalls[0] as [number, number, number, number];
    const widthArg = firstCall[2];
    expect(widthArg).toBeGreaterThan(0);

    handler?.(1);
    const secondCall = fillGraphics.fillRect.mock.calls[1] as
      | [number, number, number, number]
      | undefined;
    expect(secondCall).toBeDefined();
    const fullWidth = secondCall ? secondCall[2] : 0;
    expect(fullWidth).toBeGreaterThan(widthArg);
  });

  it('dispatches pythontopia:load-timeout after 15s when load.once("complete") never fires', () => {
    vi.useFakeTimers();
    const loader = makeLoaderSpy();
    const bgGraphics = makeGraphics();
    const fillGraphics = makeGraphics();
    const graphicsResults = [bgGraphics, fillGraphics];
    let graphicsCallCount = 0;
    const text = makeText();
    const add = {
      graphics: vi.fn(() => {
        const g = graphicsResults[graphicsCallCount] ?? fillGraphics;
        graphicsCallCount += 1;
        return g;
      }),
      text: vi.fn(() => text),
    };

    BootScene.preload.call({
      load: loader,
      add,
      scale: { width: 960, height: 640 },
    });

    expect(loader.onceHandlers.has('complete')).toBe(true);
    expect(loader.onceHandlers.has('loaderror')).toBe(true);

    vi.advanceTimersByTime(14_999);
    expect(dispatchedEvents.find((e) => e.type === 'pythontopia:load-timeout')).toBeUndefined();

    vi.advanceTimersByTime(2);
    const timeoutEvent = dispatchedEvents.find((e) => e.type === 'pythontopia:load-timeout');
    expect(timeoutEvent).toBeDefined();
    expect(timeoutEvent?.detail).toMatchObject({ elapsedMs: 15_000 });
    expect(text.setText).toHaveBeenCalledWith(expect.stringContaining('请稍候'));
  });

  it('does NOT dispatch timeout event if load completes before the timer fires', () => {
    vi.useFakeTimers();
    const loader = makeLoaderSpy();
    const add = {
      graphics: vi.fn(() => makeGraphics()),
      text: vi.fn(() => makeText()),
    };

    BootScene.preload.call({
      load: loader,
      add,
      scale: { width: 960, height: 640 },
    });

    vi.advanceTimersByTime(5_000);
    const complete = loader.onceHandlers.get('complete');
    expect(complete).toBeTypeOf('function');
    complete?.();

    vi.advanceTimersByTime(20_000);

    expect(dispatchedEvents.find((e) => e.type === 'pythontopia:load-timeout')).toBeUndefined();
  });

  it('dispatches pythontopia:load-error when loader.once("loaderror") triggers, exposing src', () => {
    vi.useFakeTimers();
    const loader = makeLoaderSpy();
    const add = {
      graphics: vi.fn(() => makeGraphics()),
      text: vi.fn(() => makeText()),
    };

    BootScene.preload.call({
      load: loader,
      add,
      scale: { width: 960, height: 640 },
    });

    const errorCb = loader.onceHandlers.get('loaderror');
    expect(errorCb).toBeTypeOf('function');
    errorCb?.({ src: '/assets/pixel/sfx/missing.ogg' });

    const errorEvent = dispatchedEvents.find((e) => e.type === 'pythontopia:load-error');
    expect(errorEvent).toBeDefined();
    expect(errorEvent?.detail).toMatchObject({ src: '/assets/pixel/sfx/missing.ogg' });
  });

  it('skips timeout/progress UI gracefully when loader has no once() and scene has no add', () => {
    const loader = makeLoaderSpy();
    const slimLoader: Record<string, unknown> = {
      on: loader.on,
      image: loader.image,
      spritesheet: loader.spritesheet,
      audio: loader.audio,
    };

    expect(() => BootScene.preload.call({ load: slimLoader })).not.toThrow();

    expect(loader.image).toHaveBeenCalled();
    expect(loader.audio).toHaveBeenCalled();
  });
});

describe('BootScene create() lifecycle wiring', () => {
  it('attaches the SceneLifecycle hooks (events.on shutdown + destroy) before starting WorldScene', async () => {
    const start = vi.fn();
    const eventsOn = vi.fn();
    const eventsOff = vi.fn();

    const ctx = {
      scene: { start },
      events: { on: eventsOn, off: eventsOff },
      tweens: { killAll: vi.fn() },
      time: { removeAllEvents: vi.fn() },
      input: { removeAllListeners: vi.fn() },
    };

    BootScene.create.call(ctx);

    // 生命周期钩子在 create() 内同步挂载，先于异步进入世界。
    expect(eventsOn).toHaveBeenCalledWith('shutdown', expect.any(Function));
    expect(eventsOn).toHaveBeenCalledWith('destroy', expect.any(Function));
    // 字体竞态修复后 create() 改为 await ensurePixelFontLoaded() 再 scene.start，
    // 故 start 在字体 Promise 落定的微任务里调用，需 waitFor 等待。
    await vi.waitFor(() => {
      expect(start).toHaveBeenCalledWith('WorldScene', { mapKey: 'town_hub' });
    });
  });

  it('emits a final progress=1 event so the outer WorldCanvas progress bar can fade out', () => {
    const events: Array<{ type: string; detail: unknown }> = [];
    const origDispatch = window.dispatchEvent.bind(window);
    window.dispatchEvent = vi.fn((event: Event) => {
      const detail =
        event instanceof CustomEvent ? (event as CustomEvent<unknown>).detail : undefined;
      events.push({ type: event.type, detail });
      return true;
    }) as typeof window.dispatchEvent;

    try {
      BootScene.create.call({
        scene: { start: vi.fn() },
        events: { on: vi.fn(), off: vi.fn() },
        tweens: { killAll: vi.fn() },
        time: { removeAllEvents: vi.fn() },
        input: { removeAllListeners: vi.fn() },
      });
    } finally {
      window.dispatchEvent = origDispatch;
    }

    const finalProgress = events.find((e) => e.type === 'pythontopia:load-progress');
    expect(finalProgress?.detail).toMatchObject({ progress: 1 });
  });
});
