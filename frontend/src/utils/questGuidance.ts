// 教学闭环（R-13 学习目标面板优化）：把「当前主线子任务」翻译成
//   1. 一句「下一步该干什么」；
//   2. 「该去找哪个 NPC / 去哪张地图」。
//
// 目标：学生不再只看到一个任务标题，而是清楚知道下一步动作 + 被自动指路到对应 NPC。
// NPC 对话再把学生引导进对应设施（工坊 / 训练场 / 图书馆），形成完整学习回路。
//
// 纯函数：只依赖静态任务数据，不碰 store / DOM / Phaser，方便单测与复用。

import { kcDisplayName } from '@/data/kcNames';
import type { MainQuestMapKey, MainSubQuest } from '@/types/mainQuest';

export type QuestActionKind =
  | 'dialog' // 与指定 NPC 对话（dialog_complete）
  | 'workshop' // 工坊生成资源（resource_consumed）
  | 'training' // 训练场提交判题（submission）
  | 'practice' // 训练场多刷题提升掌握进度（kc_mastery）
  | 'building'; // 走到地图指定建筑点（building_visit）

export interface QuestGuidance {
  kind: QuestActionKind;
  /** 要去找的 NPC key（真实存在于 data/npcs/<key>）。 */
  npcKey: string;
  /** NPC 中文名（UI 文案用）。 */
  npcName: string;
  /** 目标地图 key。 */
  mapKey: MainQuestMapKey;
  /** 目标地图中文名。 */
  mapLabel: string;
  /** 精确落点 tile（仅 building_visit 有值）：传送到此处即满足到点证据。 */
  tile: { x: number; y: number } | null;
  /** 是否需要先把玩家传送到目标地图（工坊/训练场走 NPC 对话内 route，无需传送）。 */
  teleport: boolean;
  /** 抵达后是否唤起 NPC 对话（building 任务靠到点完成，不开对话）。 */
  openDialog: boolean;
  /** 一句话「下一步该干什么」。 */
  stepText: string;
  /** 「前往」按钮文案。 */
  ctaText: string;
}

// R-14 方案A：世界收敛为单一中央小镇 town_hub，外围图（农场 / 学院 / 港口）已从主线与
// 导航弃用。学生侧只保留「小镇」与作为支线的「Bug 矿洞」，其余地图不再作为可前往目的地。
const MAP_LABELS: Readonly<Partial<Record<MainQuestMapKey, string>>> = Object.freeze({
  town_hub: 'Pythontopia 小镇',
  mine_south: 'Bug 矿洞',
});

// 与 data/npcs/index.json 对齐的中文名（只列引导会用到的 NPC）。
const NPC_NAMES: Readonly<Record<string, string>> = Object.freeze({
  mayor_py: '镇长 Py',
  mine_keeper: '矿洞守矿人',
  captain_def: '船长 Def',
  workshop_master: '工坊师傅',
  coach_q: '教务 Q',
  librarian_doc: '馆长 Doc',
  professor: '教授',
  sage_review: '复盘师 Sage',
});

// 六类资源类型的中文标签，用于「下一步」文案。
const RESOURCE_LABELS: Readonly<Record<string, string>> = Object.freeze({
  STORYBOARD: '历史故事板',
  HANDOUT: '讲义',
  READING: '拓展阅读',
  MINDMAP: '思维导图',
  PRACTICE: '练习题',
  CODE_LAB: '代码案例',
});

export function mapLabelOf(mapKey: MainQuestMapKey): string {
  // 已弃用的外围图一律回落到中央小镇，避免把新手指向不存在的死路目的地。
  return MAP_LABELS[mapKey] ?? MAP_LABELS.town_hub ?? 'Pythontopia 小镇';
}

export function npcNameOf(key: string): string {
  return NPC_NAMES[key] ?? key;
}

/** KC code → 面向学生的「知识点中文名」（不暴露原始编码）；空值回退到「本知识点」。 */
function kcLabel(code: string | undefined): string {
  return `「${kcDisplayName(code)}」`;
}

function resourceLabelOf(type: string): string {
  const zh = RESOURCE_LABELS[type];
  return zh ? `${zh}（${type}）` : type;
}

/**
 * 把一个主线子任务解析成「下一步指路」信息。
 *
 * 证据类型 → 对应 NPC / 设施：
 *   dialog_complete  → 任务指定 NPC（镇长 / 守矿人 / 船长…）
 *   resource_consumed→ 工坊师傅（对话内开「资源工坊」）
 *   submission       → 教务 Q（对话内开「训练场」）
 *   kc_mastery       → 教务 Q（训练场多刷题提升掌握进度）
 *   building_visit   → 馆长 Doc（图书馆点位；落点用证据 tile）
 */
export function resolveQuestGuidance(sub: MainSubQuest): QuestGuidance {
  const ev = sub.evidenceRequired;
  const kc = kcLabel(sub.kcCodes[0]);

  if (ev.type === 'dialog_complete') {
    const name = npcNameOf(ev.npcKey);
    return {
      kind: 'dialog',
      npcKey: ev.npcKey,
      npcName: name,
      mapKey: sub.mapKey,
      mapLabel: mapLabelOf(sub.mapKey),
      tile: null,
      teleport: true,
      openDialog: true,
      stepText: `前往「${mapLabelOf(sub.mapKey)}」，找${name}对话`,
      ctaText: `去找${name}`,
    };
  }

  if (ev.type === 'resource_consumed') {
    const name = npcNameOf('workshop_master');
    return {
      kind: 'workshop',
      npcKey: 'workshop_master',
      npcName: name,
      mapKey: 'town_hub',
      mapLabel: mapLabelOf('town_hub'),
      tile: null,
      teleport: false,
      openDialog: true,
      stepText: `找${name}进「工坊」，为${kc}生成一份${resourceLabelOf(ev.resourceType)}`,
      ctaText: '去工坊找工坊师傅',
    };
  }

  if (ev.type === 'submission') {
    const name = npcNameOf('coach_q');
    return {
      kind: 'training',
      npcKey: 'coach_q',
      npcName: name,
      mapKey: 'town_hub',
      mapLabel: mapLabelOf('town_hub'),
      tile: null,
      teleport: false,
      openDialog: true,
      stepText: `找${name}进「训练场」，提交一道${kc}练习并通过判题`,
      ctaText: '去训练场找教务 Q',
    };
  }

  if (ev.type === 'kc_mastery') {
    const name = npcNameOf('coach_q');
    return {
      kind: 'practice',
      npcKey: 'coach_q',
      npcName: name,
      mapKey: 'town_hub',
      mapLabel: mapLabelOf('town_hub'),
      tile: null,
      teleport: false,
      openDialog: true,
      stepText: `找${name}进「训练场」，多刷${kc}的题，把掌握进度刷到 ${ev.thresholdScore}`,
      ctaText: '去训练场找教务 Q',
    };
  }

  // 剩余唯一类型：building_visit（ev 已被 TS 收窄）。
  const name = npcNameOf('librarian_doc');
  return {
    kind: 'building',
    npcKey: 'librarian_doc',
    npcName: name,
    mapKey: ev.mapKey,
    mapLabel: mapLabelOf(ev.mapKey),
    tile: { x: ev.tileX, y: ev.tileY },
    teleport: true,
    openDialog: false,
    stepText: `前往「${mapLabelOf(ev.mapKey)}」图书馆的指定位置（走到即可完成）`,
    ctaText: '前往目标地点',
  };
}
