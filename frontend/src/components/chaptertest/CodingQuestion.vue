<script setup lang="ts">
// 编程题渲染器（去答案：无 hidden_test_cases / reference_solution）。
// 防作弊：编辑器禁用粘贴（onpaste 拦截 + 计数上报 → integrity.paste_blocked）。
// Pyodide 仅作「本地预检」，不计分；正式判题在服务端沙箱（提交后返回 passed/total）。
import { computed, ref } from 'vue';
import { getPyodideRunner } from '@/game/adapters/pyodide';
import type {
  ChapterTestCodingQuestionDto,
  ChapterTestResultItemDto,
} from '@/api/chapterTest';

const props = defineProps<{
  question: ChapterTestCodingQuestionDto;
  index: number;
  reviewItem?: ChapterTestResultItemDto | null;
}>();

const code = defineModel<string>('code', { required: true });
const emit = defineEmits<{ (e: 'paste-blocked'): void }>();

const isReview = computed<boolean>(() => props.reviewItem != null);

const previewRunning = ref<boolean>(false);
const previewStdout = ref<string>('');
const previewStderr = ref<string>('');
const previewRan = ref<boolean>(false);

function onPaste(event: ClipboardEvent): void {
  // 拦截粘贴并计数（§4.3）。仍允许键入，降低「整段贴答案」的收益。
  event.preventDefault();
  emit('paste-blocked');
}

async function runPreview(): Promise<void> {
  if (previewRunning.value) return;
  previewRunning.value = true;
  previewRan.value = true;
  previewStdout.value = '';
  previewStderr.value = '';
  try {
    const runner = getPyodideRunner();
    await runner.init();
    const sample = props.question.samples[0];
    const result = await runner.run(code.value, sample?.input ?? '');
    previewStdout.value = result.stdout;
    previewStderr.value = result.stderr;
  } catch (error) {
    previewStderr.value = error instanceof Error ? error.message : String(error);
  } finally {
    previewRunning.value = false;
  }
}
</script>

<template>
  <section
    class="ct-card"
    data-testid="coding-question"
    :data-qid="question.qid"
  >
    <header class="ct-card__head">
      <span class="ct-card__no">第 {{ index }} 题</span>
      <span class="ct-card__kind">编程题</span>
      <span class="ct-card__score">{{ question.score }} 分</span>
    </header>
    <h3 class="ct-coding__title">
      {{ question.title }}
    </h3>
    <pre class="ct-stem">{{ question.statementMarkdown }}</pre>

    <details
      v-if="question.samples.length"
      class="ct-coding__samples"
    >
      <summary>样例 ({{ question.samples.length }})</summary>
      <div
        v-for="(s, i) in question.samples"
        :key="i"
        class="ct-coding__sample"
      >
        <div class="ct-coding__io">
          <span class="ct-coding__io-label">输入</span>
          <pre>{{ s.input }}</pre>
        </div>
        <div class="ct-coding__io">
          <span class="ct-coding__io-label">输出</span>
          <pre>{{ s.output }}</pre>
        </div>
      </div>
    </details>

    <label class="ct-coding__editor">
      <span class="ct-coding__editor-label">你的代码（Python 3） · 禁止粘贴</span>
      <textarea
        v-model="code"
        :data-testid="`coding-input-${question.qid}`"
        :disabled="isReview"
        rows="12"
        spellcheck="false"
        placeholder="# 在此作答；正式判题在服务端沙箱跑隐藏用例"
        @paste="onPaste"
        @drop.prevent
      />
    </label>

    <div
      v-if="!isReview"
      class="ct-coding__bar"
    >
      <button
        type="button"
        class="ct-coding__btn"
        :data-testid="`coding-preview-${question.qid}`"
        :disabled="previewRunning"
        @click="runPreview"
      >
        {{ previewRunning ? '运行中…' : '本地预检（不计分）' }}
      </button>
      <span class="ct-coding__hint">Pyodide 仅供你本地验证，正式判题以服务端为准。</span>
    </div>

    <div
      v-if="previewRan && !isReview"
      class="ct-coding__preview"
      :data-testid="`coding-preview-out-${question.qid}`"
    >
      <pre
        v-if="previewStdout"
        class="ct-coding__stdout"
      >{{ previewStdout }}</pre>
      <pre
        v-if="previewStderr"
        class="ct-coding__stderr"
      >{{ previewStderr }}</pre>
    </div>

    <div
      v-if="reviewItem"
      class="ct-review"
      :class="reviewItem.correct ? 'ct-review--ok' : 'ct-review--bad'"
      :data-testid="`result-item-${question.qid}`"
    >
      <p class="ct-review__verdict">
        {{ reviewItem.correct ? '✓ 通过' : '✗ 未通过' }}
        · 得分 {{ reviewItem.awarded }}/{{ reviewItem.score }}
        <span v-if="reviewItem.totalTestCases !== undefined">
          · 用例 {{ reviewItem.passedTestCases ?? 0 }}/{{ reviewItem.totalTestCases }}
        </span>
      </p>
      <p class="ct-review__explain">
        {{ reviewItem.explain }}
      </p>
      <details
        v-if="reviewItem.referenceSolution"
        class="ct-coding__ref"
      >
        <summary>参考解</summary>
        <pre>{{ reviewItem.referenceSolution }}</pre>
      </details>
    </div>
  </section>
</template>

<style scoped>
.ct-card {
  display: grid;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-4);
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-sunken) 100%);
  border: 3px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  box-shadow: var(--pyt-shadow-card);
}
.ct-card__head { display: flex; align-items: center; gap: var(--pyt-space-2); font-size: var(--pyt-font-size-sm); }
.ct-card__no { font-weight: 700; color: var(--pyt-color-text-primary); }
.ct-card__kind {
  padding: 2px var(--pyt-space-2);
  background: var(--pyt-color-success-bg);
  border: 1px solid var(--pyt-color-success);
  color: var(--pyt-color-success);
  border-radius: var(--pyt-radius-sm);
}
.ct-card__score { margin-left: auto; color: var(--pyt-color-text-muted); }
.ct-coding__title { margin: 0; font-size: var(--pyt-font-size-lg); color: var(--pyt-color-text-primary); }
/* 题面：终端深色例外。 */
.ct-stem {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  padding: var(--pyt-space-3);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-base);
  line-height: 1.6;
}
.ct-coding__samples { font-size: var(--pyt-font-size-sm); }
.ct-coding__samples summary { cursor: pointer; color: var(--pyt-color-text-hint); }
.ct-coding__sample { display: grid; grid-template-columns: 1fr 1fr; gap: var(--pyt-space-2); margin-top: var(--pyt-space-2); }
.ct-coding__io-label { font-size: var(--pyt-font-size-xs); color: var(--pyt-color-text-muted); }
.ct-coding__io pre {
  margin: var(--pyt-space-1) 0 0;
  padding: var(--pyt-space-2);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border: 1px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-sm);
  white-space: pre-wrap;
}
.ct-coding__editor { display: flex; flex-direction: column; gap: var(--pyt-space-1); }
.ct-coding__editor-label { font-size: var(--pyt-font-size-sm); color: var(--pyt-color-text-hint); }
/* 编辑器：终端深色例外。 */
.ct-coding__editor textarea {
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-base);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  padding: var(--pyt-space-3);
  resize: vertical;
  min-height: 180px;
}
.ct-coding__bar { display: flex; align-items: center; gap: var(--pyt-space-3); flex-wrap: wrap; }
.ct-coding__btn {
  font-family: var(--pyt-font-sans);
  font-size: var(--pyt-font-size-sm);
  padding: var(--pyt-space-2) var(--pyt-space-4);
  background: var(--pyt-color-primary);
  color: var(--pyt-color-text-on-dark);
  border: 2px solid var(--pyt-color-primary-dark);
  border-radius: var(--pyt-radius-sm);
  cursor: pointer;
}
.ct-coding__btn:disabled { opacity: 0.6; cursor: not-allowed; }
.ct-coding__hint { font-size: var(--pyt-font-size-xs); color: var(--pyt-color-text-muted); }
.ct-coding__preview { display: grid; gap: var(--pyt-space-2); }
.ct-coding__stdout {
  margin: 0;
  padding: var(--pyt-space-2);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-stdout);
  border: 1px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-sm);
  white-space: pre-wrap;
}
.ct-coding__stderr {
  margin: 0;
  padding: var(--pyt-space-2);
  background: var(--pyt-color-terminal-stderr-bg);
  color: var(--pyt-color-terminal-stderr-text);
  border: 1px solid var(--pyt-color-danger);
  border-radius: var(--pyt-radius-sm);
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-sm);
  white-space: pre-wrap;
}
.ct-review {
  padding: var(--pyt-space-3);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  font-size: var(--pyt-font-size-sm);
  background: var(--pyt-color-bg-soft);
}
.ct-review--ok { border-color: var(--pyt-color-success); }
.ct-review--bad { border-color: var(--pyt-color-danger); }
.ct-review__verdict { margin: 0 0 var(--pyt-space-2); font-weight: 700; color: var(--pyt-color-text-primary); }
.ct-review__explain { margin: 0 0 var(--pyt-space-2); color: var(--pyt-color-text-hint); line-height: 1.6; }
.ct-coding__ref summary { cursor: pointer; color: var(--pyt-color-text-hint); font-size: var(--pyt-font-size-sm); }
.ct-coding__ref pre {
  margin: var(--pyt-space-2) 0 0;
  padding: var(--pyt-space-2);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border: 1px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-sm);
  white-space: pre-wrap;
}
</style>
