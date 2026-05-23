// W2 InteractableSystem：跨场景统一可交互物件框架。
//
// 设计原因：
//   - 玩家在多张地图（farm / mine / campus / port / hub）需要与 NPC、资源点、矿洞入口、
//     公告板等交互。原本每个 scene 各自实现"距离判定 + 显示提示气泡 + 监听 E 键"，
//     代码散落且测试困难。
//   - InteractableSystem 把"trigger 形状 + 进出回调 + 激活回调 + 气泡 UI"四件事
//     收敛到同一入口；scene 只需 register() 注册、update() 驱动、shutdown 时 clear()。
//   - hintRenderer 由 scene 注入，保持 core 层无 UI / 无 Phaser GameObject 依赖，
//     便于 jsdom 单测；business 端可在 scene 里实现自己的气泡控件 / Vue overlay。
//
// 隔离边界：本文件属于 game core 层；Phaser 仅 `import type`，运行时不依赖 phaser
// 模块。scene 实例只作为 TriggerContext.scene 透传给业务回调，框架自身不调任何
// Phaser API。

import type Phaser from 'phaser';

export type ITriggerShape =
  | { type: 'rect'; x: number; y: number; width: number; height: number }
  | { type: 'circle'; x: number; y: number; radius: number };

export interface ITrigger {
  id: string;
  shape: ITriggerShape;
  /** 当玩家进入时回调 */
  onEnter?: (ctx: TriggerContext) => void;
  /** 当玩家离开时回调 */
  onExit?: (ctx: TriggerContext) => void;
  /** 玩家在范围内按交互键时回调 */
  onActivate?: (ctx: TriggerContext) => void;
  /** UI 提示文本（默认 "按 E 交互"） */
  hint?: string;
  /** 是否启用（默认 true） */
  enabled?: boolean;
  /** 自定义数据，业务侧附带 */
  data?: Record<string, unknown>;
}

export interface TriggerContext {
  trigger: ITrigger;
  playerX: number;
  playerY: number;
  scene: Phaser.Scene;
}

export interface HintRenderer {
  show(text: string, opts?: { x: number; y: number }): void;
  hide(): void;
}

export interface InteractableSystemDeps {
  /** 提示气泡 UI 渲染器，由场景注入（可选，未注入则不渲染气泡，仅触发回调） */
  hintRenderer?: HintRenderer;
  /** 调试日志（如同 id 重复 register 时输出 warn） */
  logger?: (msg: string) => void;
}

export const DEFAULT_HINT = '按 E 交互';

/**
 * 判断一个 2D 点是否在形状内。
 * - rect：含边界（左/上/右/下闭区间），与 Phaser.Geom.Rectangle.Contains 一致。
 * - circle：欧氏距离 <= radius（含边界）。
 */
export function shapeContains(
  shape: ITriggerShape,
  x: number,
  y: number,
): boolean {
  if (shape.type === 'rect') {
    return (
      x >= shape.x &&
      x <= shape.x + shape.width &&
      y >= shape.y &&
      y <= shape.y + shape.height
    );
  }
  const dx = x - shape.x;
  const dy = y - shape.y;
  return dx * dx + dy * dy <= shape.radius * shape.radius;
}

export class InteractableSystem {
  // Map 保留插入顺序，迭代顺序即注册顺序；getActive / onActivate 多个 trigger
  // 时的可预测排序依赖这一点。
  private readonly triggers = new Map<string, ITrigger>();
  // activeIds 用 Set 保证 O(1) 判定；遍历活跃 trigger 时通过 triggers Map 顺序
  // 过滤，从而保留注册顺序。
  private readonly activeIds = new Set<string>();
  private readonly scene: Phaser.Scene;
  private readonly hintRenderer: HintRenderer | undefined;
  private readonly logger: ((msg: string) => void) | undefined;
  // 记录最后一次 update 传入的玩家坐标，detach / clear 触发 onExit 时复用，
  // 避免 ctx.playerX 出现 0 这种没意义的值。
  private lastPlayerX = 0;
  private lastPlayerY = 0;

  constructor(scene: Phaser.Scene, deps?: InteractableSystemDeps) {
    this.scene = scene;
    this.hintRenderer = deps?.hintRenderer;
    this.logger = deps?.logger;
  }

  /** 注册 trigger，返回 detach 函数（取消注册） */
  register(trigger: ITrigger): () => void {
    const existing = this.triggers.get(trigger.id);
    if (existing) {
      this.logger?.(
        `[InteractableSystem] trigger id "${trigger.id}" already registered, replacing`,
      );
      // 旧 trigger 若处于激活状态，按"离场"语义先调 onExit，避免业务侧观察到
      // 一个"既没 exit 又被覆盖"的鬼影 trigger。
      if (this.activeIds.has(trigger.id)) {
        this.fireExit(existing);
        this.activeIds.delete(trigger.id);
      }
    }
    this.triggers.set(trigger.id, trigger);

    let detached = false;
    return (): void => {
      if (detached) {
        return;
      }
      detached = true;
      const cur = this.triggers.get(trigger.id);
      // 只在 cur === trigger 时移除：避免"用户先 register A → 又用同 id register B
      // → 调用 A.detach()"时把 B 误删。
      if (cur !== trigger) {
        return;
      }
      if (this.activeIds.has(trigger.id)) {
        this.fireExit(cur);
        this.activeIds.delete(trigger.id);
      }
      this.triggers.delete(trigger.id);
      this.refreshHint();
    };
  }

  /** 每帧调用，传入玩家坐标与是否按了交互键。 */
  update(playerX: number, playerY: number, actionPressed: boolean): void {
    this.lastPlayerX = playerX;
    this.lastPlayerY = playerY;

    // Step 1: 计算本帧新的 activeIds，跳过 enabled=false。
    const nextActiveIds = new Set<string>();
    for (const [id, trigger] of this.triggers) {
      if (trigger.enabled === false) {
        continue;
      }
      if (shapeContains(trigger.shape, playerX, playerY)) {
        nextActiveIds.add(id);
      }
    }

    // Step 2: 触发 onEnter（按注册顺序遍历 triggers，过滤"新进入"的）。
    for (const [id, trigger] of this.triggers) {
      if (nextActiveIds.has(id) && !this.activeIds.has(id)) {
        trigger.onEnter?.({
          trigger,
          playerX,
          playerY,
          scene: this.scene,
        });
      }
    }

    // Step 3: 触发 onExit（之前 active 但本帧不在）。
    // 注意：这里要先把 activeIds 的 snapshot 拿下来再遍历，否则下面 Step 4
    // 修改 activeIds 时会影响判定。
    const prevActiveIds = [...this.activeIds];
    for (const id of prevActiveIds) {
      if (!nextActiveIds.has(id)) {
        const trigger = this.triggers.get(id);
        if (trigger) {
          this.fireExit(trigger);
        }
      }
    }

    // Step 4: commit activeIds。
    this.activeIds.clear();
    for (const id of nextActiveIds) {
      this.activeIds.add(id);
    }

    // Step 5: actionPressed 触发 onActivate（按注册顺序遍历 triggers Map，
    // 过滤出活跃的，保证多 trigger 触发顺序可预测）。
    if (actionPressed && this.activeIds.size > 0) {
      for (const [id, trigger] of this.triggers) {
        if (this.activeIds.has(id)) {
          trigger.onActivate?.({
            trigger,
            playerX,
            playerY,
            scene: this.scene,
          });
        }
      }
    }

    // Step 6: 更新气泡。
    this.refreshHint();
  }

  /** 获取当前激活的（玩家在内的）trigger 列表（按注册顺序） */
  getActive(): readonly ITrigger[] {
    const result: ITrigger[] = [];
    for (const [id, trigger] of this.triggers) {
      if (this.activeIds.has(id)) {
        result.push(trigger);
      }
    }
    return result;
  }

  /** 移除所有 trigger（scene shutdown 调用） */
  clear(): void {
    // 对所有当前激活的 trigger 调 onExit，再清空。
    for (const id of this.activeIds) {
      const trigger = this.triggers.get(id);
      if (trigger) {
        this.fireExit(trigger);
      }
    }
    this.activeIds.clear();
    this.triggers.clear();
    this.hintRenderer?.hide();
  }

  private fireExit(trigger: ITrigger): void {
    trigger.onExit?.({
      trigger,
      playerX: this.lastPlayerX,
      playerY: this.lastPlayerY,
      scene: this.scene,
    });
  }

  private refreshHint(): void {
    const renderer = this.hintRenderer;
    if (!renderer) {
      return;
    }
    if (this.activeIds.size === 0) {
      renderer.hide();
      return;
    }
    // 找到第一个活跃 trigger（按注册顺序），用它的 hint。
    for (const [id, trigger] of this.triggers) {
      if (this.activeIds.has(id)) {
        renderer.show(trigger.hint ?? DEFAULT_HINT);
        return;
      }
    }
  }
}
