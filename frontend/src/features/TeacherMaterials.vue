<template>
  <section class="admin-list materials-list" aria-label="课程材料列表">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="list-toolbar table-toolbar">
      <label class="search-field"
        ><span class="sr-only">搜索材料名称或 ID</span
        ><input v-model="search" type="search" placeholder="搜索材料名称或 ID"
      /></label>
      <label
        >章节<select v-model="chapter">
          <option value="">全部章节</option>
          <option value="unassigned">未分类</option>
          <option
            v-for="item in chapters"
            :key="item.id"
            :value="String(item.id)"
          >
            {{ item.title }}
          </option>
        </select></label
      >
      <label
        >处理状态<select v-model="status">
          <option value="">全部状态</option>
          <option
            v-for="(label, value) in statuses"
            :key="value"
            :value="value"
          >
            {{ label }}
          </option>
        </select></label
      >
      <div class="list-toolbar-actions inline-actions">
        <Button variant="outline" :disabled="loading" @click="load"
          >刷新状态</Button
        ><Button @click="openUpload()">上传课程材料</Button>
      </div>
    </div>
    <div class="admin-panel" :aria-busy="loading">
      <p
        v-if="loading && !materials.length"
        class="loading-notice"
        role="status"
      >
        正在读取资料库…
      </p>
      <div class="admin-table-scroll">
        <Table
          ><TableHeader
            ><TableRow
              ><TableHead>资料</TableHead><TableHead>关联章节</TableHead
              ><TableHead>状态</TableHead
              ><TableHead class="table-actions-heading"
                >操作</TableHead
              ></TableRow
            ></TableHeader
          ><TableBody
            ><TableRow v-for="item in paged" :key="item.id"
              ><TableCell
                ><span class="record-title">{{ item.title }}</span
                ><small
                  >#{{ item.id }} · {{ kinds[item.kind] ?? item.kind }}</small
                ></TableCell
              ><TableCell>{{
                chapterNames(item.chapter_ids ?? [], chapters)
              }}</TableCell
              ><TableCell
                ><span class="status-badge" :data-status="item.status">{{
                  statuses[item.status] ?? item.status
                }}</span
                ><small v-if="item.error">{{ item.error }}</small></TableCell
              ><TableCell
                ><div class="row-actions">
                  <Button
                    variant="outline"
                    size="sm"
                    :disabled="item.status !== 'ready' || previewBusy"
                    @click="previewMaterial(item)"
                    >预览</Button
                  ><template v-if="canEdit(item.owner_id)"
                    ><Button
                      variant="ghost"
                      size="sm"
                      @click="openMetadata(item)"
                      >编辑</Button
                    ><Button variant="ghost" size="sm" @click="openUpload(item)"
                      >替换文件</Button
                    ><Button
                      variant="ghost"
                      size="sm"
                      class="danger"
                      @click="
                        pendingDelete = item;
                        deleteError = '';
                      "
                      >删除</Button
                    ></template
                  ><span v-else class="status-badge">共享只读</span>
                </div></TableCell
              ></TableRow
            ></TableBody
          ></Table
        >
      </div>
      <div v-if="!filtered.length && !loading" class="admin-empty">
        <h3>{{ materials.length ? "没有匹配的资料" : "资料库还是空的" }}</h3>
        <p>
          {{
            materials.length
              ? "请调整搜索或筛选条件。"
              : "上传课件、文本或视频，稍后再关联章节。"
          }}
        </p>
      </div>
      <AdminPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="filtered.length"
        label="份资料"
      />
    </div>
    <AdminDialog
      v-model:open="uploadOpen"
      :title="uploadDraft.material_id ? '替换资料文件' : '上传课程材料'"
      description="资料独立存放，章节只记录关联。替换文件将保留资料 ID。"
      wide
    >
      <form class="teacher-form" @submit.prevent="upload">
        <p v-if="uploadError" class="error" role="alert">{{ uploadError }}</p>
        <label
          >资料名称<input v-model="uploadDraft.title" required maxlength="200"
        /></label>
        <ContentChapterSelect
          v-model="uploadDraft.chapter_ids"
          :chapters="chapters"
        />
        <label
          >文件<input
            type="file"
            :required="!uploadFile"
            accept=".txt,.md,.pdf,.ppt,.pptx,.mp4,.webm,.mov"
            @change="selectFile"
          /><span v-if="uploadFile" class="field-hint"
            >已选择：{{ uploadFile.name }}</span
          ></label
        >
        <div class="form-actions">
          <Button variant="outline" :disabled="busy" @click="uploadOpen = false"
            >取消</Button
          ><Button type="submit" :disabled="busy || !uploadFile">{{
            busy
              ? "正在上传…"
              : uploadDraft.material_id
                ? "替换文件"
                : "上传资料"
          }}</Button>
        </div>
      </form>
    </AdminDialog>
    <AdminDialog
      v-model:open="metadataOpen"
      title="编辑资料信息"
      description="修改名称或章节关联不会改变原有活动中的资料引用。"
    >
      <form class="teacher-form" @submit.prevent="saveMetadata">
        <p v-if="metadataError" class="error" role="alert">
          {{ metadataError }}
        </p>
        <label
          >资料名称<input
            v-model="metadata.title"
            required
            maxlength="200" /></label
        ><ContentChapterSelect
          v-model="metadata.chapter_ids"
          :chapters="chapters"
        />
        <div class="form-actions">
          <Button
            variant="outline"
            :disabled="busy"
            @click="metadataOpen = false"
            >取消</Button
          ><Button type="submit" :disabled="busy">{{
            busy ? "正在保存…" : "保存资料"
          }}</Button>
        </div>
      </form>
    </AdminDialog>
    <AdminDialog
      :open="!!pendingDelete"
      title="删除资料"
      :description="`确认删除「${pendingDelete?.title ?? ''}」？正在被活动引用的资料需要先解除引用。`"
      @update:open="
        (open) => {
          if (!open) pendingDelete = null;
        }
      "
      ><p v-if="deleteError" class="error" role="alert">{{ deleteError }}</p>
      <div class="form-actions">
        <Button variant="outline" :disabled="busy" @click="pendingDelete = null"
          >取消</Button
        ><Button
          variant="destructive"
          :disabled="busy"
          @click="deleteMaterial"
          >{{ busy ? "正在删除…" : "确认删除" }}</Button
        >
      </div></AdminDialog
    >
    <AdminDialog
      :open="!!preview"
      :title="preview?.title ?? '预览资料'"
      description="资料库预览"
      wide
      @update:open="
        (open) => {
          if (!open) closePreview();
        }
      "
      ><template v-if="preview"
        ><iframe
          v-if="['pdf', 'presentation'].includes(preview.kind)"
          :src="preview.url"
          :title="preview.title"
          class="content-preview-frame"
        /><video
          v-else-if="preview.kind === 'video'"
          :src="preview.url"
          controls
          class="content-preview-video"
        />
        <pre v-else class="content-preview-text">{{ preview.text }}</pre>
      </template></AdminDialog
    >
  </section>
</template>
<script setup lang="ts">
import {
  computed,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { Button } from "../components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "../components/ui/table";
import AdminDialog from "../components/admin/AdminDialog.vue";
import AdminPagination from "../components/admin/AdminPagination.vue";
import ContentChapterSelect from "./ContentChapterSelect.vue";
import { api, notify } from "../shared/api";
import { useSession } from "../shared/session";
import type { Chapter, Material } from "../shared/types";
import { chapterNames, matchesChapter } from "../shared/teacherContent";
import type { TeacherMaterial } from "../shared/teacherContent";
defineProps<{ chapters: Chapter[] }>();
const session = useSession();
function canEdit(ownerId: number | null) {
  return session.user?.role === "admin" || ownerId === session.user?.id;
}
const materials = ref<TeacherMaterial[]>([]),
  search = ref(""),
  chapter = ref(""),
  status = ref(""),
  page = ref(1),
  pageSize = ref(10),
  error = ref(""),
  loading = ref(false),
  busy = ref(false);
const uploadOpen = ref(false),
  uploadError = ref(""),
  uploadFile = ref<File>(),
  uploadDraft = reactive({
    title: "",
    chapter_ids: [] as number[],
    material_id: null as number | null,
  });
const metadataOpen = ref(false),
  metadataError = ref(""),
  metadata = reactive({ id: 0, title: "", chapter_ids: [] as number[] });
const pendingDelete = ref<TeacherMaterial | null>(null),
  deleteError = ref("");
const preview = ref<{
    title: string;
    kind: string;
    url: string;
    text: string;
  } | null>(null),
  previewBusy = ref(false);
const kinds: Record<string, string> = {
  text: "文本",
  pdf: "PDF",
  presentation: "演示文稿",
  video: "视频",
};
const statuses: Record<string, string> = {
  ready: "可使用",
  queued: "排队中",
  processing: "处理中",
  failed: "处理失败",
  missing: "媒体缺失",
};
const filtered = computed(() =>
  materials.value.filter(
    (item) =>
      (!status.value || item.status === status.value) &&
      matchesChapter(item.chapter_ids ?? [], chapter.value) &&
      `${item.title} ${item.id}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()),
  ),
);
const paged = computed(() =>
  filtered.value.slice(
    (page.value - 1) * pageSize.value,
    page.value * pageSize.value,
  ),
);
watch([search, chapter, status, pageSize], () => {
  page.value = 1;
});
watch(
  () => filtered.value.length,
  (total) => {
    page.value = Math.min(
      page.value,
      Math.max(1, Math.ceil(total / pageSize.value)),
    );
  },
);
let timer: ReturnType<typeof setTimeout> | undefined,
  active = true;
let previewGeneration = 0;
async function load() {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  clearTimeout(timer);
  try {
    materials.value = await api.get<TeacherMaterial[]>("/teacher/materials");
    if (
      active &&
      materials.value.some((item) =>
        ["queued", "processing"].includes(item.status),
      )
    )
      timer = setTimeout(() => void load(), 2000);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "资料库加载失败";
  } finally {
    loading.value = false;
  }
}
function openUpload(item?: TeacherMaterial) {
  if (item && uploadDraft.material_id !== item.id) {
    Object.assign(uploadDraft, {
      title: item.title,
      chapter_ids: [...(item.chapter_ids ?? [])],
      material_id: item.id,
    });
    uploadFile.value = undefined;
  } else if (!item && uploadDraft.material_id !== null) {
    Object.assign(uploadDraft, {
      title: "",
      chapter_ids: [],
      material_id: null,
    });
    uploadFile.value = undefined;
  }
  uploadError.value = "";
  uploadOpen.value = true;
}
function selectFile(event: Event) {
  uploadFile.value =
    (event.target as HTMLInputElement).files?.[0] ?? uploadFile.value;
}
async function upload() {
  if (busy.value || !uploadFile.value) return;
  busy.value = true;
  uploadError.value = "";
  try {
    const form = new FormData();
    form.set("file", uploadFile.value);
    form.set("title", uploadDraft.title);
    form.set("chapter_ids", JSON.stringify(uploadDraft.chapter_ids));
    if (uploadDraft.material_id !== null)
      form.set("material_id", String(uploadDraft.material_id));
    await api.request("/teacher/materials/upload", {
      method: "POST",
      body: form,
    });
    uploadOpen.value = false;
    uploadFile.value = undefined;
    Object.assign(uploadDraft, {
      title: "",
      chapter_ids: [],
      material_id: null,
    });
    await load();
    notify("资料已上传，处理状态将自动更新。");
  } catch (reason) {
    uploadError.value = reason instanceof Error ? reason.message : "上传失败";
  } finally {
    busy.value = false;
  }
}
function openMetadata(item: TeacherMaterial) {
  if (metadata.id !== item.id)
    Object.assign(metadata, {
      id: item.id,
      title: item.title,
      chapter_ids: [...(item.chapter_ids ?? [])],
    });
  metadataError.value = "";
  metadataOpen.value = true;
}
async function saveMetadata() {
  if (busy.value) return;
  busy.value = true;
  metadataError.value = "";
  try {
    await api.put(`/teacher/materials/${metadata.id}`, {
      title: metadata.title,
      chapter_ids: metadata.chapter_ids,
    });
    metadataOpen.value = false;
    await load();
    notify("资料信息已保存。");
  } catch (reason) {
    metadataError.value = reason instanceof Error ? reason.message : "保存失败";
  } finally {
    busy.value = false;
  }
}
async function deleteMaterial() {
  if (busy.value || !pendingDelete.value) return;
  busy.value = true;
  deleteError.value = "";
  try {
    await api.request(`/teacher/materials/${pendingDelete.value.id}`, {
      method: "DELETE",
    });
    pendingDelete.value = null;
    await load();
    notify("资料已删除。");
  } catch (reason) {
    deleteError.value = reason instanceof Error ? reason.message : "删除失败";
  } finally {
    busy.value = false;
  }
}
function closePreview() {
  previewGeneration++;
  if (preview.value?.url) URL.revokeObjectURL(preview.value.url);
  preview.value = null;
}
async function previewMaterial(item: TeacherMaterial) {
  if (previewBusy.value) return;
  previewBusy.value = true;
  error.value = "";
  closePreview();
  const generation = previewGeneration;
  try {
    if (item.kind === "text") {
      const content = await api.get<Material>(`/teacher/materials/${item.id}`);
      if (!active || generation !== previewGeneration) return;
      preview.value = {
        title: item.title,
        kind: item.kind,
        url: "",
        text: content.pages
          .map((p) => `第${p.page_no}页\n${p.text}`)
          .join("\n\n"),
      };
    } else {
      const blob = await api.blob(`/teacher/materials/${item.id}/file`);
      if (!active || generation !== previewGeneration) return;
      preview.value = {
        title: item.title,
        kind: item.kind,
        url: URL.createObjectURL(blob),
        text: "",
      };
    }
  } catch (reason) {
    if (active && generation === previewGeneration)
      error.value = reason instanceof Error ? reason.message : "预览失败";
  } finally {
    previewBusy.value = false;
  }
}
onMounted(() => void load());
onActivated(() => {
  active = true;
  if (
    materials.value.some((item) =>
      ["queued", "processing"].includes(item.status),
    )
  )
    void load();
});
onDeactivated(() => {
  active = false;
  clearTimeout(timer);
  uploadOpen.value = false;
  metadataOpen.value = false;
  pendingDelete.value = null;
  closePreview();
});
onBeforeUnmount(() => {
  active = false;
  clearTimeout(timer);
  closePreview();
});
</script>
