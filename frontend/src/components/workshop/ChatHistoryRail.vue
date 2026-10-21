<script setup lang="ts">
/**
 * ChatHistoryRail —— 工坊左栏「聊天记录」可伸缩侧栏。
 *
 * 默认收起为一条竖把手；点把手拉出，列出学生的工坊聊天历史（真实数据 /api/workshop/chat-history/turns），
 * 点某条把该次问答回看到对话流（由父级注入 WorkshopChat）。懒加载：仅首次展开时拉取；
 * 失败安全降级（不打断工坊主流程），可手动重试 / 刷新。
 */
import { computed, ref, watch } from 'vue';
import {
  listWorkshopChatHistoryTurns,
  rememberWorkshopChatHistoryTurn,
  type WorkshopChatHistoryTurnDto,
} from '@/api/workshopHistory';
import { playUiClick } from '@/composables/useStardewFx';

const props = withDefaults(
  defineProps<{
    ownerId: number | null;
    embeddedOpen?: boolean;
    pendingTurn?: WorkshopChatHistoryTurnDto | null;
  }>(),
  {
    embeddedOpen: false,
    pendingTurn: null,
  },
);

const emit = defineEmits<{ (e: 'select', turns: WorkshopChatHistoryTurnDto[]): void }>();

const open = ref(false);
const loading = ref(false);
const loaded = ref(false);
const failed = ref(false);
const turns = ref<WorkshopChatHistoryTurnDto[]>([]);
const effectiveOpen = computed<boolean>(() => props.embeddedOpen || open.value);
let loadGeneration = 0;

async function load(force = false): Promise<void> {
  const ownerId = props.ownerId;
  if (ownerId === null) return;
  const generation = ++loadGeneration;
  loading.value = true;
  failed.value = false;
  try {
    const result = await listWorkshopChatHistoryTurns(ownerId, 50, { force });
    if (generation !== loadGeneration || props.ownerId !== ownerId) return;
    turns.value = result;
    loaded.value = true;
  } catch {
    if (generation !== loadGeneration || props.ownerId !== ownerId) return;
    failed.value = true;
  } finally {
    if (generation === loadGeneration && props.ownerId === ownerId) {
      loading.value = false;
    }
  }
}

function mergeTurn(turn: WorkshopChatHistoryTurnDto): void {
  if (props.ownerId === null) return;
  const existingIndex = turns.value.findIndex((item) => item.id === turn.id);
  if (existingIndex >= 0) {
    turns.value = turns.value.map((item, index) => (index === existingIndex ? turn : item));
  } else {
    turns.value = [turn, ...turns.value];
  }
  rememberWorkshopChatHistoryTurn(props.ownerId, turn);
  failed.value = false;
}

/** 一段会话（同一 conversationId 的多轮工具 turn）；旧数据无 id 时每条各成一段。 */
interface ConversationGroup {
  key: string;
  title: string;
  turnCount: number;
  latestAt: string;
  turns: WorkshopChatHistoryTurnDto[];
}

// 按 conversationId 把多轮聊天归并成「会话」：一个窗口一条，而非每问一条。
const groups = computed<ConversationGroup[]>(() => {
  const byKey = new Map<string, WorkshopChatHistoryTurnDto[]>();
  for (const s of turns.value) {
    const key = s.conversationId || `s-${s.id}`;
    const bucket = byKey.get(key);
    if (bucket) bucket.push(s);
    else byKey.set(key, [s]);
  }
  const result: ConversationGroup[] = [];
  for (const [key, bucket] of byKey) {
    const asc = [...bucket].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    const earliest = asc[0];
    if (!earliest) continue;
    const latest = asc[asc.length - 1] ?? earliest;
    result.push({
      key,
      title: earliest.userText,
      turnCount: asc.length,
      latestAt: latest.createdAt,
      turns: bucket,
    });
  }
  // 最近活跃的会话排最前。
  return result.sort((a, b) => b.latestAt.localeCompare(a.latestAt));
});

// 本地搜索：按会话标题 / 任一轮问题文本过滤（不发后端请求）。
const query = ref('');
const filteredGroups = computed<ConversationGroup[]>(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return groups.value;
  return groups.value.filter((g) => {
    if (g.title.toLowerCase().includes(q)) return true;
    return g.turns.some((s) => s.userText.toLowerCase().includes(q));
  });
});

function toggle(): void {
  playUiClick();
  open.value = !open.value;
  // 懒加载：首次展开才拉取，避免无谓请求 / 影响首屏。
  if (open.value && !loaded.value && !loading.value) void load();
}

watch(
  () => props.embeddedOpen,
  (value) => {
    if (value && !loaded.value && !loading.value) void load();
  },
);

watch(
  () => props.ownerId,
  (ownerId, previousOwnerId) => {
    if (ownerId === previousOwnerId) return;
    // 先作废旧请求并同步清屏；transport 即使不响应 abort，晚到结果也过不了 generation+owner 双闸。
    loadGeneration += 1;
    turns.value = [];
    query.value = '';
    loaded.value = false;
    loading.value = false;
    failed.value = false;
    if (ownerId !== null && effectiveOpen.value) void load();
  },
  { immediate: true },
);

watch(
  () => props.pendingTurn,
  (turn) => {
    if (turn) mergeTurn(turn);
  },
);

function refresh(): void {
  playUiClick();
  void load(true);
}

function pick(group: ConversationGroup): void {
  playUiClick();
  // 列表项已含回放所需 payload，整段会话直接回放，无需再请求详情。
  emit('select', group.turns);
}

function shortTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const now = new Date();
  const pad = (n: number): string => String(n).padStart(2, '0');
  const sameDay = d.toDateString() === now.toDateString();
  return sameDay
    ? `${pad(d.getHours())}:${pad(d.getMinutes())}`
    : `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
</script>

<template>
  <aside
    class="history"
    :class="{ 'history--open': effectiveOpen }"
    data-testid="workshop-history-rail"
  >
    <button
      v-if="!props.embeddedOpen"
      type="button"
      class="history__handle"
      data-testid="workshop-history-handle"
      :aria-expanded="effectiveOpen"
      :title="effectiveOpen ? '收起聊天记录' : '展开聊天记录'"
      @click="toggle"
    >
      <span
        class="history__handle-icon"
        aria-hidden="true"
      >🕘</span>
      <span class="history__handle-text">聊天记录</span>
      <span
        class="history__handle-chev"
        aria-hidden="true"
      >{{ effectiveOpen ? '‹' : '›' }}</span>
    </button>

    <div
      v-if="effectiveOpen"
      class="history__panel"
      data-testid="workshop-history-panel"
    >
      <div class="history__tools">
        <input
          v-if="loaded && !failed && turns.length > 0"
          v-model="query"
          type="search"
          class="history__search"
          placeholder="搜索历史"
          data-testid="workshop-history-search"
        >
        <button
          type="button"
          class="history__refresh"
          data-testid="workshop-history-refresh"
          aria-label="刷新历史问答"
          title="刷新"
          @click="refresh"
        >
          ↻
        </button>
      </div>

      <p
        v-if="loading"
        class="history__state"
      >
        加载中…
      </p>
      <p
        v-else-if="failed"
        class="history__state"
      >
        没取到历史记录
        <button
          type="button"
          class="history__retry"
          @click="refresh"
        >
          重试
        </button>
      </p>
      <p
        v-else-if="turns.length === 0"
        class="history__state"
      >
        还没有问答记录
      </p>
      <p
        v-else-if="filteredGroups.length === 0"
        class="history__state"
      >
        没有匹配「{{ query }}」的会话。
      </p>
      <ul
        v-else
        class="history__list"
      >
        <li
          v-for="g in filteredGroups"
          :key="g.key"
        >
          <button
            type="button"
            class="history__item"
            :title="`回看并继续这段会话（${g.turnCount} 轮）：${g.title}`"
            @click="pick(g)"
          >
            <span class="history__q">{{ g.title }}</span>
            <span class="history__meta">
              <span
                v-if="g.turnCount > 1"
                class="history__turns"
              >{{ g.turnCount }} 轮</span>
              <span class="history__time">{{ shortTime(g.latestAt) }}</span>
            </span>
          </button>
        </li>
      </ul>
    </div>
  </aside>
</template>

<style scoped>
.history {
  flex: 0 0 30px;
  width: 30px;
  min-height: 0;
  display: flex;
  flex-direction: row;
  overflow: hidden;
  border: 3px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  background: linear-gradient(180deg, #fff7e6 0%, #f3e4be 100%);
  box-shadow: inset 0 0 0 2px rgba(255, 247, 230, 0.55);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  transition: flex-basis 0.22s ease, width 0.22s ease;
}
.history--open {
  flex: 0 0 clamp(220px, 21vw, 280px);
  width: clamp(220px, 21vw, 280px);
}

/* 把手：默认竖条，竖排文字 */
.history__handle {
  flex: 0 0 28px;
  width: 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 8px 2px;
  border: none;
  border-right: 2px dashed rgba(110, 79, 29, 0.35);
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  color: var(--pyt-color-text-primary);
}
.history__handle:hover {
  background: rgba(255, 224, 152, 0.4);
}
.history__handle-icon {
  font-size: 14px;
}
.history__handle-text {
  writing-mode: vertical-rl;
  text-orientation: upright;
  letter-spacing: 2px;
  font-size: 12px;
  font-weight: 700;
}
.history__handle-chev {
  font-size: 14px;
  font-weight: 700;
  color: var(--pyt-color-text-muted);
}

/* 展开面板 */
.history__panel {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
}
.history__tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  flex: 0 0 auto;
}
.history__refresh {
  font-family: inherit;
  font-size: 13px;
  line-height: 1;
  padding: 2px 6px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
}
.history__refresh:hover {
  background: var(--pyt-color-warning-bg);
}
.history__search {
  flex: 1 1 auto;
  min-width: 0;
  box-sizing: border-box;
  font-family: inherit;
  font-size: 11px;
  padding: 4px 8px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-primary);
}
.history__search:focus {
  outline: none;
  border-color: var(--pyt-color-text-hint);
}
.history__state {
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--pyt-color-text-muted);
}
.history__retry {
  font-family: inherit;
  font-size: 11px;
  margin-left: 4px;
  padding: 1px 6px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-bg-elevated);
  cursor: pointer;
}
.history__list {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0;
  scrollbar-width: thin;
}
.history__item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 9px 4px;
  text-align: left;
  font-family: inherit;
  border: 0;
  border-bottom: 1px solid rgba(140, 106, 35, 0.22);
  border-radius: 0;
  background: transparent;
  color: var(--pyt-color-text-primary);
  cursor: pointer;
}
.history__item:hover {
  background: rgba(242, 218, 162, 0.34);
}
.history__q {
  font-size: 12px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.history__meta {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
}
.history__turns {
  font-size: 9px;
  color: var(--pyt-color-text-muted);
  white-space: nowrap;
}
.history__time {
  margin-left: auto;
  font-size: 10px;
  color: var(--pyt-color-text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .history {
    flex: 0 0 auto;
    width: 100%;
  }
  .history--open {
    flex: 0 0 auto;
    width: 100%;
  }
  .history__handle {
    flex: 0 0 auto;
    width: 100%;
    flex-direction: row;
    justify-content: flex-start;
    gap: 8px;
    padding: 6px 10px;
    border-right: none;
    border-bottom: 2px dashed rgba(110, 79, 29, 0.35);
  }
  .history__handle-text {
    writing-mode: horizontal-tb;
    text-orientation: mixed;
    letter-spacing: 1px;
  }
  .history__handle-chev {
    margin-left: auto;
    transform: rotate(90deg);
  }
  .history--open {
    flex-direction: column;
  }
  .history__list {
    max-height: 38vh;
  }
}
</style>
