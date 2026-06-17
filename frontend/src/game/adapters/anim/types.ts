// 闭环动效适配层「稳定接口契约」（§3.7 + §4 Stream F 第 1 条）。
//
// 隔离原因：业务层（LoopProgress.vue / loopSlice 等）只依赖本文件声明的接口，
// 绝不直接 import gsap / motion / @rive-app/canvas —— 三方动态对象全部隔离在同目录
// 的实现文件内（domMotion.ts = Motion(DOM)、riveMascot.ts = Rive(吉祥物)、
// phaserTween.ts = Phaser tween(画布内)）。F2 的 LoopProgress 通过本接口注入/mock 解耦。
//
// 约束（AGENTS 前端规范 + §0 类型纪律）：TS strict、业务层禁 any；尊重
// prefers-reduced-motion：reduceMotion 为真时所有动效退化为「瞬时落终值」。

/** playStepDone 的结果态：成功收束 vs 失败抖动；对齐 loopTypes.LoopStepStatus 的两个终态。 */
export type AnimStepOutcome = 'done' | 'failed'

/**
 * 吉祥物表情：呼应闭环 critic 闸门结论。
 * - idle      平静待机
 * - thinking  正在检索/推理
 * - approve   critic 通过（轻反馈）
 * - reject    critic 驳回 / 步骤失败
 * - celebrate 闭环达成（大庆祝）
 */
export type MascotExpression = 'idle' | 'thinking' | 'approve' | 'reject' | 'celebrate'

/** celebrate 触达范围：吉祥物 / 画布内 / 两者（默认 both）。 */
export type CelebrateScope = 'mascot' | 'canvas' | 'both'

/** 单步动效可选项。durationMs 缺省走实现内默认；signal 支持中断（路由切换 / 快速重渲染）。 */
export interface AnimOptions {
  durationMs?: number
  signal?: AbortSignal
}

/**
 * celebrate 可选项。
 * - scope      决定触达哪些后端（默认 both）。
 * - expression 让 F2 借同一入口把 critic 结果映射到吉祥物表情（默认 'celebrate'）：
 *              通过 → 'approve' / 大达成 → 'celebrate'；驳回 → {scope:'mascot', expression:'reject'}。
 */
export interface CelebrateOptions extends AnimOptions {
  scope?: CelebrateScope
  expression?: MascotExpression
}

/**
 * 闭环动效适配器稳定接口（§3.7 / §4 F1）。业务层只调本接口，禁直接碰三方动画库。
 *
 * - reduceMotion : 当前是否处于 prefers-reduced-motion（实时读取，支持系统运行时切换）；
 *                  为真时下列动效全部退化为瞬时。
 * - playStepIn   : 某闭环步骤入场（DOM 过渡，Motion 实现）。
 * - playStepDone : 某闭环步骤收束为 done/failed（DOM 过渡，Motion 实现）。
 * - celebrate    : 闭环阶段性/最终庆祝（Rive 吉祥物 + Phaser 画布内 tween）。
 * - dispose      : 释放后端资源（Rive WASM 实例 / 残留 tween），幂等。
 */
export interface AnimAdapter {
  readonly reduceMotion: boolean
  playStepIn(target: Element, opts?: AnimOptions): Promise<void>
  playStepDone(target: Element, outcome: AnimStepOutcome, opts?: AnimOptions): Promise<void>
  celebrate(opts?: CelebrateOptions): Promise<void>
  dispose(): void
}

// ── 后端契约（三方隔离实现各自落地；AnimAdapter 依赖这些接口，便于 DI / 测试 mock） ──

/** 后端通用可选项：instant=true 时立即落终值、不创建动画（reduce-motion 降级路径）。 */
export interface BackendOptions extends AnimOptions {
  instant?: boolean
}

/** DOM 过渡后端（Motion 隔离实现）。 */
export interface DomMotionBackend {
  stepIn(target: Element, opts: BackendOptions): Promise<void>
  stepDone(target: Element, outcome: AnimStepOutcome, opts: BackendOptions): Promise<void>
}

/** 吉祥物后端（Rive 隔离实现）。 */
export interface MascotBackend {
  /** 切换吉祥物表情（trigger / boolean 输入）；资源未就绪时 fail-soft no-op。 */
  setExpression(expression: MascotExpression, opts?: BackendOptions): void
  /** 触发「庆祝」表情动画；instant 下立即返回。 */
  celebrate(opts?: BackendOptions): Promise<void>
  /** 释放 Rive WASM 实例，幂等。 */
  dispose(): void
}

/** 画布内后端（Phaser tween 隔离实现）。 */
export interface CanvasFxBackend {
  /** 画布内庆祝补间（缩放 pop 等）；无目标或 instant 时 fail-soft no-op。 */
  celebrate(opts?: BackendOptions): Promise<void>
  /** 停止残留补间，幂等。 */
  dispose(): void
}

/**
 * AnimAdapter 装配依赖（全部可选，便于测试注入假后端）。
 * - dom      : DOM 过渡后端；缺省用真实 createDomMotionBackend()。
 * - mascot   : 吉祥物后端；缺省 null（Phase 2 由 WorldCanvas 注入带 canvas 的 riveMascot）。
 * - canvasFx : 画布内后端；缺省 null（Phase 2 由 WorldScene 注入）。
 * - prefersReducedMotion : reduce-motion 读取函数；缺省读 window.matchMedia（测试可注入）。
 */
export interface AnimAdapterDeps {
  dom?: DomMotionBackend
  mascot?: MascotBackend | null
  canvasFx?: CanvasFxBackend | null
  prefersReducedMotion?: () => boolean
}
