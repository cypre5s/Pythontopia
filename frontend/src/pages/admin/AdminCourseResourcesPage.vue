<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  searchAdminCourseResources,
  setAdminCourseResourceEnabled,
  type AdminCourseResourceDto,
  type AdminCourseResourceType,
} from '@/api/adminCourseResources';
import { getCoursePack } from '@/api/course';
import type { CoursePackDto } from '@/api/dto/CoursePackDto';
import { humanizeKcText } from '@/data/kcNames';
import AdminPanel from './AdminPanel.vue';
import AdminTable from './AdminTable.vue';
import AdminPagination from './AdminPagination.vue';

const types: Array<{ value: AdminCourseResourceType; label: string }> = [
  { value: 'ALL', label: '全部资源' },
  { value: 'DOCUMENT', label: '课件文档' },
  { value: 'CHAPTER', label: '章节' },
  { value: 'KNOWLEDGE_POINT', label: '知识点' },
  { value: 'EXAMPLE', label: '示例' },
  { value: 'PROBLEM', label: '练习题' },
];
const entries = ref<AdminCourseResourceDto[]>([]);
const pack = ref<CoursePackDto | null>(null);
const selected = ref<AdminCourseResourceDto | null>(null);
const queryInput = ref('');
const query = ref('');
const type = ref<AdminCourseResourceType>('ALL');
const page = ref(0);
const size = ref(50);
const totalElements = ref(0);
const totalPages = ref(0);
const loading = ref(false);
const error = ref('');
const savingResourceKey = ref<string | null>(null);
const typeLabel = computed(() => (value: string) => types.find((item) => item.value === value)?.label ?? '课程资源');

async function load(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  error.value = '';
  try {
    const result = await searchAdminCourseResources({
      page: page.value,
      size: size.value,
      type: type.value,
      ...(query.value ? { q: query.value } : {}),
    });
    entries.value = result.content;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;
    selected.value = result.content[0] ?? null;
  } catch (cause) {
    entries.value = [];
    selected.value = null;
    error.value = cause instanceof Error ? cause.message : '课程资源目录暂不可用';
  } finally {
    loading.value = false;
  }
}

function search(): void {
  page.value = 0;
  query.value = queryInput.value.trim();
  void load();
}

function selectType(nextType: AdminCourseResourceType): void {
  type.value = nextType;
  page.value = 0;
  void load();
}

function goToPage(nextPage: number): void {
  if (loading.value || nextPage < 0 || nextPage >= totalPages.value) return;
  page.value = nextPage;
  void load();
}

function changePageSize(nextSize: number): void {
  if (loading.value || nextSize === size.value) return;
  size.value = nextSize;
  page.value = 0;
  void load();
}

function previewText(resource: AdminCourseResourceDto): string {
  return humanizeKcText(resource.preview || '该资源暂无摘要。');
}

function documentUrl(resource: AdminCourseResourceDto): string | null {
  return resource.enabled && resource.resourceType === 'DOCUMENT'
    ? `/api/course/documents/${resource.resourceId}/pdf`
    : null;
}

async function toggleAvailability(resource: AdminCourseResourceDto): Promise<void> {
  if (savingResourceKey.value) return;
  const key = `${resource.resourceType}-${resource.resourceId}`;
  savingResourceKey.value = key;
  error.value = '';
  try {
    const updated = { ...resource, enabled: !resource.enabled };
    await setAdminCourseResourceEnabled(resource.resourceType, resource.resourceId, updated.enabled);
    entries.value = entries.value.map((item) => item.resourceType === resource.resourceType && item.resourceId === resource.resourceId ? updated : item);
    if (selected.value?.resourceType === resource.resourceType && selected.value.resourceId === resource.resourceId) selected.value = updated;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '资源状态保存失败';
  } finally {
    savingResourceKey.value = null;
  }
}

onMounted(() => {
  void load();
  void getCoursePack().then((value) => { pack.value = value; }).catch(() => { pack.value = null; });
});
</script>

<template>
  <section class="admin-course-resources" data-testid="admin-course-resources" :aria-busy="loading">
    <AdminPanel title="课程资源管理" description="查看课程包 43 的目录与预览，管理资源可用状态；启停不会改写或删除课程种子。">
      <template #toolbar>
        <form class="admin-course-resources__search" @submit.prevent="search">
          <label>
            <span>搜索资源</span>
            <input v-model="queryInput" class="admin-control" type="search" maxlength="100" data-testid="admin-course-resource-search">
          </label>
          <button class="admin-button admin-button--primary" type="submit" :disabled="loading">搜索</button>
        </form>
        <div class="admin-course-resources__filters" aria-label="资源类型">
          <button
            v-for="item in types"
            :key="item.value"
            class="admin-button"
            :class="{ 'admin-button--primary': type === item.value }"
            type="button"
            :aria-pressed="type === item.value"
            :disabled="loading"
            @click="selectType(item.value)"
          >{{ item.label }}</button>
        </div>
      </template>

      <p v-if="pack" class="admin-course-resources__invariant" data-testid="admin-course-pack-counts">
        {{ pack.name }}：{{ pack.documentCount }} 份文档 · {{ pack.pageCount }} 页 · {{ pack.chapterCount }} 章 ·
        {{ pack.kcCount }} 个知识点 · {{ pack.exampleCount }} 个示例 · {{ pack.problemCount }} 道题
      </p>
      <p v-if="loading && entries.length === 0" role="status" class="admin-course-resources__state">正在读取课程资源…</p>
      <div v-else-if="error" class="admin-course-resources__error" role="alert">
        <span>读取失败：{{ error }}</span><button class="admin-button" type="button" @click="load">重试</button>
      </div>
      <p v-else-if="entries.length === 0" class="admin-course-resources__state">没有匹配的课程资源。</p>

      <div v-else class="admin-course-resources__body">
        <AdminTable label="课程资源目录" :min-width="650">
          <table>
            <thead><tr><th scope="col">资源类型</th><th scope="col">名称</th><th scope="col">所属章节或知识点</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
            <tbody>
              <tr v-for="entry in entries" :key="`${entry.resourceType}-${entry.resourceId}`">
                <td>{{ typeLabel(entry.resourceType) }}</td>
                <td>{{ humanizeKcText(entry.title) }}</td>
                <td>{{ humanizeKcText(entry.category) }}</td>
                <td>{{ entry.enabled ? '可用' : '已停用' }} · {{ entry.status === 'COURSE_SEED' ? '课程种子' : entry.status }}</td>
                <td><button class="admin-button" type="button" @click="selected = entry">预览</button> <button class="admin-button" type="button" :disabled="savingResourceKey !== null" :data-testid="`admin-course-resource-toggle-${entry.resourceType}-${entry.resourceId}`" @click="toggleAvailability(entry)">{{ savingResourceKey === `${entry.resourceType}-${entry.resourceId}` ? '处理中…' : entry.enabled ? '停用' : '启用' }}</button></td>
              </tr>
            </tbody>
          </table>
        </AdminTable>

        <aside v-if="selected" class="admin-course-resources__preview" aria-label="资源预览" data-testid="admin-course-resource-preview">
          <div>
            <p class="admin-course-resources__eyebrow">{{ typeLabel(selected.resourceType) }} · {{ humanizeKcText(selected.category) }}</p>
            <h3>{{ humanizeKcText(selected.title) }}</h3>
          </div>
          <p>{{ previewText(selected) }}</p>
          <p v-if="selected.pageCount !== null">{{ selected.pageCount }} 页</p>
          <a v-if="documentUrl(selected)" class="admin-button" :href="documentUrl(selected) ?? undefined" target="_blank" rel="noreferrer">打开 PDF</a>
        </aside>
      </div>

      <template #footer>
        <AdminPagination
          :page="page"
          :total-pages="totalPages"
          :page-size="size"
          :total-elements="totalElements"
          item-label="项资源"
          :disabled="loading"
          @page-change="goToPage"
          @page-size-change="changePageSize"
        />
      </template>
    </AdminPanel>
  </section>
</template>

<style scoped>
.admin-course-resources { min-width: 0; }
.admin-course-resources__search, .admin-course-resources__filters { display: flex; align-items: end; gap: 8px; flex-wrap: wrap; }
.admin-course-resources__search label { display: grid; gap: 4px; color: var(--admin-text-muted); font-size: 12px; }
.admin-course-resources__search input { width: min(320px, 72vw); }
.admin-course-resources__filters { margin-top: 12px; }
.admin-course-resources__invariant { padding: 12px 14px; border-radius: 10px; background: var(--admin-surface-muted); color: var(--admin-text); font-size: 13px; }
.admin-course-resources__body { display: grid; gap: 18px; }
.admin-course-resources__preview { display: grid; gap: 10px; padding: 16px; border: 1px solid var(--admin-border); border-radius: 12px; background: var(--admin-surface-muted); overflow-wrap: anywhere; }
.admin-course-resources__preview h3, .admin-course-resources__preview p { margin: 0; }
.admin-course-resources__preview p { white-space: pre-wrap; }
.admin-course-resources__eyebrow, .admin-course-resources__state { color: var(--admin-text-muted); font-size: 12px; }
.admin-course-resources__error { display: flex; justify-content: space-between; gap: 12px; color: var(--admin-danger); }
</style>
