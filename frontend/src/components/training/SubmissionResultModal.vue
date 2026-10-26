<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { getSubmissionPerformance } from '@/api/submissions';
import type { SubmissionDetailDto, SubmissionPerformanceDto, Verdict } from '@/api/dto/Submission';

const props = defineProps<{
  open: boolean;
  detail: SubmissionDetailDto;
  canOpenBugMine?: boolean;
}>();

const emit = defineEmits<{
  close: [];
  openBugMine: [];
}>();

const performance = ref<SubmissionPerformanceDto | null>(null);
const isAccepted = computed(() => props.detail.judgeResult?.verdict === 'AC');
const verdict = computed<Verdict>(() => props.detail.judgeResult?.verdict ?? 'SYSTEM_ERROR');
const verdictLabel = computed(() => ({
  AC: '答案通过',
  WA: '答案错误',
  RE: '运行错误',
  TLE: '运行超时',
  CE: '编译错误',
  MLE: '内存超限',
  SYSTEM_ERROR: '判题服务异常',
})[verdict.value]);
const submittedAt = computed(() => {
  const date = new Date(props.detail.submission.submittedAt);
  return Number.isNaN(date.getTime()) ? '时间不可用' : date.toLocaleString('zh-CN');
});
const language = computed(() => props.detail.submission.language.replaceAll('_', ' '));
const runtimeLabel = computed(() => {
  const value = props.detail.submission.runtimeMs;
  return value === null ? '—' : `${value} ms`;
});
const memoryLabel = computed(() => {
  const value = props.detail.submission.memoryKb;
  return value === null ? '—' : `${value} KB`;
});
const passedCases = computed(() => props.detail.judgeResult?.passedCases ?? 0);
const totalCases = computed(() => props.detail.judgeResult?.totalCases ?? 0);
const failureReason = computed(() => props.detail.judgeResult?.failureReason?.trim() ?? '');

let previousFocus: HTMLElement | null = null;
let previousOverflow = '';
let performanceRequestId = 0;

function close(): void {
  emit('close');
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault();
    close();
    return;
  }
  if (event.key !== 'Tab') return;

  const dialog = document.querySelector<HTMLElement>('[data-testid="submission-result-modal"]');
  const focusable = Array.from(
    dialog?.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ) ?? [],
  );
  if (focusable.length === 0) {
    event.preventDefault();
    dialog?.focus();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const activeIndex = focusable.indexOf(document.activeElement as HTMLElement);
  if (activeIndex === -1 || (event.shiftKey && activeIndex === 0)) {
    event.preventDefault();
    (event.shiftKey ? last : first)?.focus();
  } else if (!event.shiftKey && activeIndex === focusable.length - 1) {
    event.preventDefault();
    first?.focus();
  }
}

watch(
  () => [props.open, props.detail.submission.id] as const,
  async ([isOpen, submissionId], previous) => {
    const requestId = ++performanceRequestId;
    performance.value = null;
    const wasOpen = previous?.[0] ?? false;
    if (!isOpen) {
      if (wasOpen) {
        window.removeEventListener('keydown', onKeydown);
        document.body.style.overflow = previousOverflow;
        previousFocus?.focus();
        previousFocus = null;
      }
      return;
    }

    if (!wasOpen) {
      previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      previousOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      window.addEventListener('keydown', onKeydown);
    }
    await nextTick();
    document.querySelector<HTMLButtonElement>('[data-testid="submission-result-close"]')?.focus();
    if (isAccepted.value) {
      try {
        const result = await getSubmissionPerformance(submissionId);
        if (requestId === performanceRequestId && props.open) performance.value = result;
      } catch {
        if (requestId === performanceRequestId) performance.value = null;
      }
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown);
  document.body.style.overflow = previousOverflow;
});
</script>

<template>
  <Teleport to="body">
    <Transition name="submission-modal">
      <div
        v-if="open"
        class="submission-modal__backdrop"
        data-testid="submission-result-backdrop"
        @click.self="close"
      >
        <section
          class="submission-modal"
          :class="isAccepted ? 'submission-modal--accepted' : 'submission-modal--failed'"
          role="dialog"
          aria-modal="true"
          aria-labelledby="submission-result-title"
          tabindex="-1"
          data-testid="submission-result-modal"
        >
          <header class="submission-modal__header">
            <div class="submission-modal__eyebrow">
              <span class="submission-modal__eyebrow-dot" />
              正式判题结果
            </div>
            <button
              type="button"
              class="submission-modal__close"
              aria-label="关闭判题结果"
              data-testid="submission-result-close"
              @click="close"
            >
              <svg
                viewBox="0 0 20 20"
                aria-hidden="true"
              >
                <path d="m5 5 10 10M15 5 5 15" />
              </svg>
            </button>
          </header>

          <div class="submission-modal__hero">
            <div
              class="submission-modal__verdict-icon"
              aria-hidden="true"
            >
              <svg
                v-if="isAccepted"
                viewBox="0 0 32 32"
              >
                <path d="m7 17 6 6L25 10" />
              </svg>
              <svg
                v-else
                viewBox="0 0 32 32"
              >
                <path d="M10 10 22 22M22 10 10 22" />
              </svg>
            </div>
            <div>
              <h2 id="submission-result-title">
                {{ verdictLabel }}
              </h2>
              <p>
                {{ detail.judgeResult?.verdict ?? 'UNKNOWN' }}
                <span class="submission-modal__separator">/</span>
                {{ passedCases }} / {{ totalCases }} 个测试用例通过
              </p>
            </div>
          </div>

          <div class="submission-modal__meta">
            <span><b>提交时间</b>{{ submittedAt }}</span>
            <span><b>语言</b>{{ language }}</span>
            <span><b>提交编号</b>#{{ detail.submission.id }}</span>
          </div>

          <div class="submission-modal__metrics">
            <article class="metric-card">
              <div class="metric-card__label">
                <span>运行时间</span>
                <span
                  class="metric-card__glyph"
                  aria-hidden="true"
                >◷</span>
              </div>
              <strong>{{ runtimeLabel }}</strong>
              <div
                v-if="isAccepted && performance?.runtimeBeatsPercent !== null && performance?.runtimeBeatsPercent !== undefined"
                class="metric-card__rank"
              >
                <div class="metric-card__rank-heading">
                  <span>快于 {{ performance.runtimeBeatsPercent }}%</span>
                  <span>{{ performance.runtimeSampleCount }} 条 AC 提交</span>
                </div>
                <div
                  class="metric-card__bar"
                  role="progressbar"
                  :aria-valuenow="performance.runtimeBeatsPercent"
                  aria-valuemin="0"
                  aria-valuemax="100"
                  aria-label="运行时间百分位"
                >
                  <span :style="{ width: `${performance.runtimeBeatsPercent}%` }" />
                </div>
              </div>
              <small v-else-if="detail.submission.runtimeMs === null">判题器未返回运行时间</small>
              <small v-else>本次实测</small>
            </article>

            <article class="metric-card">
              <div class="metric-card__label">
                <span>内存占用</span>
                <span
                  class="metric-card__glyph"
                  aria-hidden="true"
                >▧</span>
              </div>
              <strong>{{ memoryLabel }}</strong>
              <div
                v-if="isAccepted && performance?.memoryBeatsPercent !== null && performance?.memoryBeatsPercent !== undefined"
                class="metric-card__rank"
              >
                <div class="metric-card__rank-heading">
                  <span>少于 {{ performance.memoryBeatsPercent }}%</span>
                  <span>{{ performance.memorySampleCount }} 条 AC 提交</span>
                </div>
                <div
                  class="metric-card__bar"
                  role="progressbar"
                  :aria-valuenow="performance.memoryBeatsPercent"
                  aria-valuemin="0"
                  aria-valuemax="100"
                  aria-label="内存占用百分位"
                >
                  <span :style="{ width: `${performance.memoryBeatsPercent}%` }" />
                </div>
              </div>
              <small v-else-if="detail.submission.memoryKb === null">判题器未返回峰值内存</small>
              <small v-else>本次实测</small>
            </article>
          </div>

          <p
            v-if="!isAccepted && failureReason"
            class="submission-modal__reason"
            data-testid="submission-failure-reason"
          >
            <span
              class="submission-modal__reason-mark"
              aria-hidden="true"
            >!</span>
            {{ failureReason }}
          </p>

          <footer class="submission-modal__footer">
            <span>判题记录已保存</span>
            <div class="submission-modal__actions">
              <button
                v-if="canOpenBugMine && !isAccepted && verdict !== 'CE' && verdict !== 'SYSTEM_ERROR'"
                type="button"
                class="submission-modal__secondary"
                data-testid="submission-open-bug-mine"
                @click="emit('openBugMine')"
              >
                进入 Bug 矿洞
              </button>
              <button
                type="button"
                class="submission-modal__primary"
                data-testid="submission-result-done"
                @click="close"
              >
                知道了
              </button>
            </div>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.submission-modal__backdrop {
  position: fixed;
  z-index: 1600;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(26, 33, 28, 0.58);
  backdrop-filter: blur(5px);
}

.submission-modal {
  position: relative;
  width: min(100%, 660px);
  max-height: min(88vh, 760px);
  overflow: auto;
  padding: clamp(22px, 5vw, 38px);
  border: 1px solid rgba(85, 79, 53, 0.34);
  border-radius: 18px;
  background:
    radial-gradient(circle at 94% 4%, rgba(227, 205, 152, 0.34), transparent 30%),
    linear-gradient(160deg, #fffdf6 0%, #f6f0df 100%);
  box-shadow: 0 28px 90px rgba(11, 22, 15, 0.3), inset 0 1px #fff;
  color: #26342a;
}

.submission-modal::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 5px;
  border-radius: 18px 0 0 18px;
  background: #4f9966;
  content: '';
}

.submission-modal--failed::before {
  background: #c76551;
}

.submission-modal__header,
.submission-modal__eyebrow,
.submission-modal__hero,
.submission-modal__meta,
.submission-modal__footer,
.submission-modal__actions,
.metric-card__label,
.metric-card__rank-heading {
  display: flex;
  align-items: center;
}

.submission-modal__header,
.submission-modal__footer,
.metric-card__rank-heading {
  justify-content: space-between;
}

.submission-modal__eyebrow {
  gap: 9px;
  color: #6d755f;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.submission-modal__eyebrow-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: #7c976a;
  box-shadow: 0 0 0 3px rgba(124, 151, 106, 0.15);
}

.submission-modal__close {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 1px solid rgba(77, 83, 66, 0.18);
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.52);
  color: #58604e;
  cursor: pointer;
}

.submission-modal__close svg {
  width: 18px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-width: 1.8;
}

.submission-modal__hero {
  gap: 18px;
  margin: 27px 0 22px;
}

.submission-modal__verdict-icon {
  display: grid;
  width: 62px;
  height: 62px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid rgba(57, 119, 74, 0.2);
  border-radius: 18px;
  background: #e1f0dd;
  color: #31814b;
}

.submission-modal--failed .submission-modal__verdict-icon {
  border-color: rgba(186, 82, 64, 0.2);
  background: #f9e3d9;
  color: #bd5847;
}

.submission-modal__verdict-icon svg {
  width: 34px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 3;
}

.submission-modal__hero h2 {
  margin: 0;
  color: #26342a;
  font-size: clamp(25px, 5vw, 34px);
  font-weight: 850;
  letter-spacing: -0.04em;
}

.submission-modal__hero p {
  margin: 6px 0 0;
  color: #778071;
  font-size: 14px;
}

.submission-modal__separator {
  padding: 0 5px;
  color: #b5b8ab;
}

.submission-modal__meta {
  flex-wrap: wrap;
  gap: 9px;
  margin-bottom: 16px;
}

.submission-modal__meta span {
  display: grid;
  min-width: 0;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid rgba(87, 88, 67, 0.12);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.5);
  color: #525b50;
  font-size: 12px;
}

.submission-modal__meta b {
  color: #8a907f;
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.04em;
}

.submission-modal__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px;
}

.metric-card {
  min-height: 154px;
  padding: 17px;
  border: 1px solid rgba(85, 93, 69, 0.15);
  border-radius: 13px;
  background: rgba(255, 255, 255, 0.67);
  box-shadow: 0 5px 16px rgba(53, 58, 38, 0.035);
}

.metric-card__label {
  justify-content: space-between;
  color: #707968;
  font-size: 13px;
  font-weight: 700;
}

.metric-card__glyph {
  color: #829171;
  font-size: 20px;
}

.metric-card strong {
  display: block;
  margin: 13px 0 6px;
  color: #28382d;
  font-size: clamp(24px, 5vw, 31px);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.04em;
}

.metric-card small {
  color: #8c9285;
  font-size: 11px;
}

.metric-card__rank {
  margin-top: 11px;
}

.metric-card__rank-heading {
  margin-bottom: 6px;
  color: #4a7d58;
  font-size: 11px;
  font-weight: 750;
}

.metric-card__rank-heading span:last-child {
  color: #919985;
  font-size: 10px;
  letter-spacing: 0.08em;
}

.metric-card__bar {
  height: 6px;
  overflow: hidden;
  border-radius: 10px;
  background: #e9ecdf;
}

.metric-card__bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #a2c587, #4b9662);
}

.submission-modal__reason {
  display: flex;
  gap: 10px;
  margin: 17px 0 0;
  padding: 13px 15px;
  border: 1px solid rgba(196, 111, 82, 0.2);
  border-radius: 11px;
  background: rgba(255, 239, 229, 0.68);
  color: #765a4f;
  font-size: 13px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.submission-modal__reason-mark {
  display: grid;
  width: 19px;
  height: 19px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: #e2a18b;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.submission-modal__footer {
  gap: 14px;
  margin-top: 22px;
  padding-top: 17px;
  border-top: 1px solid rgba(89, 94, 75, 0.13);
  color: #8a9181;
  font-size: 11px;
}

.submission-modal__actions {
  gap: 9px;
}

.submission-modal__secondary,
.submission-modal__primary {
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid transparent;
  border-radius: 9px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 750;
}

.submission-modal__secondary {
  border-color: rgba(179, 101, 78, 0.27);
  background: #fff5ec;
  color: #a75441;
}

.submission-modal__primary {
  background: #477c55;
  box-shadow: 0 4px 0 #315b3c;
  color: #fffdf5;
}

.submission-modal--failed .submission-modal__primary {
  background: #7c8065;
  box-shadow: 0 4px 0 #5a604b;
}

.submission-modal__close:focus-visible,
.submission-modal__secondary:focus-visible,
.submission-modal__primary:focus-visible {
  outline: 3px solid rgba(88, 143, 98, 0.45);
  outline-offset: 3px;
}

.submission-modal-enter-active,
.submission-modal-leave-active {
  transition: opacity 170ms ease, transform 170ms ease;
}

.submission-modal-enter-from,
.submission-modal-leave-to {
  opacity: 0;
}

.submission-modal-enter-from .submission-modal,
.submission-modal-leave-to .submission-modal {
  transform: translateY(9px) scale(0.985);
}

@media (max-width: 540px) {
  .submission-modal__backdrop {
    align-items: end;
    padding: 8px;
  }

  .submission-modal {
    width: 100%;
    max-height: 92vh;
    padding: 22px 18px 18px 22px;
    border-radius: 17px 17px 12px 12px;
  }

  .submission-modal__hero {
    gap: 14px;
    margin: 21px 0 17px;
  }

  .submission-modal__verdict-icon {
    width: 52px;
    height: 52px;
    border-radius: 14px;
  }

  .submission-modal__meta {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .submission-modal__meta span:first-child {
    grid-column: 1 / -1;
  }

  .submission-modal__metrics {
    gap: 9px;
  }

  .metric-card {
    min-height: 142px;
    padding: 12px;
  }

  .submission-modal__footer {
    align-items: start;
    flex-direction: column;
  }

  .submission-modal__actions {
    width: 100%;
    justify-content: flex-end;
  }
}

@media (prefers-reduced-motion: reduce) {
  .submission-modal-enter-active,
  .submission-modal-leave-active {
    transition: none;
  }
}
</style>
