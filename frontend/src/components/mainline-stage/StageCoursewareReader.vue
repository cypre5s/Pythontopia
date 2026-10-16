<script setup lang="ts">
/**
 * StageCoursewareReader —— 主线小章节「学」：精读本节 KC 关联的课件页。
 *
 * R-14 改造（推翻旧「跳 /library 外链逐页标记」）：改为站内嵌入 pdfjs 阅读器
 * （复用 `@/components/library/PdfPageView`，pdfjs 动态对象隔离在 `@/lib/pdfjsLoader`），
 * 从本章 PDF 中按课程 page_id 读取对应原始页码，只展示本节 KC 引用的页面。
 *
 * 浏览到末页后，学生可批量标记本节关联页已读；打点仍由后端校验 KC 页 ID 白名单。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import PdfPageView from '@/components/library/PdfPageView.vue';
import StageContextTutor from './StageContextTutor.vue';
import { postPagesRead } from '@/api/mainline';
import { getDocumentPage, listDocumentPages } from '@/api/courseLibrary';
import type { StageTaskProgress } from './stageTypes';

const props = withDefaults(
  defineProps<{
    stageId: number;
    chapter: number;
    /** course_document.id（= 185 + chapter）。 */
    docId: number;
    /** 本节 KC page_refs 对应的课程页 ID（用于计数与批量打点）。 */
    pageIds: number[];
    /** 本节要求阅读的课程页数。 */
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

/** 本节关联的课程页数，也作为本节内翻页上界。 */
const totalPages = computed<number>(() => props.pageIds.length);
/** 当前浏览页在本节 KC 关联页列表中的位置（1-based）。 */
const currentPage = ref<number>(1);
/** 已浏览到的最大页号（到达末页才允许标记完成）。 */
const maxVisited = ref<number>(1);
/** 已读 page_id 集合（本地乐观维护：初始来自后端）。 */
const readPageIdSet = ref<Set<number>>(new Set(props.readPageIds));
const pageNumbersById = ref<Map<number, number>>(new Map());
const pageMapLoading = ref(false);
const pageMapError = ref<string | null>(null);
const markError = ref<string | null>(null);
const pagesMapped = computed<boolean>(
  () =>
    props.pageIds.length > 0
    && props.pageIds.every((pageId) => pageNumbersById.value.has(pageId)),
);
const pdfPageNo = computed<number>(
  () => pageNumbersById.value.get(props.pageIds[currentPage.value - 1] ?? -1) ?? 1,
);

let pageMapRequest = 0;

async function loadPageNumbers(): Promise<void> {
  const requestId = ++pageMapRequest;
  const documentId = props.docId;
  pageNumbersById.value = new Map();
  pageMapError.value = null;
  if (props.pageIds.length === 0) {
    pageMapLoading.value = false;
    return;
  }
  pageMapLoading.value = true;
  try {
    const pages = await listDocumentPages(documentId);
    if (props.docId !== documentId || requestId !== pageMapRequest) return;
    const pageNumbers = new Map(pages.map((page) => [page.pageId, page.pageNo]));
    pageNumbersById.value = pageNumbers;
    if (!props.pageIds.every((pageId) => pageNumbers.has(pageId))) {
      pageMapError.value = '课程页与 PDF 页码未能对应，暂不能标记完成。';
    }
  } catch (error) {
    if (props.docId === documentId && requestId === pageMapRequest) {
      pageNumbersById.value = new Map();
      pageMapError.value = error instanceof Error
        ? `加载课件页码失败：${error.message}`
        : '加载课件页码失败，请重试。';
    }
  } finally {
    if (props.docId === documentId && requestId === pageMapRequest) {
      pageMapLoading.value = false;
    }
  }
}

watch(
  () => [props.stageId, props.docId, props.pageIds],
  () => {
    currentPage.value = 1;
    maxVisited.value = 1;
    readPageIdSet.value = new Set(props.readPageIds);
    markError.value = null;
    void loadPageNumbers();
  },
  { immediate: true },
);

/** 过线计数分母：本节关联页数（requiredPages 缺省 0 时退化为页数）。 */
const requiredCount = computed<number>(() =>
  props.requiredPages > 0 ? props.requiredPages : totalPages.value,
);

/** 已读页数 = 已读页集合与本节 pageIds 的交集（仅本节页计入）。 */
const readCount = computed<number>(
  () => props.pageIds.filter((pageId) => readPageIdSet.value.has(pageId)).length,
);

/** 过线：读满本节关联页。 */
const passed = computed<boolean>(
  () => pagesMapped.value && requiredCount.value > 0 && readCount.value >= requiredCount.value,
);

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

/** 一次性上报本节全部 pageIds（并集去重幂等），本地置已过线并 emit 进度。 */
async function markAllRead(): Promise<void> {
  if (passed.value || marking.value || !pagesMapped.value) return;
  if (!reachedEnd.value) return; // 防御：必须确实翻到末页再标记
  const allIds = Array.from(new Set(props.pageIds));
  markError.value = null;
  marking.value = true;
  try {
    // 只 await 成功；用已知 pageIds 乐观更新（不读返回体，规避 DTO 形状差异）。
    await postPagesRead(props.stageId, allIds);
    readPageIdSet.value = new Set([...readPageIdSet.value, ...allIds]);
    emit('progress', { stageId: props.stageId, readPageIds: [...readPageIdSet.value] });
  } catch (error) {
    markError.value = error instanceof Error
      ? `标记课件已读失败：${error.message}`
      : '标记课件已读失败，请检查网络后重试。';
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
    const detail = await getDocumentPage(props.docId, pdfPageNo.value);
    const body = (detail?.contentMarkdown || detail?.excerpt || '').trim();
    if (!body) return `（第 ${pdfPageNo.value} 页课件，暂无可读文本）`;
    const head = detail?.title ? `【第 ${pdfPageNo.value} 页·${detail.title}】\n` : `【第 ${pdfPageNo.value} 页课件】\n`;
    return (head + body).slice(0, PAGE_CTX_MAX);
  } catch {
    return `（第 ${pdfPageNo.value} 页课件）`;
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
      <span class="stage-courseware__hint">逐页翻阅本节关联课件（支持键盘 ← →）</span>
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

    <p
      v-if="pageMapLoading"
      class="stage-courseware__loading"
      role="status"
      data-testid="stage-courseware-map-loading"
    >
      正在加载课件页码…
    </p>

    <div
      v-else-if="pageMapError"
      class="stage-courseware__error stage-courseware__map-error"
      role="alert"
      data-testid="stage-courseware-map-error"
    >
      <p>{{ pageMapError }}</p>
      <button
        type="button"
        class="stage-courseware__navbtn"
        data-testid="stage-courseware-map-retry"
        @click="loadPageNumbers"
      >
        重试
      </button>
    </div>

    <div
      v-else-if="pagesMapped"
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
            :page-no="pdfPageNo"
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
          <span class="stage-courseware__pageno">课件原页 {{ pdfPageNo }}</span>
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
          <p
            v-if="markError"
            class="stage-courseware__error stage-courseware__mark-error"
            role="alert"
            data-testid="stage-courseware-mark-error"
          >
            {{ markError }}
          </p>
          <button
            v-if="!passed"
            type="button"
            class="stage-courseware__mark"
            data-testid="stage-courseware-mark-all"
            :disabled="!reachedEnd || marking"
            @click="markAllRead"
          >
            {{ marking ? '标记中…' : markError ? '重试标记本节课件已读' : '✅ 标记本节课件已读' }}
          </button>
          <span
            v-else
            class="stage-courseware__done"
            data-testid="stage-courseware-done"
          >本节课件已读完 ✓</span>
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
.stage-courseware__loading,
.stage-courseware__error {
  margin: 0;
  padding: var(--pyt-space-3);
  border-radius: var(--pyt-radius-md);
}
.stage-courseware__loading {
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
}
.stage-courseware__error {
  color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg, var(--pyt-color-bg-soft));
}
.stage-courseware__map-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--pyt-space-2);
  flex-wrap: wrap;
}
.stage-courseware__map-error p {
  margin: 0;
}
.stage-courseware__mark-error {
  flex: 1 1 100%;
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
