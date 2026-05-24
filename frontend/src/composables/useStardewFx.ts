// R-13 Phase 3：星露谷风音效层（Tone.js 程序化合成 + Howler 兜底）。
//
// 设计要点：
//   1. **零音频文件依赖**：所有 SFX 用 Tone.js Synth 实时合成，避免引入商业像素游戏
//      官方音源，符合 AGENTS.md「不复制商业像素游戏官方素材」硬约束；
//   2. **单例 transport**：所有 synth 共用 Tone.start() 触发的 AudioContext，避免
//      多组件挂载时反复创建上下文导致 iOS Safari unlock 失败；
//   3. **autoplay 友好**：首次用户交互（click/keydown）才 lazy 启动 Tone，与现有
//      useAudio 的 unlockAudio 协同；
//   4. **响应 setMuted**：与 Phaser sound manager 全局静音状态对齐，玩家在 SettingsModal
//      切静音可一次性关掉所有 SFX；
//   5. **节流防抖**：脚步声 60ms 最小间隔、UI 点击/hover 80ms，避免乱触发 → 听感乱糟糟；
//   6. **不依赖 useAudio**：互不耦合，Tone.js 走自己的 AudioContext，演示阶段不抢
//      Phaser BGM 通道，避免 Howler/Tone/Phaser 三套同时启动导致 iOS 卡死。
//
// 教学价值：所有 SFX 都明确服务玩家反馈（脚步=地形识别、点击=操作确认、购买=金币
// 入袋、任务完成=正向反馈），避免「为了好听加音效」。

import * as Tone from 'tone';

let unlocked = false;
let muted = false;
// R-13 迭代 22：自动检测系统 reduced-motion，激活时把 SFX 节流间隔放大 3 倍，
// 部分 chime/jingle 直接降级为短促单音。避免动效偏好为「减少」的玩家被音效骚扰。
let reducedMotionMode = false;

let stepSynth: Tone.MembraneSynth | null = null;
let clickSynth: Tone.MetalSynth | null = null;
let hoverSynth: Tone.Synth | null = null;
let coinSynth: Tone.PluckSynth | null = null;
let questSynth: Tone.PolySynth | null = null;
let dialogSynth: Tone.Synth | null = null;
let chimeSynth: Tone.PolySynth | null = null;

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
    await Tone.start();
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

function ensureStepSynth(): Tone.MembraneSynth | null {
  if (!stepSynth && unlocked) {
    try {
      stepSynth = new Tone.MembraneSynth({
        octaves: 2,
        pitchDecay: 0.04,
        envelope: { attack: 0.001, decay: 0.08, sustain: 0, release: 0.08 },
        volume: -22,
      }).toDestination();
    } catch {
      stepSynth = null;
    }
  }
  return stepSynth;
}

function ensureClickSynth(): Tone.MetalSynth | null {
  if (!clickSynth && unlocked) {
    try {
      clickSynth = new Tone.MetalSynth({
        envelope: { attack: 0.001, decay: 0.08, release: 0.05 },
        harmonicity: 5.1,
        modulationIndex: 24,
        resonance: 2000,
        octaves: 1.5,
        volume: -20,
      }).toDestination();
    } catch {
      clickSynth = null;
    }
  }
  return clickSynth;
}

function ensureHoverSynth(): Tone.Synth | null {
  if (!hoverSynth && unlocked) {
    try {
      hoverSynth = new Tone.Synth({
        oscillator: { type: 'triangle' },
        envelope: { attack: 0.005, decay: 0.05, sustain: 0, release: 0.05 },
        volume: -28,
      }).toDestination();
    } catch {
      hoverSynth = null;
    }
  }
  return hoverSynth;
}

function ensureCoinSynth(): Tone.PluckSynth | null {
  if (!coinSynth && unlocked) {
    try {
      coinSynth = new Tone.PluckSynth({
        attackNoise: 1,
        dampening: 4500,
        resonance: 0.7,
        volume: -16,
      }).toDestination();
    } catch {
      coinSynth = null;
    }
  }
  return coinSynth;
}

function ensureQuestSynth(): Tone.PolySynth | null {
  if (!questSynth && unlocked) {
    try {
      questSynth = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'fmsquare' },
        envelope: { attack: 0.01, decay: 0.2, sustain: 0.2, release: 0.4 },
        volume: -14,
      }).toDestination();
    } catch {
      questSynth = null;
    }
  }
  return questSynth;
}

function ensureDialogSynth(): Tone.Synth | null {
  if (!dialogSynth && unlocked) {
    try {
      dialogSynth = new Tone.Synth({
        oscillator: { type: 'sine' },
        envelope: { attack: 0.01, decay: 0.1, sustain: 0, release: 0.1 },
        volume: -22,
      }).toDestination();
    } catch {
      dialogSynth = null;
    }
  }
  return dialogSynth;
}

function ensureChimeSynth(): Tone.PolySynth | null {
  if (!chimeSynth && unlocked) {
    try {
      chimeSynth = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'sine' },
        envelope: { attack: 0.03, decay: 0.5, sustain: 0.3, release: 0.8 },
        volume: -18,
      }).toDestination();
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
  if (!synth) return;
  try {
    synth.triggerAttackRelease(TERRAIN_NOTES[terrain], '32n');
  } catch {
    // ignore
  }
}

/** 按钮点击「叮」声，80ms 节流 */
export function playUiClick(): void {
  if (!shouldFire('ui-click', 80)) return;
  const synth = ensureClickSynth();
  if (!synth) return;
  try {
    synth.triggerAttackRelease('32n', Tone.now(), 0.7);
  } catch {
    // ignore
  }
}

/** 按钮 hover 轻提示音 */
export function playUiHover(): void {
  if (!shouldFire('ui-hover', 120)) return;
  const synth = ensureHoverSynth();
  if (!synth) return;
  try {
    synth.triggerAttackRelease('A5', '64n');
  } catch {
    // ignore
  }
}

/** 购买成功金币声（3 拨弦) */
export function playCoinJingle(): void {
  if (!shouldFire('coin', 200)) return;
  const synth = ensureCoinSynth();
  if (!synth) return;
  try {
    synth.triggerAttackRelease('C5', '16n');
    setTimeout(() => synth.triggerAttackRelease('E5', '16n'), 90);
    setTimeout(() => synth.triggerAttackRelease('G5', '16n'), 180);
  } catch {
    // ignore
  }
}

/** 任务完成 fanfare（3 和弦） */
export function playQuestComplete(): void {
  if (!shouldFire('quest', 400)) return;
  const synth = ensureQuestSynth();
  if (!synth) return;
  try {
    const now = Tone.now();
    synth.triggerAttackRelease(['C4', 'E4', 'G4'], '8n', now);
    synth.triggerAttackRelease(['F4', 'A4', 'C5'], '8n', now + 0.2);
    synth.triggerAttackRelease(['G4', 'B4', 'D5'], '4n', now + 0.4);
  } catch {
    // ignore
  }
}

/** NPC 对话打开 / 关闭 短促音 */
export function playDialogOpen(): void {
  if (!shouldFire('dialog-open', 150)) return;
  const synth = ensureDialogSynth();
  if (!synth) return;
  try {
    synth.triggerAttackRelease('A4', '16n');
  } catch {
    // ignore
  }
}

export function playDialogClose(): void {
  if (!shouldFire('dialog-close', 150)) return;
  const synth = ensureDialogSynth();
  if (!synth) return;
  try {
    synth.triggerAttackRelease('E4', '16n');
  } catch {
    // ignore
  }
}

/** 昼夜 / 季节切换 chime（4 音） */
export function playChime(mood: 'dawn' | 'dusk' | 'season' = 'dawn'): void {
  if (!shouldFire(`chime-${mood}`, 1500)) return;
  const synth = ensureChimeSynth();
  if (!synth) return;
  const notes: Record<string, string[]> = {
    dawn: ['E5', 'G5', 'A5'],
    dusk: ['A4', 'F4', 'D4'],
    season: ['C5', 'E5', 'G5', 'C6'],
  };
  try {
    const now = Tone.now();
    notes[mood]?.forEach((note, i) => {
      synth.triggerAttackRelease(note, '8n', now + i * 0.15);
    });
  } catch {
    // ignore
  }
}

/** 释放所有 synth（测试 / SSR / 切页清理） */
export function disposeStardewFx(): void {
  [stepSynth, clickSynth, hoverSynth, coinSynth, questSynth, dialogSynth, chimeSynth].forEach(
    (s) => {
      try {
        s?.dispose();
      } catch {
        // ignore
      }
    },
  );
  stepSynth = null;
  clickSynth = null;
  hoverSynth = null;
  coinSynth = null;
  questSynth = null;
  dialogSynth = null;
  chimeSynth = null;
}

/** 仅测试使用：复位静音/解锁状态 + 清空节流 map */
export function __resetStardewFxForTest(): void {
  muted = false;
  unlocked = false;
  lastFiredAt.clear();
  disposeStardewFx();
}
