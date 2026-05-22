import { playSfx } from './sfx';

const OVERLAY_CLASS = 'pyt-celebrate';
const BURST_CLASS = 'pyt-celebrate__burst';
const PIECE_CLASS = 'pyt-celebrate__piece';
const OVERLAY_DURATION_MS = 1200;
const PIECE_COUNT = 6;

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
    playSfx('jingleLevelUp');
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
