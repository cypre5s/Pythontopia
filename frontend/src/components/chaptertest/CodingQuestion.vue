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
/* 学习通考试卷面白纸风：白卡 + 深色代码区（编程题编辑器/样例/输出保留终端深色）。 */
.ct-card {
  display: grid;
  gap: 10px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #e6e8ec;
  border-radius: 4px;
  font-family: var(--pyt-font-read), sans-serif;
}
.ct-card__head { display: flex; align-items: baseline; gap: 6px; font-size: 13px; }
.ct-card__no { font-weight: 700; color: #333; font-size: 14px; }
.ct-card__kind { color: #8a8a8a; }
.ct-card__score { margin-left: auto; color: #9aa0a8; }
.ct-coding__title { margin: 0; font-size: 15px; font-weight: 700; color: #333; }
/* 题面：白纸普通文字（markdown 源文本，保留 pre-wrap）。 */
.ct-stem {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 0;
  background: transparent;
  border: 0;
  color: #333;
  font-family: var(--pyt-font-read), sans-serif;
  font-size: 14px;
  line-height: 1.8;
}
.ct-coding__samples { font-size: 13px; }
.ct-coding__samples summary { cursor: pointer; color: #666; }
.ct-coding__sample { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 8px; }
.ct-coding__io-label { font-size: 12px; color: #9aa0a8; }
.ct-coding__io pre {
  margin: 4px 0 0;
  padding: 8px;
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border: 1px solid #d4d8df;
  border-radius: 4px;
  font-family: var(--pyt-font-mono);
  font-size: 12px;
  white-space: pre-wrap;
}
.ct-coding__editor { display: flex; flex-direction: column; gap: 4px; }
.ct-coding__editor-label { font-size: 13px; color: #666; }
/* 编辑器：终端深色（学习通编程题同款白卡深码区）。 */
.ct-coding__editor textarea {
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-base);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border: 1px solid #d4d8df;
  border-radius: 4px;
  padding: 12px;
  resize: vertical;
  min-height: 180px;
}
.ct-coding__bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.ct-coding__btn {
  font-family: var(--pyt-font-read), sans-serif;
  font-size: 13px;
  min-height: 0;
  padding: 4px 16px;
  background: #3a7bd5;
  color: #fff;
  border: 1px solid #3a7bd5;
  border-radius: 4px;
  box-shadow: none;
  cursor: pointer;
}
.ct-coding__btn:hover:not(:disabled) { background: #2f6cc0; }
.ct-coding__btn:active { transform: none; box-shadow: none; }
.ct-coding__btn:disabled { background: #f0f1f3; border-color: #dcdfe5; color: #9aa0a8; cursor: not-allowed; }
.ct-coding__hint { font-size: 12px; color: #9aa0a8; }
.ct-coding__preview { display: grid; gap: 8px; }
.ct-coding__stdout {
  margin: 0;
  padding: 8px;
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-stdout);
  border: 1px solid #d4d8df;
  border-radius: 4px;
  font-family: var(--pyt-font-mono);
  font-size: 12px;
  white-space: pre-wrap;
}
.ct-coding__stderr {
  margin: 0;
  padding: 8px;
  background: var(--pyt-color-terminal-stderr-bg);
  color: var(--pyt-color-terminal-stderr-text);
  border: 1px solid #e25c4a;
  border-radius: 4px;
  font-family: var(--pyt-font-mono);
  font-size: 12px;
  white-space: pre-wrap;
}
.ct-review {
  padding: 10px 12px;
  border: 1px solid #eef0f3;
  border-radius: 4px;
  font-size: 13px;
  background: #f7f8fa;
}
.ct-review--ok .ct-review__verdict { color: #1fa657; }
.ct-review--bad .ct-review__verdict { color: #e25c4a; }
.ct-review__verdict { margin: 0; font-weight: 700; }
.ct-review__explain { margin: 6px 0 0; color: #666; line-height: 1.7; }
.ct-coding__ref { margin-top: 6px; }
.ct-coding__ref summary { cursor: pointer; color: #666; font-size: 13px; }
.ct-coding__ref pre {
  margin: 8px 0 0;
  padding: 8px;
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border: 1px solid #d4d8df;
  border-radius: 4px;
  font-family: var(--pyt-font-mono);
  font-size: 12px;
  white-space: pre-wrap;
}
</style>
