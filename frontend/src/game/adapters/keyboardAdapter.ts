// 隔离原因：keyboardAdapter 只通过 scene.input.keyboard.addKey 读取按键状态，
// 不直接 import phaser，便于 jsdom 单测加载。
// 设计原因：复用 InputAdapter 接口契约，WASD/方向键映射到 8 方向 move，
// Shift 作为 run 修饰，F/E 作为 action，Esc 作为 cancel。action/cancel 走边沿
// 触发（仅在按下的第一帧返回 true），由 poll() 每次调用时推进 prev 状态。
import type Phaser from 'phaser';
import { computeDirection8, emptyIntent, type InputAdapter, type InputIntent } from './input';

interface KeyboardKeyState {
  isDown: boolean;
}

interface KeyMap {
  w: KeyboardKeyState | null;
  a: KeyboardKeyState | null;
  s: KeyboardKeyState | null;
  d: KeyboardKeyState | null;
  up: KeyboardKeyState | null;
  down: KeyboardKeyState | null;
  left: KeyboardKeyState | null;
  right: KeyboardKeyState | null;
  shift: KeyboardKeyState | null;
  f: KeyboardKeyState | null;
  e: KeyboardKeyState | null;
  esc: KeyboardKeyState | null;
}

function emptyKeyMap(): KeyMap {
  return {
    w: null,
    a: null,
    s: null,
    d: null,
    up: null,
    down: null,
    left: null,
    right: null,
    shift: null,
    f: null,
    e: null,
    esc: null,
  };
}

function isPressed(key: KeyboardKeyState | null): boolean {
  return key?.isDown === true;
}

export function createKeyboardAdapter(): InputAdapter {
  let keys: KeyMap = emptyKeyMap();
  let attached = false;
  let prevAction = false;
  let prevCancel = false;

  return {
    poll(): InputIntent {
      if (!attached) {
        prevAction = false;
        prevCancel = false;
        return emptyIntent();
      }
      const up = isPressed(keys.w) || isPressed(keys.up);
      const down = isPressed(keys.s) || isPressed(keys.down);
      const left = isPressed(keys.a) || isPressed(keys.left);
      const right = isPressed(keys.d) || isPressed(keys.right);

      let dx = 0;
      let dy = 0;
      if (right) dx += 1;
      if (left) dx -= 1;
      if (down) dy += 1;
      if (up) dy -= 1;

      const run = isPressed(keys.shift);
      const actionDown = isPressed(keys.f) || isPressed(keys.e);
      const cancelDown = isPressed(keys.esc);

      const actionPressed = actionDown && !prevAction;
      const cancelPressed = cancelDown && !prevCancel;
      prevAction = actionDown;
      prevCancel = cancelDown;

      return {
        move: { dx, dy },
        direction: computeDirection8(dx, dy),
        run,
        actionPressed,
        cancelPressed,
      };
    },
    attach(scene: Phaser.Scene): void {
      const kb = scene.input.keyboard;
      if (!kb) {
        return;
      }
      keys = {
        w: kb.addKey('W'),
        a: kb.addKey('A'),
        s: kb.addKey('S'),
        d: kb.addKey('D'),
        up: kb.addKey('UP'),
        down: kb.addKey('DOWN'),
        left: kb.addKey('LEFT'),
        right: kb.addKey('RIGHT'),
        shift: kb.addKey('SHIFT'),
        f: kb.addKey('F'),
        e: kb.addKey('E'),
        esc: kb.addKey('ESC'),
      };
      attached = true;
    },
    detach(): void {
      // Phaser 的 Key 对象由 KeyboardPlugin 管理，scene shutdown 时由 Phaser
      // 自行释放；这里只清掉本地引用与边沿状态，避免下一次 attach 时残留。
      keys = emptyKeyMap();
      attached = false;
      prevAction = false;
      prevCancel = false;
    },
  };
}
