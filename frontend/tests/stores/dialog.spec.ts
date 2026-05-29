/**
 * dialog store 测试：兼顾旧占位 API 与新对话树 API。
 *
 * 旧 API 用例继承前期占位测试，确保 DialogModal.vue 不会被破坏。
 * 新 API 用例覆盖：
 *   - openWithTree 成功路径（fetch 双 json，状态完整填充）
 *   - openWithTree fetch 失败时 loadError 填充
 *   - selectChoice 应用 friendship effect 并切换节点
 *   - selectChoice 选到 nextNodeId=null 关闭对话
 *   - 同 NPC 再次 open 时 friendship 从内存还原
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
 */
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  useDialogStore,
  __resetChoiceListenersForTests,
  __resetFriendshipMemoryForTests,
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
    giftJustGiven: false,
    stats: { exercisesThisWeek: '5' },
  })),
}));

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
          { label: '再见', nextNodeId: null, effects: [{ type: 'friendship', delta: 1 }] },
        ],
      },
      intro: {
        id: 'intro',
        text: '小镇分五区',
        conditions: [],
        choices: [{ label: '懂了', nextNodeId: null, effects: [{ type: 'friendship', delta: 3 }] }],
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

describe('dialog store - legacy placeholder API', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    __resetFriendshipMemoryForTests();
    __resetChoiceListenersForTests();
  });

  it('opens known placeholder NPC dialog and closes it', () => {
    const store = useDialogStore();
    store.open('mayor_py');
    expect(store.isOpen).toBe(true);
    expect(store.lines[0]).toContain('[占位]');
    store.close();
    expect(store.isOpen).toBe(false);
  });
});

describe('dialog store - tree API', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    __resetFriendshipMemoryForTests();
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

  it('selectChoice applies friendship effect and navigates to nextNodeId', async () => {
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
    expect(store.friendship).toBe(0);
    store.selectChoice(0);
    expect(store.visible).toBe(false);
    expect(store.currentNodeId).toBeNull();
  });

  it('friendship persists in memory across reopen of same npc', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    store.selectChoice(1);
    expect(store.friendship).toBe(1);
    expect(store.visible).toBe(false);
    await store.openWithTree('mayor_py');
    expect(store.friendship).toBe(1);
  });

  it('close resets both legacy and tree state', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    store.open('mayor_py');
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
    __resetFriendshipMemoryForTests();
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

  it('chooseBranch applies friendship effect via selectChoice and closes when nextNodeId is null', async () => {
    installFetchMock(async (url) => {
      if (url.endsWith('/meta.json')) return makeMeta();
      if (url.endsWith('/dialog.json')) return makeTree();
      throw new Error(`unexpected url ${url}`);
    });
    const store = useDialogStore();
    await store.openWithTree('mayor_py');
    store.markTypingDone();

    store.chooseBranch('再见');

    expect(store.friendship).toBe(1);
    expect(store.visible).toBe(false);
    expect(store.currentNodeId).toBeNull();
  });

  it('chooseBranch on injected branchChoices without tree falls back to close', () => {
    const store = useDialogStore();
    store.open('mayor_py');
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
