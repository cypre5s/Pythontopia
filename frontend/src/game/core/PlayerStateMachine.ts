// PlayerStateMachine：Sara 玩家的状态/朝向有限状态机，集中原本散落在 sprite.ts 里的切换逻辑。
// state 覆盖 Beta 玩法（idle/walk/run/interact/water/mine/talk），朝向使用与后端
// PlayerStatePatch 对齐的 8 方向罗盘记号（N/NE/E/SE/S/SW/W/NW）。

export type PlayerState = 'idle' | 'walk' | 'run' | 'interact' | 'water' | 'mine' | 'talk';
export type Direction = 'N' | 'NE' | 'E' | 'SE' | 'S' | 'SW' | 'W' | 'NW';

export interface PlayerStateSnapshot {
  state: PlayerState;
  direction: Direction;
}

export interface PlayerStateChangeEvent {
  from: PlayerState;
  to: PlayerState;
  direction: Direction;
}

type PlayerStateChangeListener = (event: PlayerStateChangeEvent) => void;

// 自转（X -> X）必须出现在每行的合法目标里：transition 把自转判为 noop 返回 true，
// 但仍需先通过 allowed.includes(to) 这一关，否则会被判成非法转移。
const TRANSITIONS: Readonly<Record<PlayerState, ReadonlyArray<PlayerState>>> = {
  idle: ['idle', 'walk', 'run', 'interact', 'water', 'mine', 'talk'],
  walk: ['walk', 'run', 'idle', 'interact'],
  run: ['walk', 'run', 'idle', 'interact'],
  interact: ['interact', 'talk', 'water', 'mine', 'idle'],
  water: ['water', 'idle'],
  mine: ['mine', 'idle'],
  talk: ['talk', 'idle'],
};

const DEFAULT_STATE: PlayerState = 'idle';
const DEFAULT_DIRECTION: Direction = 'S';

export class PlayerStateMachine {
  private currentState: PlayerState;
  private currentDirection: Direction;
  private readonly listeners: PlayerStateChangeListener[] = [];

  constructor(initial?: Partial<PlayerStateSnapshot>) {
    this.currentState = initial?.state ?? DEFAULT_STATE;
    this.currentDirection = initial?.direction ?? DEFAULT_DIRECTION;
  }

  get state(): PlayerState {
    return this.currentState;
  }

  get direction(): Direction {
    return this.currentDirection;
  }

  snapshot(): PlayerStateSnapshot {
    return { state: this.currentState, direction: this.currentDirection };
  }

  transition(to: PlayerState, opts?: { direction?: Direction }): boolean {
    const allowed = TRANSITIONS[this.currentState];
    if (!allowed.includes(to)) {
      return false;
    }
    if (to === this.currentState) {
      return true;
    }
    if (opts?.direction !== undefined) {
      this.currentDirection = opts.direction;
    }
    const from = this.currentState;
    this.currentState = to;
    const event: PlayerStateChangeEvent = {
      from,
      to,
      direction: this.currentDirection,
    };
    // 复制一份再遍历，避免监听器在回调里 off 自己时打乱遍历顺序。
    const snapshotListeners = this.listeners.slice();
    for (const listener of snapshotListeners) {
      listener(event);
    }
    return true;
  }

  face(direction: Direction): void {
    this.currentDirection = direction;
  }

  onChange(listener: PlayerStateChangeListener): () => void {
    this.listeners.push(listener);
    return () => {
      const idx = this.listeners.indexOf(listener);
      if (idx >= 0) {
        this.listeners.splice(idx, 1);
      }
    };
  }

  reset(snapshot?: Partial<PlayerStateSnapshot>): void {
    this.currentState = snapshot?.state ?? DEFAULT_STATE;
    this.currentDirection = snapshot?.direction ?? DEFAULT_DIRECTION;
  }
}
