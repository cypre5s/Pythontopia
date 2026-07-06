import { describe, expect, it, vi } from 'vitest';
import { PlayerStateMachine } from '@/game/core/PlayerStateMachine';
import type { PlayerState, PlayerStateChangeEvent } from '@/game/core/PlayerStateMachine';

describe('PlayerStateMachine', () => {
  describe('initial state', () => {
    it('defaults to idle facing south (S)', () => {
      const sm = new PlayerStateMachine();
      expect(sm.state).toBe('idle');
      expect(sm.direction).toBe('S');
    });

    it('accepts a custom full initial snapshot', () => {
      const sm = new PlayerStateMachine({ state: 'walk', direction: 'NE' });
      expect(sm.state).toBe('walk');
      expect(sm.direction).toBe('NE');
    });

    it('accepts partial initial, filling missing fields with defaults', () => {
      const sm = new PlayerStateMachine({ state: 'run' });
      expect(sm.state).toBe('run');
      expect(sm.direction).toBe('S');
    });
  });

  describe('snapshot', () => {
    it('returns an independent object that does not mutate the machine when changed', () => {
      const sm = new PlayerStateMachine({ state: 'walk', direction: 'E' });
      const snap = sm.snapshot();
      snap.state = 'mine';
      snap.direction = 'NW';
      expect(sm.state).toBe('walk');
      expect(sm.direction).toBe('E');
    });
  });

  describe('transition', () => {
    it('walk -> run is legal and updates state', () => {
      const sm = new PlayerStateMachine({ state: 'walk' });
      const ok = sm.transition('run');
      expect(ok).toBe(true);
      expect(sm.state).toBe('run');
    });

    it('water -> walk is illegal, returns false and leaves state as water', () => {
      const sm = new PlayerStateMachine({ state: 'water', direction: 'S' });
      const ok = sm.transition('walk');
      expect(ok).toBe(false);
      expect(sm.state).toBe('water');
    });

    it('illegal transition does not mutate direction even if direction opt is provided', () => {
      const sm = new PlayerStateMachine({ state: 'water', direction: 'S' });
      sm.transition('walk', { direction: 'NE' });
      expect(sm.direction).toBe('S');
    });

    it('water -> idle is legal', () => {
      const sm = new PlayerStateMachine({ state: 'water' });
      const ok = sm.transition('idle');
      expect(ok).toBe(true);
      expect(sm.state).toBe('idle');
    });

    it('idle can transition to every other state', () => {
      const targets: PlayerState[] = ['walk', 'run', 'interact', 'water', 'mine', 'talk'];
      for (const target of targets) {
        const sm = new PlayerStateMachine();
        expect(sm.transition(target)).toBe(true);
        expect(sm.state).toBe(target);
      }
    });

    it('transition can also set the new direction via opts', () => {
      const sm = new PlayerStateMachine();
      sm.transition('walk', { direction: 'NW' });
      expect(sm.state).toBe('walk');
      expect(sm.direction).toBe('NW');
    });

    it('same-state transition is a noop returning true without firing onChange', () => {
      const sm = new PlayerStateMachine({ state: 'walk', direction: 'S' });
      const listener = vi.fn();
      sm.onChange(listener);

      const ok = sm.transition('walk');

      expect(ok).toBe(true);
      expect(sm.state).toBe('walk');
      expect(listener).not.toHaveBeenCalled();
    });
  });

  describe('face', () => {
    it('only changes direction, not state, and does not fire onChange', () => {
      const sm = new PlayerStateMachine({ state: 'walk', direction: 'S' });
      const listener = vi.fn();
      sm.onChange(listener);

      sm.face('NW');

      expect(sm.direction).toBe('NW');
      expect(sm.state).toBe('walk');
      expect(listener).not.toHaveBeenCalled();
    });
  });

  describe('onChange', () => {
    it('invokes listener with from/to/direction and unsubscribe stops further notifications', () => {
      const sm = new PlayerStateMachine();
      const events: PlayerStateChangeEvent[] = [];
      const off = sm.onChange((event) => events.push(event));

      sm.transition('walk', { direction: 'E' });

      expect(events).toEqual([{ from: 'idle', to: 'walk', direction: 'E' }]);

      off();
      sm.transition('run');

      expect(events).toHaveLength(1);
    });

    it('supports multiple listeners in registration order', () => {
      const sm = new PlayerStateMachine();
      const order: string[] = [];
      sm.onChange(() => order.push('a'));
      sm.onChange(() => order.push('b'));
      sm.onChange(() => order.push('c'));

      sm.transition('walk');

      expect(order).toEqual(['a', 'b', 'c']);
    });
  });

  describe('reset', () => {
    it('resets to defaults (idle + S) when no snapshot is given', () => {
      const sm = new PlayerStateMachine({ state: 'run', direction: 'N' });
      sm.reset();
      expect(sm.state).toBe('idle');
      expect(sm.direction).toBe('S');
    });

    it('resets to a specific snapshot when provided', () => {
      const sm = new PlayerStateMachine();
      sm.reset({ state: 'talk', direction: 'NE' });
      expect(sm.state).toBe('talk');
      expect(sm.direction).toBe('NE');
    });
  });
});
