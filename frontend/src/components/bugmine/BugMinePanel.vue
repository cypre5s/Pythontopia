<script setup lang="ts">
import { computed, ref } from 'vue';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import type { BugMineCardDto } from '@/api/dto/BugMine';

const learning = useLearningStore();
const toast = useToastStore();

const session = computed(() => learning.bugMine.session);
const cards = computed<BugMineCardDto[]>(() => session.value?.cards ?? []);

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

async function reveal(card?: BugMineCardDto): Promise<void> {
  if (!card || card.revealed) return;
  try {
    await learning.revealBugMineCardById(card.id);
  } catch (error) {
    const message = error instanceof Error ? error.message : '解锁失败';
    toast.show(message, 'error');
  }
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
    v-if="session"
    class="bug-mine"
    data-testid="bug-mine-panel"
  >
    <header>
      <h2>Bug 矿洞</h2>
      <p class="meta">
        会话 #{{ session.id }} · 状态 {{ session.status }} · 已重试 {{ session.retryCount }}
      </p>
    </header>

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
    暂无活跃 Bug 矿洞。先在训练场失败后再进入。
  </p>
</template>

<style scoped>
.bug-mine {
  background: linear-gradient(180deg, #2a1d10 0%, #3d2c1a 100%);
  color: #fff7e6;
  border: 2px solid #6e4f1d;
  border-radius: 10px;
  padding: 16px;
  display: grid;
  gap: 12px;
}
header h2 {
  margin: 0;
}
.meta {
  margin: 0;
  font-size: 12px;
  color: #f3d796;
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
  background: #2e2417;
  color: #f1e5c8;
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
  background: #fff7e6;
  color: #6e4f1d;
  border: 2px solid #6e4f1d;
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
  border: 1px solid #c9a86b;
  background: #fffbe9;
  resize: vertical;
}
.retry .primary {
  align-self: start;
  background: #6e4f1d;
  color: #fff7e6;
  border: 2px solid #4d3613;
  border-radius: 6px;
  padding: 6px 14px;
  cursor: pointer;
  font-weight: 600;
}
.status {
  font-size: 12px;
  color: #6e4f1d;
  margin: 0;
}
.empty {
  padding: 14px;
  background: #fdf3d0;
  border-radius: 8px;
  text-align: center;
  color: #6e4f1d;
}
</style>
