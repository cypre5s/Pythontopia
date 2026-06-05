import { defineStore } from 'pinia';
import { npcDialog } from '@/game/data/npcDialog';
import { playDialogClose, playDialogOpen } from '@/composables/useStardewFx';
import { fetchDialogContext } from '@/api/dialog';
import type { DialogContextDto } from '@/api/dto/DialogContextDto';

/**
 * NPC 对话树 / 节点 / 选项 / 效果类型定义。
 *
 * 字段对齐 data/npcs/<key>/dialog.json 结构，严格保留
 * npcKey / rootNodeId / nodes / choices / effects / conditions 字段名，
 * 调整字段需同步更新数据文件。
 */
export type DialogEffectType =
  | 'friendship'
  | 'give_item'
  | 'quest_recommend'
  | 'set_flag'
  | 'open_panel'
  | 'route';

export interface DialogEffect {
  type: DialogEffectType;
  delta?: number;
  itemKey?: string;
  questKey?: string;
  flagKey?: string;
  route?: string;
  /**
   * open_panel 的目标面板简称（无路由参数的学习面板）。
   * 也可直接用 `route` 指定带参路由（如 `/lecture/1`）。
   */
  panel?: string;
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
  | 'gift_just_given'
  | 'has_failed_submission';

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
  /** 当前 NPC 的好感度（内存态） */
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

  /**
   * 当前对话使用的渲染上下文，由 `openWithTree` 调用 /api/dialog/context 拿到。
   *
   * - 拉取失败 / 未登录时保留为 null，DialogTree / DialogModal 渲染器自动用空 stats + 'cautious'
   *   profile 兜底，所有 {{stats.xxx}} 走 '—' 占位，不会暴露字面量；
   * - 同一玩家在同一 NPC 间切换时复用上一次 context，避免每次对话都打一次接口造成抖动；
   * - 任意 NPC `openWithTree` 触发首次拉取（context 与 NPC 的 giftJustGiven 强相关，
   *   所以每次开新 NPC 都重拉一次）。
   */
  context: DialogContextDto | null;
}

/** NPC 数据目录前缀，可被测试 mock 覆写。 */
const DEFAULT_NPC_DATA_BASE = '/data/npcs';

/**
 * `open_panel` 效果的 `panel` 简称 → 前端学习面板路由映射。
 * 带参路由（如 `/lecture/:chapter`）请在 dialog.json 里直接用 `route` 字段指定，
 * 例如 `{ "type": "open_panel", "route": "/lecture/1" }`。
 */
const PANEL_ROUTE_MAP: Readonly<Record<string, string>> = Object.freeze({
  library: '/library',
  training: '/training',
  workshop: '/workshop',
  tutor: '/tutor',
  review: '/review-hall',
  examples: '/examples',
  problems: '/problems',
});

/**
 * 内存级好感度缓存：dialog store 关闭后仍保留同会话内 NPC 的好感度。
 */
const friendshipMemory = new Map<string, number>();

/** 持久化到 localStorage，让跨刷新也保留好感度。 */
const FRIENDSHIP_KEY = 'pyt_friendship_v1';

function loadFriendshipFromStorage(): void {
  if (typeof window === 'undefined') return;
  try {
    const raw = window.localStorage.getItem(FRIENDSHIP_KEY);
    if (!raw) return;
    const obj = JSON.parse(raw) as Record<string, number>;
    for (const [k, v] of Object.entries(obj)) {
      if (typeof v === 'number') friendshipMemory.set(k, v);
    }
  } catch { /* 隐私模式 / 数据损坏 */ }
}

function persistFriendship(): void {
  if (typeof window === 'undefined') return;
  try {
    const obj: Record<string, number> = {};
    for (const [k, v] of friendshipMemory) obj[k] = v;
    window.localStorage.setItem(FRIENDSHIP_KEY, JSON.stringify(obj));
  } catch { /* 隐私模式 */ }
}

loadFriendshipFromStorage();

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

async function fetchJson<T>(url: string, timeoutMs = 8000): Promise<T> {
  // 加超时：静态 meta/dialog 理应秒回；万一卡住（代理/网络抖动）也不能让对话永远「加载中…」。
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { headers: { Accept: 'application/json' }, signal: controller.signal });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status} ${url}`);
    }
    return (await res.json()) as T;
  } finally {
    clearTimeout(timer);
  }
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
    context: null,
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
        // 只 await meta + dialog（本地静态、秒回）就立即显示对话，避免被 context（走后端
        // axios，最长 10s 超时）拖成长时间「加载中…」卡住。context 改为后台异步补。
        const [meta, tree] = await Promise.all([
          fetchJson<NpcMeta>(`${baseUrl}/${npcKey}/meta.json`),
          fetchJson<NpcDialogTree>(`${baseUrl}/${npcKey}/dialog.json`),
        ]);
        this.npcName = meta.name || npcKey;
        this.portrait = meta.portrait ?? null;
        this.tree = tree;

        const gated = this.findBestGatedNode(tree);
        const startNode = gated ?? tree.rootNodeId;
        this.currentNodeId = startNode;
        this.history = startNode ? [startNode] : [];
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        this.loadError = `NPC 数据加载失败：${npcKey}（${message}）`;
      } finally {
        this.loading = false;
      }

      // 对话本体已显示，后台异步补 context（真实 stats 用于模板渲染）。拉取失败/超时
      // 不影响对话，渲染器自动用空 stats 兜底成 '—'。只在仍是本次同一 NPC 对话时写入，
      // 防止玩家快速切换 NPC 时旧 context 覆盖新对话。
      if (this.tree && !this.loadError) {
        void fetchDialogContext(npcKey)
          .then((ctx) => {
            if (this.visible && this.npcKey === npcKey) {
              this.context = ctx;
            }
          })
          .catch((err) => {
            if (typeof console !== 'undefined' && typeof console.warn === 'function') {
              console.warn('[dialog] context fetch failed; using fallback', err);
            }
          });
      }
    },

    /**
     * 选择当前节点的某个选项：
     *   - 先按顺序应用 effects（friendship 写好感、quest_recommend 发主线任务、route/open_panel 触发导航）；
     *   - nextNodeId 为 null 时关闭对话；
     *   - 否则切换到下一节点并写入 history。
     */
    selectChoice(choiceIndex: number): void {
      const node = this.currentNode;
      if (!node) return;
      const choice = node.choices[choiceIndex];
      if (!choice) return;

      let routeToNavigate: string | null = null;
      if (choice.effects && this.npcKey) {
        for (const effect of choice.effects) {
          if (effect.type === 'friendship' && typeof effect.delta === 'number') {
            this.friendship += effect.delta;
            friendshipMemory.set(this.npcKey, this.friendship);
            persistFriendship();
          }
          // 主线发任务：NPC 推荐/激活一个主线子任务（questKey 对齐 mainQuests id，如 "1-1"）。
          // 用 lazy import 规避与 mainQuest store 的循环依赖（同 close() 的做法）。
          if (effect.type === 'quest_recommend' && effect.questKey) {
            const questKey = effect.questKey;
            void import('@stores/mainQuest').then((mod) => {
              try {
                mod.useMainQuestStore().acceptQuest(questKey);
              } catch {
                /* mainQuest store 未挂载（test / 极端环境）忽略 */
              }
            });
          }
          // 打开学习面板：复用 route 的 pythontopia:dialog-navigate 机制，
          // 既支持 effect.route 带参路由（/lecture/1），也支持 effect.panel 简称。
          if (effect.type === 'route' || effect.type === 'open_panel') {
            const target =
              effect.route ?? (effect.panel ? PANEL_ROUTE_MAP[effect.panel] ?? null : null);
            if (target) {
              routeToNavigate = target;
            }
          }
        }
      }

      if (routeToNavigate) {
        this.close();
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('pythontopia:dialog-navigate', { detail: { route: routeToNavigate } }));
        }
        return;
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
     * 评估条件列表是否全部满足（AND 逻辑）。
     * 当前实现 friendship_min 与 has_failed_submission；其余类型暂按满足处理。
     */
    evaluateConditions(conditions: DialogCondition[]): boolean {
      if (conditions.length === 0) return true;
      for (const cond of conditions) {
        switch (cond.type) {
          case 'friendship_min':
            if (this.friendship < (cond.value ?? 0)) return false;
            break;
          case 'has_failed_submission': {
            const title = this.context?.stats?.lastFailedProblemTitle;
            if (!title || title === '—' || title === '-') return false;
            break;
          }
          case 'flag_set':
          case 'kc_mastered':
          case 'first_time_this_week':
          case 'gift_just_given':
            break;
        }
      }
      return true;
    },

    /**
     * 在对话树中找 friendship-gated 特殊节点（如 friendship_max）。
     * 优先选 friendship_min 值最高且当前满足的节点。
     */
    findBestGatedNode(tree: NpcDialogTree): string | null {
      let best: { nodeId: string; threshold: number } | null = null;
      for (const [nodeId, node] of Object.entries(tree.nodes)) {
        if (nodeId === tree.rootNodeId) continue;
        const friendshipCond = node.conditions.find((c) => c.type === 'friendship_min');
        if (!friendshipCond) continue;
        const threshold = friendshipCond.value ?? 0;
        if (this.friendship >= threshold) {
          if (!best || threshold > best.threshold) {
            best = { nodeId, threshold };
          }
        }
      }
      return best?.nodeId ?? null;
    },

    /**
     * 关闭对话：
     *   - 同时清理旧 API 状态，确保 DialogModal 也会跟着收起；
     *   - friendship 不重置（写入 friendshipMemory，下次打开同 NPC 还原）；
     *   - 有过实质互动（history > 1）时自动 +1 好感。
     */
    close(): void {
      const wasOpen = this.visible || this.openNpcKey !== null;
      const closingNpcKey = this.npcKey ?? this.openNpcKey;

      if (closingNpcKey && this.history.length > 1) {
        this.friendship += 1;
        friendshipMemory.set(closingNpcKey, this.friendship);
        persistFriendship();
      }
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
      // 关闭时清掉 context，下次再开走最新数据；避免本周练习数 / 班级数据等过期。
      this.context = null;
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
