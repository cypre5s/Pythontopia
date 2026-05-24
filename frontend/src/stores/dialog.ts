import { defineStore } from 'pinia';
import { npcDialog } from '@/game/data/npcDialog';
import { playDialogClose, playDialogOpen } from '@/composables/useStardewFx';

/**
 * NPC 对话树 / 节点 / 选项 / 效果类型定义。
 *
 * 字段对齐 W2-NPC 子代理产出的 data/npcs/<key>/dialog.json 结构，
 * 字段名严格保留 npcKey / rootNodeId / nodes / choices / effects / conditions，
 * 任何字段调整都需要双方同步。
 */
export type DialogEffectType =
  | 'friendship'
  | 'give_item'
  | 'quest_recommend'
  | 'set_flag'
  | 'open_panel';

export interface DialogEffect {
  type: DialogEffectType;
  delta?: number;
  itemKey?: string;
  questKey?: string;
  flagKey?: string;
  route?: string;
}

export interface DialogChoice {
  label: string;
  nextNodeId: string | null;
  effects?: DialogEffect[];
}

/**
 * `dialog:choice` 全局事件 payload。
 *
 * 由 `chooseBranch(choiceId)` 触发，供业务侧（任务系统 / 分析 / 教师后台）订阅，
 * 解耦 UI 选项点击和下游副作用。
 */
export interface DialogChoiceEvent {
  npcKey: string | null;
  nodeId: string | null;
  choiceId: string;
  choice: DialogChoice;
  timestamp: number;
}

export type DialogChoiceListener = (event: DialogChoiceEvent) => void;

export type DialogConditionType =
  | 'friendship_min'
  | 'first_time_this_week'
  | 'kc_mastered'
  | 'flag_set'
  | 'gift_just_given';

export interface DialogCondition {
  type: DialogConditionType;
  value?: number;
  key?: string;
  kcCode?: string;
  flagKey?: string;
}

export interface DialogNode {
  id: string;
  text: string;
  conditions: DialogCondition[];
  choices: DialogChoice[];
}

export interface NpcDialogTree {
  npcKey: string;
  rootNodeId: string;
  nodes: Record<string, DialogNode>;
}

/**
 * NPC 元数据（meta.json 中我们当前组件用到的字段子集）。
 * 其余字段（schedule / favoriteGifts / spawn / tags...）后续可扩展，
 * 此处只声明 DialogTree 实际用到的，避免类型膨胀。
 */
export interface NpcMeta {
  key: string;
  name: string;
  portrait?: string;
  description?: string;
}

interface DialogState {
  /** 旧 API：占位对话用，保留向后兼容 */
  openNpcKey: string | null;
  /** 旧 API：占位对话文本行 */
  lines: string[];

  /** 对话树是否可见（新 API） */
  visible: boolean;
  /** 当前 NPC key */
  npcKey: string | null;
  /** NPC 显示名（来自 meta.json） */
  npcName: string;
  /** portrait 资源相对路径（绝对 URL 由组件拼接 /assets/pixel/portraits/） */
  portrait: string | null;
  /** 当前对话树 */
  tree: NpcDialogTree | null;
  /** 当前节点 id */
  currentNodeId: string | null;
  /** 已访问节点 ids（用于条件判定 / 防回退） */
  history: string[];
  /** 当前 NPC 的好感度（内存态；后续 R-Live-Friendship 接入持久化时迁移） */
  friendship: number;
  /** 加载错误信息（fetch meta/dialog 失败时填充） */
  loadError: string | null;
  /** 加载中（fetch 进行中） */
  loading: boolean;

  /**
   * 当前节点暴露给 UI 的分支选项快照。
   *
   * 由 `markTypingDone` 在打字机收尾时从 currentNode.choices 写入，
   * 切换节点 / 关闭对话时清空，保证 UI 只在「文本读完」之后才渲染选项，
   * 防止玩家在文字滚动期间误点。
   */
  branchChoices: DialogChoice[];

  /** 当前节点的打字机是否已完成（自然结束或玩家跳过）。 */
  isTypingDone: boolean;
}

/** NPC 数据目录前缀，可被测试 mock 覆写。 */
const DEFAULT_NPC_DATA_BASE = '/data/npcs';

/**
 * 内存级好感度缓存：dialog store 关闭后仍保留同会话内 NPC 的好感度，
 * 等 R-Live-Friendship 接入后端 /api/npc/friendship 时统一替换为 store 同步。
 */
const friendshipMemory = new Map<string, number>();

/**
 * 模块级 `dialog:choice` 事件监听集合。
 *
 * 选择自建一个最小事件总线而不是引入 mitt，原因：
 *   - 当前唯一需要的事件就是 `dialog:choice`，无需通用 emitter；
 *   - Pinia 重置（每个测试 createPinia）不会清空模块级常量，方便业务订阅跨 store 实例存活；
 *   - 测试通过 `__resetChoiceListenersForTests` 手动清空，避免状态泄漏。
 */
const choiceListeners = new Set<DialogChoiceListener>();

function emitDialogChoice(event: DialogChoiceEvent): void {
  // 复制一份再迭代，监听器内部取消订阅时不会扰乱 Set 迭代器。
  for (const listener of [...choiceListeners]) {
    try {
      listener(event);
    } catch (err) {
      // 容错：单个监听器异常不应阻断其余订阅者；仅在控制台告警。
      if (typeof console !== 'undefined' && typeof console.error === 'function') {
        console.error('[dialog:choice] listener threw:', err);
      }
    }
  }
}

async function fetchJson<T>(url: string): Promise<T> {
  const res = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} ${url}`);
  }
  return (await res.json()) as T;
}

export const useDialogStore = defineStore('dialog', {
  state: (): DialogState => ({
    openNpcKey: null,
    lines: [],
    visible: false,
    npcKey: null,
    npcName: '',
    portrait: null,
    tree: null,
    currentNodeId: null,
    history: [],
    friendship: 0,
    loadError: null,
    loading: false,
    branchChoices: [],
    isTypingDone: false,
  }),
  getters: {
    /** 旧 API：兼容 DialogModal.vue 与已有测试。 */
    isOpen: (state): boolean => state.openNpcKey !== null,
    /** 当前显示节点（新 API）。 */
    currentNode(state): DialogNode | null {
      if (!state.tree || !state.currentNodeId) return null;
      return state.tree.nodes[state.currentNodeId] ?? null;
    },
    /** 当前是否有可展示的分支选项（打字机结束 + currentNode.choices 非空）。 */
    hasBranches: (state): boolean => state.branchChoices.length > 0,
  },
  actions: {
    /**
     * 旧 API：保留占位对话能力，让现有 TownScene / DialogModal 测试无须改动。
     * 新代码请改用 openWithTree。
     */
    open(npcKey: string): void {
      this.openNpcKey = npcKey;
      this.lines = npcDialog[npcKey] ?? ['[占位] 这位居民还没有准备好对话。'];
      this.branchChoices = [];
      this.isTypingDone = false;
      playDialogOpen();
    },

    /**
     * 加载并打开 NPC 对话树。
     *
     * 流程：
     *   1. 立即 visible=true 并显示 loading（让 UI 不闪烁）；
     *   2. 并行 fetch meta.json + dialog.json；
     *   3. 失败时填充 loadError，UI 自行降级展示；
     *   4. 成功时 friendship 从内存缓存还原（默认 0）。
     *
     * @param npcKey NPC 标识，例如 mayor_py / librarian_doc
     * @param baseUrl 可选 base，便于测试 mock，默认 /data/npcs
     */
    async openWithTree(npcKey: string, baseUrl: string = DEFAULT_NPC_DATA_BASE): Promise<void> {
      this.npcKey = npcKey;
      this.visible = true;
      this.loading = true;
      playDialogOpen();
      this.loadError = null;
      this.history = [];
      this.tree = null;
      this.currentNodeId = null;
      this.npcName = npcKey;
      this.portrait = null;
      this.friendship = friendshipMemory.get(npcKey) ?? 0;
      this.branchChoices = [];
      this.isTypingDone = false;
      try {
        const [meta, tree] = await Promise.all([
          fetchJson<NpcMeta>(`${baseUrl}/${npcKey}/meta.json`),
          fetchJson<NpcDialogTree>(`${baseUrl}/${npcKey}/dialog.json`),
        ]);
        this.npcName = meta.name || npcKey;
        this.portrait = meta.portrait ?? null;
        this.tree = tree;
        this.currentNodeId = tree.rootNodeId;
        this.history = tree.rootNodeId ? [tree.rootNodeId] : [];
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        this.loadError = `NPC 数据加载失败：${npcKey}（${message}）`;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 选择当前节点的某个选项：
     *   - 先按顺序应用 effects（目前只 friendship 真正写状态，其他 effect 暂以事件占位）；
     *   - nextNodeId 为 null 时关闭对话；
     *   - 否则切换到下一节点并写入 history。
     */
    selectChoice(choiceIndex: number): void {
      const node = this.currentNode;
      if (!node) return;
      const choice = node.choices[choiceIndex];
      if (!choice) return;

      if (choice.effects && this.npcKey) {
        for (const effect of choice.effects) {
          if (effect.type === 'friendship' && typeof effect.delta === 'number') {
            this.friendship += effect.delta;
            friendshipMemory.set(this.npcKey, this.friendship);
          }
          // give_item / quest_recommend / set_flag / open_panel：
          // 当前阶段保留为占位，后续由 R-Live-Quest / R-Live-Inventory 子代理接入。
        }
      }

      if (choice.nextNodeId === null) {
        this.close();
        return;
      }
      const next = this.tree?.nodes[choice.nextNodeId];
      if (!next) {
        this.loadError = `对话节点缺失：${choice.nextNodeId}`;
        return;
      }
      this.currentNodeId = choice.nextNodeId;
      this.history.push(choice.nextNodeId);
      // 切换节点必须把上一节点的分支快照和打字机状态归零，
      // 否则 UI 会在下一段文字还没滚完时就把旧选项渲染出来。
      this.branchChoices = [];
      this.isTypingDone = false;
    },

    /**
     * 标记当前节点的打字机已结束（自然结束或玩家跳过）。
     *
     * 副作用：把 currentNode.choices 快照写入 branchChoices，UI 据此渲染选项；
     * 没有对应 currentNode 时（旧 placeholder 入口）只翻 isTypingDone 标记，
     * branchChoices 保持调用方先前注入的值。
     *
     * 幂等：多次调用只生效一次，避免重复打字机回调污染 branchChoices。
     */
    markTypingDone(): void {
      if (this.isTypingDone) return;
      this.isTypingDone = true;
      const node = this.currentNode;
      if (node) {
        this.branchChoices = node.choices.slice();
      }
    },

    /**
     * 玩家选择分支：
     *   1. 在 branchChoices 中按 label 查找 choice（label 充当 UI 层 choiceId）；
     *   2. 触发 `dialog:choice` 事件，业务侧（任务/分析/教师后台）可订阅；
     *   3. 若 tree 存在则委托给 selectChoice 走原有 effects + 导航流程；
     *      否则（纯占位入口或手动注入 branchChoices 场景）直接 close。
     *
     * 注：label 在同一节点内一般是唯一的；如果业务真的有重名 label，
     * 第一个匹配项胜出，等同于 UI 上「上面的优先」的视觉直觉。
     */
    chooseBranch(choiceId: string): void {
      const choice = this.branchChoices.find((c) => c.label === choiceId);
      if (!choice) return;
      emitDialogChoice({
        npcKey: this.npcKey,
        nodeId: this.currentNodeId,
        choiceId,
        choice,
        timestamp: Date.now(),
      });
      const node = this.currentNode;
      if (node) {
        const idx = node.choices.findIndex((c) => c.label === choiceId);
        if (idx >= 0) {
          this.selectChoice(idx);
          return;
        }
      }
      this.close();
    },

    /**
     * 订阅 `dialog:choice` 事件，返回取消订阅函数。
     *
     * 监听器集合是模块级 Set，不挂在 pinia state 上，
     * 这样 Pinia 重置（createPinia + setActivePinia）也不会清空跨页面的业务订阅。
     */
    onChoice(listener: DialogChoiceListener): () => void {
      choiceListeners.add(listener);
      return () => {
        choiceListeners.delete(listener);
      };
    },

    /**
     * 关闭对话：
     *   - 同时清理旧 API 状态，确保 DialogModal 也会跟着收起；
     *   - friendship 不重置（写入 friendshipMemory，下次打开同 NPC 还原）。
     */
    close(): void {
      const wasOpen = this.visible || this.openNpcKey !== null;
      // R-13 §-1 critical fix：抓住关闭前的 npcKey，作为主线 evidence 触发。
      // 之前 close() 直接清 npcKey，导致主线任务 1-1（找镇长 Py 对话）等
      // dialog_complete 类 evidence 永远无法触发完成。
      const closingNpcKey = this.npcKey ?? this.openNpcKey;
      this.openNpcKey = null;
      this.lines = [];
      this.visible = false;
      this.npcKey = null;
      this.npcName = '';
      this.portrait = null;
      this.tree = null;
      this.currentNodeId = null;
      this.history = [];
      this.loadError = null;
      this.loading = false;
      this.branchChoices = [];
      this.isTypingDone = false;
      if (wasOpen) {
        playDialogClose();
        // Lazy import 避免与 mainQuest store 循环依赖（dialog store 不在 mainQuest
        // 加载之前初始化）。仅当真有 npcKey 时通知主线。
        if (closingNpcKey && typeof window !== 'undefined') {
          void import('@stores/mainQuest').then((mod) => {
            try {
              mod.useMainQuestStore().onDialogClose(closingNpcKey);
            } catch {
              // 忽略：mainQuest store 未挂载（test / 极端环境）
            }
          });
        }
      }
    },
  },
});

/** 内部测试钩子：清空 friendship 内存缓存，仅供 vitest 使用。 */
export function __resetFriendshipMemoryForTests(): void {
  friendshipMemory.clear();
}

/** 内部测试钩子：清空 `dialog:choice` 监听集合，仅供 vitest 使用。 */
export function __resetChoiceListenersForTests(): void {
  choiceListeners.clear();
}
