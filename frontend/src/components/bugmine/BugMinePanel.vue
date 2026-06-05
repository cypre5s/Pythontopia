<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import type {
  BugMineCardDto,
  BugMineDepthStepDto,
  BugMineErrorBookItemDto,
} from '@/api/dto/BugMine';

const learning = useLearningStore();
const toast = useToastStore();
const router = useRouter();

const session = computed(() => learning.bugMine.session);
const errorBook = computed(() => learning.bugMine.errorBook);
const cards = computed<BugMineCardDto[]>(() => session.value?.cards ?? []);

const defaultDepthSteps: BugMineDepthStepDto[] = [
  {
    layer: 1,
    key: 'NAKED_RETRY',
    title: 'L1 裸重试',
    description: '先独立重试一次。',
    unlocked: true,
  },
  {
    layer: 2,
    key: 'COUNTER_EXAMPLE',
    title: 'L2 反例',
    description: '用反例定位偏差。',
    unlocked: false,
  },
  {
    layer: 3,
    key: 'VARIABLE_TRACE',
    title: 'L3 变量轨迹',
    description: '看关键变量变化。',
    unlocked: false,
  },
  {
    layer: 4,
    key: 'LAYERED_HINTS',
    title: 'L4 分层提示',
    description: '按层解锁提示。',
    unlocked: false,
  },
  {
    layer: 5,
    key: 'KC_VARIANT',
    title: 'L5 同 KC 变式',
    description: '迁移到同知识点。',
    unlocked: false,
  },
];

const depthStepTitles: Record<number, string> = {
  1: 'L1 裸重试',
  2: 'L2 反例',
  3: 'L3 变量轨迹',
  4: 'L4 分层提示',
  5: 'L5 同 KC 变式',
};

function cardByType(type: BugMineCardDto['cardType']): BugMineCardDto | undefined {
  return cards.value.find((card) => card.cardType === type);
}

const counterExample = computed(() => cardByType('COUNTER_EXAMPLE'));
const variableTrace = computed(() => cardByType('VARIABLE_TRACE'));
const wrongAssumption = computed(() => cardByType('WRONG_ASSUMPTION'));
const hintLayer1 = computed(() => cardByType('HINT_LAYER_1'));
const hintLayer2 = computed(() => cardByType('HINT_LAYER_2'));
const hintLayer3 = computed(() => cardByType('HINT_LAYER_3'));

const retryCode = ref<string>('');

onMounted(() => {
  void learning.loadBugMineErrorBook();
});

function normalizedDepthSteps(item: BugMineErrorBookItemDto): BugMineDepthStepDto[] {
  return defaultDepthSteps.map((fallback) => {
    const step = item.depthSteps.find((candidate) => candidate.layer === fallback.layer);
    if (!step) {
      return fallback;
    }
    return {
      ...step,
      title: depthStepTitles[step.layer] ?? step.title,
      description: step.description || fallback.description,
    };
  });
}

function formatMasteryScore(score: number): string {
  return `${Math.round(score * 100)}%`;
}

function statusLabel(item: BugMineErrorBookItemDto): string {
  if (item.status === 'RESOLVED') return '已修复';
  if (item.status === 'ABANDONED') return '已搁置';
  return '进行中';
}

async function openErrorBookItem(item: BugMineErrorBookItemDto): Promise<void> {
  await learning.loadBugMine(item.sessionId);
  if (learning.bugMine.lastError) {
    toast.show(learning.bugMine.lastError, 'error');
  }
}

async function reveal(card?: BugMineCardDto): Promise<void> {
  if (!card || card.revealed) return;
  try {
    await learning.revealBugMineCardById(card.id);
  } catch (error) {
    const message = error instanceof Error ? error.message : '解锁失败';
    toast.show(message, 'error');
  }
}

const showVariantStep = computed(
  () => (session.value?.depthLayer ?? 0) >= 5 && session.value?.variantProblem != null,
);

async function openVariantProblem(): Promise<void> {
  const variant = session.value?.variantProblem;
  if (!variant) return;
  await learning.loadTrainingProblem(variant.id);
  if (learning.training.lastError) {
    toast.show(learning.training.lastError, 'error');
    return;
  }
  await router.push({ path: '/world', query: { panel: 'training' } });
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
  if (result.status === 'PASSED') {
    toast.show('修复成功！Bug 矿洞会话已 RESOLVED。', 'success');
  } else {
    toast.show('仍未通过，可继续调整后再次重试。', 'error');
  }
}
</script>

<template>
  <section
    class="bug-mine"
    data-testid="bug-mine-panel"
  >
    <header class="bug-mine__header">
      <h2>错题矿洞</h2>
      <p class="meta">
        <template v-if="session">
          会话 #{{ session.id }} · 状态 {{ session.status }} · 深度 L{{ session.depthLayer }} · 已重试 {{ session.retryCount }}
        </template>
        <template v-else>
          画像驱动错题本 · 5 层修复路径
        </template>
      </p>
    </header>

    <section
      class="error-book"
      data-testid="error-book"
    >
      <div class="error-book__head">
        <h3>错题矿洞</h3>
        <span v-if="learning.bugMine.errorBookLoading">同步中</span>
      </div>
      <p
        v-if="learning.bugMine.errorBookError"
        class="error-book__notice"
      >
        {{ learning.bugMine.errorBookError }}
      </p>
      <p
        v-else-if="learning.bugMine.errorBookLoading && errorBook.length === 0"
        class="error-book__notice"
      >
        读取错题本...
      </p>
      <p
        v-else-if="errorBook.length === 0"
        class="error-book__notice"
      >
        暂无错题记录。
      </p>
      <div
        v-else
        class="error-book__list"
      >
        <article
          v-for="item in errorBook"
          :key="item.sessionId"
          class="error-book__item"
          :class="{ 'error-book__item--active': session?.id === item.sessionId }"
        >
          <button
            type="button"
            class="error-book__button"
            :aria-pressed="session?.id === item.sessionId"
            :data-testid="`error-book-item-${item.sessionId}`"
            @click="openErrorBookItem(item)"
          >
            <span class="error-book__title">{{ item.problemTitle }}</span>
            <span class="error-book__meta">
              {{ item.kcCode }} · {{ statusLabel(item) }} · 掌握 {{ formatMasteryScore(item.masteryScore) }} · 重试 {{ item.retryCount }}
            </span>
            <span
              v-if="item.mistakePatterns.length > 0"
              class="error-book__patterns"
            >
              {{ item.patternMatched ? '画像命中' : '待归因' }} · {{ item.mistakePatterns.join(' / ') }}
            </span>
          </button>
          <ol
            class="depth-steps"
            :data-testid="`depth-steps-${item.sessionId}`"
          >
            <li
              v-for="step in normalizedDepthSteps(item)"
              :key="step.layer"
              class="depth-step"
              :class="{
                'depth-step--unlocked': step.unlocked,
                'depth-step--current': item.depthLayer === step.layer,
              }"
            >
              <span class="depth-step__dot">{{ step.layer }}</span>
              <span class="depth-step__copy">
                <strong>{{ step.title }}</strong>
                <small>{{ step.description }}</small>
              </span>
            </li>
          </ol>
          <p class="mastery-confirm">
            掌握确认：L5 同 KC 变式通过后出矿。
          </p>
        </article>
      </div>
    </section>

    <section
      v-if="session"
      class="session-detail"
    >
      <article
        v-if="counterExample"
        class="card"
        data-testid="card-counter-example"
      >
        <h3>反例</h3>
        <p>输入：<code>{{ counterExample.payload.stdin }}</code></p>
        <p>期望：<code>{{ counterExample.payload.expectedStdout ?? counterExample.payload.expected_stdout }}</code></p>
        <p>你的输出：<code>{{ counterExample.payload.yourStdout ?? counterExample.payload.your_stdout }}</code></p>
        <p class="diff">
          差异：{{ counterExample.payload.diffSummary ?? counterExample.payload.diff_summary }}
        </p>
      </article>

      <article
        v-if="variableTrace"
        class="card"
        data-testid="card-trace"
      >
        <h3>变量轨迹</h3>
        <pre class="trace">{{ JSON.stringify(variableTrace.payload, null, 2).slice(0, 1600) }}</pre>
      </article>

      <article
        v-if="wrongAssumption"
        class="card"
        data-testid="card-assumption"
      >
        <h3>错误假设</h3>
        <p>{{ wrongAssumption.payload.youThought ?? wrongAssumption.payload.you_thought }}</p>
        <p class="actually">
          {{ wrongAssumption.payload.actually }}
        </p>
      </article>

      <article
        v-if="hintLayer1"
        class="card"
        data-testid="card-hint-1"
      >
        <h3>提示 · Layer 1</h3>
        <p class="headline">
          {{ hintLayer1.payload.headline }}
        </p>
        <p>{{ hintLayer1.payload.body }}</p>
      </article>

      <article
        v-if="hintLayer2"
        class="card"
        data-testid="card-hint-2"
      >
        <h3>提示 · Layer 2 {{ hintLayer2.revealed ? '' : '（未解锁）' }}</h3>
        <template v-if="hintLayer2.revealed">
          <p class="headline">
            {{ hintLayer2.payload.headline }}
          </p>
          <p>{{ hintLayer2.payload.body }}</p>
        </template>
        <button
          v-else
          type="button"
          class="reveal"
          @click="reveal(hintLayer2)"
        >
          解锁 Layer 2 提示
        </button>
      </article>

      <article
        v-if="hintLayer3"
        class="card"
        data-testid="card-hint-3"
      >
        <h3>提示 · Layer 3 {{ hintLayer3.revealed ? '' : '（未解锁）' }}</h3>
        <template v-if="hintLayer3.revealed">
          <p class="headline">
            {{ hintLayer3.payload.headline }}
          </p>
          <p>{{ hintLayer3.payload.body }}</p>
          <small>所有 Layer 3 都不会给完整可粘贴代码。</small>
        </template>
        <button
          v-else
          type="button"
          class="reveal"
          @click="reveal(hintLayer3)"
        >
          解锁 Layer 3 提示
        </button>
      </article>

      <article
        v-if="showVariantStep"
        class="card"
        data-testid="card-kc-variant"
      >
        <h3>L5 同 KC 变式 · 掌握确认</h3>
        <p>
          换一道同知识点题验证迁移：{{ session?.variantProblem?.title }}
        </p>
        <button
          type="button"
          class="reveal"
          data-testid="variant-problem-button"
          @click="openVariantProblem"
        >
          去做变式题
        </button>
      </article>

      <section
        class="retry"
        data-testid="retry-area"
      >
        <h3>重试提交（走服务端判题）</h3>
        <textarea
          v-model="retryCode"
          rows="10"
          spellcheck="false"
        />
        <button
          type="button"
          class="primary"
          :disabled="learning.bugMine.loading"
          data-testid="retry-button"
          @click="retry"
        >
          重新提交
        </button>
        <p
          v-if="learning.bugMine.lastRetry"
          class="status"
        >
          最近重试：状态 {{ learning.bugMine.lastRetry.status }} · 来源 {{ learning.bugMine.lastRetry.source }}
        </p>
      </section>
    </section>
    <p
      v-else
      class="empty"
    >
      选择一道错题查看矿洞详情。
    </p>
  </section>
</template>

<style scoped>
.bug-mine {
  background: linear-gradient(180deg, #2a1d10 0%, #3d2c1a 100%);
  color: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  padding: 16px;
  display: grid;
  gap: 12px;
}
header h2 {
  margin: 0;
}
.bug-mine__header {
  display: grid;
  gap: 4px;
}
.meta {
  margin: 0;
  font-size: 12px;
  color: #f3d796;
}
.error-book {
  background: rgba(20, 15, 10, 0.38);
  border: 1px solid rgba(243, 215, 150, 0.36);
  border-radius: 8px;
  padding: 10px;
  display: grid;
  gap: 10px;
}
.error-book__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.error-book__head h3 {
  margin: 0;
  font-size: 15px;
}
.error-book__head span,
.error-book__notice,
.mastery-confirm {
  margin: 0;
  color: #f3d796;
  font-size: 12px;
}
.error-book__list {
  display: grid;
  gap: 8px;
}
.error-book__item {
  background: rgba(255, 251, 233, 0.94);
  color: #2b1c0b;
  border: 1px solid rgba(243, 215, 150, 0.5);
  border-radius: 8px;
  padding: 8px;
  display: grid;
  gap: 8px;
}
.error-book__item--active {
  outline: 2px solid #f3d796;
}
.error-book__button {
  width: 100%;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  display: grid;
  gap: 3px;
  padding: 0;
  text-align: left;
}
.error-book__button:focus-visible {
  outline: 2px solid #6e2c1a;
  outline-offset: 3px;
}
.error-book__title {
  font-weight: 700;
}
.error-book__meta,
.error-book__patterns {
  color: #6e2c1a;
  font-size: 12px;
}
.depth-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
}
.depth-step {
  border: 1px solid rgba(110, 44, 26, 0.24);
  border-radius: 6px;
  min-height: 68px;
  padding: 6px;
  display: grid;
  align-content: start;
  gap: 4px;
  background: #f6ead0;
  opacity: 0.58;
}
.depth-step--unlocked {
  opacity: 1;
}
.depth-step--current {
  border-color: #6e2c1a;
  box-shadow: inset 0 0 0 1px #6e2c1a;
}
.depth-step__dot {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: #6e2c1a;
  color: #fffbe9;
  display: inline-grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
}
.depth-step__copy {
  display: grid;
  gap: 2px;
}
.depth-step__copy strong,
.depth-step__copy small {
  line-height: 1.25;
}
.depth-step__copy strong {
  font-size: 12px;
}
.depth-step__copy small {
  color: #6e2c1a;
  font-size: 11px;
}
.session-detail {
  display: grid;
  gap: 12px;
}
.card {
  background: rgba(255, 251, 233, 0.92);
  color: #2b1c0b;
  border-radius: 8px;
  padding: 12px;
  display: grid;
  gap: 6px;
}
.card h3 {
  margin: 0;
}
.card code {
  background: #fff2c9;
  padding: 1px 4px;
  border-radius: 4px;
}
.trace {
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border-radius: 6px;
  padding: 8px;
  max-height: 220px;
  overflow: auto;
}
.diff, .actually {
  color: #6e2c1a;
}
.headline {
  font-weight: 600;
}
.reveal {
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
  border: 2px solid var(--pyt-color-text-hint);
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
}
.retry {
  background: rgba(255, 251, 233, 0.92);
  color: #2b1c0b;
  border-radius: 8px;
  padding: 12px;
  display: grid;
  gap: 8px;
}
.retry textarea {
  font-family: monospace;
  padding: 8px;
  border-radius: 6px;
  border: 1px solid var(--pyt-color-border);
  background: #fffbe9;
  resize: vertical;
}
.retry .primary {
  align-self: start;
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: 6px;
  padding: 6px 14px;
  cursor: pointer;
  font-weight: 600;
}
.status {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  margin: 0;
}
.empty {
  padding: 14px;
  background: var(--pyt-color-bg-soft);
  border-radius: 8px;
  text-align: center;
  color: var(--pyt-color-text-hint);
}
@media (max-width: 760px) {
  .depth-steps {
    grid-template-columns: 1fr;
  }
  .depth-step {
    min-height: 0;
    grid-template-columns: auto 1fr;
    align-items: start;
  }
}
</style>
