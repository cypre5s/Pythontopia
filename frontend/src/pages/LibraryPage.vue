<script setup lang="ts">
// LibraryPage —— Pythontopia 校园图书馆 / 课件阅读器（packId=43）。
// 三栏可拖拽分栏（左导航 | 中阅读 | 右检索），宽度记忆在 localStorage。
// URL query 同步：?docId=&page=&kcCode=
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PdfPageView from '@/components/library/PdfPageView.vue';
import PyButton from '@/components/ui/PyButton.vue';
import { useToastStore } from '@stores/toast';
import {
  getDocumentPage,
  listAllExamples,
  listAllProblems,
  listChapterFallbacks,
  listCourseDocuments,
  listDocumentPages,
  listKcFallbacks,
  searchCourseLibrary,
} from '@/api/courseLibrary';
import { listChapters, listKnowledgeComponents } from '@/api/course';
import { listChapterVideos, videoStreamUrl, type CourseVideoDto } from '@/api/courseVideo';
import type {
  CourseDocumentSummary,
  CourseExampleSummary,
  CourseKcSummary,
  CoursePageDetail,
  CourseProblemSummary,
  LibrarySearchHit,
} from '@/api/dto/Library';
import type { ChapterDto } from '@/api/dto/ChapterDto';
import type { KcDto } from '@/api/dto/KcDto';
import { useLibrarySplitLayout } from '@/composables/useLibrarySplitLayout';
import { chapterTitleLabel } from '@/utils/chapterTitleLabel';

const { bodyRef, onSplitterPointerDown, paneStyle } = useLibrarySplitLayout();

// R-13 优化：默认显示原 PDF；第二视图由「文字摘录」改为「课件视频」。
// localStorage 记忆用户偏好，避免每次都要切回 PDF。
type PageViewMode = 'pdf' | 'video';
const VIEW_MODE_STORAGE_KEY = 'pyt_library_view_mode';
function readPreferredViewMode(): PageViewMode {
  if (typeof window === 'undefined') return 'pdf';
  try {
    const raw = window.localStorage.getItem(VIEW_MODE_STORAGE_KEY);
    return raw === 'video' ? 'video' : 'pdf';
  } catch {
    return 'pdf';
  }
}
function persistViewMode(mode: PageViewMode): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, mode);
  } catch {
    /* private mode */
  }
}

interface ChapterGroup {
  chapter: ChapterDto;
  kcs: CourseKcSummary[];
  documentId: number | null;
  pageCount: number | null;
}

const route = useRoute();
const router = useRouter();
const toast = useToastStore();

const documents = ref<CourseDocumentSummary[]>([]);
const chapters = ref<ChapterDto[]>([]);
const kcs = ref<CourseKcSummary[]>([]);
const problemsList = ref<CourseProblemSummary[]>([]);
const examplesList = ref<CourseExampleSummary[]>([]);

const currentDocId = ref<number>(parseQueryNumber(route.query.docId, 186));
const currentPage = ref<number>(parseQueryNumber(route.query.page, 1));
const currentKcCode = ref<string | null>(
  typeof route.query.kcCode === 'string' && route.query.kcCode ? route.query.kcCode : null,
);

const pageDetail = ref<CoursePageDetail | null>(null);
const pageLoading = ref<boolean>(false);
const indexLoading = ref<boolean>(true);
const documentPageList = ref<CoursePageDetail[]>([]);

const ragQuery = ref<string>('');
const ragRunning = ref<boolean>(false);
const ragResults = ref<LibrarySearchHit[]>([]);
const ragHint = ref<string>('在课件 561 页中关键词检索；登录身份不足时自动降级为本地全文检索。');

const viewMode = ref<PageViewMode>(readPreferredViewMode());
function setViewMode(mode: PageViewMode): void {
  if (viewMode.value === mode) return;
  viewMode.value = mode;
  persistViewMode(mode);
}

// R-13 优化：课件视频（替代原「文字摘录」Tab）。按当前章号拉 video_manifest，
// 优先选第一个已就绪(available)的视频；整章无视频时回退到文字摘录。
const chapterVideos = ref<CourseVideoDto[]>([]);
const videosLoading = ref<boolean>(false);
const selectedVideoId = ref<number | null>(null);
const videoChapterLoaded = ref<number | null>(null);

async function loadChapterVideos(chapterNo: number): Promise<void> {
  if (videoChapterLoaded.value === chapterNo) return;
  videosLoading.value = true;
  try {
    const list = await listChapterVideos(chapterNo);
    chapterVideos.value = list;
    videoChapterLoaded.value = chapterNo;
    const firstReady = list.find((v) => v.available) ?? list[0] ?? null;
    selectedVideoId.value = firstReady?.id ?? null;
  } finally {
    videosLoading.value = false;
  }
}

function selectVideo(id: number): void {
  selectedVideoId.value = id;
}

function formatDuration(sec: number | null): string {
  if (!sec || sec <= 0) return '';
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

const currentDocument = computed<CourseDocumentSummary | null>(
  () => documents.value.find((doc) => doc.documentId === currentDocId.value) ?? null,
);
const currentChapter = computed<ChapterDto | null>(() => {
  if (!pageDetail.value || pageDetail.value.chapterId == null) {
    return null;
  }
  return chapters.value.find((ch) => ch.chapterId === pageDetail.value?.chapterId) ?? null;
});
const chapterNoForVideo = computed<number>(
  () => currentChapter.value?.orderIndex ?? currentDocument.value?.sortOrder ?? 1,
);
const selectedVideo = computed<CourseVideoDto | null>(
  () => chapterVideos.value.find((v) => v.id === selectedVideoId.value) ?? null,
);
const currentKc = computed<CourseKcSummary | null>(() => {
  if (!currentKcCode.value) {
    return autoActiveKc.value;
  }
  return kcs.value.find((kc) => kc.kcCode === currentKcCode.value) ?? null;
});
const autoActiveKc = computed<CourseKcSummary | null>(() => {
  if (!pageDetail.value) {
    return null;
  }
  const pageId = pageDetail.value.pageId;
  return kcs.value.find((kc) => kc.pageRefs.includes(pageId)) ?? null;
});
const chapterGroups = computed<ChapterGroup[]>(() => {
  return chapters.value
    .slice()
    .sort((a, b) => a.orderIndex - b.orderIndex)
    .map((chapter) => ({
      chapter,
      kcs: kcs.value
        .filter((kc) => kc.chapterId === chapter.chapterId)
        .sort((a, b) => a.kcCode.localeCompare(b.kcCode)),
      documentId:
        documents.value.find((doc) => doc.sortOrder === chapter.orderIndex)?.documentId ?? null,
      pageCount:
        documents.value.find((doc) => doc.sortOrder === chapter.orderIndex)?.pageCount ?? null,
    }));
});

const catalogSidebarTitle = computed<string>(() => {
  const ch = chapters.value.length;
  const pages = documents.value.reduce((sum, doc) => sum + doc.pageCount, 0);
  const kc = kcs.value.length;
  if (ch > 0 && pages > 0 && kc > 0) {
    return `${ch} 章 · ${pages} 页 · ${kc} KC`;
  }
  if (ch > 0 && kc > 0) {
    return `${ch} 章 · ${kc} KC`;
  }
  return '章节目录';
});

const relatedProblems = computed<CourseProblemSummary[]>(() => {
  const code = currentKc.value?.kcCode;
  if (!code) {
    return [];
  }
  return problemsList.value.filter((p) => p.kcCode === code).slice(0, 4);
});
const relatedExamples = computed<CourseExampleSummary[]>(() => {
  const code = currentKc.value?.kcCode;
  if (!code) {
    return [];
  }
  return examplesList.value.filter((ex) => ex.kcCode === code).slice(0, 4);
});

function parseQueryNumber(value: unknown, fallback: number): number {
  if (typeof value === 'string' && /^\d+$/.test(value)) {
    return Number(value);
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  return fallback;
}

async function loadIndex(): Promise<void> {
  indexLoading.value = true;
  try {
    const [docs, chs, kcDtos, problems, examples, kcFallback] = await Promise.all([
      listCourseDocuments(),
      listChapters(43).catch((error: unknown) => {
        toast.push({ kind: 'error', text: '章节索引接口失败，已切到 fixture 兜底。' });
        void error;
        return [] as ChapterDto[];
      }),
      listKnowledgeComponents(43).catch((error: unknown) => {
        void error;
        return [] as KcDto[];
      }),
      listAllProblems(),
      listAllExamples(),
      listKcFallbacks(),
    ]);
    documents.value = docs;
    if (chs.length > 0) {
      chapters.value = chs;
    } else {
      chapters.value = await listChapterFallbacks();
    }
    kcs.value = mergeKcFromBackend(kcDtos, kcFallback);
    problemsList.value = problems;
    examplesList.value = examples;
  } finally {
    indexLoading.value = false;
  }
}

function mergeKcFromBackend(backend: KcDto[], fallback: CourseKcSummary[]): CourseKcSummary[] {
  if (backend.length === 0) {
    return fallback;
  }
  const fallbackByCode = new Map(fallback.map((kc) => [kc.kcCode, kc]));
  return backend.map((kc) => {
    const fb = fallbackByCode.get(kc.kcCode);
    return {
      kcId: kc.kcId,
      packId: 43,
      kcCode: kc.kcCode,
      kcName: kc.kcName,
      chapterId: kc.chapterId,
      chapterOrder: kc.chapterOrder,
      difficulty: kc.difficulty ?? null,
      summary: kc.summaryMarkdown ?? fb?.summary ?? '',
      pageRefs: fb?.pageRefs ?? [],
    };
  });
}

async function loadPage(docId: number, pageNo: number): Promise<void> {
  pageLoading.value = true;
  try {
    const [detail, pages] = await Promise.all([
      getDocumentPage(docId, pageNo),
      listDocumentPages(docId),
    ]);
    documentPageList.value = pages;
    pageDetail.value = detail;
    if (!detail) {
      toast.push({ kind: 'info', text: `课件 ${docId} 第 ${pageNo} 页暂无内容` });
    }
    await syncQuery();
  } catch (error) {
    pageDetail.value = null;
    const message = error instanceof Error ? error.message : String(error);
    toast.push({ kind: 'error', text: `课件加载失败：${message}` });
  } finally {
    pageLoading.value = false;
  }
}

async function syncQuery(): Promise<void> {
  const next: Record<string, string> = {
    docId: String(currentDocId.value),
    page: String(currentPage.value),
  };
  const code = currentKcCode.value ?? autoActiveKc.value?.kcCode ?? null;
  if (code) {
    next.kcCode = code;
  }
  await router.replace({ path: route.path, query: next });
}

function gotoPage(pageNo: number): void {
  if (!currentDocument.value) {
    return;
  }
  const total = currentDocument.value.pageCount;
  if (total <= 0) return;
  const target = Math.max(1, Math.min(pageNo, total));
  currentPage.value = target;
  void loadPage(currentDocId.value, target);
}

function selectDocument(docId: number): void {
  currentDocId.value = docId;
  currentPage.value = 1;
  void loadPage(docId, 1);
}

function jumpToKc(kc: CourseKcSummary): void {
  currentKcCode.value = kc.kcCode;
  if (kc.pageRefs.length === 0) {
    toast.push({ kind: 'info', text: `KC ${kc.kcCode} 暂未关联课件页` });
    return;
  }
  // 找首个属于章节对应文档的页面
  const targetPageId = kc.pageRefs[0];
  if (targetPageId === undefined) {
    return;
  }
  void resolvePageById(targetPageId);
}

function onKcSelect(group: ChapterGroup, event: Event): void {
  const code = (event.target as HTMLSelectElement).value;
  if (!code) return;
  const kc = group.kcs.find((k) => k.kcCode === code);
  if (kc) jumpToKc(kc);
}

async function resolvePageById(pageId: number): Promise<void> {
  for (const doc of documents.value) {
    const pages = await listDocumentPages(doc.documentId);
    const match = pages.find((p) => p.pageId === pageId);
    if (match) {
      currentDocId.value = doc.documentId;
      currentPage.value = match.pageNo;
      void loadPage(doc.documentId, match.pageNo);
      return;
    }
  }
  toast.push({ kind: 'error', text: `未找到 pageId=${pageId} 对应的课件页` });
}

async function runRagSearch(): Promise<void> {
  const q = ragQuery.value.trim();
  if (!q) {
    ragResults.value = [];
    return;
  }
  ragRunning.value = true;
  try {
    const hits = await searchCourseLibrary(q, { topK: 6 });
    ragResults.value = hits;
    if (hits.length === 0) {
      ragHint.value = '没有命中证据片段，请尝试更宽泛的关键词。';
    } else {
      ragHint.value = `命中 ${hits.length} 个片段（按相关性排序）。点击片段直达对应页。`;
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    ragHint.value = `检索失败：${message}`;
  } finally {
    ragRunning.value = false;
  }
}

/**
 * R-13：把课件 contentMarkdown 用 marked + DOMPurify 渲染成富文本 HTML。
 * 原页 PDF/PPT 需要服务端额外接 pdf.js，本期先把现有 OCR 文本做到漂亮排版：
 * - 章节标题（# / ##）
 * - 代码块（``` 围起 / 行首 4 空格）
 * - 列表 / 引用 / 表格
 * 用 DOMPurify 严格清洗，禁 <script>/<iframe>/onclick 等危险标签。
 */
import { marked } from 'marked';
import DOMPurify from 'dompurify';

// 不用 `DOMPurify.Config` 命名空间（dompurify@3.4 起改成普通 module export
// 不再暴露 namespace；用 sanitize 第二参形态推导出 Config 类型，避免版本耦合）。
type PurifyConfig = NonNullable<Parameters<typeof DOMPurify.sanitize>[1]>;

const PAGE_PURIFY_CONFIG: PurifyConfig = {
  ALLOWED_TAGS: [
    'p', 'br', 'strong', 'em', 'u', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'ul', 'ol', 'li', 'blockquote', 'pre', 'code',
    'table', 'thead', 'tbody', 'tr', 'th', 'td',
    'hr', 'span', 'div',
  ],
  ALLOWED_ATTR: ['class'],
};

function renderPageHtml(raw: string): string {
  if (!raw) return '';
  // OCR 文本里 \x0b 是 page break，先转换 + 重复段去掉
  // eslint-disable-next-line no-control-regex -- 故意匹配垂直制表符(\x0b)作为 OCR 分页符
  const cleaned = raw.replace(/\x0b/g, '\n\n').replace(/(\n\n)+/g, '\n\n').trim();
  const html = marked.parse(cleaned, { async: false }) as string;
  // dompurify@3.4 在 trusted-types 浏览器返回 TrustedHTML，这里强转 string
  // （Vue 的 v-html 接受字符串；TrustedHTML 在普通浏览器里实际就是 string，
  // 但 sanitize 默认 RETURN_TRUSTED_TYPE=false，应当返回 string。）
  return DOMPurify.sanitize(html, PAGE_PURIFY_CONFIG) as unknown as string;
}

function backToWorld(): void {
  void router.push({ path: '/world' });
}

function jumpToRagHit(hit: LibrarySearchHit): void {
  if (hit.documentId && hit.pageNo) {
    currentDocId.value = hit.documentId;
    currentPage.value = hit.pageNo;
    void loadPage(hit.documentId, hit.pageNo);
  } else if (hit.pageId) {
    void resolvePageById(hit.pageId);
  }
}

function openProblem(problemId: number): void {
  void router.push({ path: '/training', query: { problemId: String(problemId) } });
}

onMounted(async () => {
  await loadIndex();
  if (!currentDocId.value && documents.value.length > 0) {
    const first = documents.value[0];
    if (first) {
      currentDocId.value = first.documentId;
    }
  }
  // 证据回链（来自导师 evidence_page_ids / RAG 命中）：?pageId 解析 page_id → docId+pageNo。
  const pageId = parseQueryNumber(route.query.pageId, 0);
  if (pageId > 0) {
    await resolvePageById(pageId);
    return;
  }
  await loadPage(currentDocId.value, currentPage.value);
});

watch(
  () => route.query,
  async (next, prev) => {
    // 证据回链优先：pageId 变化时解析 page_id → docId+pageNo（再由 loadPage 归一化 URL）。
    const nextPageId = parseQueryNumber(next.pageId, 0);
    const prevPageId = prev ? parseQueryNumber(prev.pageId, 0) : 0;
    if (nextPageId > 0 && nextPageId !== prevPageId) {
      await resolvePageById(nextPageId);
      return;
    }
    if (prev && next.docId === prev.docId && next.page === prev.page) {
      return;
    }
    const docId = parseQueryNumber(next.docId, currentDocId.value);
    const pageNo = parseQueryNumber(next.page, currentPage.value);
    if (docId !== currentDocId.value || pageNo !== currentPage.value) {
      currentDocId.value = docId;
      currentPage.value = pageNo;
      await loadPage(docId, pageNo);
    }
  },
);

// 切到「课件视频」视图时按需拉取本章视频清单；切章时自动换片。
watch(
  viewMode,
  (mode) => {
    if (mode === 'video') void loadChapterVideos(chapterNoForVideo.value);
  },
  { immediate: true },
);
watch(chapterNoForVideo, (next) => {
  if (viewMode.value === 'video') void loadChapterVideos(next);
});
</script>

<template>
  <AppLayout>
    <section class="library-page">
      <header class="library-header">
        <nav
          class="breadcrumb"
          aria-label="阅读路径"
        >
          <span class="brand">Pythontopia 图书馆</span>
          <span class="sep">/</span>
          <span>{{ currentChapter?.title ?? '第 — 章' }}</span>
          <span class="sep">/</span>
          <span>{{ currentKc?.kcName ?? '—' }}</span>
          <span class="sep">/</span>
          <span>第 {{ currentPage }} 页 / {{ currentDocument?.pageCount ?? '—' }}</span>
        </nav>
        <PyButton
          test-id="library-back"
          variant="secondary"
          size="sm"
          @click="backToWorld"
        >
          ← 返回小镇
        </PyButton>
      </header>

      <div
        ref="bodyRef"
        class="library-body"
        data-testid="library-split-body"
      >
        <aside
          class="library-pane library-pane--left"
          :style="paneStyle.left()"
          aria-label="章节与 KC 导航"
        >
          <section class="side-block">
            <h3 class="side-title">
              {{ catalogSidebarTitle }}
            </h3>
            <ul class="chapter-list">
              <li
                v-for="group in chapterGroups"
                :key="group.chapter.chapterId"
                :class="['chapter-item', { active: group.documentId === currentDocId }]"
              >
                <div class="chapter-row">
                  <button
                    type="button"
                    class="chapter-head"
                    :data-testid="`chapter-doc-${group.chapter.orderIndex}`"
                    :title="
                      group.documentId
                        ? documents.find((d) => d.documentId === group.documentId)?.filename ?? ''
                        : ''
                    "
                    :disabled="!group.documentId"
                    @click="group.documentId && selectDocument(group.documentId)"
                  >
                    <span class="chapter-no">第 {{ group.chapter.orderIndex }} 章</span>
                    <span class="chapter-title">{{ chapterTitleLabel(group.chapter.title) }}</span>
                    <span
                      v-if="group.pageCount != null"
                      class="chapter-pages"
                    >{{ group.pageCount }} 页</span>
                  </button>
                  <select
                    v-if="group.kcs.length > 0"
                    class="kc-select"
                    :data-testid="`kc-select-${group.chapter.orderIndex}`"
                    :value="group.kcs.some((k) => k.kcCode === currentKc?.kcCode) ? currentKc?.kcCode : ''"
                    :title="`${group.kcs.length} 个知识点`"
                    @click.stop
                    @change="onKcSelect(group, $event)"
                  >
                    <option value="">
                      KC
                    </option>
                    <option
                      v-for="kc in group.kcs"
                      :key="kc.kcCode"
                      :value="kc.kcCode"
                    >
                      {{ kc.kcName }}{{ kc.pageRefs.length === 0 ? '（无页）' : '' }}
                    </option>
                  </select>
                </div>
              </li>
            </ul>
          </section>
        </aside>

        <div
          class="library-splitter"
          role="separator"
          aria-orientation="vertical"
          aria-label="调整左侧栏宽度"
          title="拖动调整左栏宽度"
          data-testid="library-splitter-left"
          @pointerdown="onSplitterPointerDown('left', $event)"
        />

        <main
          class="library-pane library-pane--main"
          aria-label="课件正文"
        >
          <header class="page-bar">
            <h2 class="page-title">
              {{ pageDetail?.title || currentDocument?.title || '加载中…' }}
            </h2>
            <div class="page-controls">
              <PyButton
                test-id="library-prev-page"
                variant="secondary"
                size="sm"
                :disabled="currentPage <= 1"
                @click="gotoPage(currentPage - 1)"
              >
                ◀ 上一页
              </PyButton>
              <input
                class="page-jump"
                type="number"
                min="1"
                :max="currentDocument?.pageCount ?? 1"
                :value="currentPage"
                @change="(e) => gotoPage(Number((e.target as HTMLInputElement).value))"
              >
              <PyButton
                test-id="library-next-page"
                variant="secondary"
                size="sm"
                :disabled="!!currentDocument && currentPage >= currentDocument.pageCount"
                @click="gotoPage(currentPage + 1)"
              >
                下一页 ▶
              </PyButton>
            </div>
          </header>

          <article
            class="page-content"
            data-testid="library-page-content"
          >
            <!-- R-13 D1/D2：原 PDF 视图（pdfjs-dist）+ Markdown 摘录视图切换。
                 默认显示原 PDF（不丢任何图/排版信息）；Markdown 仅做 fallback 与 RAG 命中预览。 -->
            <nav
              class="view-mode-tabs"
              data-testid="library-view-mode-tabs"
              aria-label="课件视图切换"
            >
              <button
                type="button"
                class="view-mode-tab"
                :class="{ 'view-mode-tab--active': viewMode === 'pdf' }"
                data-testid="library-view-mode-pdf"
                @click="setViewMode('pdf')"
              >
                原 PDF
              </button>
              <button
                type="button"
                class="view-mode-tab"
                :class="{ 'view-mode-tab--active': viewMode === 'video' }"
                data-testid="library-view-mode-video"
                @click="setViewMode('video')"
              >
                课件视频
              </button>
            </nav>
            <p
              v-if="pageLoading"
              class="muted"
            >
              翻页中…
            </p>
            <PdfPageView
              v-else-if="viewMode === 'pdf' && currentDocId"
              :doc-id="currentDocId"
              :page-no="currentPage"
            />
            <!-- R-13 优化：课件视频视图（替代「文字摘录」）。整章无视频时回退到文字摘录。 -->
            <div
              v-else-if="viewMode === 'video'"
              class="video-pane"
              data-testid="library-video-pane"
            >
              <p
                v-if="videosLoading"
                class="muted"
              >
                加载课件视频清单…
              </p>
              <template v-else-if="chapterVideos.length > 0">
                <div class="video-stage">
                  <video
                    v-if="selectedVideo && selectedVideo.available"
                    :key="selectedVideo.id"
                    class="video-player"
                    controls
                    preload="metadata"
                    :src="videoStreamUrl(selectedVideo.id)"
                  />
                  <div
                    v-else
                    class="video-unavailable"
                  >
                    <span
                      class="video-unavailable__icon"
                      aria-hidden="true"
                    >▶</span>
                    <p>该视频文件整理中（manifest 已登记，尚未下载到服务器）。</p>
                  </div>
                </div>
                <ul class="video-list">
                  <li
                    v-for="v in chapterVideos"
                    :key="v.id"
                  >
                    <button
                      type="button"
                      class="video-item"
                      :class="{ 'video-item--active': v.id === selectedVideoId }"
                      @click="selectVideo(v.id)"
                    >
                      <span class="video-item__title">{{ v.title }}</span>
                      <span class="video-item__meta">
                        <span v-if="formatDuration(v.durationSec)">{{ formatDuration(v.durationSec) }}</span>
                        <span
                          v-if="!v.available"
                          class="video-item__pending"
                        >未就绪</span>
                      </span>
                    </button>
                  </li>
                </ul>
              </template>
              <template v-else>
                <p class="muted video-fallback-note">
                  本章暂无课件视频，以下为文字摘录：
                </p>
                <div
                  v-if="pageDetail && (pageDetail.contentMarkdown || pageDetail.excerpt)"
                  class="page-prose page-prose--md"
                  v-html="renderPageHtml(pageDetail.contentMarkdown || pageDetail.excerpt || '')"
                />
                <p
                  v-else
                  class="muted"
                >
                  （本页暂无可读文本）
                </p>
              </template>
            </div>
            <p
              v-else-if="!indexLoading"
              class="muted"
            >
              本页未生成正文。试试切换章节或翻到下一页。
            </p>
          </article>

          <section
            v-if="currentKc"
            class="kc-anchor"
            aria-label="当前 KC 资源"
          >
            <h3>{{ currentKc.kcName }}</h3>
            <p class="muted">
              {{ currentKc.summary }}
            </p>
            <div class="kc-resource-grid">
              <div>
                <h4>关联示例（{{ relatedExamples.length }}）</h4>
                <ul>
                  <li
                    v-for="ex in relatedExamples"
                    :key="ex.exampleId"
                  >
                    <RouterLink :to="`/examples?kcCode=${ex.kcCode}`">
                      {{ ex.title }}
                    </RouterLink>
                  </li>
                </ul>
              </div>
              <div>
                <h4>关联题目（{{ relatedProblems.length }}）</h4>
                <ul>
                  <li
                    v-for="p in relatedProblems"
                    :key="p.problemId"
                  >
                    <button
                      type="button"
                      class="link-button"
                      @click="openProblem(p.problemId)"
                    >
                      {{ p.title }}
                      <span class="badge difficulty">{{ p.difficulty }}</span>
                    </button>
                  </li>
                </ul>
              </div>
            </div>
          </section>
        </main>

        <div
          class="library-splitter"
          role="separator"
          aria-orientation="vertical"
          aria-label="调整右侧栏宽度"
          title="拖动调整右栏宽度"
          data-testid="library-splitter-right"
          @pointerdown="onSplitterPointerDown('right', $event)"
        />

        <aside
          class="library-pane library-pane--right"
          :style="paneStyle.right()"
          aria-label="RAG 检索"
        >
          <h3 class="side-title">
            课件检索
          </h3>
          <form
            class="rag-form"
            @submit.prevent="runRagSearch"
          >
            <textarea
              v-model="ragQuery"
              class="rag-input"
              rows="3"
              placeholder="例：input 函数怎么用？for 循环和 while 的区别？"
              data-testid="library-rag-input"
            />
            <button
              type="submit"
              class="rag-submit"
              :disabled="ragRunning"
              data-testid="library-rag-submit"
            >
              {{ ragRunning ? '检索中…' : '检索证据' }}
            </button>
          </form>
          <p class="rag-hint">
            {{ ragHint }}
          </p>
          <ul
            class="rag-results"
            data-testid="library-rag-results"
          >
            <li
              v-for="hit in ragResults"
              :key="`${hit.documentId}-${hit.pageNo}-${hit.pageId}`"
            >
              <button
                type="button"
                class="rag-hit"
                @click="jumpToRagHit(hit)"
              >
                <header>
                  <strong>{{ hit.title || `第 ${hit.pageNo} 页` }}</strong>
                  <span class="badge">第 {{ hit.pageNo }} 页</span>
                </header>
                <p>{{ hit.snippet }}</p>
              </button>
            </li>
          </ul>
        </aside>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.library-page {
  display: flex;
  flex-direction: column;
  gap: clamp(8px, 1.2vw, 14px);
  padding: clamp(8px, 1.5vw, 16px);
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  box-sizing: border-box;
  background:
    linear-gradient(180deg, rgba(255, 247, 230, 0.7), rgba(243, 228, 190, 0.85)),
    repeating-linear-gradient(135deg, rgba(110, 79, 29, 0.05) 0 6px, transparent 6px 14px),
    #fef6dd;
}

.library-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border: 3px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  background: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-family: var(--pyt-font-pixel), var(--pyt-font-sans, sans-serif);
  font-size: 14px;
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
}
.breadcrumb .brand {
  font-weight: 700;
}
.breadcrumb .sep {
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}
.back-button {
  font-family: var(--pyt-font-pixel), monospace;
  min-height: 36px;
  padding: 0 16px;
  background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
  cursor: pointer;
  box-shadow: 3px 3px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.back-button:active {
  transform: translate(2px, 2px);
  box-shadow: 1px 1px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}

.library-body {
  flex: 1 1 0;
  display: flex;
  align-items: stretch;
  gap: 0;
  min-height: 0;
  overflow: hidden;
}

.library-pane--left,
.library-pane--right {
  flex-shrink: 0;
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 12px;
  background: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  border: 3px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  overflow: auto;
  min-height: 0;
}

.library-splitter {
  flex: 0 0 10px;
  margin: 0 2px;
  align-self: stretch;
  cursor: col-resize;
  touch-action: none;
  border-radius: 4px;
  background: repeating-linear-gradient(
    180deg,
    var(--pyt-color-border, var(--pyt-color-border)) 0 4px,
    transparent 4px 8px
  );
  opacity: 0.55;
  transition: opacity 120ms ease, background-color 120ms ease;
}
.library-splitter:hover,
.library-splitter:focus-visible {
  opacity: 1;
  background-color: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
  outline: none;
}

.side-block {
  display: grid;
  gap: 6px;
}
.side-title {
  margin: 0 0 4px;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 14px;
  letter-spacing: 1px;
  border-bottom: 2px dashed var(--pyt-color-border, var(--pyt-color-border));
  padding-bottom: 4px;
}
.chapter-list,
.kc-list,
.rag-results {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 4px;
}
.chapter-item {
  border: 2px solid var(--pyt-color-border, var(--pyt-color-border));
  background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft));
  padding: 4px 6px;
}
.chapter-item.active {
  background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
  border-color: var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.chapter-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.chapter-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  flex: 1 1 auto;
  gap: 6px;
  align-items: center;
  min-width: 0;
  background: transparent;
  border: none;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 12px;
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
  text-align: left;
  padding: 4px 2px;
  cursor: pointer;
}
.chapter-head:disabled {
  cursor: default;
  opacity: 0.65;
}
.chapter-pages {
  flex-shrink: 0;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
  font-family: var(--pyt-font-sans, sans-serif);
  font-size: 11px;
}
.chapter-no {
  flex-shrink: 0;
  font-weight: 700;
}
.chapter-title {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--pyt-font-sans, sans-serif);
  font-size: 12px;
}
.kc-list {
  margin-top: 4px;
  padding-left: 8px;
  border-left: 2px dotted var(--pyt-color-border, var(--pyt-color-border));
}
.kc-item {
  width: 100%;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px;
  text-align: left;
  background: transparent;
  border: none;
  padding: 3px 4px;
  font-size: 12px;
  color: var(--pyt-color-text-hint, var(--pyt-color-text-hint));
  cursor: pointer;
  opacity: 0.7;
}
.kc-item.has-refs {
  opacity: 1;
}
.kc-item.active {
  background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
  font-weight: 700;
}
.kc-code {
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 11px;
}

.kc-select {
  flex: 0 0 72px;
  max-width: 88px;
  font-family: var(--pyt-font-sans, sans-serif);
  font-size: 11px;
  padding: 4px 4px;
  background: var(--pyt-color-bg-primary, var(--pyt-color-bg-primary));
  border: 2px solid var(--pyt-color-border, var(--pyt-color-border));
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
  cursor: pointer;
}
.kc-select:focus {
  outline: none;
  border-color: var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}

/* R-13 优化：课件视频视图 */
.video-pane {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.video-stage {
  width: 100%;
  background: #160f04;
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  box-shadow: 3px 3px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  overflow: hidden;
}
.video-player {
  display: block;
  width: 100%;
  max-height: 52vh;
  background: #000;
}
.video-unavailable {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 36px 16px;
  color: var(--pyt-color-warning-bg);
  text-align: center;
  font-size: 13px;
}
.video-unavailable__icon {
  width: 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--pyt-color-warning-bg);
  border-radius: 50%;
  font-size: 18px;
}
.video-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 4px;
}
.video-item {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
  background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft));
  border: 2px solid var(--pyt-color-border, var(--pyt-color-border));
  font-family: var(--pyt-font-sans, sans-serif);
  font-size: 13px;
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
}
.video-item:hover {
  background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
}
.video-item--active {
  background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
  border-color: var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  font-weight: 700;
}
.video-item__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.video-item__meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 11px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}
.video-item__pending {
  padding: 0 6px;
  border: 1px solid var(--pyt-color-border, var(--pyt-color-border));
  background: var(--pyt-color-info-bg, var(--pyt-color-info-bg));
  color: #1b4a6b;
  font-size: 10px;
}
.video-fallback-note {
  margin: 0 0 4px;
}

.library-pane--main {
  flex: 1 1 0;
  min-width: 0;
  width: 0;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 12px;
  padding: 16px;
  background: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  border: 3px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  overflow: hidden;
  min-height: 0;
}
.page-bar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 8px 12px;
  border-bottom: 2px dashed var(--pyt-color-border, var(--pyt-color-border));
  padding-bottom: 6px;
}
.page-title {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: clamp(14px, 2vw, 18px);
  min-width: 0;
  flex: 1 1 12rem;
}
.page-controls {
  display: flex;
  gap: 6px;
  align-items: center;
}
.page-nav {
  min-height: 32px;
  padding: 0 10px;
  background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft));
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 12px;
  cursor: pointer;
}
.page-nav:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.page-jump {
  width: 64px;
  text-align: center;
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  background: var(--pyt-color-bg-primary, var(--pyt-color-bg-primary));
  padding: 2px 6px;
  font-family: var(--pyt-font-pixel), monospace;
}
.page-content {
  overflow: auto;
  padding: 12px;
  background: var(--pyt-color-bg-primary, var(--pyt-color-bg-primary));
  border: 2px solid var(--pyt-color-border, var(--pyt-color-border));
  font-family: var(--pyt-font-sans, sans-serif);
  font-size: 15px;
  line-height: 1.65;
  min-height: 0;
  flex: 1;
}

/* R-13 D1/D2：PDF / Markdown 视图切换 tab */
.view-mode-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
  border-bottom: 2px solid var(--pyt-color-border, var(--pyt-color-border));
  padding-bottom: 4px;
}
.view-mode-tab {
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 12px;
  padding: 4px 12px;
  background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft));
  border: 2px solid var(--pyt-color-border, var(--pyt-color-border));
  border-bottom-color: transparent;
  cursor: pointer;
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
  min-height: 28px;
}
.view-mode-tab--active {
  background: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  border-color: var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  border-bottom-color: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  font-weight: 700;
}
.view-mode-tab:hover:not(.view-mode-tab--active) {
  background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
}
.page-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}

/* R-13：page-prose 分段渲染，让 OCR 文本不再像 plaintext 一块 */
.page-prose {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.page-prose__seg {
  margin: 0;
  word-break: break-word;
}

.page-prose__para {
  font-size: 15px;
  line-height: 1.8;
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
  white-space: pre-wrap;
}

.page-prose__code {
  font-family: var(--pyt-font-mono, monospace);
  background: rgba(77, 54, 19, 0.08);
  border-left: 3px solid var(--pyt-color-border, var(--pyt-color-border));
  padding: 8px 12px;
  white-space: pre;
  overflow-x: auto;
  font-size: 13px;
}

.page-prose__hint {
  margin: 12px 0 0;
  padding: 8px 10px;
  font-size: 11px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
  background: rgba(201, 168, 107, 0.18);
  border: 1px dashed var(--pyt-color-border-soft, rgba(201, 168, 107, 0.45));
  border-radius: 4px;
}
.kc-anchor {
  background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft));
  padding: 10px 12px;
  border: 2px solid var(--pyt-color-border, var(--pyt-color-border));
}
.kc-anchor h3 {
  margin: 0 0 6px;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 14px;
}
.kc-anchor p {
  margin: 0 0 8px;
  font-size: 13px;
}
.kc-resource-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.kc-resource-grid h4 {
  margin: 0 0 4px;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 12px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}
.kc-resource-grid ul {
  list-style: square;
  padding-left: 18px;
  margin: 0;
  font-size: 13px;
}
.link-button {
  background: none;
  border: none;
  padding: 0;
  color: var(--pyt-color-info, var(--pyt-color-info));
  cursor: pointer;
  text-align: left;
  font: inherit;
  min-height: auto;
  text-decoration: underline;
}
.badge {
  display: inline-block;
  background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
  border: 1px solid var(--pyt-color-border, var(--pyt-color-border));
  padding: 0 6px;
  font-size: 10px;
  font-family: var(--pyt-font-pixel), monospace;
  margin-left: 4px;
}
.badge.difficulty {
  background: var(--pyt-color-info-bg, var(--pyt-color-info-bg));
}

.rag-form {
  display: grid;
  gap: 6px;
}
.rag-input {
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  background: var(--pyt-color-bg-primary, var(--pyt-color-bg-primary));
  padding: 6px;
  font: inherit;
  resize: vertical;
}
.rag-submit {
  min-height: 32px;
  padding: 0 8px;
  font-family: var(--pyt-font-pixel), monospace;
  background: var(--pyt-color-primary, var(--pyt-color-text-hint));
  color: var(--pyt-color-text-on-dark, var(--pyt-color-bg-elevated));
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.rag-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.rag-hint {
  margin: 0;
  font-size: 11px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}
.rag-hit {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 6px;
  text-align: left;
  background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft));
  border: 2px solid var(--pyt-color-border, var(--pyt-color-border));
  font: inherit;
  cursor: pointer;
  min-height: auto;
}
.rag-hit header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rag-hit p {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
}
.muted {
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
  font-size: 13px;
  margin: 0;
}

@media (max-width: 1100px) {
  .library-body {
    flex-direction: column;
    overflow: auto;
  }
  .library-splitter {
    display: none;
  }
  .library-pane--left,
  .library-pane--right {
    width: auto !important;
    flex-shrink: 0;
  }
  .library-pane--left {
    max-height: min(38dvh, 340px);
  }
  .library-pane--right {
    max-height: min(32dvh, 300px);
  }
  .library-pane--main {
    flex: 1 1 auto;
    width: auto;
    min-height: min(42dvh, 480px);
  }
}

@media (max-width: 720px) {
  .library-header {
    flex-direction: column;
    align-items: stretch;
  }
  .back-button {
    width: 100%;
  }
  .breadcrumb {
    font-size: 12px;
  }
  .kc-resource-grid {
    grid-template-columns: 1fr;
  }
  .page-controls {
    flex-wrap: wrap;
    width: 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 480px) {
  .chapter-row {
    flex-wrap: wrap;
  }
  .kc-select {
    width: 100%;
    max-width: none;
  }
}
</style>

<!-- 图书馆页占满 app-main 剩余视口，三栏高度随窗口伸缩 -->
<style>
.app-main:has(.library-page) {
  display: flex;
  flex-direction: column;
  min-height: calc(100dvh - 56px);
  max-height: calc(100dvh - 56px);
  overflow: hidden;
}
@supports not (height: 100dvh) {
  .app-main:has(.library-page) {
    min-height: calc(100vh - 56px);
    max-height: calc(100vh - 56px);
  }
}
</style>

