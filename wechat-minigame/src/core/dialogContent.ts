// 交互内容（视图模型）：
//  - 读取随包 NPC 对白数据（assets/data/npcs.json），按 key 查「名字 + 一句话」；
//  - 维护学习站点（建筑 route → 中文名 + 简介）映射；
//  - 把一个 Interactable 组装成对话框可直接渲染的 DialogView。
// 本模块不触碰渲染，也不持有运行时状态，纯数据 + 纯函数（数据加载除外）。
import { companionEnabled } from '../config';
import { logError, readTextFileSync } from '../runtime/wx';
import type { InteractKind, Interactable } from './TileMap';

const NPC_DATA_PATH = 'assets/data/npcs.json';

export interface NpcLine {
  name: string;
  line: string;
}

/** 随包 NPC 对白本：key → { 名字, 一句对白 }。缺失时安全返回 null。 */
export class NpcLineBook {
  constructor(private readonly entries: Map<string, NpcLine>) {}

  lookup(key: string): NpcLine | null {
    return this.entries.get(key) ?? null;
  }

  get size(): number {
    return this.entries.size;
  }
}

/** 加载随包 NPC 对白；任何失败都回退为空对白本（NPC 退化为地图标签 + key）。 */
export function loadNpcLineBook(): NpcLineBook {
  try {
    const text = readTextFileSync(NPC_DATA_PATH);
    const parsed: unknown = JSON.parse(text);
    return new NpcLineBook(parseNpcLines(parsed));
  } catch (err) {
    logError('[pythontopia] NPC 对白数据加载失败（回退到地图标签）', err);
    return new NpcLineBook(new Map<string, NpcLine>());
  }
}

interface Station {
  name: string;
  desc: string;
  /** 是否学习站点（决定徽标与是否可跳转配套学习小程序）。 */
  learning: boolean;
}

// 学习站点：建筑 route → 站点名 + 一句简介。route 取自地图对象的 route 属性。
const STATIONS: Readonly<Record<string, Station>> = {
  '/workshop': { name: '百晓工坊', desc: '提问当 AI 助教答疑导学，或生成六类专属学习资源。', learning: true },
  '/library': { name: '图书馆', desc: '561 页课件原文随你翻阅，凭证据页码学习。', learning: true },
  '/training': { name: '训练场', desc: '提交真题、服务端判题，把掌握度顶上阈值。', learning: true },
  '/bug-mine': { name: 'Bug 矿洞', desc: '把失败提交化作 Bug，逐层调试复盘（支线）。', learning: true },
  '/review-hall': { name: '复盘大厅', desc: '回顾本周学习，巩固高频易错知识点。', learning: true },
  '/arena/1': { name: '闯关竞技场', desc: '章节综合挑战，集齐子任务挑战章末 Boss。', learning: true },
  '/profile-room': { name: '镇长大厅', desc: '查看并完善你的 8 维学习画像。', learning: true },
};

export interface DialogDeepLink {
  route: string;
  label: string;
}

/** 对话框渲染所需的全部内容（已本地化、可直接画）。 */
export interface DialogView {
  kind: InteractKind;
  title: string;
  badge: string;
  body: string;
  /** 小字提示（如「学习内容在配套学习小程序中」）；无则空串。 */
  footnote: string;
  /** 跳转配套学习小程序的入口；未配置或非学习站点为 null。 */
  deepLink: DialogDeepLink | null;
}

/** 把可交互对象组装成对话框视图：NPC 取对白，建筑取学习站点，地标取风味文案。 */
export function buildDialogView(item: Interactable, book: NpcLineBook): DialogView {
  if (item.kind === 'npc') {
    const found = item.npcKey ? book.lookup(item.npcKey) : null;
    const title = item.title || found?.name || item.npcKey || 'NPC';
    const body = found?.line ?? `这位是 ${title}（${item.npcKey || '镇民'}）。`;
    return { kind: 'npc', title, badge: 'NPC', body, footnote: '', deepLink: null };
  }

  if (item.kind === 'building') {
    const station = item.route ? STATIONS[item.route] : undefined;
    const title = item.title || station?.name || '建筑';
    if (station?.learning) {
      const enabled = companionEnabled();
      return {
        kind: 'building',
        title,
        badge: '学习站点',
        body: station.desc,
        footnote: enabled ? '' : '学习内容在配套学习小程序中',
        deepLink: enabled ? { route: item.route, label: '前往学习小程序 ›' } : null,
      };
    }
    const body = station
      ? station.desc
      : item.route
        ? `这里通往：${item.route}`
        : '小镇里的一处建筑。';
    return { kind: 'building', title, badge: '建筑', body, footnote: '', deepLink: null };
  }

  const title = item.title || '地标';
  return { kind: 'landmark', title, badge: '地标', body: landmarkBody(title), footnote: '', deepLink: null };
}

function landmarkBody(title: string): string {
  if (title.includes('喷泉')) {
    return '镜月喷泉在镇中心静静流淌，是小镇的地标。';
  }
  return '小镇里的一处地标，停下脚步看看吧。';
}

function parseNpcLines(data: unknown): Map<string, NpcLine> {
  const out = new Map<string, NpcLine>();
  if (!isRecord(data)) {
    return out;
  }
  const npcs = data['npcs'];
  if (!isRecord(npcs)) {
    return out;
  }
  for (const key of Object.keys(npcs)) {
    const entry = npcs[key];
    if (!isRecord(entry)) {
      continue;
    }
    const name = entry['name'];
    const line = entry['line'];
    if (typeof name === 'string' && typeof line === 'string') {
      out.set(key, { name, line });
    }
  }
  return out;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
