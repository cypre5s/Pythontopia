// NpcFramework：把 NPC 巡逻状态、对话锚点、任务发布器收敛到一处，避免每个 scene
// 自己再写一遍 dx/dy + waypoint 循环。本框架是纯逻辑层：scene 只用 import type，
// NPC sprite 的实际渲染 / Phaser GameObject 创建留给 scene 整合层。
//
// 设计边界：
// - 巡逻：按 deltaMs 推进当前 waypoint，到达后 dwell；dwell 完成走下一个；环形循环。
// - 朝向：根据 dx/dy 自动算 8 方向（与 PlayerStateMachine 的 Direction 罗盘记号对齐）。
// - 对话：inDialog=true 时暂停巡逻（不消耗 dwell 计时），endDialog 后继续。
// - faceTowards：对话锚点触发时让 NPC 朝向玩家。
// - questPublisher / dialogAnchor：定义层标记，由 scene 整合层在交互时读取，
//   本框架不主动做任何 quest 拉取，避免和 store / api 层强耦合。
//
// 坐标系约定：使用 Phaser 屏幕坐标（X 向右、Y 向下）。N 表示 -Y，S 表示 +Y。

import type Phaser from 'phaser';
import type { Direction } from './PlayerStateMachine';

export interface NpcWaypoint {
  x: number;
  y: number;
  /** 到达后停留毫秒数（默认 0，即立刻走下一个）。 */
  dwellMs?: number;
}

/** 自由游走配置：以 NPC spawn 为锚点，在半径内随机选点。 */
export interface NpcWanderConfig {
  /** 游走半径（px），以 spawn 为圆心。 */
  radiusPx: number;
  /** 游走速度 px/s（默认取 def.speed 或 32）。 */
  speed?: number;
  /** 到点最短停留毫秒（默认 1500）。 */
  minDwellMs?: number;
  /** 到点最长停留毫秒（默认 4500）。 */
  maxDwellMs?: number;
}

export interface NpcDefinition {
  /** 唯一 ID，对应后端 npc_id 与 frontend/public/data/npcs/<key>/。 */
  id: string;
  /** 显示名（如 "Sara 老师"）。 */
  displayName: string;
  /** 立绘 key，对应 frontend/public/assets/pixel/portraits/portrait_*.png。 */
  portraitKey?: string;
  spawnX: number;
  spawnY: number;
  spawnDirection?: Direction;
  /** 巡逻路点（空数组或省略或 undefined = 站桩）。 */
  patrol?: NpcWaypoint[] | undefined;
  /** 巡逻移动速度 px/s（默认 32）。 */
  speed?: number;
  /**
   * 自由游走（R-14 需求8b「所有 NPC 不呆在原地」）：无 patrol 的 NPC 在 spawn 周围
   * radiusPx 内随机选可达点行走、到点随机 dwell。patrol 优先级高于 wander。
   */
  wander?: NpcWanderConfig | undefined;
  /** 任务发布器：true 表示该 NPC 可发布任务，scene 在交互时拉取任务列表。 */
  questPublisher?: boolean;
  /** 对话锚点偏移：玩家走到 (spawnX + dialogAnchor.x, spawnY + dialogAnchor.y) 时
   *  scene 调 faceTowards 让 NPC 转向玩家。 */
  dialogAnchor?: { x: number; y: number };
}

export interface NpcInstance {
  readonly id: string;
  readonly def: NpcDefinition;
  x: number;
  y: number;
  direction: Direction;
  currentWaypoint: number;
  inDialog: boolean;
}

interface NpcRuntime extends NpcInstance {
  /** dwell 剩余毫秒：到达 waypoint 时填入 dwellMs，按 deltaMs 倒计时。 */
  dwellRemainingMs: number;
  /** 当前 wander 目标（null=需重新选点）。 */
  wanderTargetX: number | null;
  wanderTargetY: number | null;
}

const DEFAULT_SPEED_PX_PER_S = 32;
const DEFAULT_DIRECTION: Direction = 'S';
const DEFAULT_WANDER_MIN_DWELL_MS = 1500;
const DEFAULT_WANDER_MAX_DWELL_MS = 4500;
// pickWanderTarget 最多尝试候选点次数（全被碰撞挡住则本轮放弃，dwell 后重试）。
const WANDER_CANDIDATE_TRIES = 8;
// 浮点累积容差：sub-pixel 误差也算到达，避免 NPC 在 waypoint 周围抖。
const ARRIVE_EPSILON = 1e-3;
// faceTowards：玩家几乎站在 NPC 位置时不改朝向（避免 atan2(0,0) 退化）。
const FACE_TOWARDS_EPSILON = 1e-6;

export class NpcFramework {
  private readonly npcs = new Map<string, NpcRuntime>();
  // 仅保存引用以备未来扩展（如 scene.events 钩 shutdown 自动 clear）；
  // 当前实现不直接调用 scene API，因此单测用最小 stub 即可。
  private readonly scene: Phaser.Scene;
  // 随机源（可注入便于单测确定化）。
  private readonly rng: () => number;
  // 碰撞判定（世界像素坐标 → 是否阻挡）；未注入时 wander 不做避障。
  private collides: ((x: number, y: number) => boolean) | null = null;

  constructor(scene: Phaser.Scene, opts?: { rng?: () => number }) {
    this.scene = scene;
    this.rng = opts?.rng ?? Math.random;
  }

  /** 注入碰撞判定（世界像素坐标）；wander 据此避开建筑/障碍/越界。 */
  setCollisionChecker(fn: (x: number, y: number) => boolean): void {
    this.collides = fn;
  }

  register(def: NpcDefinition): NpcInstance {
    const runtime: NpcRuntime = {
      id: def.id,
      def,
      x: def.spawnX,
      y: def.spawnY,
      direction: def.spawnDirection ?? DEFAULT_DIRECTION,
      currentWaypoint: 0,
      inDialog: false,
      dwellRemainingMs: 0,
      wanderTargetX: null,
      wanderTargetY: null,
    };
    this.npcs.set(def.id, runtime);
    return runtime;
  }

  update(deltaMs: number): void {
    if (!Number.isFinite(deltaMs) || deltaMs <= 0) {
      return;
    }
    for (const npc of this.npcs.values()) {
      this.tickNpc(npc, deltaMs);
    }
  }

  get(id: string): NpcInstance | undefined {
    return this.npcs.get(id);
  }

  getAll(): readonly NpcInstance[] {
    return Array.from(this.npcs.values());
  }

  startDialog(id: string): boolean {
    const npc = this.npcs.get(id);
    if (!npc) {
      return false;
    }
    npc.inDialog = true;
    return true;
  }

  endDialog(id: string): boolean {
    const npc = this.npcs.get(id);
    if (!npc) {
      return false;
    }
    npc.inDialog = false;
    return true;
  }

  /**
   * 瞬移 NPC 到新位置（用于日程切换），同时可选更新巡逻路点。
   */
  teleport(id: string, x: number, y: number, patrol?: NpcWaypoint[]): boolean {
    const npc = this.npcs.get(id);
    if (!npc) return false;
    npc.x = x;
    npc.y = y;
    npc.currentWaypoint = 0;
    npc.dwellRemainingMs = 0;
    npc.wanderTargetX = null;
    npc.wanderTargetY = null;
    if (patrol !== undefined) {
      (npc.def as { patrol?: NpcWaypoint[] }).patrol = patrol;
    }
    return true;
  }

  faceTowards(id: string, targetX: number, targetY: number): boolean {
    const npc = this.npcs.get(id);
    if (!npc) {
      return false;
    }
    const dx = targetX - npc.x;
    const dy = targetY - npc.y;
    if (Math.abs(dx) < FACE_TOWARDS_EPSILON && Math.abs(dy) < FACE_TOWARDS_EPSILON) {
      return true;
    }
    npc.direction = directionFromVector(dx, dy);
    return true;
  }

  clear(): void {
    this.npcs.clear();
  }

  private tickNpc(npc: NpcRuntime, deltaMs: number): void {
    if (npc.inDialog) {
      return;
    }
    const patrol = npc.def.patrol;
    if (!patrol || patrol.length === 0) {
      // 无 patrol：若配置了 wander 则自由游走，否则站桩。
      if (npc.def.wander) {
        this.tickWander(npc, npc.def.wander, deltaMs);
      }
      return;
    }

    if (npc.dwellRemainingMs > 0) {
      npc.dwellRemainingMs = Math.max(0, npc.dwellRemainingMs - deltaMs);
      return;
    }

    const target = patrol[npc.currentWaypoint];
    if (!target) {
      // 防御：noUncheckedIndexedAccess 让 patrol[idx] 可能为 undefined；
      // 正常 currentWaypoint 一直 mod patrol.length，理论不会越界。
      return;
    }

    const dx = target.x - npc.x;
    const dy = target.y - npc.y;
    const dist = Math.sqrt(dx * dx + dy * dy);

    if (dist <= ARRIVE_EPSILON) {
      // 已经在 waypoint 上（例如 spawn 与第一个 waypoint 重合）。
      // 不改 direction，直接进入 dwell + 推进 currentWaypoint。
      npc.x = target.x;
      npc.y = target.y;
      npc.dwellRemainingMs = target.dwellMs ?? 0;
      npc.currentWaypoint = (npc.currentWaypoint + 1) % patrol.length;
      return;
    }

    const speed = npc.def.speed ?? DEFAULT_SPEED_PX_PER_S;
    const step = (speed * deltaMs) / 1000;

    npc.direction = directionFromVector(dx, dy);

    if (step >= dist) {
      npc.x = target.x;
      npc.y = target.y;
      npc.dwellRemainingMs = target.dwellMs ?? 0;
      npc.currentWaypoint = (npc.currentWaypoint + 1) % patrol.length;
      return;
    }

    npc.x += (dx / dist) * step;
    npc.y += (dy / dist) * step;
  }

  private tickWander(npc: NpcRuntime, cfg: NpcWanderConfig, deltaMs: number): void {
    if (npc.dwellRemainingMs > 0) {
      npc.dwellRemainingMs = Math.max(0, npc.dwellRemainingMs - deltaMs);
      return;
    }
    if (npc.wanderTargetX === null || npc.wanderTargetY === null) {
      const target = this.pickWanderTarget(npc, cfg);
      if (!target) {
        // 周围都被挡住：歇一会再试，避免每帧空转。
        npc.dwellRemainingMs = this.randomDwell(cfg);
        return;
      }
      npc.wanderTargetX = target.x;
      npc.wanderTargetY = target.y;
    }

    const dx = npc.wanderTargetX - npc.x;
    const dy = npc.wanderTargetY - npc.y;
    const dist = Math.sqrt(dx * dx + dy * dy);
    if (dist <= ARRIVE_EPSILON) {
      npc.dwellRemainingMs = this.randomDwell(cfg);
      npc.wanderTargetX = null;
      npc.wanderTargetY = null;
      return;
    }

    const speed = cfg.speed ?? npc.def.speed ?? DEFAULT_SPEED_PX_PER_S;
    const step = (speed * deltaMs) / 1000;
    npc.direction = directionFromVector(dx, dy);

    const arriving = step >= dist;
    const nextX = arriving ? npc.wanderTargetX : npc.x + (dx / dist) * step;
    const nextY = arriving ? npc.wanderTargetY : npc.y + (dy / dist) * step;

    // 避障：下一步落点被挡则放弃当前目标，dwell 后重选，避免穿墙/越界。
    if (this.collides && this.collides(nextX, nextY)) {
      npc.wanderTargetX = null;
      npc.wanderTargetY = null;
      npc.dwellRemainingMs = this.randomDwell(cfg);
      return;
    }

    npc.x = nextX;
    npc.y = nextY;
    if (arriving) {
      npc.dwellRemainingMs = this.randomDwell(cfg);
      npc.wanderTargetX = null;
      npc.wanderTargetY = null;
    }
  }

  /** 在 spawn 锚点周围半径内随机选一个未被碰撞阻挡的点；全失败返回 null。 */
  private pickWanderTarget(
    npc: NpcRuntime,
    cfg: NpcWanderConfig,
  ): { x: number; y: number } | null {
    const radius = Math.max(0, cfg.radiusPx);
    for (let i = 0; i < WANDER_CANDIDATE_TRIES; i += 1) {
      const angle = this.rng() * Math.PI * 2;
      const r = this.rng() * radius;
      const x = npc.def.spawnX + Math.cos(angle) * r;
      const y = npc.def.spawnY + Math.sin(angle) * r;
      if (!this.collides || !this.collides(x, y)) {
        return { x, y };
      }
    }
    return null;
  }

  private randomDwell(cfg: NpcWanderConfig): number {
    const min = cfg.minDwellMs ?? DEFAULT_WANDER_MIN_DWELL_MS;
    const max = cfg.maxDwellMs ?? DEFAULT_WANDER_MAX_DWELL_MS;
    if (max <= min) return min;
    return min + this.rng() * (max - min);
  }
}

/** 从 npc id 推导默认立绘 key（对齐 portrait_*.png 命名）。 */
export function defaultPortraitKey(npcId: string): string {
  return `portrait_${npcId}`;
}

// 8 方向罗盘：屏幕坐标系下 atan2(dy, dx) 的扇区映射。
// sector=0..7，每 π/4 一档，从 E 起顺时针：
//   0=E, 1=SE, 2=S, 3=SW, 4=W, 5=NW, 6=N, 7=NE。
function directionFromVector(dx: number, dy: number): Direction {
  const angle = Math.atan2(dy, dx);
  const TWO_PI = Math.PI * 2;
  const normalized = ((angle % TWO_PI) + TWO_PI) % TWO_PI;
  const sector = Math.round(normalized / (Math.PI / 4)) % 8;
  const compass: readonly Direction[] = ['E', 'SE', 'S', 'SW', 'W', 'NW', 'N', 'NE'];
  return compass[sector] ?? DEFAULT_DIRECTION;
}
