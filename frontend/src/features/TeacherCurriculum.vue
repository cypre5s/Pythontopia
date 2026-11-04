<template>
  <section class="curriculum-workspace">
    <p class="notice">
      课程底座为
      Python语言基础。共享种子供所有班级复用，教师可以创建和编辑自己的章节与知识点；管理权限由服务端再次核实。
    </p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-if="pendingDelete" class="notice" role="alert">
      <strong>删除{{ pendingDelete.title }}？</strong>
      <p>
        仅删除没有活动、任务或学习记录引用的自建内容；有引用时服务端会拒绝。
      </p>
      <button class="danger" :disabled="busy" @click="remove">确认删除</button
      ><button @click="pendingDelete = null">取消</button>
    </div>
    <p v-if="loading">正在读取课程结构…</p>
    <template v-else
      ><form class="teacher-form" @submit.prevent="saveChapter">
        <h2>{{ chapter.id === null ? "新增章节" : "编辑章节" }}</h2>
        <label
          >章节标题<input
            v-model="chapter.title"
            required
            maxlength="200" /></label
        ><label
          >显示顺序（留空自动）<input
            v-model.number="chapter.order_index"
            type="number"
            min="1"
            max="10000" /></label
        ><button class="primary" :disabled="busy || !chapter.title.trim()">
          保存章节</button
        ><button v-if="chapter.id !== null" type="button" @click="resetChapter">
          取消编辑
        </button>
      </form>
      <div class="table-scroll">
        <div class="admin-table-scroll"><table>
          <thead>
            <tr>
              <th>顺序</th>
              <th>章节</th>
              <th>归属</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in chapters" :key="item.id">
              <td>{{ item.order_index }}</td>
              <td>
                {{ item.title }}<small>#{{ item.id }}</small>
              </td>
              <td>{{ item.owner_id === null ? "共享课程" : "教师自建" }}</td>
              <td>
                <template v-if="editable(item.owner_id)"
                  ><button @click="editChapter(item)">编辑章节</button
                  ><button
                    @click="
                      pendingDelete = {
                        kind: 'chapter',
                        id: item.id,
                        title: item.title,
                      }
                    "
                  >
                    删除章节
                  </button></template
                ><span v-else class="muted">只读</span>
              </td>
            </tr>
          </tbody>
        </table></div>
      </div>
      <form class="teacher-form" @submit.prevent="saveKnowledgePoint">
        <h2>{{ knowledgePoint.id === null ? "新增知识点" : "编辑知识点" }}</h2>
        <label
          >所属章节<select
            v-model.number="knowledgePoint.chapter_id"
            :disabled="knowledgePoint.id !== null"
            required
          >
            <option v-for="item in chapters" :key="item.id" :value="item.id">
              {{ item.title }}
            </option>
          </select></label
        ><label
          >知识点名称<input
            v-model="knowledgePoint.title"
            required
            maxlength="200" /></label
        ><button
          class="primary"
          :disabled="
            busy || !knowledgePoint.title.trim() || !knowledgePoint.chapter_id
          "
        >
          保存知识点</button
        ><button
          v-if="knowledgePoint.id !== null"
          type="button"
          @click="resetKnowledgePoint"
        >
          取消编辑
        </button>
      </form>
      <label
        >按章节查看知识点<select v-model.number="filterChapter">
          <option :value="0">全部章节</option>
          <option v-for="item in chapters" :key="item.id" :value="item.id">
            {{ item.title }}
          </option>
        </select></label
      >
      <div class="table-scroll">
        <div class="admin-table-scroll"><table>
          <thead>
            <tr>
              <th>知识点</th>
              <th>章节</th>
              <th>归属</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredKnowledgePoints" :key="item.id">
              <td>
                {{ item.title }}<small>#{{ item.id }}</small>
              </td>
              <td>
                {{ chapters.find((c) => c.id === item.chapter_id)?.title }}
              </td>
              <td>{{ item.owner_id === null ? "共享课程" : "教师自建" }}</td>
              <td>
                <template v-if="editable(item.owner_id)"
                  ><button @click="editKnowledgePoint(item)">编辑知识点</button
                  ><button
                    @click="
                      pendingDelete = {
                        kind: 'knowledge',
                        id: item.id,
                        title: item.title,
                      }
                    "
                  >
                    删除知识点
                  </button></template
                ><span v-else class="muted">只读</span>
              </td>
            </tr>
          </tbody>
        </table></div>
      </div>
      <p v-if="!filteredKnowledgePoints.length" class="notice">
        此章节尚未配置知识点。创建后可在活动编辑中绑定，任务继续通过活动引用课程内容。
      </p></template
    >
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { api, notify } from "../shared/api";
import { useSession } from "../shared/session";
import { canEditCurriculum } from "../shared/curriculum";
import type { TeacherChapter, KnowledgePoint } from "../shared/curriculum";
const emit = defineEmits<{ updated: [] }>(),
  session = useSession(),
  chapters = ref<TeacherChapter[]>([]),
  points = ref<KnowledgePoint[]>([]),
  loading = ref(true),
  busy = ref(false),
  error = ref(""),
  pendingDelete = ref<{
    kind: "chapter" | "knowledge";
    id: number;
    title: string;
  } | null>(null),
  filterChapter = ref(0),
  chapter = reactive({
    id: null as number | null,
    title: "",
    order_index: "" as number | string,
  }),
  knowledgePoint = reactive({
    id: null as number | null,
    chapter_id: 0,
    title: "",
  });
const filteredKnowledgePoints = computed(() =>
  points.value.filter(
    (p) => !filterChapter.value || p.chapter_id === filterChapter.value,
  ),
);
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
  if (!knowledgePoint.chapter_id)
    knowledgePoint.chapter_id = chapters.value[0]?.id ?? 0;
}
function resetChapter() {
  chapter.id = null;
  chapter.title = "";
  chapter.order_index = "";
}
function resetKnowledgePoint() {
  knowledgePoint.id = null;
  knowledgePoint.title = "";
}
function editChapter(item: TeacherChapter) {
  Object.assign(chapter, {
    id: item.id,
    title: item.title,
    order_index: item.order_index,
  });
}
function editKnowledgePoint(item: KnowledgePoint) {
  Object.assign(knowledgePoint, {
    id: item.id,
    chapter_id: item.chapter_id,
    title: item.title,
  });
}
async function saveChapter() {
  busy.value = true;
  error.value = "";
  try {
    const body = {
      title: chapter.title.trim(),
      ...(chapter.order_index !== ""
        ? { order_index: Number(chapter.order_index) }
        : {}),
    };
    if (chapter.id === null) await api.post("/teacher/chapters", body);
    else await api.put(`/teacher/chapters/${chapter.id}`, body);
    resetChapter();
    await load();
    emit("updated");
    notify("章节已保存。");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "保存失败";
  } finally {
    busy.value = false;
  }
}
async function saveKnowledgePoint() {
  busy.value = true;
  error.value = "";
  try {
    const body = {
      title: knowledgePoint.title.trim(),
    };
    if (knowledgePoint.id === null)
      await api.post("/teacher/knowledge-points", {
        ...body,
        chapter_id: knowledgePoint.chapter_id,
      });
    else await api.put(`/teacher/knowledge-points/${knowledgePoint.id}`, body);
    resetKnowledgePoint();
    await load();
    emit("updated");
    notify("知识点已保存，可在活动中绑定。");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "保存失败";
  } finally {
    busy.value = false;
  }
}
async function remove() {
  if (!pendingDelete.value) return;
  busy.value = true;
  error.value = "";
  try {
    const item = pendingDelete.value;
    await api.request(
      `/teacher/${item.kind === "chapter" ? "chapters" : "knowledge-points"}/${item.id}`,
      { method: "DELETE" },
    );
    if (item.kind === "chapter" && chapter.id === item.id) resetChapter();
    if (item.kind === "knowledge" && knowledgePoint.id === item.id)
      resetKnowledgePoint();
    pendingDelete.value = null;
    await load();
    emit("updated");
    notify("未被引用的内容已删除。");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "删除失败";
  } finally {
    busy.value = false;
  }
}
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
