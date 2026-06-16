// 星露谷风音效层（Tone.js 程序化合成）。
//
// 设计要点：
//   1. **零音频文件依赖**：所有 SFX 用 Tone.js Synth 实时合成，避免引入商业像素游戏
//      官方音源（许可合规）；
//   2. **单例 transport**：所有 synth 共用 Tone.start() 触发的 AudioContext，避免
//      多组件挂载时反复创建上下文导致 iOS Safari unlock 失败；
//   3. **autoplay 友好**：首次用户交互（click/keydown）才 lazy 启动 Tone，与现有
//      useAudio 的 unlockAudio 协同；
//   4. **响应 setMuted**：与 Phaser sound manager 全局静音状态对齐，玩家在 SettingsModal
//      切静音可一次性关掉所有 SFX；
//   5. **节流防抖**：脚步声 60ms 最小间隔、UI 点击/hover 80ms，避免乱触发；
//   6. **不依赖 useAudio**：Tone.js 走自己的 AudioContext，不抢 Phaser BGM 通道，
//      避免 Howler/Tone/Phaser 三套同时启动导致 iOS 卡死。

type ToneModule = typeof import('tone');
type SynthLike = {
  triggerAttackRelease: (...args: unknown[]) => unknown;
  dispose: () => void;
};

let unlocked = false;
let muted = false;
// 自动检测系统 reduced-motion，激活时把 SFX 节流间隔放大 3 倍，
// 避免动效偏好为「减少」的玩家被音效骚扰。
let reducedMotionMode = false;

let toneModule: ToneModule | null = null;
let toneLoadPromise: Promise<ToneModule | null> | null = null;
let stepSynth: SynthLike | null = null;
let clickSynth: SynthLike | null = null;
let hoverSynth: SynthLike | null = null;
let questSynth: SynthLike | null = null;
let dialogSynth: SynthLike | null = null;
let chimeSynth: SynthLike | null = null;
let lastToneStartAt = 0;

const lastFiredAt = new Map<string, number>();

function shouldFire(key: string, cooldownMs: number): boolean {
  if (muted) return false;
  if (!unlocked) return false;
  const effectiveCooldown = reducedMotionMode ? cooldownMs * 3 : cooldownMs;
  const now = Date.now();
  const prev = lastFiredAt.get(key) ?? 0;
  if (now - prev < effectiveCooldown) return false;
  lastFiredAt.set(key, now);
  return true;
}

async function loadTone(): Promise<ToneModule | null> {
  if (toneModule) return toneModule;
  if (!toneLoadPromise) {
    toneLoadPromise = import('tone')
      .then((mod) => {
        toneModule = mod;
        return mod;
      })
      .catch(() => null);
  }
  return toneLoadPromise;
}

function getTone(): ToneModule | null {
  return toneModule;
}

function nextToneStartTime(tone: ToneModule): number {
  const next = Math.max(tone.now() + 0.005, lastToneStartAt + 0.02);
  lastToneStartAt = next;
  return next;
}

function detectReducedMotion(): void {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
  try {
    const mql = window.matchMedia('(prefers-reduced-motion: reduce)');
    reducedMotionMode = mql.matches;
    if (typeof mql.addEventListener === 'function') {
      mql.addEventListener('change', (event) => {
        reducedMotionMode = event.matches;
      });
    }
  } catch {
    // SSR / 不支持 matchMedia 时静默
  }
}

if (typeof window !== 'undefined') {
  detectReducedMotion();
}

/** 用户交互后调用，激活 AudioContext */
export async function unlockStardewFx(): Promise<void> {
  if (unlocked) return;
  try {
    const tone = await loadTone();
    if (!tone) return;
    await tone.start();
    unlocked = true;
  } catch {
    // 静默：jsdom 等环境无 AudioContext
  }
}

if (typeof window !== 'undefined') {
  const handler = (): void => {
    void unlockStardewFx();
    window.removeEventListener('click', handler);
    window.removeEventListener('keydown', handler);
    window.removeEventListener('touchstart', handler);
  };
  window.addEventListener('click', handler, { once: true });
  window.addEventListener('keydown', handler, { once: true });
  window.addEventListener('touchstart', handler, { once: true });
}

export function setStardewFxMuted(value: boolean): void {
  muted = value;
}

export function isStardewFxMuted(): boolean {
  return muted;
}

function ensureStepSynth(): SynthLike | null {
  const tone = getTone();
  if (!stepSynth && unlocked && tone) {
    try {
      stepSynth = new tone.MembraneSynth({
        octaves: 2,
        pitchDecay: 0.04,
        envelope: { attack: 0.001, decay: 0.08, sustain: 0, release: 0.08 },
        volume: -22,
      }).toDestination() as SynthLike;
    } catch {
      stepSynth = null;
    }
  }
  return stepSynth;
}

function ensureClickSynth(): SynthLike | null {
  const tone = getTone();
  if (!clickSynth && unlocked && tone) {
    try {
      clickSynth = new tone.MetalSynth({
        envelope: { attack: 0.001, decay: 0.08, release: 0.05 },
        harmonicity: 5.1,
        modulationIndex: 24,
        resonance: 2000,
        octaves: 1.5,
        volume: -20,
      }).toDestination() as SynthLike;
    } catch {
      clickSynth = null;
    }
  }
  return clickSynth;
}

function ensureHoverSynth(): SynthLike | null {
  const tone = getTone();
  if (!hoverSynth && unlocked && tone) {
    try {
      hoverSynth = new tone.Synth({
        oscillator: { type: 'triangle' },
        envelope: { attack: 0.005, decay: 0.05, sustain: 0, release: 0.05 },
        volume: -28,
      }).toDestination() as SynthLike;
    } catch {
      hoverSynth = null;
    }
  }
  return hoverSynth;
}

function ensureQuestSynth(): SynthLike | null {
  const tone = getTone();
  if (!questSynth && unlocked && tone) {
    try {
      questSynth = new tone.PolySynth(tone.Synth, {
        oscillator: { type: 'fmsquare' },
        envelope: { attack: 0.01, decay: 0.2, sustain: 0.2, release: 0.4 },
        volume: -14,
      }).toDestination() as SynthLike;
    } catch {
      questSynth = null;
    }
  }
  return questSynth;
}

function ensureDialogSynth(): SynthLike | null {
  const tone = getTone();
  if (!dialogSynth && unlocked && tone) {
    try {
      dialogSynth = new tone.Synth({
        oscillator: { type: 'sine' },
        envelope: { attack: 0.01, decay: 0.1, sustain: 0, release: 0.1 },
        volume: -22,
      }).toDestination() as SynthLike;
    } catch {
      dialogSynth = null;
    }
  }
  return dialogSynth;
}

function ensureChimeSynth(): SynthLike | null {
  const tone = getTone();
  if (!chimeSynth && unlocked && tone) {
    try {
      chimeSynth = new tone.PolySynth(tone.Synth, {
        oscillator: { type: 'sine' },
        envelope: { attack: 0.03, decay: 0.5, sustain: 0.3, release: 0.8 },
        volume: -18,
      }).toDestination() as SynthLike;
    } catch {
      chimeSynth = null;
    }
  }
  return chimeSynth;
}

export type TerrainKind = 'grass' | 'stone' | 'sand' | 'wood' | 'water';

const TERRAIN_NOTES: Record<TerrainKind, string> = {
  grass: 'C2',
  stone: 'A1',
  sand: 'E2',
  wood: 'G1',
  water: 'D2',
};

/** 玩家走动脚步声（按地形微变 pitch），60ms 节流 */
export function playStep(terrain: TerrainKind = 'grass'): void {
  if (!shouldFire('step', 60)) return;
  const synth = ensureStepSynth();
  const tone = getTone();
  if (!synth || !tone) return;
  try {
    synth.triggerAttackRelease(TERRAIN_NOTES[terrain], '32n', nextToneStartTime(tone));
  } catch {
    // 忽略
  }
}

/** 按钮点击「叮」声，80ms 节流 */
export function playUiClick(): void {
  if (!shouldFire('ui-click', 80)) return;
  const synth = ensureClickSynth();
  const tone = getTone();
  if (!synth || !tone) return;
  try {
    synth.triggerAttackRelease('32n', nextToneStartTime(tone), 0.7);
  } catch {
    // 忽略
  }
}

/** 按钮 hover 轻提示音 */
export function playUiHover(): void {
  if (!shouldFire('ui-hover', 120)) return;
  const synth = ensureHoverSynth();
  const tone = getTone();
  if (!synth || !tone) return;
  try {
    synth.triggerAttackRelease('A5', '64n', nextToneStartTime(tone));
  } catch {
    // 忽略
  }
}

/** 任务完成 fanfare（3 和弦） */
export function playQuestComplete(): void {
  if (!shouldFire('quest', 400)) return;
  const synth = ensureQuestSynth();
  const tone = getTone();
  if (!synth || !tone) return;
  try {
    const now = nextToneStartTime(tone);
    synth.triggerAttackRelease(['C4', 'E4', 'G4'], '8n', now);
    synth.triggerAttackRelease(['F4', 'A4', 'C5'], '8n', now + 0.2);
    synth.triggerAttackRelease(['G4', 'B4', 'D5'], '4n', now + 0.4);
  } catch {
    // 忽略
  }
}

/** NPC 对话打开 / 关闭 短促音 */
export function playDialogOpen(): void {
  if (!shouldFire('dialog-open', 150)) return;
  const synth = ensureDialogSynth();
  const tone = getTone();
  if (!synth || !tone) return;
  try {
    synth.triggerAttackRelease('A4', '16n', nextToneStartTime(tone));
  } catch {
    // 忽略
  }
}

export function playDialogClose(): void {
  if (!shouldFire('dialog-close', 150)) return;
  const synth = ensureDialogSynth();
  const tone = getTone();
  if (!synth || !tone) return;
  try {
    synth.triggerAttackRelease('E4', '16n', nextToneStartTime(tone));
  } catch {
    // 忽略
  }
}

/** 昼夜 / 季节切换 chime（4 音） */
export function playChime(mood: 'dawn' | 'dusk' | 'season' = 'dawn'): void {
  if (!shouldFire(`chime-${mood}`, 1500)) return;
  const synth = ensureChimeSynth();
  const tone = getTone();
  if (!synth || !tone) return;
  const notes: Record<string, string[]> = {
    dawn: ['E5', 'G5', 'A5'],
    dusk: ['A4', 'F4', 'D4'],
    season: ['C5', 'E5', 'G5', 'C6'],
  };
  try {
    const now = nextToneStartTime(tone);
    notes[mood]?.forEach((note, i) => {
      synth.triggerAttackRelease(note, '8n', now + i * 0.15);
    });
  } catch {
    // 忽略
  }
}

/** 释放所有 synth（测试 / SSR / 切页清理） */
export function disposeStardewFx(): void {
  [stepSynth, clickSynth, hoverSynth, questSynth, dialogSynth, chimeSynth].forEach(
    (s) => {
      try {
        s?.dispose();
      } catch {
        // 忽略
      }
    },
  );
  stepSynth = null;
  clickSynth = null;
  hoverSynth = null;
  questSynth = null;
  dialogSynth = null;
  chimeSynth = null;
}

/** 仅测试使用：复位静音/解锁状态 + 清空节流 map */
export function __resetStardewFxForTest(): void {
  muted = false;
  unlocked = false;
  toneModule = null;
  toneLoadPromise = null;
  lastToneStartAt = 0;
  lastFiredAt.clear();
  disposeStardewFx();
}
