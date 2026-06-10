<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import FeynmanPanel from '@/components/feynman/FeynmanPanel.vue';

const route = useRoute();

function initialKc(): string {
  const raw = route.query.kc;
  if (Array.isArray(raw)) {
    return raw[0] ?? '';
  }
  return typeof raw === 'string' ? raw : '';
}

const kcInput = ref(initialKc());
const activeKc = ref(initialKc());
const trimmedKc = computed(() => kcInput.value.trim());

function openSession(): void {
  if (trimmedKc.value.length === 0) {
    return;
  }
  activeKc.value = trimmedKc.value;
}
</script>

<template>
  <AppLayout>
    <section class="feynman-page">
      <header class="feynman-page__intro">
        <p class="feynman-page__eyebrow">
          创新点 C · 费曼反演
        </p>
        <h1>教会会犯你同款错的 AI 学弟</h1>
        <p class="feynman-page__lead">
          后端会用你的真实错题与事件抽取「学弟」的误解（每条带 event_id 溯源）。用你自己的话把它讲通，
          再让它写代码、跑真判题——learning-by-teaching。
        </p>
        <form
          class="feynman-page__picker"
          @submit.prevent="openSession"
        >
          <label for="feynman-kc">选择知识点（KC 代码）</label>
          <div class="feynman-page__picker-row">
            <input
              id="feynman-kc"
              v-model="kcInput"
              type="text"
              placeholder="例如 PY43-KC-016"
              data-testid="feynman-kc-input"
            >
            <button
              type="submit"
              data-testid="feynman-kc-open"
              :disabled="trimmedKc.length === 0"
            >
              进入费曼反演
            </button>
          </div>
        </form>
      </header>

      <FeynmanPanel
        v-if="activeKc.length > 0"
        :key="activeKc"
        :kc-code="activeKc"
      />
      <p
        v-else
        class="feynman-page__hint"
        data-testid="feynman-page-hint"
      >
        先填入一个你想巩固的知识点 KC 代码，再开始费曼反演。
      </p>
    </section>
  </AppLayout>
</template>

<style scoped>
.feynman-page {
  display: grid;
  gap: var(--pyt-space-4);
  padding: var(--pyt-space-4);
  min-height: calc(100vh - 64px);
  background: linear-gradient(
    180deg,
    var(--pyt-color-bg-primary) 0%,
    var(--pyt-color-bg-sunken) 100%
  );
}

.feynman-page__intro {
  display: grid;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-4);
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
  box-shadow: var(--pyt-shadow-card);
  color: var(--pyt-color-text-primary);
}

.feynman-page__eyebrow {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-sm);
}

.feynman-page__intro h1 {
  margin: 0;
  font-family: var(--pyt-font-pixel);
}

.feynman-page__lead {
  margin: 0;
  color: var(--pyt-color-text-secondary);
  line-height: 1.7;
}

.feynman-page__picker {
  display: grid;
  gap: var(--pyt-space-1);
  margin-top: var(--pyt-space-2);
}

.feynman-page__picker label {
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
}

.feynman-page__picker-row {
  display: flex;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
}

.feynman-page__picker input {
  flex: 1 1 220px;
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  font: inherit;
}

.feynman-page__picker button {
  border: 2px solid var(--pyt-color-border-strong);
  background: var(--pyt-color-primary);
  color: var(--pyt-color-text-on-dark);
  padding: var(--pyt-space-2) var(--pyt-space-4);
  font-family: var(--pyt-font-pixel);
  cursor: pointer;
}

.feynman-page__picker button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.feynman-page__hint {
  margin: 0;
  padding: var(--pyt-space-4);
  color: var(--pyt-color-text-muted);
}
</style>
