// R-13 Phase 10 NPC 对话树集成测试（目标 30 用例）。
//
// 覆盖：
//   - openWithTree fetch 流程（成功 / 失败 / 加载中）
//   - selectChoice 路径切换 / friendship effect / 关闭流程
//   - chooseBranch 触发 dialog:choice 事件
//   - 多次 open / close 状态机
//   - 内存友好度跨实例存活
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  useDialogStore,
  __resetFriendshipMemoryForTests,
  __resetChoiceListenersForTests,
  type NpcDialogTree,
} from '@stores/dialog';

const TREE_MAYOR: NpcDialogTree = {
  npcKey: 'mayor_py',
  rootNodeId: 'root',
  nodes: {
    root: {
      id: 'root',
      text: '欢迎来到 Pythontopia',
      conditions: [],
      choices: [
        { label: '谢谢', nextNodeId: 'thanks', effects: [{ type: 'friendship', delta: 5 }] },
        { label: '介绍一下', nextNodeId: 'intro' },
        { label: '再见', nextNodeId: null },
      ],
    },
    thanks: {
      id: 'thanks',
      text: '客气',
      conditions: [],
      choices: [
        { label: '继续聊', nextNodeId: 'intro' },
        { label: '告辞', nextNodeId: null, effects: [{ type: 'friendship', delta: 1 }] },
      ],
    },
    intro: {
      id: 'intro',
      text: '本镇有 5 张地图...',
      conditions: [],
      choices: [
        { label: '明白', nextNodeId: null },
      ],
    },
  },
};

const META = { key: 'mayor_py', name: '镇长 Py', portrait: 'mayor.png' };

beforeEach(() => {
  setActivePinia(createPinia());
  __resetFriendshipMemoryForTests();
  __resetChoiceListenersForTests();
  // 给 fetch 加 mock：根据 url 返回 meta 或 tree
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    if (url.endsWith('/meta.json')) {
      return { ok: true, json: async () => META } as unknown as Response;
    }
    if (url.endsWith('/dialog.json')) {
      return { ok: true, json: async () => TREE_MAYOR } as unknown as Response;
    }
    return { ok: false, json: async () => ({}) } as unknown as Response;
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('dialog-tree.integration · openWithTree fetch', () => {
  it('成功加载 meta + tree 并 visible=true', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    expect(d.visible).toBe(true);
    expect(d.npcName).toBe('镇长 Py');
    expect(d.tree?.npcKey).toBe('mayor_py');
    expect(d.currentNodeId).toBe('root');
    expect(d.loadError).toBeNull();
  });

  it('fetch 抛错 → loadError 填充 + visible 仍 true（UI 自行降级）', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('网络断了'));
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    expect(d.loadError).toContain('网络断了');
    expect(d.visible).toBe(true);
  });

  it('loading 期间 loading=true 后 false', async () => {
    const d = useDialogStore();
    const promise = d.openWithTree('mayor_py');
    expect(d.loading).toBe(true);
    await promise;
    expect(d.loading).toBe(false);
  });

  it('open 同 NPC 二次重置 history + currentNodeId', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.selectChoice(1); // go to intro
    expect(d.currentNodeId).toBe('intro');
    await d.openWithTree('mayor_py');
    expect(d.currentNodeId).toBe('root');
    expect(d.history).toEqual(['root']);
  });
});

describe('dialog-tree.integration · selectChoice 状态机', () => {
  it('选「谢谢」→ friendship +5 + 跳 thanks 节点', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.selectChoice(0);
    expect(d.friendship).toBe(5);
    expect(d.currentNodeId).toBe('thanks');
    expect(d.history).toEqual(['root', 'thanks']);
  });

  it('选「介绍一下」→ 跳 intro，无 friendship 变化', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.selectChoice(1);
    expect(d.friendship).toBe(0);
    expect(d.currentNodeId).toBe('intro');
  });

  it('选「再见」→ nextNodeId=null → close 对话', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.selectChoice(2);
    expect(d.visible).toBe(false);
    expect(d.npcKey).toBeNull();
  });

  it('连续走 root → thanks → 告辞：friendship 累加 7（5+1+1auto）', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.selectChoice(0); // root → thanks, +5
    d.selectChoice(1); // thanks → 告辞 null, +1 effect; close() auto +1 (history>1)
    expect(d.friendship).toBe(7);
    expect(d.visible).toBe(false);
  });

  it('选择非法 choice index → 静默', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.selectChoice(999);
    expect(d.currentNodeId).toBe('root');
  });

  it('nextNodeId 指向不存在的节点 → loadError 填充', async () => {
    const broken: NpcDialogTree = {
      npcKey: 'broken',
      rootNodeId: 'r',
      nodes: {
        r: {
          id: 'r',
          text: 'x',
          conditions: [],
          choices: [{ label: '走丢', nextNodeId: 'ghost' }],
        },
      },
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.endsWith('/meta.json')) return { ok: true, json: async () => META } as unknown as Response;
      return { ok: true, json: async () => broken } as unknown as Response;
    });
    const d = useDialogStore();
    await d.openWithTree('broken');
    d.selectChoice(0);
    expect(d.loadError).toContain('ghost');
  });
});

describe('dialog-tree.integration · 内存友好度跨实例', () => {
  it('close 后再 open 同 NPC：friendship 从 memory 还原（含 auto +1）', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.selectChoice(0); // +5
    d.selectChoice(0); // thanks → 继续聊 → intro
    d.close(); // history.length > 1 → auto +1
    await d.openWithTree('mayor_py');
    expect(d.friendship).toBe(6); // 5 + 1 auto
  });

  it('close 后再 open 不同 NPC：friendship 重置为 0', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.selectChoice(0);
    d.close();
    await d.openWithTree('farmer_kc');
    expect(d.friendship).toBe(0);
  });
});

describe('dialog-tree.integration · chooseBranch 事件总线', () => {
  it('订阅 onChoice 监听 dialog:choice 事件', async () => {
    const d = useDialogStore();
    const listener = vi.fn();
    const unsubscribe = d.onChoice(listener);
    await d.openWithTree('mayor_py');
    d.markTypingDone();
    d.chooseBranch('谢谢');
    expect(listener).toHaveBeenCalledOnce();
    const event = listener.mock.calls[0]?.[0];
    expect(event?.npcKey).toBe('mayor_py');
    expect(event?.nodeId).toBe('root');
    expect(event?.choiceId).toBe('谢谢');
    unsubscribe();
  });

  it('取消订阅后不再触发', async () => {
    const d = useDialogStore();
    const listener = vi.fn();
    const unsubscribe = d.onChoice(listener);
    unsubscribe();
    await d.openWithTree('mayor_py');
    d.markTypingDone();
    d.chooseBranch('谢谢');
    expect(listener).not.toHaveBeenCalled();
  });

  it('多个监听器：单个抛错不影响其他', async () => {
    const d = useDialogStore();
    const boom = vi.fn(() => {
      throw new Error('boom');
    });
    const ok = vi.fn();
    d.onChoice(boom);
    d.onChoice(ok);
    await d.openWithTree('mayor_py');
    d.markTypingDone();
    d.chooseBranch('谢谢');
    expect(boom).toHaveBeenCalled();
    expect(ok).toHaveBeenCalled();
  });
});

describe('dialog-tree.integration · markTypingDone 与分支', () => {
  it('打字未结束：branchChoices 为空', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    expect(d.branchChoices).toEqual([]);
  });

  it('打字结束：branchChoices = currentNode.choices', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.markTypingDone();
    expect(d.branchChoices.length).toBe(3);
  });

  it('markTypingDone 幂等：多次调用 branchChoices 长度不变', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.markTypingDone();
    const len = d.branchChoices.length;
    d.markTypingDone();
    d.markTypingDone();
    expect(d.branchChoices.length).toBe(len);
  });

  it('切换节点后 branchChoices 自动归零', async () => {
    const d = useDialogStore();
    await d.openWithTree('mayor_py');
    d.markTypingDone();
    expect(d.branchChoices.length).toBe(3);
    d.selectChoice(0); // 切到 thanks
    expect(d.branchChoices.length).toBe(0);
    expect(d.isTypingDone).toBe(false);
  });
});
