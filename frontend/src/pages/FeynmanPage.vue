<script setup lang="ts">
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import FeynmanPanel from '@/components/feynman/FeynmanPanel.vue';
import KcPicker from '@/components/feynman/KcPicker.vue';

const route = useRoute();

function initialKc(): string {
  const raw = route.query.kc;
  if (Array.isArray(raw)) {
    return raw[0] ?? '';
  }
  return typeof raw === 'string' ? raw : '';
}

const activeKc = ref(initialKc());

interface SuggestedKc {
  code: string;
  label: string;
}

// 学生多半不记得知识点全名，给几个常见入口（点一下即开课）。
const suggestedKcs: SuggestedKc[] = [
  { code: 'PY43-KC-031', label: 'for 循环与遍历' },
  { code: 'PY43-KC-019', label: '数值类型与运算' },
  { code: 'PY43-KC-009', label: 'Python 语言概述' },
  { code: 'PY43-KC-048', label: '文件操作' },
];

function pick(code: string): void {
  activeKc.value = code;
}

// 面板内「换知识点」：收起会话、回到选择器。
function resetPicker(): void {
  activeKc.value = '';
}
</script>

<template>
  <AppLayout>
    <section
      class="feynman-page"
      :class="{ 'feynman-page--active': activeKc.length > 0 }"
    >
      <div class="feynman-page__content">
        <header
          v-if="activeKc.length === 0"
          class="feynman-page__intro"
        >
          <div class="feynman-page__hero">
            <h1 class="feynman-page__title">
              费曼反演
            </h1>
            <p class="feynman-page__lead">
              找茬 → 讲明白 → 真判题出师。
            </p>
          </div>

          <div class="feynman-page__picker">
            <KcPicker @select="pick" />
            <div class="feynman-page__chips">
              <span class="feynman-page__chips-hint">常用：</span>
              <button
                v-for="kc in suggestedKcs"
                :key="kc.code"
                type="button"
                class="feynman-page__chip"
                @click="pick(kc.code)"
              >
                {{ kc.label }}
              </button>
            </div>
          </div>
        </header>

        <FeynmanPanel
          v-if="activeKc.length > 0"
          :key="activeKc"
          :kc-code="activeKc"
          @change-kc="resetPicker"
        />
        <p
          v-else
          class="feynman-page__hint"
          data-testid="feynman-page-hint"
        >
          选个知识点开始。
        </p>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.feynman-page {
  padding: var(--pyt-space-4);
  min-height: calc(100vh - 64px);
  min-height: calc(100dvh - 64px);
  background: linear-gradient(
    180deg,
    var(--pyt-color-bg-primary) 0%,
    var(--pyt-color-bg-sunken) 100%
  );
}

/* 内容居中并限宽，避免在宽屏上各卡片被拉得过大（对齐其它页的内容密度）。 */
.feynman-page__content {
  display: grid;
  gap: var(--pyt-space-4);
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
}

/* 进入会话后整页不上下滚动：锁定视口高度，让面板内部各区自行滚动腾出视口。 */
.feynman-page--active {
  height: calc(100vh - 52px);
  height: calc(100dvh - 52px);
  min-height: 0;
  overflow: hidden;
}

.feynman-page--active .feynman-page__content {
  height: 100%;
  min-height: 0;
  max-width: none;
}

@media (max-width: 900px) {
  /* 窄屏回归自然流：单列堆叠时允许整页滚动，避免内容被压扁。 */
  .feynman-page--active {
    height: auto;
    min-height: calc(100dvh - 52px);
    overflow: visible;
  }
}

.feynman-page__intro {
  display: grid;
  gap: var(--pyt-space-4);
  padding: var(--pyt-space-5, 24px) var(--pyt-space-4);
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-border));
  border-radius: var(--pyt-radius-lg, 8px);
  background:
    radial-gradient(120% 130% at 0% 0%, rgba(212, 175, 55, 0.13) 0%, transparent 55%),
    var(--pyt-color-bg-elevated);
  box-shadow: var(--pyt-shadow-card);
  color: var(--pyt-color-text-primary);
}

.feynman-page__hero {
  display: grid;
  gap: var(--pyt-space-2);
  max-width: 64ch;
}

.feynman-page__title {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  font-size: clamp(20px, 3.2vw, 28px);
  line-height: 1.3;
  text-shadow: 2px 2px 0 var(--pyt-color-border);
}

.feynman-page__lead {
  margin: 0;
  color: var(--pyt-color-text-secondary);
  line-height: 1.8;
}

.feynman-page__steps {
  display: flex;
  flex-wrap: wrap;
  gap: var(--pyt-space-2) var(--pyt-space-4);
  margin: var(--pyt-space-1) 0 0;
  padding: 0;
  list-style: none;
}

.feynman-page__steps li {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}

.feynman-page__step-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--pyt-color-primary);
  color: var(--pyt-color-text-on-dark);
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
}

.feynman-page__picker {
  display: grid;
  gap: var(--pyt-space-3);
}

.feynman-page__chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--pyt-space-2);
}

.feynman-page__chips-hint {
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
}

.feynman-page__chip {
  border: 1px solid var(--pyt-color-border);
  border-radius: 999px;
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  padding: 4px 12px;
  font: inherit;
  font-size: var(--pyt-font-size-sm);
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
}

.feynman-page__chip:hover {
  background: var(--pyt-color-bg-elevated);
  border-color: var(--pyt-color-primary);
}

.feynman-page__chip--active {
  background: var(--pyt-color-primary);
  color: var(--pyt-color-text-on-dark);
  border-color: var(--pyt-color-border-strong);
}

.feynman-page__hint {
  margin: 0;
  padding: var(--pyt-space-4);
  color: var(--pyt-color-text-muted);
}
</style>
