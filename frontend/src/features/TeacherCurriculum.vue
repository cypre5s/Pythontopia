<template>
  <section class="curriculum-workspace">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在读取课程结构…</p>
    <template v-else>
      <div
        ref="tabList"
        class="admin-subtabs"
        role="tablist"
        aria-label="课程结构"
      >
        <Button
          id="curriculum-chapters-tab"
          variant="ghost"
          role="tab"
          class="admin-tab"
          :aria-selected="activeTab === 'chapters'"
          aria-controls="curriculum-chapters-panel"
          :tabindex="activeTab === 'chapters' ? 0 : -1"
          @click="activeTab = 'chapters'"
          @keydown="moveTab($event, 'chapters')"
          >章节 <span class="count-badge">{{ chapters.length }}</span></Button
        >
        <Button
          id="curriculum-knowledge-tab"
          variant="ghost"
          role="tab"
          class="admin-tab"
          :aria-selected="activeTab === 'knowledge'"
          aria-controls="curriculum-knowledge-panel"
          :tabindex="activeTab === 'knowledge' ? 0 : -1"
          @click="activeTab = 'knowledge'"
          @keydown="moveTab($event, 'knowledge')"
          >知识点 <span class="count-badge">{{ points.length }}</span></Button
        >
      </div>
      <section
        v-show="activeTab === 'chapters'"
        id="curriculum-chapters-panel"
        class="curriculum-tab-panel"
        role="tabpanel"
        aria-labelledby="curriculum-chapters-tab"
        tabindex="0"
      >
        <div class="table-toolbar list-toolbar">
          <label class="search-field"
            ><span class="sr-only">搜索章节</span
            ><input
              v-model="chapterSearch"
              type="search"
              placeholder="搜索章节标题" /></label
          ><Button :disabled="busy" @click="startChapter">新增章节</Button>
        </div>
        <div class="admin-panel">
          <div class="admin-table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">顺序</th>
                  <th scope="col">章节</th>
                  <th scope="col">知识点</th>
                  <th scope="col">归属</th>
                  <th scope="col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in pagedChapters" :key="item.id">
                  <td>{{ item.order_index }}</td>
                  <td>
                    {{ item.title }}<small>#{{ item.id }}</small>
                  </td>
                  <td>
                    {{ points.filter((p) => p.chapter_id === item.id).length }}
                  </td>
                  <td>
                    <span class="status-badge">{{
                      item.owner_id === null ? "共享课程" : "教师自建"
                    }}</span>
                  </td>
                  <td>
                    <div v-if="editable(item.owner_id)" class="row-actions">
                      <Button
                        variant="ghost"
                        size="sm"
                        :disabled="busy"
                        @click="editChapter(item)"
                        >编辑章节</Button
                      ><Button
                        variant="ghost"
                        size="sm"
                        class="danger"
                        :disabled="busy"
                        @click="askDelete('chapter', item)"
                        >删除章节</Button
                      >
                    </div>
                    <span v-else class="muted">只读</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!filteredChapters.length" class="admin-empty">
              {{
                chapterSearch
                  ? "没有匹配的章节，请调整搜索关键词。"
                  : "还没有章节，先创建第一个章节。"
              }}
            </p>
          </div>
          <AdminPagination
            v-model:page="chapterPage"
            v-model:page-size="chapterPageSize"
            :total="filteredChapters.length"
            label="个章节"
          />
        </div>
      </section>
      <section
        v-show="activeTab === 'knowledge'"
        id="curriculum-knowledge-panel"
        class="curriculum-tab-panel"
        role="tabpanel"
        aria-labelledby="curriculum-knowledge-tab"
        tabindex="0"
      >
        <div class="table-toolbar list-toolbar">
          <label
            >按章节查看知识点<select v-model.number="filterChapter">
              <option :value="0">全部章节</option>
              <option v-for="item in chapters" :key="item.id" :value="item.id">
                {{ item.title }}
              </option>
            </select></label
          ><label class="search-field"
            ><span class="sr-only">搜索知识点</span
            ><input
              v-model="knowledgeSearch"
              type="search"
              placeholder="搜索知识点名称" /></label
          ><Button
            :disabled="busy || !chapters.length"
            @click="startKnowledgePoint"
            >新增知识点</Button
          >
        </div>
        <div class="admin-panel">
          <div class="admin-table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">知识点</th>
                  <th scope="col">所属章节</th>
                  <th scope="col">归属</th>
                  <th scope="col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in pagedKnowledgePoints" :key="item.id">
                  <td>
                    {{ item.title }}<small>#{{ item.id }}</small>
                  </td>
                  <td>
                    {{ chapters.find((c) => c.id === item.chapter_id)?.title }}
                  </td>
                  <td>
                    <span class="status-badge">{{
                      item.owner_id === null ? "共享课程" : "教师自建"
                    }}</span>
                  </td>
                  <td>
                    <div v-if="editable(item.owner_id)" class="row-actions">
                      <Button
                        variant="ghost"
                        size="sm"
                        :disabled="busy"
                        @click="editKnowledgePoint(item)"
                        >编辑知识点</Button
                      ><Button
                        variant="ghost"
                        size="sm"
                        class="danger"
                        :disabled="busy"
                        @click="askDelete('knowledge', item)"
                        >删除知识点</Button
                      >
                    </div>
                    <span v-else class="muted">只读</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!filteredKnowledgePoints.length" class="admin-empty">
              {{
                knowledgeSearch
                  ? "没有匹配的知识点，请调整搜索关键词。"
                  : "此章节尚未配置知识点。创建后可在活动编辑中绑定。"
              }}
            </p>
          </div>
          <AdminPagination
            v-model:page="knowledgePage"
            v-model:page-size="knowledgePageSize"
            :total="filteredKnowledgePoints.length"
            label="个知识点"
          />
        </div>
      </section>
    </template>
    <AdminDialog
      :open="chapterEditorOpen"
      :title="chapter.id === null ? '新增章节' : '编辑章节'"
      description="填写章节标题与学习顺序，留空顺序时自动排在末尾。"
      @update:open="setChapterOpen"
    >
      <p v-if="chapterError" class="error" role="alert">{{ chapterError }}</p>
      <form class="teacher-form" @submit.prevent="saveChapter">
        <label
          >章节标题<input
            v-model="chapter.title"
            :disabled="busy"
            required
            maxlength="200"
            placeholder="例如：循环结构与应用"
            autofocus /></label
        ><label
          >显示顺序（留空自动）<input
            v-model.number="chapter.order_index"
            :disabled="busy"
            type="number"
            min="1"
            max="10000"
            placeholder="自动排在末尾"
        /></label>
        <div class="form-actions">
          <Button
            variant="outline"
            :disabled="busy"
            @click="setChapterOpen(false)"
            >取消</Button
          ><Button type="submit" :disabled="busy || !chapter.title.trim()">{{
            busy ? "正在保存…" : "保存章节"
          }}</Button>
        </div>
      </form>
    </AdminDialog>
    <AdminDialog
      :open="knowledgeEditorOpen"
      :title="knowledgePoint.id === null ? '新增知识点' : '编辑知识点'"
      description="将知识点归入课程章节，之后可在活动编辑中绑定。"
      @update:open="setKnowledgeOpen"
    >
      <p v-if="knowledgeError" class="error" role="alert">
        {{ knowledgeError }}
      </p>
      <form class="teacher-form" @submit.prevent="saveKnowledgePoint">
        <label
          >所属章节<select
            v-model.number="knowledgePoint.chapter_id"
            aria-label="所属章节"
            :disabled="busy || knowledgePoint.id !== null"
            required
          >
            <option v-for="item in chapters" :key="item.id" :value="item.id">
              {{ item.title }}
            </option></select
          ><span v-if="knowledgePoint.id !== null" class="field-hint"
            >已有知识点的所属章节不可更改。</span
          ></label
        ><label
          >知识点名称<input
            v-model="knowledgePoint.title"
            :disabled="busy"
            required
            maxlength="200"
            placeholder="例如：for 循环与 range"
            autofocus
        /></label>
        <div class="form-actions">
          <Button
            variant="outline"
            :disabled="busy"
            @click="setKnowledgeOpen(false)"
            >取消</Button
          ><Button
            type="submit"
            :disabled="
              busy || !knowledgePoint.title.trim() || !knowledgePoint.chapter_id
            "
            >{{ busy ? "正在保存…" : "保存知识点" }}</Button
          >
        </div>
      </form>
    </AdminDialog>
    <AdminDialog
      :open="!!pendingDelete"
      title="删除课程内容"
      :description="
        '确认删除「' +
        (pendingDelete?.title ?? '') +
        '」？仅可删除未被活动、任务或学习记录引用的自建内容。'
      "
      @update:open="setDeleteOpen"
    >
      <p v-if="deleteError" class="error" role="alert">{{ deleteError }}</p>
      <div class="form-actions">
        <Button variant="outline" :disabled="busy" @click="setDeleteOpen(false)"
          >取消</Button
        ><Button variant="destructive" :disabled="busy" @click="remove">{{
          busy ? "正在删除…" : "确认删除"
        }}</Button>
      </div>
    </AdminDialog>
  </section>
</template>
<script setup lang="ts">
import {
  computed,
  nextTick,
  onDeactivated,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { Button } from "../components/ui/button";
import AdminDialog from "../components/admin/AdminDialog.vue";
import AdminPagination from "../components/admin/AdminPagination.vue";
import { api, notify } from "../shared/api";
import { useSession } from "../shared/session";
import { canEditCurriculum } from "../shared/curriculum";
import type { TeacherChapter, KnowledgePoint } from "../shared/curriculum";
const emit = defineEmits<{ updated: [] }>();
const session = useSession();
const chapters = ref<TeacherChapter[]>([]),
  points = ref<KnowledgePoint[]>([]);
const loading = ref(true),
  busy = ref(false),
  error = ref("");
const chapterError = ref(""),
  knowledgeError = ref(""),
  deleteError = ref("");
const activeTab = ref<"chapters" | "knowledge">("chapters"),
  tabList = ref<HTMLDivElement | null>(null);
const chapterSearch = ref(""),
  knowledgeSearch = ref(""),
  filterChapter = ref(0);
const chapterPage = ref(1),
  knowledgePage = ref(1),
  chapterPageSize = ref(10),
  knowledgePageSize = ref(10);
const chapterEditorOpen = ref(false),
  knowledgeEditorOpen = ref(false);
const pendingDelete = ref<{
  kind: "chapter" | "knowledge";
  id: number;
  title: string;
} | null>(null);
const chapter = reactive({
  id: null as number | null,
  title: "",
  order_index: "" as number | string,
});
const knowledgePoint = reactive({
  id: null as number | null,
  chapter_id: 0,
  title: "",
});
const chapterDrafts = new Map<number | null, typeof chapter>();
const knowledgeDrafts = new Map<number | null, typeof knowledgePoint>();
let chapterDraftActive = false,
  knowledgeDraftActive = false;
const filteredChapters = computed(() =>
  chapters.value.filter((item) =>
    item.title.toLowerCase().includes(chapterSearch.value.trim().toLowerCase()),
  ),
);
const filteredKnowledgePoints = computed(() =>
  points.value.filter(
    (item) =>
      (!filterChapter.value || item.chapter_id === filterChapter.value) &&
      item.title
        .toLowerCase()
        .includes(knowledgeSearch.value.trim().toLowerCase()),
  ),
);
const chapterPageCount = computed(() =>
  Math.max(1, Math.ceil(filteredChapters.value.length / chapterPageSize.value)),
);
const knowledgePageCount = computed(() =>
  Math.max(
    1,
    Math.ceil(filteredKnowledgePoints.value.length / knowledgePageSize.value),
  ),
);
const pagedChapters = computed(() =>
  filteredChapters.value.slice(
    (chapterPage.value - 1) * chapterPageSize.value,
    chapterPage.value * chapterPageSize.value,
  ),
);
const pagedKnowledgePoints = computed(() =>
  filteredKnowledgePoints.value.slice(
    (knowledgePage.value - 1) * knowledgePageSize.value,
    knowledgePage.value * knowledgePageSize.value,
  ),
);
watch([chapterSearch, chapterPageSize], () => {
  chapterPage.value = 1;
});
watch([knowledgeSearch, filterChapter, knowledgePageSize], () => {
  knowledgePage.value = 1;
});
watch(chapterPageCount, (count) => {
  chapterPage.value = Math.min(chapterPage.value, count);
});
watch(knowledgePageCount, (count) => {
  knowledgePage.value = Math.min(knowledgePage.value, count);
});
async function moveTab(
  event: KeyboardEvent,
  current: "chapters" | "knowledge",
) {
  if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
  event.preventDefault();
  activeTab.value =
    event.key === "Home"
      ? "chapters"
      : event.key === "End"
        ? "knowledge"
        : current === "chapters"
          ? "knowledge"
          : "chapters";
  await nextTick();
  tabList.value
    ?.querySelector<HTMLButtonElement>('[aria-selected="true"]')
    ?.focus();
}
function editable(owner: number | null) {
  return canEditCurriculum(session.user?.role, session.user?.id, owner);
}
async function load() {
  const data = await api.get<{
    chapters: TeacherChapter[];
    knowledge_points: KnowledgePoint[];
  }>("/teacher/courses");
  chapters.value = data.chapters;
  points.value = data.knowledge_points ?? [];
  if (
    filterChapter.value &&
    !chapters.value.some((c) => c.id === filterChapter.value)
  )
    filterChapter.value = 0;
  if (!knowledgePoint.chapter_id)
    knowledgePoint.chapter_id = chapters.value[0]?.id ?? 0;
}
function setChapterOpen(open: boolean) {
  if (!busy.value) chapterEditorOpen.value = open;
}
function setKnowledgeOpen(open: boolean) {
  if (!busy.value) knowledgeEditorOpen.value = open;
}
function setDeleteOpen(open: boolean) {
  if (!busy.value && !open) pendingDelete.value = null;
}
function openChapter(item?: TeacherChapter) {
  if (chapterDraftActive) chapterDrafts.set(chapter.id, { ...chapter });
  chapterDraftActive = true;
  const id = item?.id ?? null;
  Object.assign(
    chapter,
    chapterDrafts.get(id) ?? {
      id,
      title: item?.title ?? "",
      order_index: item?.order_index ?? "",
    },
  );
  chapterError.value = "";
  chapterEditorOpen.value = true;
}
function startChapter() {
  openChapter();
}
function editChapter(item: TeacherChapter) {
  openChapter(item);
}
function openKnowledgePoint(item?: KnowledgePoint) {
  if (knowledgeDraftActive)
    knowledgeDrafts.set(knowledgePoint.id, { ...knowledgePoint });
  knowledgeDraftActive = true;
  const id = item?.id ?? null;
  Object.assign(
    knowledgePoint,
    knowledgeDrafts.get(id) ?? {
      id,
      title: item?.title ?? "",
      chapter_id:
        item?.chapter_id ?? (filterChapter.value || chapters.value[0]?.id || 0),
    },
  );
  knowledgeError.value = "";
  knowledgeEditorOpen.value = true;
}
function startKnowledgePoint() {
  openKnowledgePoint();
}
function editKnowledgePoint(item: KnowledgePoint) {
  openKnowledgePoint(item);
}
function askDelete(
  kind: "chapter" | "knowledge",
  item: { id: number; title: string },
) {
  deleteError.value = "";
  pendingDelete.value = { kind, id: item.id, title: item.title };
}
async function saveChapter() {
  if (busy.value) return;
  busy.value = true;
  chapterError.value = "";
  try {
    const body = {
      title: chapter.title.trim(),
      ...(chapter.order_index !== ""
        ? { order_index: Number(chapter.order_index) }
        : {}),
    };
    if (chapter.id === null) await api.post("/teacher/chapters", body);
    else await api.put("/teacher/chapters/" + chapter.id, body);
    chapterDrafts.delete(chapter.id);
    chapterDraftActive = false;
    Object.assign(chapter, { id: null, title: "", order_index: "" });
    chapterEditorOpen.value = false;
    await load();
    emit("updated");
    notify("章节已保存。");
  } catch (e) {
    const message = e instanceof Error ? e.message : "保存失败";
    if (chapterEditorOpen.value) chapterError.value = message;
    else error.value = message;
  } finally {
    busy.value = false;
  }
}
async function saveKnowledgePoint() {
  if (busy.value) return;
  busy.value = true;
  knowledgeError.value = "";
  try {
    const body = { title: knowledgePoint.title.trim() };
    if (knowledgePoint.id === null)
      await api.post("/teacher/knowledge-points", {
        ...body,
        chapter_id: knowledgePoint.chapter_id,
      });
    else await api.put("/teacher/knowledge-points/" + knowledgePoint.id, body);
    knowledgeDrafts.delete(knowledgePoint.id);
    knowledgeDraftActive = false;
    Object.assign(knowledgePoint, {
      id: null,
      title: "",
      chapter_id: filterChapter.value || chapters.value[0]?.id || 0,
    });
    knowledgeEditorOpen.value = false;
    await load();
    emit("updated");
    notify("知识点已保存，可在活动中绑定。");
  } catch (e) {
    const message = e instanceof Error ? e.message : "保存失败";
    if (knowledgeEditorOpen.value) knowledgeError.value = message;
    else error.value = message;
  } finally {
    busy.value = false;
  }
}
async function remove() {
  const item = pendingDelete.value;
  if (!item || busy.value) return;
  busy.value = true;
  deleteError.value = "";
  try {
    await api.request(
      "/teacher/" +
        (item.kind === "chapter" ? "chapters" : "knowledge-points") +
        "/" +
        item.id,
      { method: "DELETE" },
    );
    if (item.kind === "chapter") {
      chapterDrafts.delete(item.id);
      if (chapter.id === item.id) {
        chapterDraftActive = false;
        Object.assign(chapter, { id: null, title: "", order_index: "" });
      }
    } else {
      knowledgeDrafts.delete(item.id);
      if (knowledgePoint.id === item.id) {
        knowledgeDraftActive = false;
        Object.assign(knowledgePoint, {
          id: null,
          title: "",
          chapter_id: chapters.value[0]?.id ?? 0,
        });
      }
    }
    pendingDelete.value = null;
    await load();
    emit("updated");
    notify("未被引用的内容已删除。");
  } catch (e) {
    const message = e instanceof Error ? e.message : "删除失败";
    if (pendingDelete.value) deleteError.value = message;
    else error.value = message;
  } finally {
    busy.value = false;
  }
}
onDeactivated(() => {
  chapterEditorOpen.value = false;
  knowledgeEditorOpen.value = false;
  pendingDelete.value = null;
});
onMounted(async () => {
  try {
    await load();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "课程结构读取失败";
  } finally {
    loading.value = false;
  }
});
</script>
