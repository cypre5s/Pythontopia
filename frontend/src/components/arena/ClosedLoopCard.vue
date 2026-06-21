<script setup lang="ts">
import { computed, ref } from 'vue';
import type { BossProblemDto } from '@/api/dto/Boss';

const props = defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });

// 题面常把「描述 + ```代码``` + 报错 + 样例」混在一段里。按 ``` 代码围栏切成
// 文字段 / 代码段，分别用题面样式 / 代码框渲染，避免代码与题干糊成一坨报错文本。
interface StatementSegment {
  type: 'text' | 'code';
  content: string;
}
// 按行解析，双识别代码块：① ``` 围栏；② 中文「【错误代码】/【代码】…」标签后到下一个
// 【…】标签前的行。两种格式的代码都抽成独立代码框，与题干/样例分开（线上 boss 题多用标签式）。
const FENCE_RE = /^\s*```/;
const CODE_LABEL_RE = /^\s*【\s*(?:错误代码|出错代码|错误的代码|错误程序|有误代码|代码|参考代码|示例代码|程序代码|原代码|原始代码)\s*】\s*$/;
const SECTION_LABEL_RE = /^\s*【[^】]+】/;
const segments = computed<StatementSegment[]>(() => {
  const src = props.problem.statement ?? '';
  const out: StatementSegment[] = [];
  let buf: string[] = [];
  let mode: StatementSegment['type'] = 'text';
  let inFence = false;
  let inCodeSection = false;
  const flush = (): void => {
    const content = buf.join('\n').replace(/^\n+/, '').replace(/\s+$/, '');
    if (content.trim().length > 0) out.push({ type: mode, content });
    buf = [];
  };
  for (const line of src.split('\n')) {
    if (FENCE_RE.test(line)) {
      flush();
      inFence = !inFence;
      inCodeSection = false;
      mode = inFence ? 'code' : 'text';
      continue; // 围栏行本身是分隔符，不渲染
    }
    if (inFence) {
      buf.push(line);
      continue;
    }
    if (CODE_LABEL_RE.test(line)) {
      // 代码标签作为题面小标题留在 prose；其后到下一个【…】标签前的行作为代码段。
      buf.push(line);
      flush();
      mode = 'code';
      inCodeSection = true;
      continue;
    }
    if (inCodeSection && SECTION_LABEL_RE.test(line)) {
      flush();
      mode = 'text';
      inCodeSection = false;
      buf.push(line);
      continue;
    }
    buf.push(line);
  }
  flush();
  // 无任何可分段内容时回退为单个文字段，保证始终有内容渲染。
  return out.length > 0 ? out : [{ type: 'text', content: src.trim() }];
});

const step = ref<'read' | 'locate' | 'fix'>('read');
// 定位笔记单独存放，绝不写进提交的 code（否则「出错原因」散文会被当代码判 → 必败）。
const locateNote = ref('');

function advanceStep(): void {
  if (step.value === 'read') step.value = 'locate';
  else if (step.value === 'locate') step.value = 'fix';
}
</script>

<template>
  <div
    class="closed-loop-card"
    data-testid="closed-loop-card"
  >
    <header class="card-head">
      <span class="card-icon">⟲</span>
      <h3>调试闭环</h3>
      <div class="steps">
        <span :class="['step-dot', { active: step === 'read' }]">1.阅读</span>
        <span class="step-arrow">→</span>
        <span :class="['step-dot', { active: step === 'locate' }]">2.定位</span>
        <span class="step-arrow">→</span>
        <span :class="['step-dot', { active: step === 'fix' }]">3.修复</span>
      </div>
    </header>

    <div
      v-if="step === 'read'"
      class="phase-panel"
    >
      <h4>阶段 1：阅读报错信息</h4>
      <div class="cl-statement">
        <template
          v-for="(seg, i) in segments"
          :key="i"
        >
          <pre
            v-if="seg.type === 'code'"
            class="cl-code"
          ><code>{{ seg.content }}</code></pre>
          <p
            v-else
            class="cl-prose"
            v-text="seg.content"
          />
        </template>
      </div>
      <button
        type="button"
        class="advance-btn"
        @click="advanceStep"
      >
        我看懂了，进入定位 →
      </button>
    </div>

    <div
      v-else-if="step === 'locate'"
      class="phase-panel"
    >
      <h4>阶段 2：定位错误根源</h4>
      <div class="cl-statement cl-statement--dim">
        <template
          v-for="(seg, i) in segments"
          :key="i"
        >
          <pre
            v-if="seg.type === 'code'"
            class="cl-code"
          ><code>{{ seg.content }}</code></pre>
          <p
            v-else
            class="cl-prose"
            v-text="seg.content"
          />
        </template>
      </div>
      <textarea
        v-model="locateNote"
        class="locate-input"
        rows="3"
        placeholder="写出你认为出错的行号和原因，例如：第 5 行 IndexError，因为列表只有 3 个元素…"
      />
      <button
        type="button"
        class="advance-btn"
        @click="advanceStep"
      >
        已定位，开始修复 →
      </button>
    </div>

    <div
      v-else
      class="phase-panel"
    >
      <h4>阶段 3：编写修复代码</h4>
      <textarea
        v-model="code"
        data-testid="closed-loop-fix"
        rows="10"
        placeholder="# 写修复后的完整代码"
        spellcheck="false"
        class="fix-textarea"
      />
    </div>
  </div>
</template>

<style scoped>
.closed-loop-card { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.card-icon { font-size: 20px; }
.card-head h3 { margin: 0; font-size: 16px; color: var(--pyt-color-text-primary); }
.steps { display: flex; align-items: center; gap: 4px; margin-left: auto; font-size: 11px; }
.step-dot { padding: 2px 8px; border-radius: 3px; background: var(--pyt-color-info-bg); color: var(--pyt-color-info); border: 1px solid transparent; }
.step-dot.active { background: var(--pyt-color-warning-bg); color: var(--pyt-color-warning); border-color: var(--pyt-color-gold); }
.step-arrow { color: var(--pyt-color-text-muted); }
.phase-panel { display: grid; gap: 10px; }
.phase-panel h4 { margin: 0; color: var(--pyt-color-text-hint); font-size: 14px; }
/* 题面分段：文字段（描述/报错/输入输出/样例）与代码段分开渲染，不再糊成一坨报错文本。 */
.cl-statement { display: grid; gap: 8px; }
.cl-statement--dim { opacity: 0.62; }
.cl-prose { margin: 0; padding: 8px 10px; white-space: pre-wrap; word-break: break-word; font-size: 13px; line-height: 1.7; color: var(--pyt-color-text-primary); background: var(--pyt-color-bg-soft); border: 1px solid var(--pyt-color-border); border-radius: var(--pyt-radius-sm); }
.cl-code { margin: 0; padding: 10px 12px; overflow-x: auto; white-space: pre; font-family: var(--pyt-font-mono, monospace); font-size: 13px; line-height: 1.6; color: var(--pyt-color-terminal-text); background: var(--pyt-color-terminal-bg); border: 2px solid var(--pyt-color-border-strong); border-radius: var(--pyt-radius-sm); }
.cl-code code { font-family: inherit; background: none; padding: 0; }
.locate-input { font-family: inherit; font-size: 13px; background: var(--pyt-color-terminal-bg); color: var(--pyt-color-terminal-text); border: 2px solid var(--pyt-color-gold); border-radius: var(--pyt-radius-sm); padding: 8px; resize: vertical; }
.advance-btn { align-self: flex-start; font-family: inherit; font-size: 12px; padding: 6px 14px; background: var(--pyt-color-info-bg); color: var(--pyt-color-info); border: 2px solid var(--pyt-color-info); border-radius: 4px; cursor: pointer; box-shadow: 2px 2px 0 rgba(42, 111, 151, 0.35); }
.advance-btn:hover { background: #c2dcee; }
.fix-textarea { font-family: var(--pyt-font-mono, monospace); font-size: 13px; background: var(--pyt-color-terminal-bg); color: var(--pyt-color-terminal-text); border: 2px solid var(--pyt-color-success); border-radius: var(--pyt-radius-sm); padding: 8px; resize: vertical; min-height: 160px; }
</style>
