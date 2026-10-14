<script setup lang="ts">
/**
 * StageCoursewareReader —— 主线小章节「学」：站内内置 PPT（PDF）整章精读。
 *
 * R-14 改造（推翻旧「跳 /library 外链逐页标记」）：改为站内嵌入 pdfjs 阅读器
 * （复用 `@/components/library/PdfPageView`，pdfjs 动态对象隔离在 `@/lib/pdfjsLoader`），
 * 按【整章】展示本章 PPT（course_document id = 185 + chapter，即 docId）。
 *
 * 看完判定：整章页数可能很多（最多 116 页），逐页打点不现实 → 学生用「上一页 /
 * 下一页 / 末页」在章内翻阅；浏览到末页后启用「✅ 标记整章课件已读」按钮，点击
 * 一次性批量上报本章全部 pageIds（`postPagesRead`，后端并集去重幂等），本地置
 * 「已过线 ✓」并向上 emit 进度。过线 = 读满整章（readPageIds ∩ pageIds 数 ≥ requiredPages）。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import PdfPageView from '@/components/library/PdfPageView.vue';
import StageContextTutor from './StageContextTutor.vue';
import { postPagesRead } from '@/api/mainline';
import { getDocumentPage } from '@/api/courseLibrary';
import type { StageTaskProgress } from './stageTypes';

const props = withDefaults(
  defineProps<{
    stageId: number;
    chapter: number;
    /** course_document.id（= 185 + chapter）。 */
    docId: number;
    /** 本章文档全部页 id（用于计数与批量打点）。 */
    pageIds: number[];
    /** 本章页数（全部看完过线）。 */
    requiredPages: number;
    /** 已读页 id。 */
    readPageIds: number[];
    /** 本节知识点（传给锚定问答，收敛课件 RAG 检索范围）。 */
    kcCodes?: string[];
    /**
     * 是否为学习页当前激活的 tab。三件套用 v-show 常驻挂载，
     * 键盘翻页快捷键只在本组件可见时响应，避免在「看/练」里误翻页。
     */
    active?: boolean;
  }>(),
  { active: true, kcCodes: () => [] },
);

const emit = defineEmits<{ (e: 'progress', p: StageTaskProgress): void }>();

/** 本章 PPT 总页数（= 关联 pageIds 数，与 PDF numPages 对齐，作章内翻页上界）。 */
const totalPages = computed<number>(() => props.pageIds.length);

/** 已读 page_id 集合（本地乐观维护：初始来自后端，标记整章后并入全部 pageIds）。 */
const readSet = ref<Set<number>>(new Set(props.readPageIds));

/** 过线计数分母：本章页数（requiredPages 缺省 0 时退化为页数）。 */
const requiredCount = computed<number>(() =>
  props.requiredPages > 0 ? props.requiredPages : totalPages.value,
);

/** 已读页数 = readSet 与本章 pageIds 的交集数（仅本章页计入）。 */
const readCount = computed<number>(() => props.pageIds.filter((id) => readSet.value.has(id)).length);

/** 过线：读满整章。 */
const passed = computed<boolean>(
  () => requiredCount.value > 0 && readCount.value >= requiredCount.value,
);

/** 当前浏览页（1-based，章内页号，驱动 PdfPageView）。 */
const currentPage = ref<number>(1);
/** 已浏览到的最大页号（到达末页才允许标记完成）。 */
const maxVisited = ref<number>(1);
/** 是否已翻到/浏览到末页。 */
const reachedEnd = computed<boolean>(
  () => totalPages.value > 0 && maxVisited.value >= totalPages.value,
);

/** 批量上报进行中（防重复点击）。 */
const marking = ref<boolean>(false);

function goTo(page: number): void {
  if (totalPages.value === 0) return;
  const clamped = Math.min(Math.max(1, page), totalPages.value);
  currentPage.value = clamped;
  if (clamped > maxVisited.value) {
    maxVisited.value = clamped;
  }
}

function goPrev(): void {
  goTo(currentPage.value - 1);
}

function goNext(): void {
  goTo(currentPage.value + 1);
}

function goLast(): void {
  goTo(totalPages.value);
}

/** 一次性上报本章全部 pageIds（并集去重幂等），本地置已过线并 emit 进度。 */
async function markAllRead(): Promise<void> {
  if (passed.value || marking.value || props.pageIds.length === 0) return;
  if (!reachedEnd.value) return; // 防御：必须确实翻到末页再标记
  const allIds = Array.from(new Set(props.pageIds));
  marking.value = true;
  try {
    // 只 await 成功；用已知 pageIds 乐观更新（不读返回体，规避 DTO 形状差异）。
    await postPagesRead(props.stageId, allIds);
    readSet.value = new Set([...readSet.value, ...allIds]);
    emit('progress', { stageId: props.stageId, readPageIds: [...readSet.value] });
  } catch {
    // 打点失败静默：用户可再次点击重试（服务端幂等）。
  } finally {
    marking.value = false;
  }
}

// ── 键盘翻页：← / → 上下页，End 末页（仅本 tab 激活、且焦点不在输入框时）──
function onKeydown(event: KeyboardEvent): void {
  if (!props.active || totalPages.value === 0) return;
  const target = event.target as HTMLElement | null;
  const tag = target?.tagName ?? '';
  if (tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable) return;
  if (event.key === 'ArrowLeft') {
    event.preventDefault();
    goPrev();
  } else if (event.key === 'ArrowRight') {
    event.preventDefault();
    goNext();
  } else if (event.key === 'End') {
    event.preventDefault();
    goLast();
  }
}

onMounted(() => {
  if (typeof window !== 'undefined') window.addEventListener('keydown', onKeydown);
});
onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('keydown', onKeydown);
});

// ── 锚定当前页的「百晓助教」问答（R-15 反馈：升级原关键词检索）──
// 把当前正在看的这一页 PPT 文本作为 context 喂给 /api/tutor/ask，让助教紧扣这一页作答。
// contextProvider 是函数：提交问答的「此刻」才懒取当前页文本（翻页后自动变），失败回退空串。
const ctxKcCodes = computed<string[]>(() => props.kcCodes ?? []);
const PAGE_CTX_MAX = 1600;

async function currentPageContext(): Promise<string> {
  try {
    const detail = await getDocumentPage(props.docId, currentPage.value);
    const body = (detail?.contentMarkdown || detail?.excerpt || '').trim();
    if (!body) return `（第 ${currentPage.value} 页课件，暂无可读文本）`;
    const head = detail?.title ? `【第 ${currentPage.value} 页·${detail.title}】\n` : `【第 ${currentPage.value} 页课件】\n`;
    return (head + body).slice(0, PAGE_CTX_MAX);
  } catch {
    return `（第 ${currentPage.value} 页课件）`;
  }
}
</script>

<template>
  <section
    class="stage-courseware"
    data-testid="stage-courseware"
  >
    <header class="stage-courseware__head">
      <h3 class="stage-courseware__title">
        <span
          class="stage-courseware__icon"
          aria-hidden="true"
        >📖</span> 学 · 课件精读
      </h3>
      <span class="stage-courseware__hint">逐页翻阅（支持键盘 ← →），浏览到末页后可标记整章已读</span>
      <span
        v-if="passed"
        class="stage-courseware__passed"
        data-testid="stage-courseware-passed"
      >已过线 ✓</span>
      <span
        v-else
        class="stage-courseware__count"
        data-testid="stage-courseware-count"
      >已读 {{ readCount }} / {{ requiredCount }} 页</span>
    </header>

    <div
      v-if="pageIds.length > 0"
      class="stage-courseware__body"
    >
      <!-- 左：PDF 单屏自适应 + 紧凑翻页工具条（页面不再上下滚动） -->
      <div class="stage-courseware__reader">
        <div
          class="stage-courseware__viewer"
          data-testid="stage-courseware-viewer"
        >
          <PdfPageView
            :doc-id="docId"
            :page-no="currentPage"
            fit="contain"
          />
        </div>

        <div class="stage-courseware__nav">
          <button
            type="button"
            class="stage-courseware__navbtn"
            data-testid="stage-courseware-prev"
            :disabled="currentPage <= 1"
            @click="goPrev"
          >
            ‹ 上一页
          </button>
          <span
            class="stage-courseware__pageno"
            data-testid="stage-courseware-pageno"
          >第 {{ currentPage }} / {{ totalPages }} 页</span>
          <button
            type="button"
            class="stage-courseware__navbtn"
            data-testid="stage-courseware-next"
            :disabled="currentPage >= totalPages"
            @click="goNext"
          >
            下一页 ›
          </button>
          <button
            type="button"
            class="stage-courseware__navbtn stage-courseware__navbtn--ghost"
            data-testid="stage-courseware-last"
            :disabled="currentPage >= totalPages"
            @click="goLast"
          >
            末页 ⇥
          </button>

          <span class="stage-courseware__nav-spacer" />

          <span
            v-if="!passed && !reachedEnd"
            class="stage-courseware__tip"
          >翻到最后一页后可标记 ✓</span>
          <button
            v-if="!passed"
            type="button"
            class="stage-courseware__mark"
            data-testid="stage-courseware-mark-all"
            :disabled="!reachedEnd || marking"
            @click="markAllRead"
          >
            {{ marking ? '标记中…' : '✅ 标记整章课件已读' }}
          </button>
          <span
            v-else
            class="stage-courseware__done"
            data-testid="stage-courseware-done"
          >本章课件已读完 ✓</span>
        </div>
      </div>

      <!-- 右：锚定当前页的「百晓助教」问答（基于这一页 PPT 内容作答） -->
      <StageContextTutor
        class="stage-courseware__tutor"
        :kc-codes="ctxKcCodes"
        scope-label="这一页课件"
        :context-provider="currentPageContext"
      />
    </div>

    <p
      v-else
      class="stage-courseware__empty"
    >
      本小节暂未关联课件。
    </p>
  </section>
</template>

<style scoped>
.stage-courseware {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  height: 100%;
  min-height: 0;
}
.stage-courseware__head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
}
.stage-courseware__title {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-lg);
  color: var(--pyt-color-text-primary);
  white-space: nowrap;
}
.stage-courseware__icon {
  color: var(--pyt-color-info);
}
.stage-courseware__hint {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage-courseware__passed {
  flex: 0 0 auto;
  font-size: var(--pyt-font-size-xs);
  padding: 2px 8px;
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  white-space: nowrap;
}
.stage-courseware__count {
  flex: 0 0 auto;
  font-size: var(--pyt-font-size-xs);
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
  white-space: nowrap;
}

/* ── 单屏两栏：左 PDF（自适应高度）+ 右锚定问答 ── */
.stage-courseware__body {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(240px, 28%, 330px);
  gap: var(--pyt-space-3);
  align-items: stretch;
}
.stage-courseware__reader {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
}
.stage-courseware__viewer {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: var(--pyt-space-1);
  background: var(--pyt-color-bg-sunken);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  box-shadow: inset 0 0 0 2px var(--pyt-color-bg-soft);
}
.stage-courseware__viewer :deep(.pdf-page-view) {
  width: 100%;
  height: 100%;
  justify-content: center;
}
/* 页码已在工具条显示，contain 单屏下隐藏组件内置 meta 行省高度 */
.stage-courseware__viewer :deep(.state--meta) {
  display: none;
}

/* 紧凑单行工具条：小号翻页键 + 页码 + 标记按钮，全部一行收纳 */
.stage-courseware__nav {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
}
.stage-courseware__navbtn {
  font-family: var(--pyt-font-read), sans-serif;
  font-size: var(--pyt-font-size-xs);
  line-height: 1.4;
  min-height: 0;
  padding: 2px 8px;
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  box-shadow: none;
  color: var(--pyt-color-text-primary);
  cursor: pointer;
}
.stage-courseware__navbtn:active,
.stage-courseware__mark:active {
  transform: none;
  box-shadow: none;
}
.stage-courseware__navbtn:hover:not(:disabled) {
  border-color: var(--pyt-color-gold);
  background: var(--pyt-color-warning-bg);
}
.stage-courseware__navbtn--ghost {
  background: var(--pyt-color-bg-soft);
}
.stage-courseware__navbtn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.stage-courseware__pageno {
  font-size: var(--pyt-font-size-xs);
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
  min-width: 8ch;
  text-align: center;
}
.stage-courseware__nav-spacer {
  flex: 1 1 auto;
}
.stage-courseware__mark {
  font-family: var(--pyt-font-read), sans-serif;
  font-size: var(--pyt-font-size-xs);
  min-height: 0;
  padding: 2px 10px;
  background: var(--pyt-color-success-bg);
  border: 1px solid var(--pyt-color-success);
  border-radius: var(--pyt-radius-sm);
  box-shadow: none;
  color: var(--pyt-color-success);
  cursor: pointer;
}
.stage-courseware__mark:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--pyt-color-bg-elevated);
  border-color: var(--pyt-color-border);
  color: var(--pyt-color-text-muted);
}
.stage-courseware__done {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-success);
  white-space: nowrap;
}
.stage-courseware__tip {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
  white-space: nowrap;
}
.stage-courseware__tutor {
  min-height: 0;
}
.stage-courseware__empty {
  margin: 0;
  padding: var(--pyt-space-3);
  text-align: center;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  border: 1px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}

@media (max-width: 900px) {
  .stage-courseware {
    height: auto;
  }
  .stage-courseware__body {
    grid-template-columns: minmax(0, 1fr);
  }
  .stage-courseware__viewer {
    flex: 0 0 auto;
    height: 56vh;
  }
  .stage-courseware__tutor {
    min-height: 220px;
  }
}
</style>
