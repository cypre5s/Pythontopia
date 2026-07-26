/**
 * dialog store 测试：兼顾旧占位 API 与新对话树 API。
 *
 * 旧 API 用例继承前期占位测试，确保 DialogModal.vue 不会被破坏。
 * 新 API 用例覆盖：
 *   - openWithTree 成功路径（fetch 双 json，状态完整填充）
 *   - openWithTree fetch 失败时 loadError 填充
 *   - selectChoice 切换节点
 *   - selectChoice 选到 nextNodeId=null 关闭对话
 *
 * W2-dialog-upgrade 追加覆盖：
 *   - branchChoices / isTypingDone 初值
 *   - markTypingDone 在 currentNode 存在时快照 choices；幂等
 *   - hasBranches getter 与 branchChoices.length 同步
 *   - chooseBranch 触发 dialog:choice 全局事件 + 委托给 selectChoice 走 effects
 *   - chooseBranch 在无 tree 时直接 close（占位/手动注入场景）
 *   - onChoice 返回的取消订阅函数能正确 detach 监听器
 *   - selectChoice 跨节点导航时 branchChoices/isTypingDone 自动归零
 *   - close 同时清空 branchChoices/isTypingDone
 *
 * 注：本文件全部使用<b>合成 fixture</b>，只验 store 状态机；
 * 与真实 data/npcs/** 的契约（条件语义 / effect 路由 / questKey 标识空间）
 * 由 dialogNpcData.spec.ts 覆盖。
 */
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClientError } from '@/api/http';
import {
  useDialogStore,
  __resetChoiceListenersForTests,
  __resetDialogFlagsForTests,
  hasDialogFlag,
  isoWeekOf,
  type DialogChoiceEvent,
  type NpcDialogTree,
  type NpcMeta,
} from '@/stores/dialog';

// R-12 W2-NPC：openWithTree 现在会并行拉 /api/dialog/context 取真实 stats。
// 单测环境无后端，统一 stub 一个稳定上下文，避免 axios 真发请求引发抖动；
// 同时保证 store.context 在测试断言里可观察。
vi.mock('@/api/dialog', () => ({
  fetchDialogContext: vi.fn(async () => ({
    playerName: '测试者',
    profileType: 'cautious',
    stats: { exercisesThisWeek: '5' },
  })),
}));

// R-15 §8：openWithTree 现在还并行拉 GET /api/npc/{key}/greeting（走 axios http 实例）。
// 统一 mock 该实例，避免真实网络并让寒暄状态在断言里可控；默认返回「无记忆」(none)，
// 具体寒暄用例按需用 mockResolvedValueOnce / mockRejectedValueOnce 覆盖首个调用。
const httpMocks = vi.hoisted(() => ({ get: vi.fn() }));
// 同时导出 ApiClientError：dialog store 现在按其 code/status 判定寒暄是否值得重试，
// 真实类在被 mock 的模块里不可用，这里提供等价最小实现供测试构造可重试/不可重试错误。
vi.mock('@/api/http', () => ({
  http: httpMocks,
  ApiClientError: class ApiClientError extends Error {
    code: string;
    status: number | undefined;
    constructor(error: { code: string; message?: string }, status?: number) {
      super(error.message ?? error.code);
      this.name = 'ApiClientError';
      this.code = error.code;
      this.status = status;
    }
  },
}));

interface GreetingDtoLike {
  greeting: string;
  available: boolean;
  grounded: boolean;
  citedEventIds: number[];
  degraded: boolean;
  cached: boolean;
}

function greetingDto(partial: Partial<GreetingDtoLike> = {}): GreetingDtoLike {
  return {
    greeting: '',
    available: false,
    grounded: false,
    citedEventIds: [],
    degraded: false,
    cached: false,
    ...partial,
  };
}

/** 推进微任务，让 openWithTree 内非阻塞的 void fetchGreeting 落定。 */
const flush = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0));

// 每个用例前重置 http mock 默认值为「无记忆」，避免上一例残留 once 影响其它（含旧 openWithTree 用例）。
beforeEach(() => {
  httpMocks.get.mockReset();
  httpMocks.get.mockResolvedValue({ data: greetingDto() });
});

function makeTree(): NpcDialogTree {
  return {
    npcKey: 'mayor_py',
    rootNodeId: 'greet',
    nodes: {
      greet: {
        id: 'greet',
        text: '欢迎来到 Pythontopia',
        conditions: [],
        choices: [
          { label: '了解小镇', nextNodeId: 'intro' },
          { label: '再见', nextNodeId: null },
        ],
      },
      intro: {
        id: 'intro',
        text: '小镇分五区',
        conditions: [],
        choices: [{ label: '懂了', nextNodeId: null }],
      },
    },
  };
}

function makeMeta(): NpcMeta {
  return {
    key: 'mayor_py',
    name: '镇长 Py',
    portrait: 'portrait_mayor_py.png',
  };
}

function installFetchMock(handler: (url: string) => Promise<unknown>): void {
  const fn = vi.fn(async (input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();
    const data = await handler(url);
    return new Response(JSON.stringify(data), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fn);
}

describe('dialog store - tree API', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    __resetChoiceListenersForTests();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('openWithTree fills npcName/portrait/tree from fetched json', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    expect(store.visible).toBe(true);
    expect(store.npcKey).toBe('mayor_py');
    expect(store.npcName).toBe('镇长 Py');
    expect(store.portrait).toBe('portrait_mayor_py.png');
    expect(store.currentNodeId).toBe('greet');
    expect(store.tree?.nodes.greet?.text).toContain('Pythontopia');
    expect(store.loadError).toBeNull();
    expect(store.loading).toBe(false);
    // R-12 W2-NPC：openWithTree 同时填充对话渲染上下文，方便组件渲染 stats / profileAdaptive。
    expect(store.context?.playerName).toBe('测试者');
    expect(store.context?.stats.exercisesThisWeek).toBe('5');
  });

  it('openWithTree records loadError when fetch fails', async () => {
    const fn = vi.fn(async () => new Response('not found', { status: 404 }));
    vi.stubGlobal('fetch', fn);
    const store = useDialogStore();
    await store.openWithTree('ghost');
    expect(store.visible).toBe(true);
    expect(store.loadError).toContain('NPC 数据加载失败：ghost');
    expect(store.tree).toBeNull();
  });

  it('selectChoice navigates to nextNodeId then closes when nextNodeId is null', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    store.selectChoice(0);
    expect(store.currentNodeId).toBe('intro');
    expect(store.history).toEqual(['greet', 'intro']);
    store.selectChoice(0);
    expect(store.visible).toBe(false);
    expect(store.currentNodeId).toBeNull();
  });

  it('close resets tree state', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    store.close();
    expect(store.visible).toBe(false);
    expect(store.openNpcKey).toBeNull();
    expect(store.tree).toBeNull();
    expect(store.currentNodeId).toBeNull();
    expect(store.history).toEqual([]);
    expect(store.branchChoices).toEqual([]);
    expect(store.isTypingDone).toBe(false);
    expect(store.context).toBeNull();
  });
});

describe('dialog store - typewriter & branch API (W2-dialog-upgrade)', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    __resetChoiceListenersForTests();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('initial state has empty branchChoices and isTypingDone=false', () => {
    const store = useDialogStore();
    expect(store.branchChoices).toEqual([]);
    expect(store.isTypingDone).toBe(false);
    expect(store.hasBranches).toBe(false);
  });

  it('markTypingDone snapshots currentNode.choices into branchChoices and is idempotent', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    expect(store.isTypingDone).toBe(false);
    expect(store.hasBranches).toBe(false);

    store.markTypingDone();
    expect(store.isTypingDone).toBe(true);
    expect(store.hasBranches).toBe(true);
    expect(store.branchChoices.map((c) => c.label)).toEqual(['了解小镇', '再见']);

    // 幂等：第二次调用不应清空或重复填充 branchChoices。
    const snapshot = store.branchChoices;
    store.markTypingDone();
    expect(store.branchChoices).toBe(snapshot);
  });

  it('hasBranches getter reflects branchChoices length', () => {
    const store = useDialogStore();
    expect(store.hasBranches).toBe(false);
    store.branchChoices = [{ label: 'A', nextNodeId: null }];
    expect(store.hasBranches).toBe(true);
    store.branchChoices = [];
    expect(store.hasBranches).toBe(false);
  });

  it('chooseBranch emits dialog:choice event and delegates to selectChoice for navigation', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    store.markTypingDone();

    const received: DialogChoiceEvent[] = [];
    store.onChoice((ev) => received.push(ev));

    store.chooseBranch('了解小镇');

    expect(received).toHaveLength(1);
    const ev = received[0]!;
    expect(ev.npcKey).toBe('mayor_py');
    expect(ev.nodeId).toBe('greet');
    expect(ev.choiceId).toBe('了解小镇');
    expect(ev.choice.nextNodeId).toBe('intro');
    expect(typeof ev.timestamp).toBe('number');

    // selectChoice 已被委托执行：导航到 intro，分支状态被重置等待下一次 markTypingDone。
    expect(store.currentNodeId).toBe('intro');
    expect(store.branchChoices).toEqual([]);
    expect(store.isTypingDone).toBe(false);
  });

  it('chooseBranch delegates to selectChoice and closes when nextNodeId is null', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    store.markTypingDone();

    store.chooseBranch('再见');

    expect(store.visible).toBe(false);
    expect(store.currentNodeId).toBeNull();
  });

  it('chooseBranch on injected branchChoices without tree falls back to close', () => {
    const store = useDialogStore();
    store.openNpcKey = 'mayor_py';
    store.branchChoices = [{ label: '继续', nextNodeId: null }];
    const received: DialogChoiceEvent[] = [];
    store.onChoice((ev) => received.push(ev));

    store.chooseBranch('继续');

    expect(received).toHaveLength(1);
    expect(received[0]!.choiceId).toBe('继续');
    expect(store.isOpen).toBe(false);
  });

  it('chooseBranch with unknown choiceId is a no-op', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    store.markTypingDone();
    const received: DialogChoiceEvent[] = [];
    store.onChoice((ev) => received.push(ev));

    store.chooseBranch('not_a_real_label');

    expect(received).toEqual([]);
    expect(store.currentNodeId).toBe('greet');
  });

  it('onChoice returns a teardown that detaches the listener', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    store.markTypingDone();

    const listener = vi.fn();
    const off = store.onChoice(listener);
    off();

    store.chooseBranch('了解小镇');
    expect(listener).not.toHaveBeenCalled();
  });

  it('selectChoice navigation resets branchChoices and isTypingDone for the next node', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    store.markTypingDone();
    expect(store.hasBranches).toBe(true);
    expect(store.isTypingDone).toBe(true);

    store.selectChoice(0);

    expect(store.currentNodeId).toBe('intro');
    expect(store.branchChoices).toEqual([]);
    expect(store.isTypingDone).toBe(false);
    expect(store.hasBranches).toBe(false);
  });
});

describe('dialog store - condition system (P3 接通)', () => {
  function makeConditionalTree(): NpcDialogTree {
    return {
      npcKey: 'sage_review',
      rootNodeId: 'greet',
      nodes: {
        greet: {
          id: 'greet',
          text: '坐一坐',
          conditions: [],
          choices: [
            { label: '开始本周复盘', nextNodeId: 'weekly_retro' },
            { label: '上次失败的提交？', nextNodeId: 'last_failed' },
            { label: '再见', nextNodeId: null },
          ],
        },
        weekly_retro: {
          id: 'weekly_retro',
          text: '本周复盘…',
          conditions: [{ type: 'first_time_this_week', key: 'weekly_retro' }],
          choices: [
            {
              label: '写下心得',
              nextNodeId: null,
              effects: [{ type: 'set_flag', flagKey: 'weekly_retro_done' }],
            },
            { label: '我下周再写', nextNodeId: null },
          ],
        },
        last_failed: {
          id: 'last_failed',
          text: '你最近一次失败…',
          conditions: [{ type: 'has_failed_submission' }],
          choices: [{ label: '下去打', nextNodeId: null }],
        },
        weekly_recap: {
          id: 'weekly_recap',
          text: '上周你解锁了…',
          conditions: [{ type: 'first_time_this_week', key: 'weekly_recap' }],
          choices: [
            { label: '现在就去', nextNodeId: null },
            {
              label: '给我热力图',
              nextNodeId: null,
              effects: [{ type: 'give_item', itemKey: 'class_heatmap' }],
            },
          ],
        },
      },
    };
  }

  beforeEach(() => {
    setActivePinia(createPinia());
    __resetChoiceListenersForTests();
    __resetDialogFlagsForTests();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    __resetDialogFlagsForTests();
  });

  it('isoWeekOf 返回 ISO 周标识', () => {
    expect(isoWeekOf(new Date('2026-06-10T00:00:00Z'))).toMatch(/^2026-W\d{2}$/);
  });

  it('每周插话节点抢占入口，且本周第二次打开回落 root', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeConditionalTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();

    await store.openWithTree('sage_review');
    expect(store.currentNodeId).toBe('weekly_recap');
    store.close();

    await store.openWithTree('sage_review');
    expect(store.currentNodeId).toBe('greet');
  });

  it('markTypingDone 过滤条件不满足的分支（无失败提交 → 隐藏 last_failed 入口）', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeConditionalTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('sage_review');
    // 第一次入口是 weekly_recap；先关再开走 greet。
    store.close();
    await store.openWithTree('sage_review');
    expect(store.currentNodeId).toBe('greet');

    store.markTypingDone();
    const labels = store.branchChoices.map((c) => c.label);
    expect(labels).toContain('开始本周复盘');
    expect(labels).not.toContain('上次失败的提交？');
    expect(labels).toContain('再见');
  });

  it('完成 weekly_retro（写下心得）后，本周内该分支被隐藏', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeConditionalTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('sage_review');
    store.close();
    await store.openWithTree('sage_review');
    store.markTypingDone();
    store.chooseBranch('开始本周复盘');
    expect(store.currentNodeId).toBe('weekly_retro');
    store.markTypingDone();
    store.chooseBranch('写下心得');

    await store.openWithTree('sage_review');
    store.markTypingDone();
    expect(store.branchChoices.map((c) => c.label)).not.toContain('开始本周复盘');
  });

  it('只是「进入」weekly_retro 但没写完，不消耗本周额度', async () => {
    // 语义是「完成才算做过」：进入即记周记录会让选「我下周再写」的玩家本周再也进不去复盘。
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeConditionalTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('sage_review');
    store.close();
    await store.openWithTree('sage_review');
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

  it('set_flag / give_item 效果落持久 flag（不再被静默丢弃）', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeConditionalTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('sage_review');
    // 入口是 weekly_recap，选 give_item 分支。
    store.markTypingDone();
    store.chooseBranch('给我热力图');
    expect(hasDialogFlag('item:class_heatmap')).toBe(true);

    await store.openWithTree('sage_review');
    store.markTypingDone();
    store.chooseBranch('开始本周复盘');
    store.markTypingDone();
    store.chooseBranch('写下心得');
    expect(hasDialogFlag('weekly_retro_done')).toBe(true);
  });

  it('quest_recommend claim_class_quest 导航到公告板班级任务页签', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) {
        const tree: NpcDialogTree = {
          npcKey: 'class_president',
          rootNodeId: 'greet',
          nodes: {
            greet: {
              id: 'greet',
              text: '公告板今天有新任务',
              conditions: [],
              choices: [
                {
                  label: '领班级任务',
                  nextNodeId: null,
                  effects: [{ type: 'quest_recommend', questKey: 'claim_class_quest' }],
                },
              ],
            },
          },
        };
        return tree;
      }
      throw new Error(`unexpected url ${url}`);
    });
    const events: string[] = [];
    const onNavigate = (e: Event): void => {
      events.push(String((e as CustomEvent<{ route: string }>).detail.route));
    };
    window.addEventListener('pythontopia:dialog-navigate', onNavigate);
    try {
      const store = useDialogStore();
      await store.openWithTree('class_president');
      expect(store.currentNodeId).toBe('greet');
      store.markTypingDone();
      store.chooseBranch('领班级任务');
      expect(events).toEqual(['/board?tab=classroom']);
      expect(store.visible).toBe(false);
    } finally {
      window.removeEventListener('pythontopia:dialog-navigate', onNavigate);
    }
  });
});

describe('dialog store - 入口插话每周阻尼（kc_mastered 不永久劫持 greet）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    __resetChoiceListenersForTests();
    __resetDialogFlagsForTests();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    __resetDialogFlagsForTests();
  });

  it('kc_mastered 插话节点本周只抢占一次入口', async () => {
    const tree: NpcDialogTree = {
      npcKey: 'foreman_bug',
      rootNodeId: 'greet',
      nodes: {
        greet: {
          id: 'greet',
          text: '矿洞欢迎你',
          conditions: [],
          choices: [{ label: '再见', nextNodeId: null }],
        },
        mine_boss_hint: {
          id: 'mine_boss_hint',
          text: 'B5 层那只 IndentationError…',
          conditions: [{ type: 'kc_mastered', kcCode: 'PY43-KC-019' }],
          choices: [{ label: '学到了', nextNodeId: null }],
        },
      },
    };
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return tree;
      throw new Error(`unexpected url ${url}`);
    });

    const { useLearningStore } = await import('@/stores/learning');
    const learning = useLearningStore();
    learning.field.masterySnapshot = [
      { kcCode: 'PY43-KC-019', masteryScore: 0.6 },
    ] as never;

    const store = useDialogStore();
    await store.openWithTree('foreman_bug');
    expect(store.currentNodeId).toBe('mine_boss_hint');
    store.close();

    // 条件仍为真（掌握度不会消失），但本周已插话过 → 回落 greet 主菜单。
    await store.openWithTree('foreman_bug');
    expect(store.currentNodeId).toBe('greet');
  });
});

describe('dialog store - NPC greeting (R-15 §8)', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    __resetChoiceListenersForTests();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('fetchGreeting sets ready with personalized text when available', async () => {
    httpMocks.get.mockResolvedValueOnce({
      data: greetingDto({
        greeting: '上次 for 循环错了 3 次，今天去矿洞复盘？',
        available: true,
        grounded: true,
        citedEventIds: [9],
      }),
    });
    const store = useDialogStore();
    store.visible = true;
    store.npcKey = 'foreman_bug';

    await store.fetchGreeting('foreman_bug');

    expect(httpMocks.get).toHaveBeenCalledWith('/api/npc/foreman_bug/greeting', {
      suppressNetworkToast: true,
      timeout: 15_000,
    });
    expect(store.greetingStatus).toBe('ready');
    expect(store.greetingText).toContain('for 循环错了 3 次');
    expect(store.greetingGrounded).toBe(true);
  });

  it('fetchGreeting falls back to memory-unavailable on request failure', async () => {
    httpMocks.get.mockRejectedValueOnce(new Error('network down'));
    const store = useDialogStore();
    store.visible = true;
    store.npcKey = 'foreman_bug';

    await store.fetchGreeting('foreman_bug');

    expect(store.greetingStatus).toBe('fallback');
    expect(store.greetingText).toBe('');
  });

  it('fetchGreeting maps degraded=true to fallback (service unavailable)', async () => {
    httpMocks.get.mockResolvedValueOnce({ data: greetingDto({ degraded: true }) });
    const store = useDialogStore();
    store.visible = true;
    store.npcKey = 'foreman_bug';

    await store.fetchGreeting('foreman_bug');

    expect(store.greetingStatus).toBe('fallback');
  });

  it('fetchGreeting maps no-memory (available=false, degraded=false) to none', async () => {
    httpMocks.get.mockResolvedValueOnce({ data: greetingDto() });
    const store = useDialogStore();
    store.visible = true;
    store.npcKey = 'foreman_bug';

    await store.fetchGreeting('foreman_bug');

    expect(store.greetingStatus).toBe('none');
    expect(store.greetingText).toBe('');
  });

  it('ignores a stale greeting result after switching NPC', async () => {
    let resolveGreeting!: (value: { data: GreetingDtoLike }) => void;
    httpMocks.get.mockReturnValueOnce(
      new Promise((res) => {
        resolveGreeting = res;
      }),
    );
    const store = useDialogStore();
    store.visible = true;
    store.npcKey = 'npc_a';

    const pending = store.fetchGreeting('npc_a');
    expect(store.greetingStatus).toBe('loading');
    // 玩家在响应回来前切到了别的 NPC。
    store.npcKey = 'npc_b';
    resolveGreeting({ data: greetingDto({ greeting: 'A 的寒暄', available: true }) });
    await pending;

    // 旧 NPC 的结果被守卫丢弃，不回写覆盖新对话。
    expect(store.greetingText).toBe('');
    expect(store.greetingStatus).toBe('loading');
  });

  it('openWithTree fires the greeting fetch in parallel and resolves to ready', async () => {
    httpMocks.get.mockResolvedValue({ data: greetingDto({ greeting: '嗨，同学', available: true }) });
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();

    await store.openWithTree('mayor_py');
    // 寒暄不阻塞对话：脚本入口节点已就绪。
    expect(store.currentNodeId).toBe('greet');
    expect(httpMocks.get).toHaveBeenCalledWith('/api/npc/mayor_py/greeting', {
      suppressNetworkToast: true,
      timeout: 15_000,
    });

    await flush();
    expect(store.greetingStatus).toBe('ready');
    expect(store.greetingText).toContain('嗨');
  });

  it('fetchGreeting retries once on a transient 5xx then resolves ready (no fallback flash)', async () => {
    httpMocks.get
      .mockRejectedValueOnce(new ApiClientError({ code: 'INTERNAL_ERROR', message: 'boom' }, 503))
      .mockResolvedValueOnce({
        data: greetingDto({ greeting: '嗨，同学', available: true, grounded: true }),
      });
    const store = useDialogStore();
    store.visible = true;
    store.npcKey = 'foreman_bug';

    await store.fetchGreeting('foreman_bug');

    expect(httpMocks.get).toHaveBeenCalledTimes(2);
    expect(store.greetingStatus).toBe('ready');
    expect(store.greetingText).toContain('嗨');
  });

  it('fetchGreeting does not retry on timeout (slow call) and falls back', async () => {
    httpMocks.get.mockRejectedValueOnce(new ApiClientError({ code: 'TIMEOUT', message: 'timeout' }));
    const store = useDialogStore();
    store.visible = true;
    store.npcKey = 'foreman_bug';

    await store.fetchGreeting('foreman_bug');

    expect(httpMocks.get).toHaveBeenCalledTimes(1);
    expect(store.greetingStatus).toBe('fallback');
  });

  it('close resets greeting state', () => {
    const store = useDialogStore();
    store.visible = true;
    store.npcKey = 'foreman_bug';
    store.greetingStatus = 'ready';
    store.greetingText = '你好';
    store.greetingGrounded = true;

    store.close();

    expect(store.greetingStatus).toBe('idle');
    expect(store.greetingText).toBe('');
    expect(store.greetingGrounded).toBe(false);
  });
});
