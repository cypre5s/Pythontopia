import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createTouchAdapter } from '@/game/adapters/touchAdapter';

interface PointerLike {
  id: number;
  x: number;
  y: number;
}

type PointerHandler = (pointer: PointerLike) => void;

interface MockSceneHandle {
  scene: Parameters<ReturnType<typeof createTouchAdapter>['attach']>[0];
  handlers: Map<string, PointerHandler>;
  onSpy: ReturnType<typeof vi.fn>;
  offSpy: ReturnType<typeof vi.fn>;
}

function createMockScene(): MockSceneHandle {
  const handlers = new Map<string, PointerHandler>();
  const onSpy = vi.fn((event: string, cb: PointerHandler): void => {
    handlers.set(event, cb);
  });
  const offSpy = vi.fn((event: string): void => {
    handlers.delete(event);
  });
  const scene = {
    input: {
      on: onSpy,
      off: offSpy,
    },
  } as unknown as Parameters<ReturnType<typeof createTouchAdapter>['attach']>[0];
  return { scene, handlers, onSpy, offSpy };
}

function fire(handle: MockSceneHandle, event: string, pointer: PointerLike): void {
  const cb = handle.handlers.get(event);
  if (cb) {
    cb(pointer);
  }
}

describe('createTouchAdapter', () => {
  let mock: MockSceneHandle;

  beforeEach(() => {
    mock = createMockScene();
  });

  it('returns empty intent when not a touch device', () => {
    const adapter = createTouchAdapter({ isTouchDevice: () => false });
    adapter.attach(mock.scene);
    const intent = adapter.poll();
    expect(intent.move).toEqual({ dx: 0, dy: 0 });
    expect(intent.direction).toBeNull();
    expect(intent.actionPressed).toBe(false);
  });

  it('registers pointerdown/pointermove/pointerup listeners on attach', () => {
    const adapter = createTouchAdapter({
      isTouchDevice: () => true,
      screenWidth: 800,
      joystickRadius: 60,
    });
    adapter.attach(mock.scene);
    expect(mock.onSpy).toHaveBeenCalledWith('pointerdown', expect.any(Function));
    expect(mock.onSpy).toHaveBeenCalledWith('pointermove', expect.any(Function));
    expect(mock.onSpy).toHaveBeenCalledWith('pointerup', expect.any(Function));
  });

  it('captures left-half pointerdown as joystick origin and outputs zero before move', () => {
    const adapter = createTouchAdapter({
      isTouchDevice: () => true,
      screenWidth: 800,
      joystickRadius: 60,
    });
    adapter.attach(mock.scene);

    fire(mock, 'pointerdown', { id: 1, x: 120, y: 300 });
    const intent = adapter.poll();
    expect(intent.move).toEqual({ dx: 0, dy: 0 });
    expect(intent.direction).toBeNull();
  });

  it('normalises joystick move to [-1, 1] relative to radius', () => {
    const adapter = createTouchAdapter({
      isTouchDevice: () => true,
      screenWidth: 800,
      joystickRadius: 60,
    });
    adapter.attach(mock.scene);

    fire(mock, 'pointerdown', { id: 1, x: 100, y: 200 });
    fire(mock, 'pointermove', { id: 1, x: 130, y: 200 });
    const intent = adapter.poll();
    expect(intent.move.dx).toBeCloseTo(0.5, 5);
    expect(intent.move.dy).toBeCloseTo(0, 5);
    expect(intent.direction).toBe('E');
  });

  it('clamps joystick magnitude to the configured radius', () => {
    const adapter = createTouchAdapter({
      isTouchDevice: () => true,
      screenWidth: 800,
      joystickRadius: 60,
    });
    adapter.attach(mock.scene);

    fire(mock, 'pointerdown', { id: 1, x: 100, y: 200 });
    fire(mock, 'pointermove', { id: 1, x: 100, y: 400 });
    const intent = adapter.poll();
    expect(intent.move.dx).toBeCloseTo(0, 5);
    expect(intent.move.dy).toBeCloseTo(1, 5);
    expect(intent.direction).toBe('S');
  });

  it('resets joystick to zero on pointerup', () => {
    const adapter = createTouchAdapter({
      isTouchDevice: () => true,
      screenWidth: 800,
      joystickRadius: 60,
    });
    adapter.attach(mock.scene);

    fire(mock, 'pointerdown', { id: 1, x: 100, y: 200 });
    fire(mock, 'pointermove', { id: 1, x: 130, y: 230 });
    fire(mock, 'pointerup', { id: 1, x: 130, y: 230 });

    const intent = adapter.poll();
    expect(intent.move).toEqual({ dx: 0, dy: 0 });
    expect(intent.direction).toBeNull();
  });

  it('treats right-half pointerdown as action and emits one-shot actionPressed', () => {
    const adapter = createTouchAdapter({
      isTouchDevice: () => true,
      screenWidth: 800,
      joystickRadius: 60,
    });
    adapter.attach(mock.scene);

    fire(mock, 'pointerdown', { id: 2, x: 700, y: 500 });
    const first = adapter.poll();
    expect(first.actionPressed).toBe(true);

    const second = adapter.poll();
    expect(second.actionPressed).toBe(false);

    fire(mock, 'pointerup', { id: 2, x: 700, y: 500 });
    const third = adapter.poll();
    expect(third.actionPressed).toBe(false);
  });

  it('cleans up listeners on detach', () => {
    const adapter = createTouchAdapter({
      isTouchDevice: () => true,
      screenWidth: 800,
      joystickRadius: 60,
    });
    adapter.attach(mock.scene);
    adapter.detach();
    expect(mock.offSpy).toHaveBeenCalledWith('pointerdown', expect.any(Function));
    expect(mock.offSpy).toHaveBeenCalledWith('pointermove', expect.any(Function));
    expect(mock.offSpy).toHaveBeenCalledWith('pointerup', expect.any(Function));
  });
});
