<script setup lang="ts">
/**
 * 共享「做题工作区」：把题面 + 作答收进统一的 OJ 卡片框架（对标 /training 与 EndlessPage 的排布）。
 *
 * - 有 kind（dual_track / closed_loop / debug / bug_mine …）→ 单栏铺 KindRouter 的自带卡（题面+编辑器同卡）；
 * - 无 kind → 两栏「题面 | 通用编辑器」，并展示样例 + 一键复制。
 * 头部 meta（KC / 难度 / 计时 / 额外 chips）、底部操作（actions 插槽）+ 统一 JudgeResultPanel 由本组件统一收口，
 * 供无尽远征 / 快速刷题等复用，避免各页各写一套丑陋的题面拼接。
 */
import { computed } from 'vue';
import { KindRouter } from '@/components/arena';
import PythonCodeEditor from '@/components/training/PythonCodeEditor.vue';
import JudgeResultPanel from '@/components/challenge/JudgeResultPanel.vue';
import { kcDisplayName } from '@/data/kcNames';
import { useToastStore } from '@stores/toast';
import type { BossProblemDto } from '@/api/dto/Boss';
import type { ArenaLevelKind } from '@/types/arena';
import type { ChallengeResult } from '@/api/dto/CodeChallenge';

const props = defineProps<{
  problem: BossProblemDto;
  kind: ArenaLevelKind | null;
  code: string;
  /** 作答区标题（如题型中文名「判题战斗」）。 */
  title: string;
  kcCode?: string | null;
  difficulty?: number;
  elapsedSec?: number;
  aiGenerated?: boolean;
  /** Bug 矿洞题型的倒计时；远征页可放宽，普通刷题保持默认。 */
  bugMineTimeLimitSeconds?: number;
  /** 统一判题结果（驱动 JudgeResultPanel，带 aria-live 播报）。 */
  result?: ChallengeResult | null;
}>();

const emit = defineEmits<{
  (e: 'update:code', value: string): void;
  (e: 'timeout'): void;
}>();

const toast = useToastStore();

const codeProxy = computed<string>({
  get: () => props.code,
  set: (value) => emit('update:code', value),
});

// 有 kind → 单栏铺自带卡；无 kind → 两栏题面|编辑器。
const isTwoPane = computed<boolean>(() => props.kind === null);

const metaParts = computed<string[]>(() => {
  const parts: string[] = [];
  if (props.kcCode) parts.push(kcDisplayName(props.kcCode));
  if (typeof props.difficulty === 'number') parts.push(`难度 ${props.difficulty} / 5`);
  if (typeof props.elapsedSec === 'number') parts.push(`⏱ ${props.elapsedSec}s`);
  return parts;
});

async function copySample(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text ?? '');
    toast.show('已复制到剪贴板', 'success');
  } catch {
    toast.show('复制失败，请手动选择复制', 'error');
  }
}
</script>

<template>
  <article
    class="cw-card"
    :class="{ 'cw-card--two': isTwoPane }"
    data-testid="challenge-workspace"
  >
    <!-- 题面（仅无 kind 的两栏模式：左列独立题面 + 样例） -->
    <aside
      v-if="isTwoPane"
      class="cw-problem"
    >
      <header class="cw-problem__head">
        <h3 class="cw-problem__title">
          {{ title }}
        </h3>
        <span
          class="cw-badge"
          :class="aiGenerated ? 'is-ai' : 'is-golden'"
        >{{ aiGenerated ? 'AI · 5 重审查' : 'Golden 兜底' }}</span>
      </header>
      <div class="cw-meta">
        <span
          v-for="(m, i) in metaParts"
          :key="i"
          class="cw-meta__chip"
        >{{ m }}</span>
        <slot name="chips" />
      </div>
      <pre class="cw-statement">{{ problem.statement }}</pre>
      <section
        v-if="problem.sampleInput || problem.sampleOutput"
        class="cw-samples"
      >
        <h4 class="cw-samples__title">
          输入输出样例
        </h4>
        <div class="cw-sample-grid">
          <div class="cw-sample">
            <span class="cw-sample__label">输入</span>
            <div class="cw-iobox">
              <button
                type="button"
                class="cw-copy"
                title="复制输入"
                @click="copySample(problem.sampleInput ?? '')"
              >
                ⧉
              </button>
              <pre>{{ problem.sampleInput || '(空)' }}</pre>
            </div>
          </div>
          <div class="cw-sample">
            <span class="cw-sample__label">输出</span>
            <div class="cw-iobox">
              <button
                type="button"
                class="cw-copy"
                title="复制输出"
                @click="copySample(problem.sampleOutput ?? '')"
              >
                ⧉
              </button>
              <pre>{{ problem.sampleOutput || '(空)' }}</pre>
            </div>
          </div>
        </div>
      </section>
    </aside>

    <!-- 作答 -->
    <section class="cw-work">
      <header class="cw-work__head">
        <div class="cw-work__title">
          <h3>{{ isTwoPane ? '作答' : title }}</h3>
          <span
            v-if="!isTwoPane && metaParts.length"
            class="cw-work__sub"
          >{{ metaParts.join(' · ') }}</span>
        </div>
        <div
          v-if="!isTwoPane"
          class="cw-chips"
        >
          <slot name="chips" />
        </div>
      </header>
      <div class="cw-work__body">
        <KindRouter
          v-if="kind"
          v-model:code="codeProxy"
          :kind="kind"
          :problem="problem"
          :bug-mine-time-limit-seconds="props.bugMineTimeLimitSeconds ?? 30"
          @timeout="emit('timeout')"
        >
          <pre class="cw-statement">{{ problem.statement }}</pre>
        </KindRouter>
        <div
          v-else
          class="cw-editor-host"
          data-testid="challenge-code"
        >
          <PythonCodeEditor v-model="codeProxy" />
        </div>
      </div>
      <footer class="cw-work__foot">
        <div class="cw-actions">
          <slot name="actions" />
        </div>
        <div
          v-if="result"
          class="cw-result"
          data-testid="challenge-result"
        >
          <JudgeResultPanel
            :result="result"
            mode="judge"
          />
          <slot name="result-extra" />
        </div>
      </footer>
    </section>
  </article>
</template>

<style scoped>
.cw-card {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}
.cw-card--two {
  grid-template-columns: minmax(300px, 0.92fr) minmax(420px, 1.08fr);
}

.cw-problem,
.cw-work {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: rgba(255, 249, 234, 0.94);
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  box-shadow: 0 4px 0 rgba(74, 54, 29, 0.32), 0 10px 28px rgba(60, 41, 18, 0.14);
}

.cw-problem__head,
.cw-work__head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(74, 54, 29, 0.22);
  background: rgba(255, 252, 242, 0.82);
}
.cw-problem__title {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: 18px;
}
.cw-work__title h3 {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: 16px;
}
.cw-work__sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--pyt-color-text-hint);
}
.cw-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 16px 0;
}
.cw-meta__chip {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  background: rgba(241, 216, 150, 0.42);
  border: 1px solid rgba(74, 54, 29, 0.22);
  border-radius: 4px;
  padding: 4px 8px;
}
.cw-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.cw-badge {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
}
.cw-badge.is-ai {
  color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
  border: 1px solid var(--pyt-color-success);
}
.cw-badge.is-golden {
  color: var(--pyt-color-warning);
  background: var(--pyt-color-warning-bg);
  border: 1px solid var(--pyt-color-warning);
}

.cw-problem {
  padding-bottom: 14px;
  overflow-y: auto;
}
.cw-statement {
  margin: 12px 16px 0;
  padding: 12px 14px;
  background: var(--pyt-color-terminal-bg);
  border: 1px solid var(--pyt-color-border-strong);
  border-radius: 6px;
  white-space: pre-wrap;
  font-family: 'Consolas', 'JetBrains Mono', 'Cascadia Code', Menlo, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  color: var(--pyt-color-terminal-text);
}
.cw-samples {
  margin: 14px 16px 0;
}
.cw-samples__title {
  margin: 0 0 8px;
  font-family: var(--pyt-font-read);
  font-size: 15px;
}
.cw-sample-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.cw-sample {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cw-sample__label {
  font-family: var(--pyt-font-read);
  font-size: 13px;
  font-weight: 700;
}
.cw-iobox {
  position: relative;
}
.cw-iobox pre {
  margin: 0;
  padding: 10px 12px;
  min-height: 46px;
  overflow: auto;
  white-space: pre-wrap;
  border-radius: 6px;
  font-family: 'Consolas', 'JetBrains Mono', 'Cascadia Code', Menlo, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.55;
  color: var(--pyt-color-terminal-text);
  background: var(--pyt-color-terminal-bg);
  border: 1px solid var(--pyt-color-border-strong);
}
.cw-copy {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 1;
  padding: 2px 7px;
  font-size: 12px;
  cursor: pointer;
  color: #cfe4f5;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 4px;
  opacity: 0;
  transition: opacity 120ms ease;
}
.cw-iobox:hover .cw-copy,
.cw-copy:focus-visible {
  opacity: 1;
}

.cw-work__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cw-editor-host {
  flex: 1 1 auto;
  min-height: 240px;
  border-radius: 6px;
  overflow: hidden;
}
.cw-work__foot {
  flex: 0 0 auto;
  display: grid;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid rgba(74, 54, 29, 0.22);
  background: rgba(255, 252, 242, 0.7);
}
.cw-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.cw-result {
  display: grid;
  gap: 6px;
}

@media (max-width: 1024px) {
  .cw-card--two {
    grid-template-columns: 1fr;
    grid-auto-rows: minmax(220px, auto);
    overflow-y: auto;
  }
}
</style>
