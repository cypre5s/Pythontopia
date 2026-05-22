import { playSfx } from '@/composables/useAudio';
import { getPhaserGame } from '@game/adapters/phaserGame';

const OVERLAY_CLASS = 'pyt-celebrate';
const BURST_CLASS = 'pyt-celebrate__burst';
const PIECE_CLASS = 'pyt-celebrate__piece';
const OVERLAY_DURATION_MS = 1200;
const PIECE_COUNT = 6;
// Phaser confetti 持续 0.2s 发射 + 1.5s 飘落，所以销毁挂 2s 兜底，避免泄漏。
const PHASER_CONFETTI_LIFESPAN_MS = 1500;
const PHASER_CONFETTI_DURATION_MS = 200;
const PHASER_CONFETTI_QUANTITY = 30;
const PHASER_CONFETTI_DEPTH = 100;
const PHASER_CONFETTI_CLEANUP_MS = 2_000;

export function celebrateMature(target: HTMLElement | null): void {
  if (typeof document === 'undefined') {
    return;
  }
  const host: HTMLElement | null = target ?? (document.body as HTMLElement | null);
  if (!host) {
    return;
  }

  const overlay = document.createElement('div');
  overlay.className = OVERLAY_CLASS;
  overlay.setAttribute('aria-hidden', 'true');

  const burst = document.createElement('div');
  burst.className = BURST_CLASS;
  overlay.appendChild(burst);

  for (let i = 0; i < PIECE_COUNT; i += 1) {
    const piece = document.createElement('div');
    piece.className = PIECE_CLASS;
    const leftPercent = 15 + (i * 70) / Math.max(1, PIECE_COUNT - 1);
    piece.style.left = `${leftPercent}%`;
    piece.style.animationDelay = `${(i % 3) * 80}ms`;
    overlay.appendChild(piece);
  }

  host.appendChild(overlay);

  try {
    playSfx('sfx_jingle_levelup');
  } catch {
    /* sfx 失败不影响视觉反馈 */
  }

  const remove = (): void => {
    if (overlay.parentNode) {
      overlay.parentNode.removeChild(overlay);
    }
  };
  const timers: { setTimeout: typeof setTimeout } = typeof window !== 'undefined' ? window : globalThis;
  timers.setTimeout(remove, OVERLAY_DURATION_MS);
}

export interface KcUnlockOptions {
  kcCode: string;
  kcName: string;
  /** 允许调用方关闭 Phaser 粒子（例如 Pyodide 调试页 Phaser 未初始化）。 */
  withPhaserBurst?: boolean;
}

export function celebrateKcUnlock(opts: KcUnlockOptions): void {
  // 复用 DOM 飘带浮层做主反馈，确保 Phaser 不可用时也有可视效果。
  celebrateMature(null);
  if (opts.withPhaserBurst === false) {
    return;
  }
  playPhaserConfetti('fx_confetti_burst');
}

export function celebrateLevelUp(): void {
  try {
    playSfx('sfx_jingle_complete', { volume: 0.5 });
  } catch {
    // sfx 失败不影响视觉反馈。
  }
  playPhaserConfetti('fx_confetti_burst');
}

// 在屏幕中心一次性喷射 confetti。Phaser 缺失或贴图未加载时静默降级。
function playPhaserConfetti(textureKey: string): void {
  const game = getPhaserGame();
  if (!game) return;
  const scene = pickActiveScene(game);
  if (!scene) return;
  const textures = (scene as unknown as { textures?: { exists?(k: string): boolean } }).textures;
  if (!textures?.exists?.(textureKey)) {
    return;
  }
  const cam = (scene as unknown as { cameras?: { main?: { midPoint?: { x: number; y: number } } } })
    .cameras?.main;
  const midX = cam?.midPoint?.x ?? 0;
  const midY = cam?.midPoint?.y ?? 0;
  const add = (scene as unknown as {
    add: {
      particles(
        x: number,
        y: number,
        key: string,
        config: Record<string, unknown>,
      ): { destroy(): void; setDepth?(d: number): unknown };
    };
  }).add;
  try {
    const emitter = add.particles(midX, midY, textureKey, {
      lifespan: PHASER_CONFETTI_LIFESPAN_MS,
      speed: { min: 100, max: 400 },
      angle: { min: 220, max: 320 },
      scale: { start: 1, end: 0.3 },
      gravityY: 200,
      quantity: PHASER_CONFETTI_QUANTITY,
      duration: PHASER_CONFETTI_DURATION_MS,
    });
    emitter.setDepth?.(PHASER_CONFETTI_DEPTH);
    const timers: { setTimeout: typeof setTimeout } = typeof window !== 'undefined' ? window : globalThis;
    timers.setTimeout(() => {
      try {
        emitter.destroy();
      } catch {
        // 场景被销毁后再次 destroy 是 no-op。
      }
    }, PHASER_CONFETTI_CLEANUP_MS);
  } catch {
    // 粒子 API 不可用（mock 桩）时静默忽略。
  }
}

interface PhaserGameLike {
  scene: {
    scenes?: unknown[];
    getScenes?(isActive?: boolean): unknown[];
  };
}

function pickActiveScene(game: PhaserGameLike): unknown {
  const sceneManager = game.scene;
  if (typeof sceneManager.getScenes === 'function') {
    const active = sceneManager.getScenes(true);
    if (active && active.length > 0) return active[0];
  }
  if (Array.isArray(sceneManager.scenes) && sceneManager.scenes.length > 0) {
    return sceneManager.scenes[0];
  }
  return null;
}
