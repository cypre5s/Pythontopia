/**
 * dialogNpcData.spec.ts —— 用<b>真实 data/npcs/**</b> 驱动的对话回归测试。
 *
 * 与 dialog.spec.ts 的分工：那边全是合成 fixture（只验 store 状态机），
 * 因此「数据与 store 约定漂移」类缺陷（条件形同虚设 / 路由抢占 / questKey 无标识空间）
 * 全部从它下面漏过去。这里改为把 fetch 指到磁盘上的真实 dialog.json：
 *
 *   - 缺陷 2：路由类 effect 抢占 nextNodeId → retro_thanks / retro_seal 永久不可达；
 *   - 缺陷 3：effect 数组顺序决定跳转目标（"最后一个 effect 赢"）；
 *   - 缺陷 4：quest_recommend.questKey 在后端无对应标识空间，静默塌缩成主线地图；
 *   - 缺陷 5：give_item 可无限重复触发；first_time_this_week 在「看到」而非「完成」时消耗；
 *   - 缺陷 6：未被引用且无条件的死节点（arena_guard.enter）。
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  useDialogStore,
  resolveEffectRoute,
  hasDialogFlag,
  __resetChoiceListenersForTests,
  __resetDialogFlagsForTests,
  __resetQuestWarningsForTests,
  type DialogEffect,
  type NpcDialogTree,
} from '@/stores/dialog';
import { useToastStore } from '@/stores/toast';

vi.mock('@/api/dialog', () => ({
  fetchDialogContext: vi.fn(async () => ({
    playerName: '测试者',
    profileType: 'cautious',
    stats: {},
  })),
}));

const httpMocks = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('@/api/http', () => ({
  http: httpMocks,
  ApiClientError: class ApiClientError extends Error {},
}));

/**
 * 仓库根的 data/npcs 目录（<b>唯一真源</b>，非 frontend/public 下由 sync 脚本生成的镜像）。
 * vitest 的 cwd 通常是 frontend/；从仓库根跑时退回到 ./data/npcs。
 */
const NPC_DATA_DIR = ((): string => {
  const fromFrontend = resolve(process.cwd(), '../data/npcs');
  return existsSync(fromFrontend) ? fromFrontend : resolve(process.cwd(), 'data/npcs');
})();

function npcFile(npcKey: string, kind: 'meta' | 'dialog'): string {
  return resolve(NPC_DATA_DIR, npcKey, `${kind}.json`);
}

function readNpcTree(npcKey: string): NpcDialogTree {
  return JSON.parse(readFileSync(npcFile(npcKey, 'dialog'), 'utf8')) as NpcDialogTree;
}

function allNpcKeys(): string[] {
  return readdirSync(NPC_DATA_DIR).filter((entry) => existsSync(npcFile(entry, 'dialog')));
}

/** 把 fetch 接到磁盘：openWithTree 因此跑在真实 meta/dialog 数据上。 */
function installRealNpcFetch(): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      const matched = /\/data\/npcs\/([^/]+)\/(meta|dialog)\.json$/.exec(url);
      if (!matched) throw new Error(`unexpected url ${url}`);
      const body = readFileSync(npcFile(matched[1]!, matched[2] as 'meta' | 'dialog'), 'utf8');
      return new Response(body, {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }),
  );
}

/** 收集 pythontopia:dialog-navigate 事件，返回 [routes, 取消订阅]。 */
function captureNavigations(): [string[], () => void] {
  const routes: string[] = [];
  const onNavigate = (event: Event): void => {
    const detail = (event as CustomEvent<{ route?: string }>).detail;
    if (detail?.route) routes.push(detail.route);
  };
  window.addEventListener('pythontopia:dialog-navigate', onNavigate);
  return [routes, () => window.removeEventListener('pythontopia:dialog-navigate', onNavigate)];
}

beforeEach(() => {
  setActivePinia(createPinia());
  __resetChoiceListenersForTests();
  __resetDialogFlagsForTests();
  __resetQuestWarningsForTests();
  httpMocks.get.mockReset();
  httpMocks.get.mockResolvedValue({
    data: {
      greeting: '',
      available: false,
      grounded: false,
      citedEventIds: [],
      degraded: false,
      cached: false,
    },
  });
  installRealNpcFetch();
});

afterEach(() => {
  vi.unstubAllGlobals();
  __resetDialogFlagsForTests();
  vi.restoreAllMocks();
});

describe('真实 NPC 数据 · 静态契约', () => {
  it('每个 quest_recommend.questKey 都落在已知任务标识空间内', () => {
    // 后端主线标识空间是 mainline_stage(chapter, stage_index) + 数字主键，不存在 questKey；
    // 只允许显式落地过的 key 与「章-节」序号，杜绝 read_chapter_today 这类
    // 「根本不是任务却被当 quest 处理」的写法（点了就被静默拽去主线地图）。
    const allowed = new Set(['claim_class_quest']);
    const chapterStage = /^\d+-\d+$/;
    const offenders: string[] = [];
    for (const npcKey of allNpcKeys()) {
      const tree = readNpcTree(npcKey);
      for (const node of Object.values(tree.nodes)) {
        for (const choice of node.choices ?? []) {
          for (const effect of choice.effects ?? []) {
            if (effect.type !== 'quest_recommend') continue;
            const key = effect.questKey ?? '';
            if (!allowed.has(key) && !chapterStage.test(key)) {
              offenders.push(`${npcKey}.${node.id}: ${key}`);
            }
          }
        }
      }
    }
    expect(offenders).toEqual([]);
  });

  it('没有「未被引用且无条件」的死节点（永远进不去）', () => {
    // pickEntryNodeId 只把「带条件的未引用节点」当插话入口（conds.length === 0 直接 continue），
    // 因此无条件又没人引用的节点 100% 不可达 —— arena_guard.enter 曾是这样的哑节点。
    const dead: string[] = [];
    for (const npcKey of allNpcKeys()) {
      const tree = readNpcTree(npcKey);
      const referenced = new Set<string>();
      for (const node of Object.values(tree.nodes)) {
        for (const choice of node.choices ?? []) {
          if (choice.nextNodeId) referenced.add(choice.nextNodeId);
        }
      }
      for (const [id, node] of Object.entries(tree.nodes)) {
        if (id === tree.rootNodeId || referenced.has(id)) continue;
        if ((node.conditions ?? []).length === 0) dead.push(`${npcKey}.${id}`);
      }
    }
    expect(dead).toEqual([]);
  });
});

describe('缺陷 2 · 路由类 effect 不再抢占 nextNodeId', () => {
  it('复盘师「写下心得」先走到 retro_thanks，而不是当场关窗跳走', async () => {
    const [routes, stop] = captureNavigations();
    try {
      const store = useDialogStore();
      await store.openWithTree('sage_review');
      // 首开会被 weekly_retro 之外的插话/root 决定入口；确保停在 greet 主菜单。
      if (store.currentNodeId !== 'greet') {
        store.close();
        await store.openWithTree('sage_review');
      }
      expect(store.currentNodeId).toBe('greet');

      store.markTypingDone();
      store.chooseBranch('开始本周复盘');
      expect(store.currentNodeId).toBe('weekly_retro');

      store.markTypingDone();
      store.chooseBranch('写下心得');

      // 关键回归：带 nextNodeId 的选项即便同时挂了导航类 effect，也必须先把分支走完。
      expect(store.currentNodeId).toBe('retro_thanks');
      expect(store.visible).toBe(true);
      expect(routes).toEqual([]);
    } finally {
      stop();
    }
  });

  it('走到分支终点才发放 retro_seal 并触发暂存的导航', async () => {
    const [routes, stop] = captureNavigations();
    try {
      const store = useDialogStore();
      await store.openWithTree('sage_review');
      if (store.currentNodeId !== 'greet') {
        store.close();
        await store.openWithTree('sage_review');
      }
      store.markTypingDone();
      store.chooseBranch('开始本周复盘');
      store.markTypingDone();
      store.chooseBranch('写下心得');
      expect(hasDialogFlag('item:retro_seal')).toBe(false);

      store.markTypingDone();
      store.chooseBranch('谢 Sage');

      expect(hasDialogFlag('item:retro_seal')).toBe(true);
      expect(routes).toEqual(['/review-hall']);
      expect(store.visible).toBe(false);
    } finally {
      stop();
    }
  });

  it('中途关闭对话时暂存路由被丢弃（玩家放弃不该被强拽走）', async () => {
    const [routes, stop] = captureNavigations();
    try {
      const store = useDialogStore();
      await store.openWithTree('sage_review');
      if (store.currentNodeId !== 'greet') {
        store.close();
        await store.openWithTree('sage_review');
      }
      store.markTypingDone();
      store.chooseBranch('开始本周复盘');
      store.markTypingDone();
      store.chooseBranch('写下心得');
      expect(store.pendingRoute).toBe('/review-hall');

      store.close();

      expect(store.pendingRoute).toBeNull();
      expect(routes).toEqual([]);
    } finally {
      stop();
    }
  });
});

describe('缺陷 3 · 跳转目标与 effect 书写顺序无关', () => {
  const questFirst: DialogEffect[] = [
    { type: 'quest_recommend', questKey: '1-1' },
    { type: 'open_panel', route: '/lecture/1' },
  ];
  const panelFirst: DialogEffect[] = [
    { type: 'open_panel', route: '/lecture/1' },
    { type: 'quest_recommend', questKey: '1-1' },
  ];

  it('open_panel 优先于 quest_recommend，两种书写顺序结果一致', () => {
    expect(resolveEffectRoute(questFirst)).toBe('/lecture/1');
    expect(resolveEffectRoute(panelFirst)).toBe('/lecture/1');
  });

  it('panel 简称与 route 同级；同级时先声明者胜', () => {
    expect(resolveEffectRoute([{ type: 'open_panel', panel: 'training' }])).toBe('/training');
    expect(
      resolveEffectRoute([
        { type: 'route', route: '/library' },
        { type: 'open_panel', panel: 'training' },
      ]),
    ).toBe('/library');
  });

  it('无导航类 effect 时返回 null', () => {
    expect(resolveEffectRoute([{ type: 'set_flag', flagKey: 'x' }])).toBeNull();
    expect(resolveEffectRoute(undefined)).toBeNull();
  });

  it('镇长「领取本章任务·去上课」跳讲解页而非主线地图（真实数据）', async () => {
    const [routes, stop] = captureNavigations();
    try {
      const store = useDialogStore();
      await store.openWithTree('mayor_py');
      if (store.currentNodeId !== 'greet') {
        store.close();
        await store.openWithTree('mayor_py');
      }
      store.markTypingDone();
      store.chooseBranch('领取本章任务·去上课');
      expect(routes).toEqual(['/lecture/1']);
    } finally {
      stop();
    }
  });
});

describe('缺陷 4 · 未知 questKey 告警而非静默跳主线', () => {
  it('未知 questKey 落主线地图并 console.warn（同一 key 只警告一次）', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    expect(resolveEffectRoute([{ type: 'quest_recommend', questKey: 'no_such_quest' }])).toBe(
      '/mainline-map',
    );
    expect(resolveEffectRoute([{ type: 'quest_recommend', questKey: 'no_such_quest' }])).toBe(
      '/mainline-map',
    );

    expect(warn).toHaveBeenCalledTimes(1);
    expect(String(warn.mock.calls[0]?.[0])).toContain('no_such_quest');
  });

  it('章-节形式的 questKey 也会告警（前端解析不出真实 stageId）', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    expect(resolveEffectRoute([{ type: 'quest_recommend', questKey: '7-1' }])).toBe(
      '/mainline-map',
    );
    expect(warn).toHaveBeenCalledTimes(1);
    expect(String(warn.mock.calls[0]?.[0])).toContain('stageId');
  });

  it('claim_class_quest 仍按显式映射跳公告板班级页签，且不告警', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    expect(resolveEffectRoute([{ type: 'quest_recommend', questKey: 'claim_class_quest' }])).toBe(
      '/board?tab=classroom',
    );
    expect(warn).not.toHaveBeenCalled();
  });
});

describe('缺陷 5 · give_item 幂等 / 周额度按「完成」结算', () => {
  it('同一道具重复领取只弹一次 toast（馆长「证据条」不再无限刷）', async () => {
    const store = useDialogStore();
    const toast = useToastStore();
    const showSpy = vi.spyOn(toast, 'show').mockImplementation(() => undefined);

    for (let round = 0; round < 2; round += 1) {
      await store.openWithTree('librarian_doc');
      if (store.currentNodeId !== 'greet') {
        store.close();
        await store.openWithTree('librarian_doc');
      }
      store.markTypingDone();
      store.chooseBranch('我想找某个 KC 的证据');
      store.markTypingDone();
      store.chooseBranch('要');
      expect(hasDialogFlag('item:evidence_slip')).toBe(true);
    }

    expect(showSpy).toHaveBeenCalledTimes(1);
    expect(String(showSpy.mock.calls[0]?.[0])).toContain('证据条');
  });

  it('选「我下周再写」不消耗本周复盘额度，下次仍能进入 weekly_retro', async () => {
    const store = useDialogStore();
    await store.openWithTree('sage_review');
    if (store.currentNodeId !== 'greet') {
      store.close();
      await store.openWithTree('sage_review');
    }
    store.markTypingDone();
    store.chooseBranch('开始本周复盘');
    expect(store.currentNodeId).toBe('weekly_retro');
    store.markTypingDone();
    store.chooseBranch('我下周再写');
    expect(store.visible).toBe(false);

    await store.openWithTree('sage_review');
    store.markTypingDone();
    expect(store.branchChoices.map((c) => c.label)).toContain('开始本周复盘');
  });

  it('真正写完心得后，本周内「开始本周复盘」被隐藏', async () => {
    const store = useDialogStore();
    await store.openWithTree('sage_review');
    if (store.currentNodeId !== 'greet') {
      store.close();
      await store.openWithTree('sage_review');
    }
    store.markTypingDone();
    store.chooseBranch('开始本周复盘');
    store.markTypingDone();
    store.chooseBranch('写下心得');
    store.markTypingDone();
    store.chooseBranch('谢 Sage');

    await store.openWithTree('sage_review');
    store.markTypingDone();
    expect(store.branchChoices.map((c) => c.label)).not.toContain('开始本周复盘');
  });
});
