// 隔离原因：集中 LPC 精灵表的"帧数学"与 Phaser 动画注册，避免各处自写 row/col→frame。
// adapter 层（出现 Phaser 概念，但不直接 import phaser，只用最小接口）。
//
// 支持三种布局：
//   - `walk9`   ：9 列 × 4 行仅行走表（旧 NPC 合成图 npc_*.png）。
//   - `universal`：13 列 × 21 行标准 LPC 表（旧主角 SaraFullSheet + 工具叠加层）。
//   - `ulpc`    ：13 列 × 42 行「Expanded Universal LPC」表（官方生成器产出）：
//                 base 6 动作(0-20) + climb(21)/idle(22)/jump(26)/sit(30)/emote(34)/run(38)。
//                 由 scripts/build_ulpc_characters.py 用官方 ULPC 素材库合成。
// 偏移与帧数取自官方 sources/state/constants.ts（ANIMATION_OFFSETS / ANIMATION_CONFIGS）。
import type { Direction } from '@api/dto/Direction';

export type CardinalDirection = 'UP' | 'LEFT' | 'DOWN' | 'RIGHT';

export type LpcLayout = 'universal' | 'walk9' | 'ulpc';

/** 星露谷风高层动作。 */
export type CharacterAction =
  | 'idle'
  | 'walk'
  | 'run'
  | 'jump'
  | 'sit'
  | 'emote'
  | 'cast'
  | 'thrust'
  | 'slash'
  | 'shoot'
  | 'hurt'
  | 'climb'
  | 'water'
  | 'hoe'
  | 'chop'
  | 'mine'
  | 'fish'
  | 'interact'
  | 'celebrate';

type Bank =
  | 'spellcast'
  | 'thrust'
  | 'walk'
  | 'slash'
  | 'shoot'
  | 'hurt'
  | 'climb'
  | 'idle'
  | 'jump'
  | 'sit'
  | 'emote'
  | 'run';

const DIR_ROW_ORDER: readonly CardinalDirection[] = ['UP', 'LEFT', 'DOWN', 'RIGHT'];

const UNIVERSAL_COLS = 13;
const WALK9_COLS = 9;

interface BankSpec {
  firstRow: number;
  frames: number;
  /** 单行动画（所有朝向复用同一行，如 hurt / climb）。 */
  singleRow?: boolean;
}

// 13 列表的 bank 行偏移（universal 用前 6 个；ulpc 用全部）。与官方 constants.ts 对齐。
const BANKS_13: Readonly<Record<Bank, BankSpec>> = {
  spellcast: { firstRow: 0, frames: 7 },
  thrust: { firstRow: 4, frames: 8 },
  walk: { firstRow: 8, frames: 9 },
  slash: { firstRow: 12, frames: 6 },
  shoot: { firstRow: 16, frames: 13 },
  hurt: { firstRow: 20, frames: 6, singleRow: true },
  climb: { firstRow: 21, frames: 6, singleRow: true },
  idle: { firstRow: 22, frames: 2 },
  jump: { firstRow: 26, frames: 5 },
  sit: { firstRow: 30, frames: 3 },
  emote: { firstRow: 34, frames: 3 },
  run: { firstRow: 38, frames: 8 },
};

const WALK9_WALK_ROW: Readonly<Record<CardinalDirection, number>> = { UP: 0, LEFT: 1, DOWN: 2, RIGHT: 3 };

const UNIVERSAL_BANKS: ReadonlySet<Bank> = new Set(['spellcast', 'thrust', 'walk', 'slash', 'shoot', 'hurt']);

function bankAvailable(layout: LpcLayout, bank: Bank): boolean {
  if (layout === 'walk9') return bank === 'walk';
  if (layout === 'universal') return UNIVERSAL_BANKS.has(bank);
  return true; // ulpc：全部
}

// 动作 → bank（与布局相关：run/jump/idle/sit/emote 仅 ulpc 有真实帧）。
function actionBank(layout: LpcLayout, action: CharacterAction): Bank | null {
  switch (action) {
    case 'walk':
      return 'walk';
    case 'run':
      return layout === 'ulpc' ? 'run' : 'walk'; // 非 ulpc：用走帧加速代替
    case 'jump':
      return layout === 'ulpc' ? 'jump' : null;
    case 'idle':
      return layout === 'ulpc' ? 'idle' : null;
    case 'sit':
      return layout === 'ulpc' ? 'sit' : null;
    case 'emote':
      return layout === 'ulpc' ? 'emote' : 'spellcast';
    case 'climb':
      return layout === 'ulpc' ? 'climb' : null;
    case 'cast':
    case 'interact':
    case 'celebrate':
      return 'spellcast';
    case 'thrust':
    case 'water':
    case 'hoe':
      return 'thrust';
    case 'slash':
    case 'chop':
    case 'mine':
      return 'slash';
    case 'shoot':
    case 'fish':
      return 'shoot';
    case 'hurt':
      return 'hurt';
  }
}

const BANK_FRAME_RATE: Readonly<Record<Bank, number>> = {
  spellcast: 9,
  thrust: 12,
  walk: 10,
  slash: 13,
  shoot: 8,
  hurt: 7,
  climb: 8,
  idle: 4,
  jump: 10,
  sit: 6,
  emote: 6,
  run: 14,
};

const LOOP_ACTIONS: ReadonlySet<CharacterAction> = new Set(['walk', 'run', 'idle']);

const ALL_ACTIONS: readonly CharacterAction[] = [
  'walk', 'run', 'jump', 'idle', 'sit', 'emote', 'climb',
  'cast', 'thrust', 'slash', 'shoot', 'hurt',
  'water', 'hoe', 'chop', 'mine', 'fish', 'interact', 'celebrate',
];

/** 对角线方向收敛到最近 4 向（横向优先）。 */
export function cardinalize(dir: Direction): CardinalDirection {
  if (dir === 'UP' || dir === 'DOWN' || dir === 'LEFT' || dir === 'RIGHT') return dir;
  if (dir === 'UP_LEFT' || dir === 'DOWN_LEFT') return 'LEFT';
  if (dir === 'UP_RIGHT' || dir === 'DOWN_RIGHT') return 'RIGHT';
  return 'DOWN';
}

function colsFor(layout: LpcLayout): number {
  return layout === 'walk9' ? WALK9_COLS : UNIVERSAL_COLS;
}

/** 某布局某朝向的「待机帧」绝对索引（走路周期第 0 帧）。 */
export function idleFrame(layout: LpcLayout, dir: Direction): number {
  const cardinal = cardinalize(dir);
  if (layout === 'walk9') return WALK9_WALK_ROW[cardinal] * WALK9_COLS;
  return (BANKS_13.walk.firstRow + DIR_ROW_ORDER.indexOf(cardinal)) * UNIVERSAL_COLS;
}

function bankFramesByBank(layout: LpcLayout, bank: Bank, dir: Direction): number[] {
  const cols = colsFor(layout);
  const cardinal = cardinalize(dir);

  if (layout === 'walk9') {
    if (bank !== 'walk') return [];
    const base = WALK9_WALK_ROW[cardinal] * cols;
    return [1, 2, 3, 4, 5, 6, 7, 8].map((o) => base + o);
  }

  if (!bankAvailable(layout, bank)) return [];
  const spec = BANKS_13[bank];
  const row = spec.singleRow ? spec.firstRow : spec.firstRow + DIR_ROW_ORDER.indexOf(cardinal);
  const base = row * cols;
  if (bank === 'walk') return Array.from({ length: spec.frames - 1 }, (_, i) => base + i + 1);
  return Array.from({ length: spec.frames }, (_, i) => base + i);
}

/** 动画 key：`<prefix>_<action>_<dir>`。 */
export function animKey(prefix: string, action: CharacterAction, dir: Direction): string {
  return `${prefix}_${action}_${cardinalize(dir).toLowerCase()}`;
}

/** 该动作在该布局下是否有真实帧。 */
export function actionAvailable(layout: LpcLayout, action: CharacterAction): boolean {
  const bank = actionBank(layout, action);
  return bank !== null && bankAvailable(layout, bank);
}

/** 解析要播放的动画 key；不可用返回 null（调用方降级）。 */
export function resolveAnimKey(
  prefix: string,
  layout: LpcLayout,
  action: CharacterAction,
  dir: Direction,
): string | null {
  if (!actionAvailable(layout, action)) return null;
  return animKey(prefix, action, dir);
}

// ---- 精灵表布局解析 ----

const UNIVERSAL_SHEET_KEYS = new Set<string>(['player_sara']);

export function spriteLayout(key: string): LpcLayout {
  if (key === 'player_sara2' || key.startsWith('ulpc_')) return 'ulpc';
  if (UNIVERSAL_SHEET_KEYS.has(key)) return 'universal';
  if (key.startsWith('npc_')) return 'walk9';
  return 'universal';
}

export function registerUniversalSheetKey(key: string): void {
  UNIVERSAL_SHEET_KEYS.add(key);
}

// ---- 工具叠加层（LPC Hand Tools，universal thrust 帧对齐）----

export interface ToolOverlay {
  fg: string;
  bg: string;
}

// R-14：农场浇水/锄头玩法已移除，不再挂载作物工具叠加层。
const ACTION_TOOL: Partial<Record<CharacterAction, ToolOverlay>> = {};

export function toolOverlayForAction(action: CharacterAction): ToolOverlay | null {
  return ACTION_TOOL[action] ?? null;
}

export const TOOL_TEXTURE_KEYS: readonly string[] = [];

// ---- 超尺寸武器叠加层（ULPC 192px，与标准 slash 同帧同步）----
// 砍树/挖矿用「斧头挥砍」：身体播标准 slash，斧头用 192px attack_slash 叠加。

export interface WeaponOverlay {
  fg: string;
  bg: string;
}

const ACTION_WEAPON: Partial<Record<CharacterAction, WeaponOverlay>> = {
  chop: { fg: 'weapon_axe', bg: 'weapon_axe_bg' },
  mine: { fg: 'weapon_axe', bg: 'weapon_axe_bg' },
};

export function weaponOverlayForAction(action: CharacterAction): WeaponOverlay | null {
  return ACTION_WEAPON[action] ?? null;
}

export const WEAPON_TEXTURE_KEYS: readonly string[] = ['weapon_axe', 'weapon_axe_bg'];

/** 192px 超尺寸武器图：6 列 × 4 行（up/left/down/right），仅 slash。key `<prefix>_slash_<dir>`。 */
export function registerWeaponSlashAnims(
  anims: AnimsManagerLike,
  textureKey: string,
  prefix: string,
): void {
  const rowByDir: Record<CardinalDirection, number> = { UP: 0, LEFT: 1, DOWN: 2, RIGHT: 3 };
  for (const dir of CARDINALS) {
    const key = `${prefix}_slash_${dir.toLowerCase()}`;
    if (anims.exists(key)) continue;
    const base = rowByDir[dir] * 6;
    const frames = [0, 1, 2, 3, 4, 5].map((i) => ({ key: textureKey, frame: base + i }));
    anims.create({ key, frames, frameRate: BANK_FRAME_RATE.slash, repeat: 0 });
  }
}

// ---- Phaser 动画注册 ----

export interface AnimFrameRef {
  key: string;
  frame: number;
}
export interface AnimCreateConfig {
  key: string;
  frames: AnimFrameRef[];
  frameRate: number;
  repeat: number;
}
export interface AnimsManagerLike {
  exists(key: string): boolean;
  create(config: AnimCreateConfig): unknown;
}

const CARDINALS: readonly CardinalDirection[] = ['UP', 'LEFT', 'DOWN', 'RIGHT'];

/**
 * 为某角色纹理注册全部可用动作动画（按布局裁剪）。
 * 命名 `<prefix>_<action>_<dir>`；walk/run/idle 循环，其余一次性。
 */
export function registerCharacterAnimations(
  anims: AnimsManagerLike,
  textureKey: string,
  prefix: string,
  layout: LpcLayout,
): void {
  for (const action of ALL_ACTIONS) {
    const bank = actionBank(layout, action);
    if (!bank || !bankAvailable(layout, bank)) continue;
    const repeat = LOOP_ACTIONS.has(action) ? -1 : 0;
    // run 在非 ulpc 用走帧加速；ulpc 用真实 run 帧。
    const frameRate = action === 'run' && layout !== 'ulpc' ? 16 : BANK_FRAME_RATE[bank];
    for (const dir of CARDINALS) {
      const key = animKey(prefix, action, dir);
      if (anims.exists(key)) continue;
      const frames = bankFramesByBank(layout, bank, dir).map((frame) => ({ key: textureKey, frame }));
      if (frames.length === 0) continue;
      anims.create({ key, frames, frameRate, repeat });
    }
  }
}
