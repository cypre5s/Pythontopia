<script setup lang="ts">
import { computed, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useReducedMotion } from '@/composables/useReducedMotion';
import { useFeynmanStore } from '@/stores/feynman';
import BugHunterGame from '@/components/feynman/BugHunterGame.vue';
import { kcDisplayName } from '@/data/kcNames';
import { isFeynmanSelfTestKc } from '@/data/feynmanKcs';
import type { FeynmanJudgeResultDto } from '@/api/feynman';

const props = defineProps<{
  kcCode: string;
}>();

const emit = defineEmits<{ (e: 'change-kc'): void }>();

const store = useFeynmanStore();
const {
  currentSession,
  turns,
  loading,
  teaching,
  judging,
  error,
  latestProducedCode,
  latestJudgeResult,
} = storeToRefs(store);
const teachingText = ref('');
const teachFailed = ref(false);
const prefersReducedMotion = useReducedMotion();
// 概念模式：非课程 KC（课件外自由概念）→ 通识知识，以代码找茬为准、不计入掌握度。
const isConcept = computed(() => !isFeynmanSelfTestKc(props.kcCode));
const misconceptions = computed(() => currentSession.value?.tuteeState.misconceptions ?? []);
const isIndependentTeaching = computed(
  () => currentSession.value !== null && misconceptions.value.length === 0,
);

const canTeach = computed(
  () => currentSession.value !== null && teachingText.value.trim().length > 0 && !teaching.value,
);

// 已判定通过的会话不能再判：后端幂等只回放旧结论，按钮继续可点只会误导学生反复等待。
const alreadyPassed = computed(() => latestJudgeResult.value?.status === 'PASSED');

const canJudge = computed(
  () =>
    currentSession.value !== null &&
    !isConcept.value &&
    !isIndependentTeaching.value &&
    currentSession.value.status !== 'JUDGED_PASS' &&
    latestJudgeResult.value?.status !== 'PASSED' &&
    latestProducedCode.value.trim().length > 0 &&
    !judging.value,
);

const judgeDisabledHint = computed(() => {
  if (!currentSession.value) return '';
  if (isConcept.value) return '通识概念没有代表题，本模式以代码找茬为准，不做真判题。';
  if (isIndependentTeaching.value) return '当前没有真实错题记录，自主讲题不会进行错题专项判题或掌握度回流。';
  if (alreadyPassed.value) return '本会话已判定教学成功，不重复计分。';
  return '';
});

const judgeText = computed(() => {
  const result = latestJudgeResult.value;
  if (isConcept.value) {
    return result?.status === 'NA'
      ? result.messageZh
      : '通识概念以代码找茬为准，不进行代表题真判题，也不计入掌握度。';
  }
  if (isIndependentTeaching.value) {
    return '自主讲题可以继续对话；当前没有真实错题记录，不进行错题专项真判题或掌握度回流。';
  }
  if (currentSession.value?.status === 'JUDGED_PASS' && !result) {
    return '本会话已经通过真判题，掌握度已完成回流。';
  }
  if (!result) {
    return '等待代码';
  }
  return result.messageZh;
});

const judgeButtonText = computed(() => {
  if (judging.value) {
    return '判题中…';
  }
  if (isConcept.value) {
    return '概念模式无需判题';
  }
  if (isIndependentTeaching.value) {
    return '自主讲题不进行真判题';
  }
  if (
    currentSession.value?.status === 'JUDGED_PASS' ||
    latestJudgeResult.value?.status === 'PASSED'
  ) {
    return '已通过';
  }
  return '真判题裁定';
});

const misconceptionSummary = computed(() =>
  misconceptions.value.map((item) => item.tag).join('、'),
);
const latestTurn = computed(() => turns.value.at(-1) ?? null);
const teachHint = computed(() => store.bugHunt?.hintMd ?? '');
const kcLabel = computed(() => kcDisplayName(props.kcCode));
const latestNotice = computed(() => turns.value.at(-1)?.noticeMd ?? '');

/** 学弟中性描述代码在做什么（不暴露「我以为…」式自我纠错）。 */
const TUTEE_CODE_DESC: Record<string, string> = {
  off_by_one_range: '我写了个循环，用 range(1, 5) 从 1 累加到 5，最后打印总和。',
  input_type_string: '让用户输入年龄，然后直接加 1 打印出来。',
  list_alias_mutation: '先建列表 a，令 b = a，往 b 里 append 一个数，再 print(a) 看结果。',
};
// 仅当 bug 代码确实匹配该题型签名时才套固定文案：LLM 现生的 bug（贴任意 KC/课件外概念）会复用
// 兜底 tag，若无脑套文案会出现「正则的题配『我写了个循环』开场白」的错位，此时回退中性开场白。
const TUTEE_DESC_SIGNATURE: Record<string, RegExp> = {
  off_by_one_range: /range\s*\(/,
  input_type_string: /input\s*\(/,
  list_alias_mutation: /\.append\s*\(/,
};
const tuteeVoice = computed(() => {
  const hunt = store.bugHunt;
  if (!hunt?.buggyCode) return '';
  const tag = hunt.misconceptionTag ?? '';
  const desc = TUTEE_CODE_DESC[tag];
  const signature = TUTEE_DESC_SIGNATURE[tag];
  if (desc && signature && signature.test(hunt.buggyCode)) return desc;
  return '我按自己的理解写了段代码，你来帮我看看哪里不对。';
});

const hasChat = computed(
  () =>
    tuteeVoice.value.length > 0 || turns.value.length > 0 || Boolean(latestTurn.value?.taughtFix),
);

function judgeClass(result: FeynmanJudgeResultDto | null): string {
  if ((isConcept.value || isIndependentTeaching.value) && (!result || result.status === 'PENDING')) {
    return 'feynman-panel__judge--na';
  }
  if (currentSession.value?.status === 'JUDGED_PASS' && !result) {
    return 'feynman-panel__judge--passed';
  }
  if (!result) {
    return 'feynman-panel__judge--pending';
  }
  return `feynman-panel__judge--${result.status.toLowerCase()}`;
}

async function start(): Promise<void> {
  teachFailed.value = false;
  teachingText.value = '';
  try {
    await store.startSession(props.kcCode);
  } catch {
    // 错误已写入 store.error 并在面板内提示。
  }
}

// AI 学弟不可用时后端 fail fast（FEYNMAN_TUTEE_UNAVAILABLE），本轮不落库也不判成败。
// 保留学生这次讲的内容以便原样重试，绝不本地伪造一个「学弟已听懂」。
const lastTeachAttempt = ref('');

async function submitTeaching(): Promise<void> {
  const explanation = teachingText.value.trim();
  if (!canTeach.value || explanation.length === 0) {
    return;
  }
  teachFailed.value = false;
  try {
    await store.teach(explanation);
    teachingText.value = '';
    lastTeachAttempt.value = '';
  } catch {
    teachFailed.value = true;
    // 错误已写入 store.error 并在面板内提示，这里吞掉以避免未处理的 promise rejection。
  }
}

async function retryTeaching(): Promise<void> {
  if (teaching.value || lastTeachAttempt.value.length === 0) {
    return;
  }
  teachingText.value = lastTeachAttempt.value;
  await submitTeaching();
}

async function submitJudge(): Promise<void> {
  if (!canJudge.value) {
    return;
  }
  try {
    await store.judge();
  } catch {
    // 同上：错误经 store.error 呈现。
  }
}
</script>

<template>
  <section
    class="feynman-panel"
    :class="{ 'feynman-panel--reduced-motion': prefersReducedMotion }"
    :data-motion="prefersReducedMotion ? 'reduced' : 'default'"
    data-testid="feynman-panel"
  >
    <header class="feynman-panel__bar">
      <div class="feynman-panel__bar-title">
        <span class="feynman-panel__eyebrow">费曼反演</span>
        <h2 class="feynman-panel__title">
          {{ kcLabel }}
        </h2>
      </div>
      <span
        v-if="isConcept"
        class="feynman-panel__concept-badge"
        data-testid="feynman-concept-badge"
        title="课件外概念 · 通识知识：以代码找茬为准，不计入掌握度"
      >通识 · 非课件</span>
      <p
        v-if="currentSession && misconceptionSummary"
        class="feynman-panel__misc"
        data-testid="feynman-tutee-state"
      >
        <span class="feynman-panel__misc-tag">误区</span>
        <span class="feynman-panel__misc-text">{{ misconceptionSummary }}</span>
      </p>
      <div class="feynman-panel__bar-actions">
        <button
          type="button"
          class="feynman-panel__ghost"
          @click="emit('change-kc')"
        >
          换知识点
        </button>
        <button
          type="button"
          class="feynman-panel__start"
          data-testid="feynman-start"
          :disabled="loading"
          @click="start"
        >
          {{ currentSession ? '重来' : '开始' }}
        </button>
      </div>
    </header>

    <p
      v-if="error"
      class="feynman-panel__error"
      role="alert"
      data-testid="feynman-error"
    >
      <span>{{ error }}</span>
      <button
        v-if="lastTeachAttempt"
        type="button"
        class="feynman-panel__retry"
        data-testid="feynman-teach-retry"
        :disabled="teaching"
        @click="retryTeaching"
      >
        {{ teaching ? '重试中…' : '重试这次讲解' }}
      </button>
    </p>

    <p
      v-if="isIndependentTeaching"
      class="feynman-panel__independent-note"
      data-testid="feynman-independent-teaching"
      role="status"
    >
      当前知识点没有真实错题记录，可以直接讲解知识点或题目。本次不会创建错题记录，也不会进行错题专项判题。
    </p>

    <p
      v-if="!currentSession"
      class="feynman-panel__intro-hint"
    >
      点「开始」，可以讲解知识点、讨论题目，或和 AI 学弟一起找代码问题。
    </p>

    <div
      v-else
      class="feynman-panel__stage"
    >
      <div class="feynman-panel__game">
        <BugHunterGame />
      </div>

      <div class="feynman-panel__flow">
        <article
          class="feynman-panel__card feynman-panel__chat"
          data-testid="feynman-dialog"
        >
          <header class="feynman-panel__chat-header">
            <span class="feynman-panel__chat-avatar">弟</span>
            <div class="feynman-panel__chat-meta">
              <span class="feynman-panel__chat-name">学弟</span>
              <span class="feynman-panel__chat-status">
                <span
                  class="feynman-panel__chat-dot"
                  aria-hidden="true"
                />
                费曼反演
              </span>
            </div>
          </header>

          <div class="feynman-panel__chat-body">
            <p
              v-if="!hasChat"
              class="feynman-panel__muted"
            >
              {{ isIndependentTeaching ? '直接向学弟讲解知识点，或把不会的题目描述给学弟。' : '找茬后学弟会开口。' }}
            </p>

            <div
              v-if="tuteeVoice"
              class="chat-msg chat-msg--tutee"
              data-testid="feynman-tutee-voice"
            >
              <span class="chat-msg__avatar">弟</span>
              <div class="chat-msg__bubble">
                <p>{{ tuteeVoice }}</p>
              </div>
            </div>

            <template
              v-for="turn in turns"
              :key="turn.id"
            >
              <div
                v-if="turn.role === 'TUTEE'"
                class="chat-msg chat-msg--tutee"
              >
                <span class="chat-msg__avatar">弟</span>
                <div class="chat-msg__bubble">
                  <p>{{ turn.content }}</p>
                  <p
                    v-if="turn.taughtFix"
                    class="chat-msg__fix"
                    data-testid="feynman-fix"
                  >
                    {{ turn.taughtFix }}
                  </p>
                </div>
              </div>
              <div
                v-else
                class="chat-msg chat-msg--me"
              >
                <div class="chat-msg__bubble">
                  <p>{{ turn.content }}</p>
                </div>
                <span class="chat-msg__avatar">我</span>
              </div>
              <p
                v-if="turn.noticeMd"
                class="feynman-panel__system-notice"
                data-testid="feynman-system-notice"
                role="status"
              >
                {{ turn.noticeMd }}
              </p>
            </template>

            <p
              v-if="latestNotice"
              class="feynman-panel__notice"
              data-testid="feynman-notice"
            >
              {{ latestNotice }}
            </p>

            <p
              v-if="teachHint"
              class="feynman-panel__teach-hint"
              data-testid="feynman-teach-hint"
            >
              {{ teachHint }}
            </p>
          </div>

          <div class="feynman-panel__chat-input">
            <textarea
              v-model="teachingText"
              class="feynman-panel__teach-input"
              data-testid="feynman-teach-input"
              :placeholder="isIndependentTeaching ? '把题目或知识点讲给学弟听…' : '讲清错在哪…'"
              rows="2"
            />
            <div class="feynman-panel__chat-send-row">
              <button
                type="button"
                class="feynman-panel__teach-submit feynman-panel__send"
                data-testid="feynman-teach-submit"
                :disabled="!canTeach"
                @click="submitTeaching"
              >
                {{ teaching ? '思考中…' : teachFailed ? '重试提交' : '讲给学弟听' }}
              </button>
            </div>
          </div>
        </article>

        <article
          class="feynman-panel__card feynman-panel__verdict"
          :class="judgeClass(latestJudgeResult)"
        >
          <h3>真判题</h3>
          <pre
            class="feynman-panel__code"
            data-testid="feynman-code"
          ><code>{{ latestProducedCode || '等学弟写代码' }}</code></pre>
          <p
            class="feynman-panel__judge-msg"
            data-testid="feynman-judge"
          >
            {{ judgeText }}
          </p>
          <div class="feynman-panel__verdict-foot">
            <span
              v-if="judgeDisabledHint"
              class="feynman-panel__judge-hint"
              data-testid="feynman-judge-hint"
            >
              {{ judgeDisabledHint }}
            </span>
            <button
              type="button"
              class="feynman-panel__teach-submit"
              data-testid="feynman-judge-submit"
              :disabled="!canJudge"
              @click="submitJudge"
            >
              {{ judgeButtonText }}
            </button>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.feynman-panel {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  height: 100%;
  min-height: 0;
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read);
}

.feynman-panel__bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  flex-shrink: 0;
  gap: var(--pyt-space-2) var(--pyt-space-4);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border: 2px solid var(--pyt-color-border-strong);
  border-left: 4px solid var(--pyt-color-gold);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-elevated);
  box-shadow: var(--pyt-shadow-card);
}

.feynman-panel__bar-title {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.feynman-panel__eyebrow {
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}

.feynman-panel__title {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  font-size: clamp(14px, 1.6vw, 17px);
  color: var(--pyt-color-text-primary);
}

.feynman-panel__misc {
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-space-2);
  margin: 0;
  min-width: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}

.feynman-panel__independent-note {
  flex-shrink: 0;
  margin: 0;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border-left: 3px solid var(--pyt-color-info);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-info-bg);
  color: var(--pyt-color-info);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.6;
}

.feynman-panel__misc-tag {
  flex-shrink: 0;
  padding: 1px 8px;
  border: 1px solid var(--pyt-color-warning);
  border-radius: var(--pyt-radius-full);
  background: var(--pyt-color-warning-bg);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xs);
}

.feynman-panel__concept-badge {
  flex-shrink: 0;
  padding: 2px 10px;
  border: 1px solid var(--pyt-color-info);
  border-radius: var(--pyt-radius-full);
  background: var(--pyt-color-info-bg);
  color: var(--pyt-color-info);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xs);
  white-space: nowrap;
}

.feynman-panel__bar-actions {
  display: flex;
  gap: var(--pyt-space-2);
  margin-left: auto;
}

.feynman-panel__ghost,
.feynman-panel__start,
.feynman-panel__teach-submit {
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  cursor: pointer;
  transition: transform var(--pyt-motion-base) ease;
}

.feynman-panel__ghost {
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
}

.feynman-panel__start,
.feynman-panel__teach-submit {
  background: var(--pyt-color-primary);
  color: var(--pyt-color-text-on-dark);
}

.feynman-panel__chat-send-row,
.feynman-panel__verdict-foot {
  display: flex;
  justify-content: flex-end;
}

.feynman-panel__verdict-foot {
  margin-top: auto;
}

.feynman-panel__send {
  background: #07c160;
  border-color: #06a14a;
}

.feynman-panel__send:hover:not(:disabled) {
  background: #06ad56;
}

.feynman-panel__ghost:hover:not(:disabled),
.feynman-panel__start:hover:not(:disabled),
.feynman-panel__teach-submit:hover:not(:disabled) {
  transform: translateY(-1px);
}

.feynman-panel__ghost:disabled,
.feynman-panel__start:disabled,
.feynman-panel__teach-submit:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.feynman-panel__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pyt-space-3);
  flex-shrink: 0;
  margin: 0;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border: 2px solid var(--pyt-color-danger);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-danger-bg);
  color: var(--pyt-color-danger);
  font-size: var(--pyt-font-size-sm);
}

.feynman-panel__retry {
  flex-shrink: 0;
  border: 2px solid var(--pyt-color-danger);
  border-radius: var(--pyt-radius-sm);
  padding: 2px var(--pyt-space-2);
  background: transparent;
  color: var(--pyt-color-danger);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xs);
  cursor: pointer;
}

.feynman-panel__retry:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.feynman-panel__intro-hint {
  flex-shrink: 0;
  margin: 0;
  padding: var(--pyt-space-3);
  border: 2px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-hint);
}

.feynman-panel__stage {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: var(--pyt-space-3);
}

.feynman-panel__game,
.feynman-panel__flow {
  min-height: 0;
  overflow-y: auto;
}

.feynman-panel__flow {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: var(--pyt-space-3);
  padding-right: 2px;
}

.feynman-panel__card {
  display: grid;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-3);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-soft);
  box-shadow: var(--pyt-shadow-card);
  font-size: var(--pyt-font-size-sm);
}

.feynman-panel__card h3 {
  margin: 0;
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
}

.feynman-panel__muted {
  margin: 0;
  color: var(--pyt-color-text-muted);
  text-align: center;
}

/* ── 微信式聊天气泡 ── */
.feynman-panel__chat {
  min-height: 120px;
  grid-template-rows: auto 1fr auto;
  overflow: hidden;
  background: #ececec;
  border-color: #d8d8d8;
}

.feynman-panel__chat-header {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  margin: calc(-1 * var(--pyt-space-3)) calc(-1 * var(--pyt-space-3)) 0;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  background: #f7f7f7;
  border-bottom: 1px solid #d8d8d8;
}

.feynman-panel__chat-avatar {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: #576b95;
  color: #fff;
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
}

.feynman-panel__chat-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  line-height: 1.2;
}

.feynman-panel__chat-name {
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
}

.feynman-panel__chat-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}

.feynman-panel__chat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #07c160;
}

.feynman-panel__chat-body {
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-2) var(--pyt-space-1);
}

.chat-msg {
  display: flex;
  align-items: flex-start;
  gap: var(--pyt-space-2);
  max-width: 88%;
}

.chat-msg--tutee {
  align-self: flex-start;
}

.chat-msg--me {
  align-self: flex-end;
  flex-direction: row;
}

.chat-msg__avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: #576b95;
  color: #fff;
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
}

.chat-msg--me .chat-msg__avatar {
  background: #07c160;
}

.chat-msg__bubble {
  padding: 8px 12px;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
  line-height: 1.55;
}

.chat-msg--me .chat-msg__bubble {
  background: #95ec69;
}

.chat-msg__bubble p {
  margin: 0;
  word-break: break-word;
}

.chat-msg__fix {
  margin: 6px 0 0 !important;
  padding-top: 6px;
  border-top: 1px dashed rgba(0, 0, 0, 0.12);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-success);
}

.feynman-panel__chat-input {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  padding-top: var(--pyt-space-2);
  border-top: 1px solid #d8d8d8;
  background: #f7f7f7;
  margin: 0 calc(-1 * var(--pyt-space-3)) calc(-1 * var(--pyt-space-3));
  padding: var(--pyt-space-2) var(--pyt-space-3);
}

.feynman-panel__teach-hint {
  margin: 0;
  padding: var(--pyt-space-2);
  border-radius: var(--pyt-radius-sm);
  background: rgba(255, 209, 102, 0.35);
  color: var(--pyt-color-text-hint);
  font-size: var(--pyt-font-size-xs);
  text-align: center;
}

.feynman-panel__system-notice {
  align-self: center;
  margin: 0;
  max-width: 92%;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border: 1px dashed var(--pyt-color-warning);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-text-hint);
  font-size: var(--pyt-font-size-xs);
  line-height: 1.55;
  text-align: center;
}

.feynman-panel__teach-input {
  width: 100%;
  resize: none;
  min-height: 48px;
  border: 1px solid #d8d8d8;
  border-radius: var(--pyt-radius-sm);
  background: #fff;
  color: var(--pyt-color-text-primary);
  padding: var(--pyt-space-2);
  font: inherit;
  line-height: 1.5;
}

.feynman-panel__verdict {
  display: flex;
  flex-direction: column;
  border-color: var(--pyt-color-info);
}

.feynman-panel__code {
  margin: 0;
  max-height: 22vh;
  padding: var(--pyt-space-2);
  overflow: auto;
  border: 2px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-sm);
}

.feynman-panel__judge-msg {
  margin: 0;
  line-height: 1.6;
}

.feynman-panel__judge--passed {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}

.feynman-panel__judge--failed {
  border-color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
}

.feynman-panel__judge--error {
  border-color: var(--pyt-color-warning);
  background: var(--pyt-color-warning-bg);
}

.feynman-panel__judge--na {
  border-color: var(--pyt-color-info);
  background: var(--pyt-color-info-bg);
}

.feynman-panel__judge--pending {
  border-color: var(--pyt-color-warning);
  background: var(--pyt-color-warning-bg);
}

.feynman-panel__judge-hint {
  margin-right: auto;
  color: var(--pyt-color-text-hint);
  font-size: var(--pyt-font-size-xs);
  line-height: 1.4;
}

.feynman-panel__notice {
  margin: 0;
  padding: var(--pyt-space-2);
  border: 1px dashed var(--pyt-color-info);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-info-bg);
  color: var(--pyt-color-text-hint);
  font-size: var(--pyt-font-size-xs);
  line-height: 1.5;
}

@media (max-width: 900px) {
  .feynman-panel {
    height: auto;
  }

  .feynman-panel__stage {
    grid-template-columns: 1fr;
    overflow-y: visible;
  }

  .feynman-panel__game,
  .feynman-panel__flow {
    overflow-y: visible;
  }

  .feynman-panel__code {
    max-height: none;
  }

  .feynman-panel__bar-actions {
    margin-left: 0;
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .feynman-panel__ghost,
  .feynman-panel__start,
  .feynman-panel__teach-submit {
    transition: none;
  }
}

.feynman-panel--reduced-motion .feynman-panel__ghost,
.feynman-panel--reduced-motion .feynman-panel__start,
.feynman-panel--reduced-motion .feynman-panel__teach-submit {
  transition: none;
}
</style>
