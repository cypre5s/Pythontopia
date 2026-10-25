<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import PyButton from '@/components/ui/PyButton.vue';
import PyEmptyState from '@/components/ui/PyEmptyState.vue';
import MarkdownView from '@/components/common/MarkdownView.vue';
import { getSubmission } from '@/api/submissions';
import SubmissionResultModal from '@/components/training/SubmissionResultModal.vue';
import type { SubmissionDetailDto } from '@/api/dto/Submission';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { kcDisplayName } from '@/data/kcNames';
import type {
  BugMineCardDto,
  BugMineDepthStepDto,
  BugMineEchoCandidateDto,
  BugMineErrorBookItemDto,
} from '@/api/dto/BugMine';
import {
  canAccessCard,
  depthLockMessage,
  DEPTH_LAYER_LABELS,
} from '@/utils/bugMineDepth';

const learning = useLearningStore();
const toast = useToastStore();
const router = useRouter();
const props = defineProps<{ reviewKc?: string | null }>();
const initialDataLoaded = ref(false);
const retryResultDetail = ref<SubmissionDetailDto | null>(null);
const retryResultOpen = ref(false);

const session = computed(() => learning.bugMine.session);
// 题干优先用后端随会话下发的 session.problem（零额外请求）；缺失时回退到单题接口结果。
const problem = computed(() => session.value?.problem ?? learning.bugMine.problem);
const errorBook = computed(() => learning.bugMine.errorBook);
const echoes = computed(() => learning.bugMine.echoes);
const cards = computed<BugMineCardDto[]>(() => session.value?.cards ?? []);

// 错题本 + 遗忘回声左侧栏：可一键收起，给「题面 | 作答」工作区让出全宽。
const sidebarOpen = ref(true);

const defaultDepthSteps: BugMineDepthStepDto[] = [
  { layer: 1, key: 'bare_retry', title: 'L1 裸重试', description: '先独立重试一次。', unlocked: true },
  { layer: 2, key: 'counter_example', title: 'L2 反例', description: '用反例定位偏差。', unlocked: false },
  { layer: 3, key: 'variable_trace', title: 'L3 变量轨迹', description: '看关键变量变化。', unlocked: false },
  { layer: 4, key: 'layered_hints', title: 'L4 分层提示', description: '按层解锁提示。', unlocked: false },
  { layer: 5, key: 'same_kc_variant', title: 'L5 同 KC 变式', description: '迁移到同知识点。', unlocked: false },
];

function cardByType(type: BugMineCardDto['cardType']): BugMineCardDto | undefined {
  return cards.value.find((card) => card.cardType === type);
}

const counterExample = computed(() => cardByType('COUNTER_EXAMPLE'));
const variableTrace = computed(() => cardByType('VARIABLE_TRACE'));
const wrongAssumption = computed(() => cardByType('WRONG_ASSUMPTION'));
const hintLayer1 = computed(() => cardByType('HINT_LAYER_1'));
const hintLayer2 = computed(() => cardByType('HINT_LAYER_2'));
const hintLayer3 = computed(() => cardByType('HINT_LAYER_3'));
const hintCards = computed(() =>
  [hintLayer1.value, hintLayer2.value, hintLayer3.value].filter(
    (card): card is BugMineCardDto => card != null,
  ),
);

const retryCode = ref<string>('');
const depthLayer = computed(() => session.value?.depthLayer ?? 1);

// 诊断层下拉：仅在「已解锁」层（L1..当前系统判定深度）间切换查看，不能跳到锁定层——
// 仍保留「系统决定能下探多深」的教育原则，下拉只是已解锁层的导航。
const LAYER_NAMES: Record<number, string> = {
  1: '原题重试',
  2: '反例',
  3: '变量轨迹',
  4: '分层提示',
  5: '掌握确认',
};
const selectedLayer = ref(1);
const unlockedLayers = computed<{ layer: number; name: string }[]>(() => {
  const max = Math.min(5, Math.max(1, depthLayer.value));
  const out: { layer: number; name: string }[] = [];
  for (let l = 1; l <= max; l += 1) {
    out.push({ layer: l, name: LAYER_NAMES[l] ?? `L${l}` });
  }
  return out;
});
// 切换会话 / 系统下探（depthLayer 变化）→ 自动跳到当前系统判定层，让玩家看到最新解锁的诊断。
watch(
  () => [session.value?.id, session.value?.depthLayer] as const,
  ([, layer]) => {
    if (typeof layer === 'number') selectedLayer.value = layer;
  },
  { immediate: true },
);

const sessionDepthSteps = computed<BugMineDepthStepDto[]>(() => {
  if (session.value?.depthSteps?.length) {
    return session.value.depthSteps;
  }
  return defaultDepthSteps;
});

// 左侧题面分页：题面 / 样例 / 知识点与错因。
type ProblemTab = 'statement' | 'samples' | 'kc';
const problemTab = ref<ProblemTab>('statement');

// 左栏选题区：搜索 + 状态筛选 + 排序（系统已按入矿优先级排序，这里允许学生临时切换视角）。
// 注：错题本接口只返回 OPEN 会话，故没有「已完成」视图（后端加 status 过滤参数后再补）。
const searchQuery = ref<string>('');
type StatusFilter = 'ALL' | 'OPEN';
const statusFilter = ref<StatusFilter>('ALL');
type SortBy = 'priority' | 'mastery' | 'decay';
const sortBy = ref<SortBy>('priority');

/** 同一 KC + 同一题只保留排序后的第一条：重复入矿会在错题本里堆出多条等价条目。 */
function dedupeErrorBook(items: BugMineErrorBookItemDto[]): BugMineErrorBookItemDto[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = `${item.kcCode}#${item.problemId}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

const filteredErrorBook = computed<BugMineErrorBookItemDto[]>(() => {
  const keyword = searchQuery.value.trim().toLowerCase();
  const filtered = errorBook.value.filter((item) => {
    const matchesKeyword =
      keyword === '' ||
      item.problemTitle.toLowerCase().includes(keyword) ||
      item.kcCode.toLowerCase().includes(keyword);
    const matchesStatus = statusFilter.value === 'ALL' || item.status === 'OPEN';
    return matchesKeyword && matchesStatus;
  });
  const sorted = [...filtered];
  if (sortBy.value === 'mastery') {
    sorted.sort((a, b) => a.masteryScore - b.masteryScore);
  } else if (sortBy.value === 'decay') {
    sorted.sort((a, b) => b.decayRisk - a.decayRisk);
  } else {
    sorted.sort((a, b) => b.minePriority - a.minePriority);
  }
  return dedupeErrorBook(sorted);
});

/**
 * 遗忘回声候选：过滤掉「该 KC 已在错题本里有 OPEN 会话」的项。
 * 入矿不会改变该 KC 的 FSRS 到期状态，候选仍会留在列表里，再点一次就是第二条同 KC 会话。
 */
const visibleEchoes = computed<BugMineEchoCandidateDto[]>(() => {
  const minedKcs = new Set(
    errorBook.value.filter((item) => item.status === 'OPEN').map((item) => item.kcCode),
  );
  return echoes.value.filter((candidate) => !minedKcs.has(candidate.kcCode));
});

// 当前会话在错题本中的条目：用于把「历史错因 / 掌握度 / 遗忘风险」带进题干上下文与系统引导。
const activeErrorItem = computed<BugMineErrorBookItemDto | null>(() => {
  const active = session.value;
  if (!active) return null;
  return errorBook.value.find((item) => item.sessionId === active.id) ?? null;
});
const mistakePatterns = computed<string[]>(() => activeErrorItem.value?.mistakePatterns ?? []);

async function openRequestedReview(kcCode: string): Promise<void> {
  const hasOpenSession = errorBook.value.some(
    (item) => item.kcCode === kcCode && item.status === 'OPEN',
  );
  const isDue = echoes.value.some((candidate) => candidate.kcCode === kcCode);
  if (!hasOpenSession && !isDue) {
    toast.show('没有可用的历史错题，已改为该知识点的远征练习。', 'info');
    await router.push({ path: '/expedition', query: { kcCode } });
    return;
  }
  await learning.openBugMineEchoByKc(kcCode);
  if (learning.bugMine.lastError) toast.show(learning.bugMine.lastError, 'error');
}

onMounted(async () => {
  await Promise.all([learning.loadBugMineErrorBook(), learning.loadBugMineEchoes()]);
  initialDataLoaded.value = true;
  if (props.reviewKc) await openRequestedReview(props.reviewKc);
});

watch(() => props.reviewKc, (kcCode) => {
  if (initialDataLoaded.value && kcCode) void openRequestedReview(kcCode);
});

// 后端已随会话下发题干则零额外请求；仅当 session.problem 缺失时回退到单题接口拉取。
watch(
  () => session.value?.problemId,
  (problemId) => {
    if (problemId != null && session.value?.problem == null) {
      void learning.loadBugMineProblem(problemId);
    }
  },
  { immediate: true },
);

function normalizedDepthSteps(item: BugMineErrorBookItemDto): BugMineDepthStepDto[] {
  const source = item.depthSteps.length > 0 ? item.depthSteps : defaultDepthSteps;
  return source.map((step) => ({
    ...step,
    title: DEPTH_LAYER_LABELS[step.layer] ?? step.title,
  }));
}

function formatMasteryScore(score: number): string {
  return `${Math.round(score * 100)}%`;
}

function formatPriority(score: number): string {
  return `${Math.round(score * 100)}%`;
}

function sessionStatusLabel(status: string): string {
  if (status === 'RESOLVED') return '已通关';
  if (status === 'ABANDONED') return '已搁置';
  return '进行中';
}

function difficultyLabel(value: string | undefined): string {
  switch ((value ?? '').toUpperCase()) {
    case 'EASY':
      return '简单';
    case 'MEDIUM':
      return '中等';
    case 'HARD':
      return '困难';
    default:
      return value && value.length > 0 ? value : '未标注';
  }
}

const SHORT_LAYER_NAMES: Record<number, string> = {
  1: '重试',
  2: '反例',
  3: '轨迹',
  4: '提示',
  5: '变式',
};
function shortLayerName(layer: number): string {
  return SHORT_LAYER_NAMES[layer] ?? `L${layer}`;
}

function cardAccessible(card?: BugMineCardDto): boolean {
  if (!card || !session.value) return false;
  return canAccessCard(session.value.depthLayer, card);
}

function cardVisible(card?: BugMineCardDto): boolean {
  return card != null;
}

async function openErrorBookItem(item: BugMineErrorBookItemDto): Promise<void> {
  await learning.loadBugMine(item.sessionId);
  if (learning.bugMine.lastError) {
    toast.show(learning.bugMine.lastError, 'error');
  }
}

async function openEchoCandidate(candidate: BugMineEchoCandidateDto): Promise<void> {
  const opened = await learning.openBugMineEchoByKc(candidate.kcCode);
  if (!opened) {
    toast.show(learning.bugMine.lastError ?? '遗忘回声入矿失败', 'error');
  }
}

async function reveal(card?: BugMineCardDto): Promise<void> {
  if (!card || card.revealed) return;
  if (!cardAccessible(card)) {
    toast.show(depthLockMessage(depthLayer.value, card.cardType), 'error');
    return;
  }
  try {
    await learning.revealBugMineCardById(card.id);
  } catch (error) {
    const message = error instanceof Error ? error.message : '解锁失败';
    toast.show(message, 'error');
  }
}

const showVariantStep = computed(
  () => depthLayer.value >= 5 && session.value?.variantProblem != null,
);

async function openVariantProblem(): Promise<void> {
  const variant = session.value?.variantProblem;
  const activeSessionId = session.value?.id;
  if (!variant || activeSessionId == null) return;
  learning.setPendingVariantSession(activeSessionId);
  await learning.loadTrainingProblem(variant.id);
  if (learning.training.lastError) {
    toast.show(learning.training.lastError, 'error');
    return;
  }
  // S1：私有 bugMineSessionId 不写进 URL，改由 store 承载（上方 setPendingVariantSession 已设）。
  // problemId（共享课程题）与 variant（模式标志，非 id）非私有，保留在 URL 以支持刷新可达 / 自清。
  await router.push({
    path: '/training',
    query: {
      problemId: String(variant.id),
      variant: '1',
    },
  });
}

async function retry(): Promise<void> {
  if (!retryCode.value.trim()) {
    toast.show('请粘贴新的代码再重试。', 'error');
    return;
  }
  const result = await learning.retryBugMine(retryCode.value);
  if (!result) {
    toast.show(learning.bugMine.lastError ?? '重试失败', 'error');
    return;
  }
  try {
    retryResultDetail.value = await getSubmission(result.id);
    retryResultOpen.value = true;
  } catch {
    toast.show('判题结果已保存，详情暂时无法加载。', 'error');
  }
  if (result.status === 'PASSED') {
    toast.show('修复成功！Bug 矿洞会话已 RESOLVED。', 'success');
  } else {
    toast.show('仍未通过，系统已下探一层，解锁更深一层诊断。', 'error');
  }
}
</script>

<template>
  <section
    class="bug-mine"
    data-testid="bug-mine-panel"
  >
    <header class="bug-mine__header">
      <button
        type="button"
        class="bug-mine__rail-toggle"
        :aria-pressed="sidebarOpen"
        data-testid="error-book-toggle"
        @click="sidebarOpen = !sidebarOpen"
      >
        {{ sidebarOpen ? '« 收起错题本' : '» 错题本' }}
      </button>
      <div class="bug-mine__heading">
        <h2>错题矿洞</h2>
        <span class="bug-mine__subtitle">
          系统按掌握度与遗忘风险逐层开放诊断 · 看懂再出坑，而非直接给答案
        </span>
      </div>
    </header>

    <div
      class="bug-mine__body"
      :class="{ 'bug-mine__body--no-rail': !sidebarOpen }"
    >
      <!-- 左栏：错题选择区（遗忘回声 + 错题本 + 筛选），可一键收起 -->
      <aside
        v-show="sidebarOpen"
        class="rail"
      >
        <section
          v-if="visibleEchoes.length > 0 || learning.bugMine.echoesLoading || learning.bugMine.echoesError"
          class="board"
          data-testid="echo-board"
        >
          <div class="board__head">
            <div class="board__titles">
              <h3>遗忘回声</h3>
              <span class="board__caption">系统挑出的高遗忘风险知识点</span>
            </div>
            <span
              v-if="learning.bugMine.echoesLoading"
              class="board__note"
            >巡矿中</span>
          </div>
          <p
            v-if="learning.bugMine.echoesError"
            class="notice notice--error"
          >
            {{ learning.bugMine.echoesError }}
          </p>
          <ul
            v-else
            class="rail-list"
          >
            <li
              v-for="candidate in visibleEchoes"
              :key="candidate.kcCode"
              class="echo-card"
              :data-testid="`echo-candidate-${candidate.kcCode}`"
            >
              <div class="echo-card__info">
                <span class="kc-code">{{ kcDisplayName(candidate.kcCode) }}</span>
                <span class="echo-card__title">{{ candidate.problemTitle }}</span>
              </div>
              <div class="echo-card__foot">
                <span
                  class="chip chip--accent"
                  :title="candidate.recommendationReason"
                >推荐 {{ formatPriority(candidate.minePriority) }}</span>
                <PyButton
                  variant="primary"
                  size="sm"
                  :disabled="learning.bugMine.loading"
                  :test-id="`open-echo-${candidate.kcCode}`"
                  @click="openEchoCandidate(candidate)"
                >
                  入矿
                </PyButton>
              </div>
            </li>
          </ul>
        </section>

        <section
          class="board board--grow"
          data-testid="error-book"
        >
          <div class="board__head">
            <div class="board__titles">
              <h3>错题本</h3>
              <span class="board__caption">判题失败后点「进入 Bug 矿洞」入册 · 按系统入矿优先级排序</span>
            </div>
            <span
              v-if="learning.bugMine.errorBookLoading"
              class="board__note"
            >同步中</span>
          </div>

          <div class="rail-filters">
            <input
              v-model="searchQuery"
              type="search"
              class="rail-filters__search"
              placeholder="搜索题目或知识点…"
              data-testid="error-book-search"
            >
            <div class="rail-filters__row">
              <select
                v-model="sortBy"
                class="rail-filters__select"
                aria-label="排序方式"
                data-testid="error-book-sort"
              >
                <option value="priority">
                  推荐优先
                </option>
                <option value="mastery">
                  掌握度低优先
                </option>
                <option value="decay">
                  遗忘风险高优先
                </option>
              </select>
              <div
                class="seg"
                role="group"
                aria-label="状态筛选"
              >
                <button
                  type="button"
                  class="seg__btn"
                  :class="{ 'seg__btn--on': statusFilter === 'ALL' }"
                  @click="statusFilter = 'ALL'"
                >
                  全部
                </button>
                <button
                  type="button"
                  class="seg__btn"
                  :class="{ 'seg__btn--on': statusFilter === 'OPEN' }"
                  @click="statusFilter = 'OPEN'"
                >
                  进行中
                </button>
              </div>
            </div>
          </div>

          <p
            v-if="learning.bugMine.errorBookError"
            class="notice notice--error"
          >
            {{ learning.bugMine.errorBookError }}
          </p>
          <p
            v-else-if="learning.bugMine.errorBookLoading && errorBook.length === 0"
            class="notice"
          >
            读取错题本...
          </p>
          <PyEmptyState
            v-else-if="errorBook.length === 0"
            title="暂无错题"
            description="训练场判题失败后点「进入 Bug 矿洞」，这道题就会入册到这里。"
          />
          <PyEmptyState
            v-else-if="filteredErrorBook.length === 0"
            title="没有匹配的错题"
            description="换个关键词或筛选条件再试。"
          />
          <ul
            v-else
            class="rail-list"
          >
            <li
              v-for="item in filteredErrorBook"
              :key="item.sessionId"
            >
              <button
                type="button"
                class="error-item"
                :class="{ 'error-item--active': session?.id === item.sessionId }"
                :aria-pressed="session?.id === item.sessionId"
                :data-testid="`error-book-item-${item.sessionId}`"
                @click="openErrorBookItem(item)"
              >
                <!-- 错题本接口只返回 OPEN 会话，故不再渲染恒为「进行中」的状态徽标。 -->
                <span class="error-item__eyebrow">
                  <span class="kc-code">{{ kcDisplayName(item.kcCode) }}</span>
                </span>
                <span class="error-item__title">{{ item.problemTitle }}</span>
                <span
                  v-if="item.recommendationReason"
                  class="error-item__reason"
                >
                  <span class="chip chip--accent">推荐 {{ formatPriority(item.minePriority) }}</span>
                  {{ item.recommendationReason }}
                </span>
                <span class="error-item__foot">
                  <ol
                    class="stepper stepper--mini"
                    :data-testid="`depth-steps-${item.sessionId}`"
                    :aria-label="`系统分配深度 当前 L${item.depthLayer} 目标 L${item.profileDepthTarget}`"
                  >
                    <li
                      v-for="step in normalizedDepthSteps(item)"
                      :key="step.layer"
                      class="stepper__node"
                      :class="{
                        'stepper__node--unlocked': step.unlocked,
                        'stepper__node--current': item.depthLayer === step.layer,
                        'stepper__node--target': item.profileDepthTarget === step.layer,
                      }"
                    >
                      {{ step.layer }}
                    </li>
                  </ol>
                  <span class="error-item__mastery">掌握 {{ formatMasteryScore(item.masteryScore) }}</span>
                </span>
              </button>
            </li>
          </ul>
        </section>
      </aside>

      <!-- 主区：题面（左）| 作答 · 逐层诊断（右），仿做题界面左右分栏 -->
      <main
        v-if="session"
        class="bm-main"
      >
        <!-- 左：题面（题面 / 样例 / 知识点与错因） -->
        <section
          class="bm-problem"
          data-testid="problem-panel"
        >
          <header class="problem__head">
            <div class="problem__title-wrap">
              <span class="problem__eyebrow">正在调试</span>
              <h3 class="problem__title">
                {{ problem?.title ?? activeErrorItem?.problemTitle ?? `题目 #${session.problemId}` }}
              </h3>
              <div class="problem__tags">
                <span class="chip chip--soft kc-code">{{ kcDisplayName(problem?.kcCode ?? activeErrorItem?.kcCode) }}</span>
                <span
                  v-if="problem"
                  class="chip chip--soft"
                >难度 {{ difficultyLabel(problem.difficulty) }}</span>
              </div>
            </div>
          </header>

          <nav class="tabs">
            <button
              type="button"
              class="tabs__btn"
              :class="{ 'tabs__btn--on': problemTab === 'statement' }"
              @click="problemTab = 'statement'"
            >
              题面
            </button>
            <button
              type="button"
              class="tabs__btn"
              :class="{ 'tabs__btn--on': problemTab === 'samples' }"
              @click="problemTab = 'samples'"
            >
              样例
              <span
                v-if="problem?.samples?.length"
                class="tabs__count"
              >{{ problem.samples.length }}</span>
            </button>
            <button
              type="button"
              class="tabs__btn"
              :class="{ 'tabs__btn--on': problemTab === 'kc' }"
              @click="problemTab = 'kc'"
            >
              知识点与错因
            </button>
          </nav>

          <div class="problem__body bm-problem__body">
            <p
              v-if="learning.bugMine.problemLoading && !problem"
              class="notice"
            >
              读取题干...
            </p>
            <p
              v-else-if="learning.bugMine.problemError"
              class="notice notice--error"
            >
              题干加载失败：{{ learning.bugMine.problemError }}
            </p>

            <template v-else-if="problemTab === 'statement'">
              <MarkdownView
                v-if="problem?.statementMarkdown"
                :source="problem.statementMarkdown"
                :allow-images="false"
              />
              <p
                v-else
                class="notice"
              >
                暂无题面内容。
              </p>
              <div
                v-if="problem?.inputDescription"
                class="problem__io"
              >
                <h4 class="problem__io-title">
                  输入
                </h4>
                <MarkdownView
                  :source="problem.inputDescription"
                  :allow-images="false"
                />
              </div>
              <div
                v-if="problem?.outputDescription"
                class="problem__io"
              >
                <h4 class="problem__io-title">
                  输出
                </h4>
                <MarkdownView
                  :source="problem.outputDescription"
                  :allow-images="false"
                />
              </div>
            </template>

            <template v-else-if="problemTab === 'samples'">
              <div
                v-if="problem?.samples?.length"
                class="samples"
              >
                <div
                  v-for="(sample, index) in problem.samples"
                  :key="index"
                  class="sample"
                >
                  <div class="sample__col">
                    <span class="sample__label">输入 {{ index + 1 }}</span>
                    <pre class="sample__pre">{{ sample.input || '(空)' }}</pre>
                  </div>
                  <div class="sample__col">
                    <span class="sample__label">期望输出 {{ index + 1 }}</span>
                    <pre class="sample__pre">{{ sample.output || '(空)' }}</pre>
                  </div>
                </div>
              </div>
              <p
                v-else
                class="notice"
              >
                该题暂无公开样例。
              </p>
            </template>

            <template v-else>
              <div class="kc-meta">
                <p class="kv">
                  <span class="kv__key">知识点</span>
                  <span class="kc-code">{{ kcDisplayName(problem?.kcCode ?? activeErrorItem?.kcCode) }}</span>
                </p>
                <p
                  v-if="activeErrorItem"
                  class="kv"
                >
                  <span class="kv__key">掌握度</span>
                  <span>{{ formatMasteryScore(activeErrorItem.masteryScore) }}</span>
                </p>
                <p
                  v-if="activeErrorItem"
                  class="kv"
                >
                  <span class="kv__key">遗忘风险</span>
                  <span>{{ formatMasteryScore(activeErrorItem.decayRisk) }}</span>
                </p>
              </div>
              <div
                v-if="mistakePatterns.length"
                class="kc-patterns"
              >
                <span class="kc-patterns__label">历史错因</span>
                <span
                  v-for="pattern in mistakePatterns"
                  :key="pattern"
                  class="chip chip--warn"
                >{{ pattern }}</span>
              </div>
              <p
                v-if="activeErrorItem?.recommendationReason"
                class="kc-reason"
              >
                {{ activeErrorItem.recommendationReason }}
              </p>
            </template>
          </div>
        </section>

        <!-- 右：作答 · 逐层诊断（诊断层下拉 + 选中层内容 + 常驻作答框） -->
        <section class="bm-work">
          <header class="bm-work__head">
            <div class="bm-work__row">
              <h3 class="bm-work__title">
                逐层诊断
              </h3>
              <label class="bm-layer-pick">
                <span class="bm-layer-pick__label">诊断层</span>
                <select
                  v-model.number="selectedLayer"
                  class="bm-layer-pick__select"
                  data-testid="layer-select"
                >
                  <option
                    v-for="opt in unlockedLayers"
                    :key="opt.layer"
                    :value="opt.layer"
                  >
                    L{{ opt.layer }} · {{ opt.name }}
                  </option>
                </select>
              </label>
            </div>
            <div
              class="statusbar"
              data-testid="session-statusbar"
            >
              <span class="statusbar__id">会话 #{{ session.id }}</span>
              <span
                class="bm-chip"
                :class="{
                  'is-resolved': session.status === 'RESOLVED',
                  'is-abandoned': session.status === 'ABANDONED',
                }"
                :title="session.status"
              >{{ sessionStatusLabel(session.status) }}</span>
              <span
                class="bm-chip bm-chip--accent"
                title="系统判定"
              >当前 L{{ session.depthLayer }}</span>
              <span
                class="bm-chip bm-chip--target"
                title="画像建议"
              >目标 L{{ session.profileDepthTarget }}</span>
              <span class="bm-chip">重试 {{ session.retryCount }} 次</span>
            </div>
          </header>

          <!-- 系统分配深度（细条，只读）：强调深度由系统判定，下拉只在已解锁层间查看 -->
          <section
            class="assign assign--slim"
            data-testid="depth-assignment"
          >
            <ol
              class="stepper stepper--track"
              data-testid="session-depth-steps"
            >
              <li
                v-for="step in sessionDepthSteps"
                :key="step.layer"
                class="stepper__node stepper__node--track"
                :class="{
                  'stepper__node--unlocked': step.layer < session.depthLayer,
                  'stepper__node--current': session.depthLayer === step.layer,
                  'stepper__node--target': session.profileDepthTarget === step.layer,
                  'stepper__node--locked': step.layer > session.depthLayer,
                }"
              >
                <span class="stepper__num">{{ step.layer }}</span>
                <span class="stepper__name">{{ shortLayerName(step.layer) }}</span>
              </li>
            </ol>
            <span class="assign__hint">
              深度由系统按掌握度 · 遗忘风险 · 画像自动判定；下拉仅在已解锁层间查看
            </span>
          </section>

          <!-- 系统引导 -->
          <div
            v-if="session.profileGuidance || mistakePatterns.length"
            class="guidance"
            data-testid="profile-guidance"
          >
            <span class="guidance__badge">系统引导</span>
            <div class="guidance__text">
              <p
                v-if="session.profileGuidance"
                class="guidance__line"
              >
                {{ session.profileGuidance }}
              </p>
              <p
                v-if="mistakePatterns.length"
                class="guidance__line guidance__line--sub"
              >
                你在相似题上多次出现：{{ mistakePatterns.join(' / ') }}，系统已据此设定目标层级
                L{{ session.profileDepthTarget }}。
              </p>
            </div>
          </div>

          <!-- 选中诊断层内容（一次一层；锁定层不在下拉里，无法选中） -->
          <div
            class="bm-diag"
            data-testid="layer-content"
          >
            <!-- L1 原题重试：说明在此，作答框常驻于下方 -->
            <p
              v-if="selectedLayer === 1"
              class="bm-diag__note"
            >
              先不看更深提示，按题意在下方独立改一版并提交；通过即出坑，未通过系统才继续下探。
            </p>

            <!-- L2 反例 -->
            <article
              v-else-if="selectedLayer === 2 && cardVisible(counterExample)"
              class="bm-card"
              data-testid="card-counter-example"
            >
              <h4 class="layer__title">
                反例
              </h4>
              <p
                v-if="!cardAccessible(counterExample)"
                class="lock"
              >
                {{ depthLockMessage(depthLayer, 'COUNTER_EXAMPLE') }}
              </p>
              <template v-else-if="counterExample?.revealed">
                <p class="kv">
                  <span class="kv__key">输入</span><code>{{ counterExample.payload.stdin }}</code>
                </p>
                <p class="kv">
                  <span class="kv__key">期望</span><code>{{ counterExample.payload.expectedStdout ?? counterExample.payload.expected_stdout }}</code>
                </p>
                <p class="kv">
                  <span class="kv__key">实际</span><code>{{ counterExample.payload.yourStdout ?? counterExample.payload.your_stdout }}</code>
                </p>
                <p class="kv kv--diff">
                  <span class="kv__key">差异</span>{{ counterExample.payload.diffSummary ?? counterExample.payload.diff_summary }}
                </p>
              </template>
              <PyButton
                v-else
                variant="secondary"
                size="sm"
                @click="reveal(counterExample)"
              >
                解锁反例卡
              </PyButton>
            </article>

            <!-- L3 变量轨迹 -->
            <article
              v-else-if="selectedLayer === 3 && cardVisible(variableTrace)"
              class="bm-card"
              data-testid="card-trace"
            >
              <h4 class="layer__title">
                变量轨迹
              </h4>
              <p
                v-if="!cardAccessible(variableTrace)"
                class="lock"
              >
                {{ depthLockMessage(depthLayer, 'VARIABLE_TRACE') }}
              </p>
              <pre
                v-else-if="variableTrace?.revealed"
                class="trace"
              >{{ JSON.stringify(variableTrace.payload, null, 2).slice(0, 1600) }}</pre>
              <PyButton
                v-else
                variant="secondary"
                size="sm"
                @click="reveal(variableTrace)"
              >
                解锁变量轨迹
              </PyButton>
            </article>

            <!-- L4 分层提示：错误假设 + 提示 1/2/3 -->
            <div
              v-else-if="selectedLayer === 4 && (cardVisible(wrongAssumption) || hintCards.length > 0)"
              class="bm-card"
            >
              <h4 class="layer__title">
                分层提示
              </h4>
              <div class="subcards">
                <article
                  v-if="cardVisible(wrongAssumption)"
                  class="subcard"
                  :class="{ 'subcard--locked': !cardAccessible(wrongAssumption) }"
                  data-testid="card-assumption"
                >
                  <h5 class="subcard__title">
                    错误假设
                  </h5>
                  <p
                    v-if="!cardAccessible(wrongAssumption)"
                    class="lock"
                  >
                    {{ depthLockMessage(depthLayer, 'WRONG_ASSUMPTION') }}
                  </p>
                  <template v-else-if="wrongAssumption?.revealed">
                    <p class="kv">
                      <span class="kv__key">误区</span>{{ wrongAssumption.payload.youThought ?? wrongAssumption.payload.you_thought }}
                    </p>
                    <p class="kv kv--diff">
                      <span class="kv__key">正解</span>{{ wrongAssumption.payload.actually }}
                    </p>
                  </template>
                  <PyButton
                    v-else
                    variant="secondary"
                    size="sm"
                    @click="reveal(wrongAssumption)"
                  >
                    解锁错误假设
                  </PyButton>
                </article>

                <article
                  v-if="cardVisible(hintLayer1)"
                  class="subcard"
                  data-testid="card-hint-1"
                  :class="{ 'subcard--locked': !cardAccessible(hintLayer1) }"
                >
                  <h5 class="subcard__title">
                    提示 1
                  </h5>
                  <p
                    v-if="!cardAccessible(hintLayer1)"
                    class="lock"
                  >
                    {{ depthLockMessage(depthLayer, 'HINT_LAYER_1') }}
                  </p>
                  <template v-else-if="hintLayer1?.revealed">
                    <p class="headline">
                      {{ hintLayer1.payload.headline }}
                    </p>
                    <p>{{ hintLayer1.payload.body }}</p>
                  </template>
                  <PyButton
                    v-else
                    variant="secondary"
                    size="sm"
                    @click="reveal(hintLayer1)"
                  >
                    解锁 Layer 1 提示
                  </PyButton>
                </article>

                <article
                  v-if="cardVisible(hintLayer2)"
                  class="subcard"
                  data-testid="card-hint-2"
                  :class="{ 'subcard--locked': !cardAccessible(hintLayer2) }"
                >
                  <h5 class="subcard__title">
                    提示 2
                  </h5>
                  <p
                    v-if="!cardAccessible(hintLayer2)"
                    class="lock"
                  >
                    {{ depthLockMessage(depthLayer, 'HINT_LAYER_2') }}
                  </p>
                  <template v-else-if="hintLayer2?.revealed">
                    <p class="headline">
                      {{ hintLayer2.payload.headline }}
                    </p>
                    <p>{{ hintLayer2.payload.body }}</p>
                  </template>
                  <PyButton
                    v-else
                    variant="secondary"
                    size="sm"
                    @click="reveal(hintLayer2)"
                  >
                    解锁 Layer 2 提示
                  </PyButton>
                </article>

                <article
                  v-if="cardVisible(hintLayer3)"
                  class="subcard"
                  data-testid="card-hint-3"
                  :class="{ 'subcard--locked': !cardAccessible(hintLayer3) }"
                >
                  <h5 class="subcard__title">
                    提示 3
                  </h5>
                  <p
                    v-if="!cardAccessible(hintLayer3)"
                    class="lock"
                  >
                    {{ depthLockMessage(depthLayer, 'HINT_LAYER_3') }}
                  </p>
                  <template v-else-if="hintLayer3?.revealed">
                    <p class="headline">
                      {{ hintLayer3.payload.headline }}
                    </p>
                    <p>{{ hintLayer3.payload.body }}</p>
                  </template>
                  <PyButton
                    v-else
                    variant="secondary"
                    size="sm"
                    @click="reveal(hintLayer3)"
                  >
                    解锁 Layer 3 提示
                  </PyButton>
                </article>
              </div>
            </div>

            <!-- L5 掌握确认（同 KC 变式） -->
            <article
              v-else-if="selectedLayer === 5"
              class="bm-card bm-card--variant"
              data-testid="card-kc-variant"
            >
              <h4 class="layer__title">
                掌握确认
              </h4>
              <template v-if="showVariantStep">
                <p>换一道同知识点题验证迁移：{{ session.variantProblem?.title }}</p>
                <p
                  v-if="session.masteryConfirmed"
                  class="status"
                >
                  变式掌握已确认。
                </p>
                <PyButton
                  v-else
                  variant="primary"
                  test-id="variant-problem-button"
                  @click="openVariantProblem"
                >
                  去做变式题
                </PyButton>
              </template>
              <p
                v-else
                class="lock"
              >
                需系统将深度下探到 L5 并匹配到同 KC 变式题后开放。
              </p>
            </article>

            <p
              v-else
              class="notice"
            >
              本层暂无可展示的诊断内容。
            </p>
          </div>

          <!-- 作答核心：原题重试编辑器（始终可见，仿做题界面右侧作答区） -->
          <div
            class="bm-answer"
            data-testid="retry-area"
          >
            <div class="bm-answer__head">
              <h4 class="layer__title">
                作答 · 原题重试
              </h4>
              <span class="tag tag--primary">通过即出坑</span>
            </div>
            <textarea
              v-model="retryCode"
              class="retry__code"
              rows="8"
              spellcheck="false"
              placeholder="粘贴修正后的代码…"
            />
            <div class="layer__actions">
              <PyButton
                variant="primary"
                :disabled="learning.bugMine.loading"
                test-id="retry-button"
                @click="retry"
              >
                重新提交（服务端判题）
              </PyButton>
              <span
                v-if="learning.bugMine.lastRetry"
                class="status"
              >
                {{ learning.bugMine.lastRetry.status }} · {{ learning.bugMine.lastRetry.source }}
              </span>
              <button
                v-if="retryResultDetail"
                type="button"
                class="status-link"
                data-testid="retry-result-reopen"
                @click="retryResultOpen = true"
              >
                查看本次判题详情
              </button>
            </div>
          </div>
        </section>
      </main>
      <PyEmptyState
        v-else
        class="detail-empty"
        title="未选择矿洞会话"
        description="从左侧错题本或遗忘回声选一条，进入逐层调试。"
      />
    </div>
    <SubmissionResultModal
      v-if="retryResultDetail"
      :open="retryResultOpen"
      :detail="retryResultDetail"
      @close="retryResultOpen = false"
    />
  </section>
</template>

<style scoped>
.bug-mine {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-rows: auto 1fr;
  gap: var(--pyt-space-3);
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-soft) 100%);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: var(--pyt-space-3) var(--pyt-space-4);
  color: var(--pyt-color-text-primary);
}

/* Header */
.bug-mine__header {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-3);
  flex-wrap: wrap;
}
.bug-mine__rail-toggle {
  flex: 0 0 auto;
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-md);
  padding: 6px 12px;
  font-size: var(--pyt-font-size-xs);
  cursor: pointer;
  white-space: nowrap;
}
.bug-mine__rail-toggle:hover {
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-text-primary);
}
.bug-mine__heading {
  display: flex;
  align-items: baseline;
  gap: var(--pyt-space-3);
  flex-wrap: wrap;
}
.bug-mine__header h2 {
  margin: 0;
  color: var(--pyt-color-text-primary);
}
.bug-mine__subtitle {
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
}

/* Body: master-detail */
.bug-mine__body {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: var(--pyt-space-3);
}
/* 收起错题本 → 工作区独占全宽 */
.bug-mine__body--no-rail {
  grid-template-columns: minmax(0, 1fr);
}

/* ---------- Left rail ---------- */
.rail {
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  padding-right: var(--pyt-space-1);
}
.board {
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: var(--pyt-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  flex: 0 0 auto;
}
.board--grow {
  flex: 1 0 auto;
}
.board__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.board__titles {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.board__head h3 {
  margin: 0;
  font-size: var(--pyt-font-size-base);
  color: var(--pyt-color-text-primary);
}
.board__caption {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.board__note {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  white-space: nowrap;
}
.rail-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}

/* Filters */
.rail-filters {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-1);
}
.rail-filters__search {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  font-size: var(--pyt-font-size-sm);
}
.rail-filters__row {
  display: flex;
  gap: var(--pyt-space-1);
  align-items: center;
  flex-wrap: wrap;
}
.rail-filters__select {
  flex: 0 0 auto;
  padding: 5px 8px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  font-size: var(--pyt-font-size-xs);
}
.seg {
  display: inline-flex;
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
  overflow: hidden;
}
.seg__btn {
  border: none;
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-xs);
  padding: 5px 8px;
  cursor: pointer;
}
.seg__btn--on {
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
}

/* Echo candidate */
.echo-card {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-2);
}
.echo-card__info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.echo-card__title {
  font-weight: 600;
  color: var(--pyt-color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.echo-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}

/* Error book item */
.error-item {
  width: 100%;
  text-align: left;
  cursor: pointer;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border-soft);
  border-left: 3px solid transparent;
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-2);
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-1);
  color: inherit;
  transition: border-color var(--pyt-motion-base), background var(--pyt-motion-base);
}
.error-item:hover {
  background: var(--pyt-color-bg-elevated);
  border-color: var(--pyt-color-border);
}
.error-item--active {
  border-color: var(--pyt-color-gold);
  border-left-color: var(--pyt-color-gold);
  background: var(--pyt-color-bg-elevated);
  box-shadow: 0 0 0 1px var(--pyt-color-gold) inset;
}
.error-item__eyebrow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
}
.error-item__title {
  font-weight: 700;
  font-size: var(--pyt-font-size-base);
  color: var(--pyt-color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.error-item__reason {
  display: block;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.error-item__reason .chip {
  margin-right: 4px;
}
.error-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  margin-top: 2px;
}
.error-item__mastery {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  white-space: nowrap;
}

.kc-code {
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Chips */
.chip {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
  font-size: var(--pyt-font-size-xs);
  font-weight: 600;
  line-height: 1.6;
  white-space: nowrap;
}
.chip--soft {
  background: var(--pyt-color-bg-sunken);
  border-color: var(--pyt-color-border-soft);
}
.chip--accent {
  background: var(--pyt-color-accent-bg);
  border-color: var(--pyt-color-accent);
  color: var(--pyt-color-accent);
}
.chip--warn {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-warning, var(--pyt-color-border));
  color: var(--pyt-color-text-hint);
}

/* ---------- Right workspace: 题面 | 作答 两栏 ---------- */
.bm-main {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(280px, 0.85fr) minmax(320px, 1.15fr);
  gap: var(--pyt-space-3);
}
.detail-empty {
  align-self: stretch;
}

/* 左：题面 */
.bm-problem {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: var(--pyt-space-3);
  overflow: hidden;
}
.problem__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  flex: 0 0 auto;
}
.problem__title-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.problem__eyebrow {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  letter-spacing: 0.5px;
}
.problem__title {
  margin: 0;
  font-size: var(--pyt-font-size-lg);
  color: var(--pyt-color-text-primary);
}
.problem__tags {
  display: flex;
  gap: var(--pyt-space-1);
  flex-wrap: wrap;
}

/* Tabs */
.tabs {
  display: flex;
  gap: var(--pyt-space-1);
  border-bottom: 1px solid var(--pyt-color-border-soft);
  flex: 0 0 auto;
}
.tabs__btn {
  border: none;
  background: transparent;
  color: var(--pyt-color-text-muted);
  padding: 6px 10px;
  font-size: var(--pyt-font-size-sm);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.tabs__btn--on {
  color: var(--pyt-color-text-primary);
  border-bottom-color: var(--pyt-color-gold);
  font-weight: 700;
}
.tabs__count {
  font-size: 10px;
  background: var(--pyt-color-bg-sunken);
  border-radius: var(--pyt-radius-full);
  padding: 0 6px;
  color: var(--pyt-color-text-hint);
}
.problem__body {
  min-height: 0;
  overflow-y: auto;
  padding-right: var(--pyt-space-1);
}
.bm-problem__body {
  flex: 1 1 auto;
}
.problem__io {
  margin-top: var(--pyt-space-2);
}
.problem__io-title {
  margin: 0 0 4px;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
}

/* Samples */
.samples {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.sample {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--pyt-space-2);
}
.sample__col {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.sample__label {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.sample__pre {
  margin: 0;
  background: var(--pyt-color-bg-primary);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-sm);
  padding: 6px 8px;
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-xs);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* KC tab */
.kc-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--pyt-space-3);
  margin-bottom: var(--pyt-space-2);
}
.kc-patterns {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--pyt-space-1);
  margin-bottom: var(--pyt-space-2);
}
.kc-patterns__label {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  margin-right: 2px;
}
.kc-reason {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}

/* 右：作答 · 逐层诊断 */
.bm-work {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: var(--pyt-space-3);
}
.bm-work__head {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.bm-work__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
}
.bm-work__title {
  margin: 0;
  font-size: var(--pyt-font-size-base);
  color: var(--pyt-color-text-primary);
}
.bm-layer-pick {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-1);
}
.bm-layer-pick__label {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.bm-layer-pick__select {
  padding: 5px 10px;
  border: 1px solid var(--pyt-color-gold);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  font-size: var(--pyt-font-size-sm);
  font-weight: 700;
  cursor: pointer;
}

/* Status bar */
.statusbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--pyt-space-2);
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-2) var(--pyt-space-3);
}
.statusbar__id {
  font-weight: 700;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-on-dark, #fff);
  background: var(--pyt-color-primary);
  border: 1px solid var(--pyt-color-primary-dark);
  border-radius: var(--pyt-radius-full);
  padding: 2px 12px;
  white-space: nowrap;
}
.bm-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  font-size: var(--pyt-font-size-xs);
  font-weight: 600;
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-full);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
  white-space: nowrap;
}
.bm-chip.is-resolved {
  color: var(--pyt-color-success);
  border-color: var(--pyt-color-success);
}
.bm-chip.is-abandoned {
  color: var(--pyt-color-text-muted);
}
.bm-chip--accent {
  color: var(--pyt-color-primary-dark);
  border-color: var(--pyt-color-primary);
  background: var(--pyt-color-warning-bg);
}
.bm-chip--target {
  color: var(--pyt-color-accent);
  border-color: var(--pyt-color-accent);
  background: var(--pyt-color-accent-bg);
}

/* Assignment (depth track) - slim, read-only */
.assign {
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.assign--slim {
  flex: 0 0 auto;
  flex-direction: row;
  align-items: center;
  gap: var(--pyt-space-3);
  flex-wrap: wrap;
}
.assign__hint {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}

/* Stepper */
.stepper {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  align-items: center;
  gap: var(--pyt-space-1);
}
.stepper__node {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: var(--pyt-radius-md);
  border: 1px solid var(--pyt-color-border-soft);
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  opacity: 0.5;
}
.stepper__node--unlocked {
  opacity: 1;
  background: var(--pyt-color-bg-elevated);
  border-color: var(--pyt-color-border);
  color: var(--pyt-color-text-hint);
}
.stepper__node--current {
  opacity: 1;
  background: var(--pyt-color-gold);
  border-color: var(--pyt-color-primary);
  color: var(--pyt-color-primary-dark);
}
.stepper__node--target {
  outline: 2px dashed var(--pyt-color-accent);
  outline-offset: 1px;
}
.stepper__node--locked {
  opacity: 0.5;
}
.stepper--mini {
  gap: 3px;
}
.stepper--mini .stepper__node {
  width: 16px;
  height: 16px;
  font-size: 9px;
  border-radius: var(--pyt-radius-sm);
}

/* Track variant of stepper: number + name, connected */
.stepper--track {
  gap: 0;
  flex-wrap: wrap;
}
.stepper__node--track {
  width: auto;
  height: auto;
  flex-direction: column;
  gap: 1px;
  padding: 4px 10px;
  min-width: 56px;
  position: relative;
}
.stepper__node--track:not(:last-child) {
  margin-right: 14px;
}
.stepper__node--track:not(:last-child)::after {
  content: '';
  position: absolute;
  right: -14px;
  top: 50%;
  width: 14px;
  height: 2px;
  background: var(--pyt-color-border-soft);
}
.stepper__num {
  font-size: var(--pyt-font-size-sm);
}
.stepper__name {
  font-size: 10px;
  font-weight: 600;
}

/* Guidance */
.guidance {
  flex: 0 0 auto;
  display: flex;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-info);
  border-radius: var(--pyt-radius-md);
}
.guidance__badge {
  flex: 0 0 auto;
  align-self: flex-start;
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
  color: var(--pyt-color-text-on-dark, #fff);
  background: var(--pyt-color-info);
  border-radius: var(--pyt-radius-sm);
  padding: 1px 7px;
}
.guidance__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.guidance__line {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
  line-height: 1.45;
}
.guidance__line--sub {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}

/* 选中诊断层内容 */
.bm-diag {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-right: var(--pyt-space-1);
}
.bm-diag__note {
  margin: 0;
  padding: var(--pyt-space-3);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.6;
  color: var(--pyt-color-text-muted);
  background: var(--pyt-color-bg-soft);
  border: 1px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
.bm-card {
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.bm-card--variant {
  border-color: var(--pyt-color-accent);
  background: var(--pyt-color-accent-bg);
}
.layer__title {
  margin: 0;
  font-size: var(--pyt-font-size-base);
  color: var(--pyt-color-text-primary);
}

/* Sub cards (L4) */
.subcards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--pyt-space-2);
}
.subcard {
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-2);
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-1);
}
.subcard--locked {
  opacity: 0.7;
  border-style: dashed;
}
.subcard__title {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
}

/* 作答核心：原题重试 */
.bm-answer {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-primary);
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-3);
  box-shadow: 0 2px 0 var(--pyt-color-border);
}
.bm-answer__head {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
}
.layer__actions {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-3);
  flex-wrap: wrap;
}

/* Tags */
.tag {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.3px;
  padding: 2px 8px;
  border-radius: var(--pyt-radius-full);
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-hint);
}
.tag--primary {
  background: var(--pyt-color-gold);
  border-color: var(--pyt-color-primary);
  color: var(--pyt-color-primary-dark);
}

/* shared content */
.bm-card p,
.subcard p {
  margin: 0;
  line-height: 1.5;
  font-size: var(--pyt-font-size-sm);
}
.kv {
  display: flex;
  gap: var(--pyt-space-1);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.5;
}
.kv__key {
  flex: 0 0 auto;
  color: var(--pyt-color-text-muted);
  font-weight: 600;
}
.bm-card code,
.subcard code {
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border-soft);
  padding: 0 4px;
  border-radius: var(--pyt-radius-sm);
  font-family: var(--pyt-font-mono);
  overflow-wrap: anywhere;
}
.lock {
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  line-height: 1.4;
}
.trace {
  margin: 0;
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border-radius: var(--pyt-radius-md);
  padding: var(--pyt-space-2);
  max-height: 220px;
  overflow: auto;
  font-size: var(--pyt-font-size-xs);
}
.kv--diff,
.headline {
  color: var(--pyt-color-text-primary);
  font-weight: 600;
}
.retry__code {
  font-family: var(--pyt-font-mono);
  padding: var(--pyt-space-2);
  border-radius: var(--pyt-radius-md);
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  resize: vertical;
  min-height: 120px;
}
.status {
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
}
.status-link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--pyt-color-primary, #477c55);
  cursor: pointer;
  font: inherit;
  font-size: var(--pyt-font-size-sm);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.notice {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
}
.notice--error {
  color: var(--pyt-color-danger, #b4451f);
}

@media (max-width: 960px) {
  .bug-mine {
    height: auto;
    min-height: 100%;
  }
  .bug-mine__body,
  .bug-mine__body--no-rail {
    grid-template-columns: 1fr;
  }
  .bm-main {
    grid-template-columns: 1fr;
  }
  .rail,
  .problem__body,
  .bm-diag {
    overflow: visible;
  }
  .bm-problem {
    overflow: visible;
  }
  .sample {
    grid-template-columns: 1fr;
  }
}
</style>
