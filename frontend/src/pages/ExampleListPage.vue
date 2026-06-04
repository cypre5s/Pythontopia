<script setup lang="ts">
// ExampleListPage —— 46 个课件示例的卡片列表。按 KC 过滤，每张卡片显示
// 代码片段 + 解释，提供"复制代码"和"看上下文"（跳 LibraryPage 对应页）。
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PyButton from '@/components/ui/PyButton.vue';
import { listAllExamples, listKcFallbacks } from '@/api/courseLibrary';
import { listKnowledgeComponents } from '@/api/course';
import { useToastStore } from '@stores/toast';
import type {
  CourseExampleSummary,
  CourseKcSummary,
} from '@/api/dto/Library';
import type { KcDto } from '@/api/dto/KcDto';

const route = useRoute();
const router = useRouter();
const toast = useToastStore();

const examples = ref<CourseExampleSummary[]>([]);
const kcs = ref<CourseKcSummary[]>([]);
const filterKcCodes = ref<string[]>(parseInitialKcFilter());
const loading = ref<boolean>(true);

function parseInitialKcFilter(): string[] {
  const value = route.query.kcCode;
  if (typeof value === 'string' && value) {
    return [value];
  }
  if (Array.isArray(value)) {
    return value.filter((v): v is string => typeof v === 'string' && v.length > 0);
  }
  return [];
}

const kcByCode = computed(() => new Map(kcs.value.map((kc) => [kc.kcCode, kc])));

const groupedByKc = computed<Array<{ kc: CourseKcSummary | null; list: CourseExampleSummary[] }>>(() => {
  const kcFilter = new Set(filterKcCodes.value);
  const filtered = examples.value.filter(
    (ex) => kcFilter.size === 0 || kcFilter.has(ex.kcCode),
  );
  const buckets = new Map<string, CourseExampleSummary[]>();
  for (const ex of filtered) {
    let bucket = buckets.get(ex.kcCode);
    if (!bucket) {
      bucket = [];
      buckets.set(ex.kcCode, bucket);
    }
    bucket.push(ex);
  }
  return Array.from(buckets.entries())
    .map(([kcCode, list]) => {
      const kc = kcByCode.value.get(kcCode) ?? null;
      return { kc, list };
    })
    .sort((a, b) => {
      const oa = a.kc?.chapterOrder ?? 99;
      const ob = b.kc?.chapterOrder ?? 99;
      if (oa !== ob) return oa - ob;
      return (a.kc?.kcCode ?? '').localeCompare(b.kc?.kcCode ?? '');
    });
});

function toggleKcFilter(code: string): void {
  const next = new Set(filterKcCodes.value);
  if (next.has(code)) {
    next.delete(code);
  } else {
    next.add(code);
  }
  filterKcCodes.value = Array.from(next);
}

function clearFilter(): void {
  filterKcCodes.value = [];
}

async function copyCode(code: string): Promise<void> {
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(code);
      toast.push({ kind: 'success', text: '已复制示例代码到剪贴板' });
      return;
    }
  } catch (error) {
    void error;
  }
  toast.push({ kind: 'info', text: '当前浏览器不支持剪贴板，请手动选中复制。' });
}

function openContext(example: CourseExampleSummary): void {
  const targetPage = example.pageRefs[0];
  const query: Record<string, string> = {
    docId: String(example.documentId),
    kcCode: example.kcCode,
  };
  if (targetPage) {
    query.page = String(targetPage);
  }
  void router.push({ path: '/library', query });
}

function backToWorld(): void {
  void router.push({ path: '/world' });
}

onMounted(async () => {
  loading.value = true;
  try {
    const [exList, kcDtos] = await Promise.all([
      listAllExamples(),
      listKnowledgeComponents(43).catch(() => [] as KcDto[]),
    ]);
    examples.value = exList;

    if (kcDtos.length > 0) {
      kcs.value = kcDtos.map((kc) => ({
        kcId: kc.kcId,
        packId: 43,
        kcCode: kc.kcCode,
        kcName: kc.kcName,
        chapterId: kc.chapterId,
        chapterOrder: kc.chapterOrder,
        difficulty: kc.difficulty ?? null,
        summary: kc.summaryMarkdown ?? '',
        pageRefs: [],
      }));
    } else {
      kcs.value = await listKcFallbacks();
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    toast.push({ kind: 'error', text: `示例库加载失败：${message}` });
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <AppLayout>
    <section class="example-list-page">
      <header class="page-header">
        <div>
          <h1>Pythontopia 示例库</h1>
          <p class="muted">
            packId=43 · 共 {{ examples.length }} 示例 ·
            <template v-if="filterKcCodes.length">
              已筛 KC × {{ filterKcCodes.length }}
            </template>
            <template v-if="loading">
              · 加载中…
            </template>
          </p>
        </div>
        <PyButton
          test-id="example-back"
          variant="secondary"
          size="sm"
          @click="backToWorld"
        >
          ← 返回小镇
        </PyButton>
      </header>

      <section class="filter-bar">
        <h3>按 KC 筛选</h3>
        <div class="chip-row">
          <button
            v-for="kc in kcs"
            :key="kc.kcCode"
            type="button"
            :class="['chip', { active: filterKcCodes.includes(kc.kcCode) }]"
            :title="kc.kcName"
            @click="toggleKcFilter(kc.kcCode)"
          >
            {{ kc.kcName }}
          </button>
        </div>
        <PyButton
          v-if="filterKcCodes.length"
          class="reset-button"
          variant="secondary"
          size="sm"
          @click="clearFilter"
        >
          清空筛选
        </PyButton>
      </section>

      <div
        class="kc-bucket-list"
        data-testid="example-bucket-list"
      >
        <section
          v-for="bucket in groupedByKc"
          :key="bucket.kc?.kcCode ?? 'unknown'"
          class="kc-bucket"
        >
          <header class="kc-bucket-head">
            <h2>
              {{ bucket.kc?.kcName ?? '未匹配 KC' }}
            </h2>
            <span class="muted">第 {{ bucket.kc?.chapterOrder ?? '—' }} 章 · {{ bucket.list.length }} 示例</span>
          </header>
          <div class="example-grid">
            <article
              v-for="ex in bucket.list"
              :key="ex.exampleId"
              class="example-card"
              data-testid="example-card"
            >
              <header class="card-head">
                <h3>{{ ex.title || `示例 #${ex.exampleId}` }}</h3>
                <span class="badge">#{{ ex.exampleId }}</span>
              </header>
              <p
                v-if="ex.explanationMarkdown"
                class="card-explain"
              >
                {{ ex.explanationMarkdown }}
              </p>
              <pre
                class="card-code"
                v-text="ex.code || '（示例尚未提供代码片段）'"
              />
              <footer class="card-footer">
                <PyButton
                  test-id="copy-code"
                  variant="secondary"
                  size="sm"
                  @click="copyCode(ex.code || '')"
                >
                  复制代码
                </PyButton>
                <PyButton
                  test-id="open-context"
                  variant="secondary"
                  size="sm"
                  @click="openContext(ex)"
                >
                  看上下文 →
                </PyButton>
              </footer>
            </article>
          </div>
        </section>
        <p
          v-if="!loading && groupedByKc.length === 0"
          class="empty"
        >
          没有示例匹配当前筛选。
        </p>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.example-list-page {
  display: grid;
  gap: 16px;
  padding: 16px;
  min-height: calc(100vh - 56px);
  background:
    linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%),
    repeating-linear-gradient(45deg, rgba(110, 79, 29, 0.04) 0 4px, transparent 4px 12px);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 16px;
  background: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  border: 3px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.page-header h1 {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 22px;
}
.muted {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}
.filter-bar {
  display: grid;
  gap: 8px;
  padding: 12px;
  background: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  border: 3px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.filter-bar h3 {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 13px;
}
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-height: 110px;
  overflow-y: auto;
}
.chip {
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 11px;
  padding: 4px 8px;
  border: 2px solid var(--pyt-color-border, var(--pyt-color-border));
  background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft));
  cursor: pointer;
  min-height: auto;
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
}
.chip.active {
  background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
  border-color: var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  font-weight: 700;
}
.reset-button {
  justify-self: start;
}

.kc-bucket-list {
  display: grid;
  gap: 16px;
}
.kc-bucket {
  background: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  border: 3px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  box-shadow: 4px 4px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  padding: 12px;
}
.kc-bucket-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  border-bottom: 2px dashed var(--pyt-color-border, var(--pyt-color-border));
  padding-bottom: 6px;
  margin-bottom: 10px;
}
.kc-bucket-head h2 {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 16px;
}
.example-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}
.example-card {
  display: grid;
  gap: 8px;
  padding: 10px;
  background: var(--pyt-color-bg-soft, var(--pyt-color-bg-soft));
  border: 2px solid var(--pyt-color-border, var(--pyt-color-border));
}
.card-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.card-head h3 {
  margin: 0;
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: 14px;
}
.card-explain {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}
.card-code {
  margin: 0;
  padding: 8px;
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border, var(--pyt-color-border));
  font-family: var(--pyt-font-mono, 'JetBrains Mono', monospace);
  font-size: 12px;
  line-height: 1.45;
  max-height: 160px;
  overflow: auto;
  white-space: pre-wrap;
}
.card-footer {
  display: flex;
  gap: 8px;
}
.ghost-button {
  font-family: var(--pyt-font-pixel), monospace;
  min-height: 28px;
  padding: 0 8px;
  background: var(--pyt-color-bg-primary, var(--pyt-color-bg-primary));
  border: 2px solid var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  cursor: pointer;
  font-size: 11px;
  box-shadow: 2px 2px 0 var(--pyt-color-border-strong, var(--pyt-color-text-primary));
}
.badge {
  display: inline-block;
  padding: 1px 6px;
  background: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
  border: 1px solid var(--pyt-color-border, var(--pyt-color-border));
  font-family: var(--pyt-font-pixel), monospace;
  font-size: 11px;
}
.badge.kc {
  background: var(--pyt-color-info-bg, var(--pyt-color-info-bg));
}
.empty {
  text-align: center;
  margin: 0;
  padding: 24px;
  color: var(--pyt-color-text-muted, var(--pyt-color-text-muted));
}
</style>
