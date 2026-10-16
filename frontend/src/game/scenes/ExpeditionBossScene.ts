import type { ExpeditionBossView } from '@/types/rogue';
import type { ExpeditionGameLike } from './ExpeditionScene';

export const EXPEDITION_BOSS_SCENE_KEY = 'ExpeditionBossScene';
export const BOSS_IMPACT_SOUND_KEY = 'sfx_boss_impact';

const BOSS_PORTRAITS: Readonly<Record<number, string>> = {
  1: '/assets/pixel/bosses/boss-1-abacus-spirit.png',
  2: '/assets/pixel/bosses/boss-2-interpreter-apprentice.png',
  3: '/assets/pixel/bosses/boss-3-binary-cyclop.png',
  4: '/assets/pixel/bosses/boss-4-loop-tower.png',
  5: '/assets/pixel/bosses/boss-5-dict-prism.png',
  6: '/assets/pixel/bosses/boss-6-file-keeper.png',
  7: '/assets/pixel/bosses/boss-7-mirror-abstraction.png',
};
const BOSS_IMPACT_SOUND_URL = '/assets/pixel/sfx/boss/impactSoft_medium_000.ogg';
const PIXEL_FONT = '"Zpix", "Ark Pixel 12", monospace';

interface GraphicsLike {
  x?: number;
  y?: number;
  fillStyle(color: number, alpha?: number): GraphicsLike;
  fillRect(x: number, y: number, width: number, height: number): GraphicsLike;
  fillRoundedRect(x: number, y: number, width: number, height: number, radius?: number): GraphicsLike;
  lineStyle(width: number, color: number, alpha?: number): GraphicsLike;
  lineBetween(x1: number, y1: number, x2: number, y2: number): GraphicsLike;
  strokeRoundedRect(x: number, y: number, width: number, height: number, radius?: number): GraphicsLike;
  setDepth(depth: number): GraphicsLike;
  setAlpha(alpha: number): GraphicsLike;
  setPosition(x: number, y?: number): GraphicsLike;
  setScale(scale: number): GraphicsLike;
  destroy(): void;
}

interface DisplayObjectLike {
  x?: number;
  y?: number;
  setOrigin(x: number, y?: number): DisplayObjectLike;
  setDepth(depth: number): DisplayObjectLike;
  setDisplaySize(width: number, height: number): DisplayObjectLike;
  setPosition(x: number, y?: number): DisplayObjectLike;
  setTint(color: number): DisplayObjectLike;
  clearTint(): DisplayObjectLike;
  destroy(): void;
}

interface TextLike {
  setOrigin(x: number, y?: number): TextLike;
  setDepth(depth: number): TextLike;
  setColor(color: string): TextLike;
}

interface SceneLoaderLike {
  image(key: string, url: string): void;
  audio(key: string, url: string | string[]): void;
}

export interface ExpeditionBossSceneContext {
  add: {
    graphics(): GraphicsLike;
    text(x: number, y: number, content: string, style?: Record<string, unknown>): TextLike;
    image(x: number, y: number, key: string): DisplayObjectLike;
  };
  load?: SceneLoaderLike;
  tweens?: {
    add(config: Record<string, unknown>): unknown;
    killAll?(): void;
  };
  cameras?: {
    main?: {
      shake(duration: number, intensity?: number): void;
      flash(duration: number, red?: number, green?: number, blue?: number): void;
    };
  };
  scale?: {
    width: number;
    height: number;
    on(event: string, callback: (...args: unknown[]) => void): void;
    off?(event: string, callback: (...args: unknown[]) => void): void;
  };
  events?: {
    on(event: string, callback: () => void): void;
  };
  children?: {
    removeAll(destroyChild?: boolean): void;
  };
}

export interface ExpeditionBossSceneOptions {
  onBossHit?: () => void;
}

let currentView: ExpeditionBossView | null = null;
let liveScene: ExpeditionBossSceneContext | null = null;
let bossPortrait: DisplayObjectLike | null = null;
let playerAvatar: GraphicsLike | null = null;
let lastHitAttemptId: string | null = null;
let onBossHit: (() => void) | null = null;

function portraitKey(chapter: number): string {
  return `expedition_boss_${chapter}`;
}

function sameView(left: ExpeditionBossView | null, right: ExpeditionBossView): boolean {
  return left !== null
    && left.chapter === right.chapter
    && left.name === right.name
    && left.phase === right.phase
    && left.phasesTotal === right.phasesTotal
    && left.hp === right.hp
    && left.maxHp === right.maxHp
    && left.status === right.status
    && left.attemptId === right.attemptId;
}

function reducedMotion(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function size(scene: ExpeditionBossSceneContext): { width: number; height: number } {
  return { width: scene.scale?.width ?? 960, height: scene.scale?.height ?? 360 };
}

function addText(
  scene: ExpeditionBossSceneContext,
  x: number,
  y: number,
  content: string,
  fontSize: string,
  color: string,
  originX = 0.5,
  originY = 0.5,
): TextLike {
  return scene.add.text(x, y, content, {
    fontFamily: PIXEL_FONT,
    fontSize,
    color,
  }).setOrigin(originX, originY).setDepth(5);
}

function drawBossArena(scene: ExpeditionBossSceneContext): void {
  if (!currentView) return;
  scene.tweens?.killAll?.();
  scene.children?.removeAll(true);
  bossPortrait = null;
  playerAvatar = null;

  const view = currentView;
  const { width, height } = size(scene);
  const background = scene.add.graphics();
  background.fillStyle(0x18213a, 1).fillRect(0, 0, width, height);
  background.fillStyle(0x25314f, 0.94).fillRect(0, 0, width, height * 0.72);
  background.fillStyle(0x485b73, 0.28).fillRect(0, height * 0.72, width, height * 0.12);
  background.fillStyle(0x354432, 1).fillRect(0, height * 0.84, width, height * 0.16);
  background.setDepth(0);

  const arenaLights = scene.add.graphics();
  arenaLights.fillStyle(0xf5d38a, 0.12).fillRoundedRect(width * 0.32, height * 0.09, width * 0.6, height * 0.72, 22);
  arenaLights.lineStyle(2, 0xf5d38a, 0.4).lineBetween(20, height * 0.84, width - 20, height * 0.84);
  arenaLights.setDepth(1);

  addText(scene, width / 2, 24, view.name, '16px', '#fff3c9');
  addText(scene, width - 20, 24, `阶段 ${view.phase} / ${view.phasesTotal}`, '13px', '#f8d989', 1, 0.5);
  drawPhaseProgress(scene, view, width);
  drawPlayer(scene, width, height);

  const portraitPath = BOSS_PORTRAITS[view.chapter];
  if (portraitPath) {
    const portraitWidth = Math.min(width * 0.56, height * 0.9 * 1.5);
    const portraitHeight = portraitWidth / 1.5;
    bossPortrait = scene.add.image(width * 0.59, height * 0.52, portraitKey(view.chapter))
      .setOrigin(0.5, 0.5)
      .setDisplaySize(portraitWidth, portraitHeight)
      .setDepth(2);
    if (!reducedMotion()) {
      scene.tweens?.add({
        targets: bossPortrait,
        y: height * 0.52 - 4,
        duration: 850,
        yoyo: true,
        repeat: -1,
        ease: 'Sine.easeInOut',
      });
    }
  }

  drawHealth(scene, view, width, height);
  const compact = width < 600;
  addText(
    scene,
    compact ? width / 2 : width * 0.72,
    compact ? height - 52 : height - 20,
    statusText(view),
    '12px',
    view.status === 'failed' ? '#ffd5ce' : '#fff3c9',
  );
}

function drawPhaseProgress(scene: ExpeditionBossSceneContext, view: ExpeditionBossView, width: number): void {
  const total = Math.max(1, Math.min(8, view.phasesTotal));
  const completed = view.status === 'passed' ? view.phase : view.phase - 1;
  const segmentWidth = Math.min(30, (width * 0.35) / total);
  const gap = 6;
  const startX = width / 2 - (total * segmentWidth + (total - 1) * gap) / 2;
  const progress = scene.add.graphics();
  for (let phase = 1; phase <= total; phase += 1) {
    const active = phase === view.phase;
    const color = phase <= completed ? 0x9fe0aa : active ? 0xffd166 : 0x607080;
    progress.fillStyle(color, active ? 1 : 0.78)
      .fillRoundedRect(startX + (phase - 1) * (segmentWidth + gap), 45, segmentWidth, 8, 3);
  }
  progress.setDepth(3);
}

function drawPlayer(scene: ExpeditionBossSceneContext, width: number, height: number): void {
  const x = width * 0.17;
  const y = height * 0.66;
  const avatar = scene.add.graphics();
  avatar.fillStyle(0xffd9a5, 1).fillRect(-7, -27, 14, 13);
  avatar.fillStyle(0x2caa89, 1).fillRoundedRect(-11, -13, 22, 24, 3);
  avatar.fillStyle(0x193f43, 1).fillRect(-10, 11, 8, 11).fillRect(2, 11, 8, 11);
  avatar.fillStyle(0xe8c36f, 1).fillRect(10, -5, 15, 4);
  avatar.setPosition(x, y).setDepth(3);
  playerAvatar = avatar;
  addText(scene, x, y + 34, '你 · 学习者', '11px', '#e2f1dc');
}

function drawHealth(
  scene: ExpeditionBossSceneContext,
  view: ExpeditionBossView,
  width: number,
  height: number,
): void {
  addText(scene, 18, height - 23, `体力 ${view.hp} / ${view.maxHp}`, '13px', '#ffd7cf', 0, 0.5);
  const maxHp = Math.max(0, Math.min(12, view.maxHp));
  const hp = Math.max(0, Math.min(maxHp, view.hp));
  const bar = scene.add.graphics();
  for (let index = 0; index < maxHp; index += 1) {
    bar.fillStyle(index < hp ? 0xe96d67 : 0x657080, 0.95)
      .fillRoundedRect(112 + index * 16, height - 30, 11, 13, 2);
  }
  bar.setDepth(3);
  if (width >= 600) {
    addText(scene, width - 18, height - 23, '体力取自本局结算', '10px', '#d4dded', 1, 0.5);
  }
}

function statusText(view: ExpeditionBossView): string {
  switch (view.status) {
    case 'judging': return '判题中 · 战场等待结果';
    case 'settling': return '正在确认本次结算';
    case 'passed': return '判题通过 · Boss 受到命中';
    case 'failed': return `未通过 · 当前体力 ${view.hp} / ${view.maxHp}`;
    case 'ready': return '提交答案开始挑战';
  }
}

function playBossHit(scene: ExpeditionBossSceneContext): void {
  if (!currentView || !bossPortrait) return;
  const { width, height } = size(scene);
  if (!reducedMotion()) {
    scene.cameras?.main?.shake(130, 0.008);
    scene.cameras?.main?.flash(90, 255, 230, 170);
    scene.tweens?.add({
      targets: bossPortrait,
      x: width * 0.59 + 14,
      yoyo: true,
      repeat: 2,
      duration: 36,
      ease: 'Sine.easeInOut',
    });
    if (playerAvatar) {
      scene.tweens?.add({
        targets: playerAvatar,
        x: width * 0.44,
        yoyo: true,
        duration: 110,
        ease: 'Quad.easeOut',
      });
    }
    const impact = scene.add.graphics();
    impact.fillStyle(0xfff0a6, 1)
      .fillRect(-2, -17, 4, 34)
      .fillRect(-17, -2, 34, 4)
      .fillRect(-12, -12, 7, 7)
      .fillRect(5, -10, 7, 7)
      .fillRect(-10, 5, 7, 7)
      .setPosition(width * 0.47, height * 0.55)
      .setScale(0.45)
      .setDepth(4)
      .setAlpha(1);
    scene.tweens?.add({
      targets: impact,
      scale: 1.4,
      alpha: 0,
      duration: 180,
      onComplete: () => impact.destroy(),
    });
  }
}

function isNewSettledPass(view: ExpeditionBossView): boolean {
  return view.status === 'passed'
    && view.attemptId !== null
    && view.attemptId !== lastHitAttemptId;
}

function playNewSettledPass(scene: ExpeditionBossSceneContext): void {
  if (!currentView || !isNewSettledPass(currentView)) return;
  lastHitAttemptId = currentView.attemptId;
  playBossHit(scene);
  onBossHit?.();
}

export function loadExpeditionBoss(
  view: ExpeditionBossView,
  options?: ExpeditionBossSceneOptions,
): void {
  if (options?.onBossHit !== undefined) onBossHit = options.onBossHit;
  const changed = !sameView(currentView, view);
  currentView = { ...view };
  if (!changed || !liveScene) return;

  drawBossArena(liveScene);
  playNewSettledPass(liveScene);
}

export function __resetExpeditionBossForTests(): void {
  currentView = null;
  liveScene = null;
  bossPortrait = null;
  playerAvatar = null;
  lastHitAttemptId = null;
  onBossHit = null;
}

export const ExpeditionBossScene = {
  key: EXPEDITION_BOSS_SCENE_KEY,
  preload(this: ExpeditionBossSceneContext): void {
    const view = currentView;
    if (!view) return;
    const portrait = BOSS_PORTRAITS[view.chapter];
    if (portrait) this.load?.image(portraitKey(view.chapter), portrait);
    this.load?.audio(BOSS_IMPACT_SOUND_KEY, BOSS_IMPACT_SOUND_URL);
  },
  create(this: ExpeditionBossSceneContext): void {
    // eslint-disable-next-line @typescript-eslint/no-this-alias -- cache scene context for updates until shutdown.
    liveScene = this;
    drawBossArena(this);
    playNewSettledPass(this);

    const resize = (): void => {
      if (liveScene === this) drawBossArena(this);
    };
    this.scale?.on('resize', resize);
    this.events?.on('shutdown', () => {
      this.scale?.off?.('resize', resize);
      if (liveScene === this) {
        liveScene = null;
        bossPortrait = null;
      }
    });
  },
};

function sceneRegistered(manager: NonNullable<ExpeditionGameLike['scene']>): boolean {
  if (manager.keys && EXPEDITION_BOSS_SCENE_KEY in manager.keys) return true;
  if (typeof manager.getScene === 'function') {
    try {
      return Boolean(manager.getScene(EXPEDITION_BOSS_SCENE_KEY));
    } catch {
      return false;
    }
  }
  return false;
}

export function ensureExpeditionBossSceneStarted(game: ExpeditionGameLike | null): void {
  const manager = game?.scene;
  if (!manager) return;
  if (!sceneRegistered(manager)) {
    manager.add?.(EXPEDITION_BOSS_SCENE_KEY, ExpeditionBossScene, true);
  } else {
    manager.start?.(EXPEDITION_BOSS_SCENE_KEY);
  }
  manager.bringToTop?.(EXPEDITION_BOSS_SCENE_KEY);
}

export function stopExpeditionBossScene(game: ExpeditionGameLike | null): void {
  game?.scene?.stop?.(EXPEDITION_BOSS_SCENE_KEY);
}
