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
 */
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  useDialogStore,
  __resetFriendshipMemoryForTests,
  type NpcDialogTree,
  type NpcMeta,
} from '@/stores/dialog';

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
  });
});
