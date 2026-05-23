import { describe, expect, it } from 'vitest';
import { createGamepadAdapter } from '@/game/adapters/gamepadAdapter';

interface FakeButton {
  pressed: boolean;
}

interface FakeGamepad {
  connected: boolean;
  axes: readonly number[];
  buttons: readonly FakeButton[];
}

function makeButtons(pressed: Partial<Record<number, boolean>> = {}): FakeButton[] {
  const list: FakeButton[] = [];
  for (let i = 0; i < 17; i += 1) {
    list.push({ pressed: pressed[i] === true });
  }
  return list;
}

function makeGamepad(opts: {
  axes?: readonly number[];
  buttons?: Partial<Record<number, boolean>>;
  connected?: boolean;
}): FakeGamepad {
  return {
    connected: opts.connected ?? true,
    axes: opts.axes ?? [0, 0, 0, 0],
    buttons: makeButtons(opts.buttons),
  };
}

function makeGamepadSource(gp: FakeGamepad | null): () => readonly (Gamepad | null)[] {
  return () => [gp as unknown as Gamepad | null];
}

describe('createGamepadAdapter', () => {
  it('returns empty intent when no gamepad is connected', () => {
    const adapter = createGamepadAdapter({ getGamepads: () => [null, null] });
    const intent = adapter.poll();
    expect(intent.move).toEqual({ dx: 0, dy: 0 });
    expect(intent.direction).toBeNull();
    expect(intent.actionPressed).toBe(false);
    expect(intent.cancelPressed).toBe(false);
    expect(intent.run).toBe(false);
  });

  it('applies a 0.15 magnitude deadzone to the left stick', () => {
    const tinyDrift = makeGamepad({ axes: [0.1, 0.05, 0, 0] });
    const adapter = createGamepadAdapter({
      deadzone: 0.15,
      getGamepads: makeGamepadSource(tinyDrift),
    });
    const intent = adapter.poll();
    expect(intent.move).toEqual({ dx: 0, dy: 0 });
    expect(intent.direction).toBeNull();
  });

  it('passes through axes outside the deadzone and discretises 8 directions', () => {
    const gp = makeGamepad({ axes: [0.8, 0, 0, 0] });
    const adapter = createGamepadAdapter({
      deadzone: 0.15,
      getGamepads: makeGamepadSource(gp),
    });
    const intent = adapter.poll();
    expect(intent.move.dx).toBeCloseTo(0.8, 5);
    expect(intent.move.dy).toBeCloseTo(0, 5);
    expect(intent.direction).toBe('E');
  });

  it('emits A button as one-shot actionPressed (edge triggered)', () => {
    let pressed = true;
    const adapter = createGamepadAdapter({
      getGamepads: () => [
        makeGamepad({ buttons: { 0: pressed } }) as unknown as Gamepad,
      ],
    });

    const firstFrame = adapter.poll();
    expect(firstFrame.actionPressed).toBe(true);

    const secondFrame = adapter.poll();
    expect(secondFrame.actionPressed).toBe(false);

    pressed = false;
    const thirdFrame = adapter.poll();
    expect(thirdFrame.actionPressed).toBe(false);

    pressed = true;
    const fourthFrame = adapter.poll();
    expect(fourthFrame.actionPressed).toBe(true);
  });

  it('emits B button as one-shot cancelPressed (edge triggered)', () => {
    let pressed = true;
    const adapter = createGamepadAdapter({
      getGamepads: () => [
        makeGamepad({ buttons: { 1: pressed } }) as unknown as Gamepad,
      ],
    });

    const firstFrame = adapter.poll();
    expect(firstFrame.cancelPressed).toBe(true);

    const secondFrame = adapter.poll();
    expect(secondFrame.cancelPressed).toBe(false);

    pressed = false;
    adapter.poll();
    pressed = true;
    const reArmed = adapter.poll();
    expect(reArmed.cancelPressed).toBe(true);
  });

  it('treats right stick click (button 11) as run modifier', () => {
    const gp = makeGamepad({ buttons: { 11: true }, axes: [0.5, 0, 0, 0] });
    const adapter = createGamepadAdapter({
      getGamepads: makeGamepadSource(gp),
    });
    const intent = adapter.poll();
    expect(intent.run).toBe(true);
    expect(intent.move.dx).toBeCloseTo(0.5, 5);
  });

  it('selects the first connected gamepad and ignores disconnected slots', () => {
    const disconnected = makeGamepad({ axes: [0.9, 0, 0, 0], connected: false });
    const active = makeGamepad({ axes: [-0.7, 0.7, 0, 0], connected: true });
    const adapter = createGamepadAdapter({
      deadzone: 0.15,
      getGamepads: () => [
        disconnected as unknown as Gamepad,
        active as unknown as Gamepad,
        null,
      ],
    });
    const intent = adapter.poll();
    expect(intent.move.dx).toBeCloseTo(-0.7, 5);
    expect(intent.move.dy).toBeCloseTo(0.7, 5);
    expect(intent.direction).toBe('SW');
  });

  it('resets edge-trigger state when the gamepad disconnects', () => {
    let pressed = true;
    let active = true;
    const adapter = createGamepadAdapter({
      getGamepads: () => {
        if (!active) {
          return [null];
        }
        return [makeGamepad({ buttons: { 0: pressed } }) as unknown as Gamepad];
      },
    });

    expect(adapter.poll().actionPressed).toBe(true);
    pressed = true;
    expect(adapter.poll().actionPressed).toBe(false);

    active = false;
    adapter.poll();

    active = true;
    pressed = true;
    expect(adapter.poll().actionPressed).toBe(true);
  });
});
