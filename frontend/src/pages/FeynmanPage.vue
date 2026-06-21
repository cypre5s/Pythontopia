<script setup lang="ts">
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import FeynmanPanel from '@/components/feynman/FeynmanPanel.vue';
import KcPicker from '@/components/feynman/KcPicker.vue';
import { FEYNMAN_SELF_TEST_KCS, FEYNMAN_SUGGESTED_KCS, isFeynmanSelfTestKc } from '@/data/feynmanKcs';

const route = useRoute();

function initialKc(): string {
  const raw = route.query.kc;
  const code = Array.isArray(raw) ? raw[0] ?? '' : typeof raw === 'string' ? raw : '';
  // 深链带入的 KC 也须是「可用」自测知识点，否则回到选择器让学生自选。
  return code && isFeynmanSelfTestKc(code) ? code : '';
}

const activeKc = ref(initialKc());

// 自测仅开放「可编程、可找茬」的知识点（隐藏纯史实/概念 KC）。
const availableKcs = FEYNMAN_SELF_TEST_KCS;
// 学生多半不记得知识点全名，给几个常见入口（点一下即开课），均取自可用集合。
const suggestedKcs = FEYNMAN_SUGGESTED_KCS;

function pick(code: string): void {
  activeKc.value = code;
}

// 课件外自由概念：直接用概念词开局（概念模式——后端无代表题，仅找茬+对话、不计掌握度、标「通识」）。
const conceptDraft = ref('');
function startConcept(): void {
  const concept = conceptDraft.value.trim();
  if (concept.length === 0) return;
  activeKc.value = concept;
  conceptDraft.value = '';
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
            <KcPicker
              :codes="availableKcs"
              @select="pick"
            />
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
            <form
              class="feynman-page__concept"
              @submit.prevent="startConcept"
            >
              <input
                v-model="conceptDraft"
                type="text"
                class="feynman-page__concept-input"
                maxlength="30"
                placeholder="课件里没有？直接输入概念，如「装饰器」「正则表达式」"
                data-testid="feynman-concept-input"
              >
              <button
                type="submit"
                class="feynman-page__concept-go"
                :disabled="conceptDraft.trim().length === 0"
                data-testid="feynman-concept-go"
              >
                用这个概念开始
              </button>
            </form>
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

/* 课件外自由概念入口 */
.feynman-page__concept {
  display: flex;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
  align-items: center;
  margin-top: var(--pyt-space-1);
}
.feynman-page__concept-input {
  flex: 1 1 240px;
  min-width: 0;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border: 2px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-bg-primary);
  color: var(--pyt-color-text-primary);
  font: inherit;
}
.feynman-page__concept-input:focus {
  outline: none;
  border-style: solid;
  border-color: var(--pyt-color-primary);
}
.feynman-page__concept-go {
  flex: 0 0 auto;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  cursor: pointer;
}
.feynman-page__concept-go:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
