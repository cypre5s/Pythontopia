import { defineStore } from 'pinia';
import { playDialogClose, playDialogOpen } from '@/composables/useStardewFx';
import { fetchDialogContext } from '@/api/dialog';
import { http, ApiClientError } from '@/api/http';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import type { DialogContextDto } from '@/api/dto/DialogContextDto';

/**
 * NPC 对话树 / 节点 / 选项 / 效果类型定义。
 *
 * 字段对齐 data/npcs/<key>/dialog.json 结构，严格保留
 * npcKey / rootNodeId / nodes / choices / effects / conditions 字段名，
 * 调整字段需同步更新数据文件。
 */
export type DialogEffectType =
  | 'give_item'
  | 'quest_recommend'
  | 'set_flag'
  | 'open_panel'
  | 'route';

export interface DialogEffect {
  type: DialogEffectType;
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
  | 'first_time_this_week'
  | 'kc_mastered'
  | 'flag_set'
  | 'has_failed_submission'
  | 'no_failed_submission';

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
 * 其余字段（schedule / spawn / tags...）后续可扩展，
 * 此处只声明 DialogTree 实际用到的，避免类型膨胀。
 */
export interface NpcMeta {
  key: string;
  name: string;
  portrait?: string;
  description?: string;
}

/** `GET /api/npc/{key}/greeting` 响应（对齐后端 NpcGreetingResponse；TS strict 显式 DTO，禁 any）。 */
export interface NpcGreetingDto {
  greeting: string;
  available: boolean;
  grounded: boolean;
  citedEventIds: number[];
  degraded: boolean;
  cached: boolean;
}

/**
 * NPC 首句个性化寒暄状态机（R-15 §8）：
 *   - `idle`：未发起 / 已重置；
 *   - `loading`：与 meta/dialog 并行拉取中；
 *   - `ready`：拿到记忆感知寒暄，插入脚本对话首节点之前展示；
 *   - `fallback`：记忆 / agent 服务不可用 → 显示「（记忆服务暂不可用）」并照常走脚本（不伪造内容）；
 *   - `none`：该生暂无可引用记忆（如新玩家），既不寒暄也不报故障，直接走脚本。
 */
export type GreetingStatus = 'idle' | 'loading' | 'ready' | 'fallback' | 'none';

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
   * - 任意 NPC `openWithTree` 触发首次拉取（context 可能随 NPC / 玩家实时数据变化，
   *   所以每次开新 NPC 都重拉一次）。
   */
  context: DialogContextDto | null;

  /** NPC 首句个性化寒暄状态（R-15 §8，详见 {@link GreetingStatus}）。 */
  greetingStatus: GreetingStatus;
  /** `ready` 时的个性化寒暄文本（展示在脚本对话首节点之前）。 */
  greetingText: string;
  /** 寒暄是否落在被引用的记忆证据上（防幻觉透明度，供 UI 可选标注）。 */
  greetingGrounded: boolean;
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

// ─────────────────── 对话 flag / 每周一次 持久化 ───────────────────
// set_flag 效果与 flag_set / first_time_this_week 条件共用的轻量持久层：
// 走 localStorage（与主线进度同策略），SSR / 隐私模式下读写失败一律静默降级。

const FLAGS_STORAGE_KEY = 'pythontopia.dialogFlags';
const WEEKLY_STORAGE_KEY = 'pythontopia.dialogWeeklySeen';

function readStorageMap(storageKey: string): Record<string, string> {
  if (typeof localStorage === 'undefined') return {};
  try {
    const raw = localStorage.getItem(storageKey);
    const parsed: unknown = raw ? JSON.parse(raw) : {};
    if (typeof parsed === 'object' && parsed !== null) {
      return parsed as Record<string, string>;
    }
  } catch {
    /* 损坏数据按空处理 */
  }
  return {};
}

function writeStorageMap(storageKey: string, map: Record<string, string>): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(storageKey, JSON.stringify(map));
  } catch {
    /* 配额/隐私模式失败静默 */
  }
}

/** ISO 周标识（如 2026-W24），first_time_this_week 以它为「本周」边界。 */
export function isoWeekOf(date: Date): string {
  // ISO 8601：周四所在的年/周。先把日期移到本周四，再算它是当年第几周。
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const day = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - day);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
  return `${d.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
}

export function setDialogFlag(flagKey: string): void {
  const flags = readStorageMap(FLAGS_STORAGE_KEY);
  flags[flagKey] = '1';
  writeStorageMap(FLAGS_STORAGE_KEY, flags);
}

export function hasDialogFlag(flagKey: string): boolean {
  return readStorageMap(FLAGS_STORAGE_KEY)[flagKey] === '1';
}

function weeklySeenThisWeek(key: string): boolean {
  return readStorageMap(WEEKLY_STORAGE_KEY)[key] === isoWeekOf(new Date());
}

function markWeeklySeen(key: string): void {
  const map = readStorageMap(WEEKLY_STORAGE_KEY);
  map[key] = isoWeekOf(new Date());
  writeStorageMap(WEEKLY_STORAGE_KEY, map);
}

/** 入口插话节点的「本周已抢占过」记录 key。 */
function entrySeenKey(npcKey: string, nodeId: string): string {
  return `entry:${npcKey}:${nodeId}`;
}

/** give_item 道具的展示名（R-14 后无背包，发放=flag 记录 + toast 反馈）。 */
const DIALOG_ITEM_LABELS: Readonly<Record<string, string>> = Object.freeze({
  retro_seal: '复盘印章',
  class_heatmap: '班级热力图',
  evidence_slip: '证据条',
});

/** 内部测试钩子：清空对话 flag / 每周记录，仅供 vitest 使用。 */
export function __resetDialogFlagsForTests(): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.removeItem(FLAGS_STORAGE_KEY);
    localStorage.removeItem(WEEKLY_STORAGE_KEY);
  } catch {
    /* 忽略 */
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

/**
 * 寒暄请求的独立超时（ms）：后端 graph 读超时为 30s，前端必须给后端足够时间跑完，否则后端还在生成、
 * 前端先超时就会误显「记忆服务暂不可用」横幅（假阴性 fallback）。快档模型（flash）寒暄实测约 3s，
 * 正常调用远快于此；15s 仅用于覆盖冷启动 / graph 抖动等慢尾。
 */
const GREETING_TIMEOUT_MS = 15_000;

/** 寒暄请求最大尝试次数：瞬时快失败重试一次；慢调用（＝超时）不重试，避免叠加等待。 */
const GREETING_MAX_ATTEMPTS = 2;

/**
 * 是否值得重试：仅对「通常很快返回」的瞬时故障重试——后端 5xx 或网络错误（如 graph 短暂抖动）。
 * 超时（TIMEOUT）说明本就慢，再试只会叠加等待；4xx 是确定性错误，重试无意义——两者一律不重试。
 *
 * <b>依赖后端「失败不缓存」</b>：重试会触发后端一次全新计算（fresh graph 调用），故瞬时抖动可被自愈；
 * 若后端缓存了失败，这里的重试将永远拿到同一份降级结果而无法恢复。
 */
function isRetryableGreetingError(err: unknown): boolean {
  if (err instanceof ApiClientError) {
    if (err.code === 'NETWORK_ERROR') return true;
    if (typeof err.status === 'number' && err.status >= 500) return true;
  }
  return false;
}

/**
 * 拉取 NPC 首句个性化寒暄（JWT 经 axios http 实例自动注入 Authorization）。
 *
 * 该请求是「可选 / 后台」性质：失败已有降级（fallback 提示 + 照常走脚本），故 `suppressNetworkToast=true`，
 * 不弹全局网络错误 toast。叠加独立超时 + 瞬时快失败重试一次（见 {@link isRetryableGreetingError}），
 * 避免下游一次性抖动就闪「记忆服务暂不可用」。按 collision 约定，寒暄 API 内联在 dialog store，不进共享 api index。
 */
async function fetchNpcGreeting(npcKey: string): Promise<NpcGreetingDto> {
  let lastError: unknown;
  for (let attempt = 1; attempt <= GREETING_MAX_ATTEMPTS; attempt += 1) {
    try {
      const res = await http.get<NpcGreetingDto>(`/api/npc/${encodeURIComponent(npcKey)}/greeting`, {
        suppressNetworkToast: true,
        timeout: GREETING_TIMEOUT_MS,
      });
      return res.data;
    } catch (err) {
      lastError = err;
      if (attempt < GREETING_MAX_ATTEMPTS && isRetryableGreetingError(err)) {
        continue;
      }
      throw err;
    }
  }
  throw lastError;
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
    loadError: null,
    loading: false,
    branchChoices: [],
    isTypingDone: false,
    context: null,
    greetingStatus: 'idle',
    greetingText: '',
    greetingGrounded: false,
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
     * 加载并打开 NPC 对话树。
     *
     * 流程：
     *   1. 立即 visible=true 并显示 loading（让 UI 不闪烁）；
     *   2. 并行 fetch meta.json + dialog.json；
     *   3. 失败时填充 loadError，UI 自行降级展示；
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
      this.branchChoices = [];
      this.isTypingDone = false;
      this.greetingStatus = 'idle';
      this.greetingText = '';
      this.greetingGrounded = false;
      // R-15 §8：与 meta/dialog 并行拉取首句个性化寒暄（非阻塞），结果展示在脚本对话首节点之前。
      void this.fetchGreeting(npcKey);
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

        // 入口可能被「插话」节点抢占（每周复盘/班级周报等条件内容），否则走 root。
        const entryId = this.pickEntryNodeId(tree);
        this.currentNodeId = entryId;
        this.history = entryId ? [entryId] : [];
        const entryNode = entryId ? tree.nodes[entryId] : undefined;
        if (entryNode) {
          this.markNodeShown(entryNode);
          if (entryId !== tree.rootNodeId) {
            markWeeklySeen(entrySeenKey(tree.npcKey, entryId));
          }
        }
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
     * 并行拉取 NPC 首句个性化寒暄并落 store（由 openWithTree 触发，非阻塞）。
     *
     * <b>fail-fast</b>：端点失败（网络/超时/4xx/5xx）一律落 `fallback`（前端显示「（记忆服务暂不可用）」），
     * 绝不伪造个性化内容；后端「无记忆」确定态落 `none`（既不寒暄也不报故障）。仅当仍是本次同一 NPC 对话时
     * 才写入，避免玩家快速切换 NPC 时旧结果回写覆盖新对话。
     */
    async fetchGreeting(npcKey: string): Promise<void> {
      this.greetingStatus = 'loading';
      this.greetingText = '';
      this.greetingGrounded = false;
      try {
        const dto = await fetchNpcGreeting(npcKey);
        if (!(this.visible && this.npcKey === npcKey)) return;
        if (dto.available && dto.greeting.trim().length > 0) {
          this.greetingText = dto.greeting;
          this.greetingGrounded = dto.grounded;
          this.greetingStatus = 'ready';
        } else if (dto.degraded) {
          this.greetingStatus = 'fallback';
        } else {
          this.greetingStatus = 'none';
        }
      } catch (err) {
        if (!(this.visible && this.npcKey === npcKey)) return;
        // 记忆/agent 下游不可用 → 展示「（记忆服务暂不可用）」并照常走脚本（不伪造个性化内容）。
        this.greetingStatus = 'fallback';
        if (typeof console !== 'undefined' && typeof console.warn === 'function') {
          console.warn('[dialog] greeting fetch failed; showing memory-unavailable notice', err);
        }
      }
    },

    /**
     * 选择当前节点的某个选项：
     *   - 先按顺序应用 effects（quest_recommend 发主线任务、route/open_panel 触发导航）；
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
          // NPC「推荐主线」：R-15 退役旧 mainQuest acceptQuest 后，统一引导到冒险地图入口。
          if (effect.type === 'quest_recommend' && effect.questKey) {
            if (effect.questKey === 'claim_class_quest') {
              // 班长「领班级任务」：班级任务不在主线里，直接带到公告板·班级任务页签，
              // 学生在那里真实领取教师发布的任务。
              routeToNavigate = '/board?tab=classroom';
            } else {
              routeToNavigate = '/mainline-map';
            }
          }
          // set_flag：落持久 flag，供 flag_set 条件与后续内容解锁使用。
          if (effect.type === 'set_flag' && effect.flagKey) {
            setDialogFlag(effect.flagKey);
          }
          // give_item：R-14 后无背包系统，发放 = flag 记录（item:<key>）+ toast 反馈，
          // 既不静默丢弃也不引入无教育目的的库存 UI。
          if (effect.type === 'give_item' && effect.itemKey) {
            const itemKey = effect.itemKey;
            setDialogFlag(`item:${itemKey}`);
            try {
              useToastStore().show(
                `获得「${DIALOG_ITEM_LABELS[itemKey] ?? itemKey}」`,
                'success',
              );
            } catch {
              /* toast store 未挂载（test / 极端环境）忽略 */
            }
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
      // 进入条件节点（如 weekly_retro）即记录展示，「每周一次」从此刻起算。
      this.markNodeShown(next);
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
        // 过滤掉「目标节点条件不满足」的选项：例如没有失败提交时隐藏
        // 「上次失败的提交是什么？」、本周已复盘过时隐藏「开始本周复盘」。
        this.branchChoices = node.choices.filter((choice) => {
          if (!choice.nextNodeId || !this.tree) return true;
          const target = this.tree.nodes[choice.nextNodeId];
          if (!target) return true;
          return this.evaluateConditions(target.conditions ?? []);
        });
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
     *
     * - first_time_this_week：该 key 本 ISO 周尚未展示过（展示时由 markNodeShown 记录）；
     * - flag_set：set_flag 效果落过对应 flagKey；
     * - kc_mastered：learning store 掌握度快照中该 KC ≥ 阈值（默认 0.4，与「测·达标」一致）；
     * - has_failed_submission / no_failed_submission：基于对话 context 的最近失败提交；
     *   context 尚未到达（后台异步拉取中）时按「无失败记录」处理，保证新玩家路径可见。
     */
    evaluateConditions(conditions: DialogCondition[]): boolean {
      if (conditions.length === 0) return true;
      const hasFailed = (): boolean => {
        const title = this.context?.stats?.['lastFailedProblemTitle'];
        return Boolean(title) && title !== '—' && title !== '-';
      };
      for (const cond of conditions) {
        switch (cond.type) {
          case 'has_failed_submission':
            if (!hasFailed()) return false;
            break;
          case 'no_failed_submission':
            if (hasFailed()) return false;
            break;
          case 'first_time_this_week': {
            const key = cond.key ?? cond.flagKey;
            if (key && weeklySeenThisWeek(key)) return false;
            break;
          }
          case 'flag_set': {
            const key = cond.flagKey ?? cond.key;
            if (key && !hasDialogFlag(key)) return false;
            break;
          }
          case 'kc_mastered': {
            if (!cond.kcCode) break;
            try {
              const learning = useLearningStore();
              const snap = learning.field.masterySnapshot.find(
                (entry) => entry.kcCode === cond.kcCode,
              );
              const threshold = cond.value ?? 0.4;
              if (!snap || snap.masteryScore < threshold) return false;
            } catch {
              // learning store 不可用（极端测试环境）→ 视为未达成，隐藏增益内容。
              return false;
            }
            break;
          }
        }
      }
      return true;
    },

    /**
     * 节点真正展示给玩家时调用：first_time_this_week 类条件记下「本周已看过」，
     * 下次评估即不满足，实现「每周一次」的插话语义。
     */
    markNodeShown(node: DialogNode): void {
      for (const cond of node.conditions ?? []) {
        if (cond.type === 'first_time_this_week') {
          const key = cond.key ?? cond.flagKey;
          if (key) markWeeklySeen(key);
        }
      }
    },

    /**
     * 选择对话入口节点：优先「插话」节点，否则 rootNodeId。
     *
     * 插话节点 = 不被任何 choice 引用、且自带非空 conditions 的节点
     * （如每周复盘 / 班级周报 / 防幻觉告诫 / 矿洞 boss 提示）。按数据声明顺序
     * 取第一个条件满足的；条件全不满足时回落到 rootNodeId。
     *
     * 入口抢占统一带「每周一次」阻尼（entry:<npc>:<node> 周记录）：
     * kc_mastered 这类「一旦达成永远为真」的条件不会永久劫持 greet 主菜单。
     */
    pickEntryNodeId(tree: NpcDialogTree): string {
      const referenced = new Set<string>();
      for (const node of Object.values(tree.nodes)) {
        for (const choice of node.choices ?? []) {
          if (choice.nextNodeId) referenced.add(choice.nextNodeId);
        }
      }
      for (const [id, node] of Object.entries(tree.nodes)) {
        if (id === tree.rootNodeId || referenced.has(id)) continue;
        const conds = node.conditions ?? [];
        if (conds.length === 0) continue;
        if (weeklySeenThisWeek(entrySeenKey(tree.npcKey, id))) continue;
        if (this.evaluateConditions(conds)) {
          return id;
        }
      }
      return tree.rootNodeId;
    },

    /**
     * 关闭对话：
     *   - 同时清理旧 API 状态，确保 DialogModal 也会跟着收起。
     */
    close(): void {
      const wasOpen = this.visible || this.openNpcKey !== null;

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
      // 寒暄随对话关闭复位，下次开新 NPC 重新拉取。
      this.greetingStatus = 'idle';
      this.greetingText = '';
      this.greetingGrounded = false;
      if (wasOpen) {
        playDialogClose();
        // R-15：旧 mainQuest onDialogClose（dialog_complete 子任务推进）已退役，对话关闭不再通知主线。
      }
    },
  },
});

/** 内部测试钩子：清空 `dialog:choice` 监听集合，仅供 vitest 使用。 */
export function __resetChoiceListenersForTests(): void {
  choiceListeners.clear();
}
