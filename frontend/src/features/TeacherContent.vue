<template>
  <div>
    <p v-if="error" class="error">{{ error }}</p>
    <TeacherCurriculum
      v-if="section === 'curriculum'"
      @updated="curriculumUpdated"
    /><template v-else-if="section === 'materials'"
      ><form class="teacher-form" @submit.prevent="upload">
        <h2>上传课程材料</h2>
        <label>资料名称<input v-model="uploadForm.title" required /></label
        ><label
          >所属章节<select v-model.number="uploadForm.chapter_id">
            <option v-for="c in courses.chapters" :key="c.id" :value="c.id">
              {{ c.title }}
            </option>
          </select></label
        ><label
          >更新已有材料（留空创建）<input
            v-model="uploadForm.material_id"
            type="number"
            placeholder="材料 ID" /></label
        ><label
          >文件<input
            type="file"
            accept=".pdf,.ppt,.pptx,.mp4,.webm,.txt,.md"
            required
            @change="selectFile" /></label
        ><button class="primary" :disabled="uploadBusy">
          {{ uploadBusy ? "上传中…" : "上传并处理" }}
        </button>
      </form>
      <p class="notice">
        PPT
        转换在受限工作进程中处理；依赖缺失会标明失败。更新文件产生新版本，旧引用保留原版本。
      </p>
      <button @click="loadMaterials">刷新状态</button>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>资料 / ID</th>
              <th>版本</th>
              <th>类型</th>
              <th>状态</th>
              <th>处理反馈</th>
              <th>预览</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in materials" :key="`${m.id}-${m.version}`">
              <td>
                {{ m.title }}<small>#{{ m.id }}</small>
              </td>
              <td>{{ m.version }}</td>
              <td>{{ m.kind }}</td>
              <td>{{ m.status }}</td>
              <td>{{ m.error ?? m.notice }}</td>
              <td>
                <button
                  :disabled="m.status !== 'ready' || !classroomId"
                  @click="previewMaterial(m)"
                >
                  预览
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <section v-if="preview" class="material-preview">
        <button @click="closePreview">收起预览</button
        ><iframe
          v-if="['pdf', 'presentation'].includes(preview.kind)"
          :src="preview.url"
          title="教师 PDF 预览"
        ></iframe
        ><video
          v-else-if="preview.kind === 'video'"
          :src="preview.url"
          controls
        ></video>
        <pre v-else>{{ preview.text }}</pre>
      </section></template
    ><template v-else-if="section === 'activities'"
      ><p class="notice">
        共享种子不可覆盖。创建教师自己的活动，将材料与 NPC
        绑定，再发布引用活动的新任务版本。
      </p>
      <form class="teacher-form" @submit.prevent="saveActivity">
        <h2>{{ activity.edit ? "编辑自建活动" : "新建活动" }}</h2>
        <label>活动 ID<input v-model="activity.id" required /></label
        ><label>标题<input v-model="activity.title" required /></label
        ><label
          >章节<select v-model.number="activity.chapter_id">
            <option v-for="c in courses.chapters" :key="c.id" :value="c.id">
              {{ c.title }}
            </option>
          </select></label
        ><label
          >类型<select v-model="activity.kind">
            <option value="lesson">教学</option>
            <option value="reading">阅读</option>
            <option value="practice">练习</option>
            <option value="assessment">测验</option>
          </select></label
        ><label
          >NPC<select v-model="activity.npc_key">
            <option v-for="n in courses.npcs" :key="n.key" :value="n.key">
              {{ n.name }}
            </option>
          </select></label
        ><label
          >材料<select v-model="activity.material_id">
            <option value="">无材料</option>
            <option
              v-for="m in materials"
              :key="`${m.id}-${m.version}`"
              :value="m.id"
            >
              {{ m.title }} · v{{ m.version }}
            </option>
          </select></label
        ><KnowledgePointSelect
          v-model="activity.knowledge_point_ids"
          :chapter-id="activity.chapter_id"
          :points="courses.knowledge_points ?? []"
        /><label class="checkbox-label"
          ><input
            v-model="activity.assistant_allowed"
            type="checkbox"
          />允许主动使用助教</label
        ><button class="primary">
          {{ activity.edit ? "保存新版本" : "创建活动" }}</button
        ><button
          v-if="activity.edit"
          type="button"
          @click="
            activity.edit = false;
            activity.id = '';
          "
        >
          另建活动
        </button>
      </form>
      <table>
        <thead>
          <tr>
            <th>活动</th>
            <th>NPC</th>
            <th>知识点</th>
            <th>版本</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in courses.activities" :key="a.id">
            <td>
              {{ a.title }}<small>{{ a.id }}</small>
            </td>
            <td>{{ a.npc_key }}</td>
            <td>
              {{
                (a.knowledge_point_ids ?? [])
                  .map(
                    (id) =>
                      courses.knowledge_points?.find((k) => k.id === id)
                        ?.title ?? id,
                  )
                  .join("、") || "未绑定"
              }}
            </td>
            <td>{{ a.version }}</td>
            <td>
              <button
                v-if="
                  session.user?.role === 'admin' ||
                  a.owner_id === session.user?.id
                "
                @click="editActivity(a)"
              >
                编辑绑定</button
              ><span v-else class="muted">共享只读</span>
            </td>
          </tr>
        </tbody>
      </table></template
    ><template v-else-if="section === 'quests'"
      ><p class="notice">
        先校验再发布。重复
        ID、悬空引用、循环、非法效果和缺失内容会被拒绝；任务运行实例绑定原始版本。
      </p>
      <label
        >已有任务<select @change="selectQuest">
          <option value="">选择定义作为起点</option>
          <option v-for="(q, index) in quests" :key="index" :value="index">
            {{ String(q.title ?? q.id) }}
          </option>
        </select></label
      ><label
        >任务 JSON<textarea
          v-model="questJson"
          class="json-editor"
          rows="20"
          spellcheck="false"
        ></textarea>
      </label>
      <div class="inline-actions">
        <button @click="validateQuest">校验任务</button
        ><button class="primary" @click="publishQuest">创建任务版本</button>
      </div>
      <pre v-if="validation" class="notice">{{ validation }}</pre>
      <h2>新增 NPC</h2>
      <p class="muted">配置地图坐标与脚本对白；绑定新活动无需复制业务页面。</p>
      <label
        >NPC JSON<textarea
          v-model="npcJson"
          class="json-editor"
          rows="9"
          spellcheck="false"
        ></textarea></label
      ><button @click="createNpc">创建 NPC</button></template
    ><template v-else
      ><p class="notice">
        公开样例与私有答案、隐藏用例分开保存。题目必须绑定自己的活动，学生接口不会返回私有内容。
      </p>
      <label
        >题目 JSON<textarea
          v-model="questionJson"
          class="json-editor"
          rows="18"
          spellcheck="false"
        ></textarea></label
      ><button class="primary" @click="createQuestion">创建题目</button>
      <h2>当前题库</h2>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>题目</th>
              <th>类型</th>
              <th>活动</th>
              <th>版本</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="q in questions" :key="q.id">
              <td>
                {{ q.title }}<small>{{ q.id }}</small>
              </td>
              <td>{{ q.kind }}</td>
              <td>{{ q.activity_id }}</td>
              <td>{{ q.version }}</td>
            </tr>
          </tbody>
        </table>
      </div></template
    >
  </div>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, reactive } from "vue";
import { api, notify } from "../shared/api";
import TeacherCurriculum from "./TeacherCurriculum.vue";
import KnowledgePointSelect from "./KnowledgePointSelect.vue";
import type { KnowledgePoint } from "../shared/curriculum";
import { useSession } from "../shared/session";
import type {
  Activity,
  Chapter,
  Material,
  Npc,
  Question,
} from "../shared/types";
interface TeacherActivity extends Omit<Activity, "material" | "questions"> {
  material_id: number | null;
  knowledge_point_ids: number[];
  owner_id: number | null;
}
const session = useSession(),
  emit = defineEmits<{ updated: [] }>();
const props = defineProps<{ section: string; classroomId: number | null }>(),
  courses = ref<{
    chapters: Chapter[];
    activities: TeacherActivity[];
    npcs: Npc[];
    knowledge_points: KnowledgePoint[];
  }>({ chapters: [], activities: [], npcs: [], knowledge_points: [] }),
  materials = ref<(Material & { error?: string })[]>([]),
  quests = ref<Record<string, unknown>[]>([]),
  questions = ref<(Question & { activity_id: string })[]>([]),
  error = ref(""),
  uploadForm = reactive({ title: "", chapter_id: 179, material_id: "" }),
  uploadBusy = ref(false),
  activity = reactive({
    edit: false,
    id: "",
    title: "",
    chapter_id: 179,
    kind: "lesson",
    npc_key: "mayor_py",
    material_id: "" as string | number,
    assistant_allowed: true,
    knowledge_point_ids: [] as number[],
  }),
  questJson = ref(""),
  validation = ref(""),
  npcJson = ref(
    JSON.stringify(
      {
        key: "new_resident",
        name: "新居民",
        map_key: "town_hub",
        tile_x: 22,
        tile_y: 33,
        portrait: "",
        dialogue: [{ id: "hello", text: "你好，欢迎探索小镇。" }],
      },
      null,
      2,
    ),
  ),
  questionJson = ref(
    JSON.stringify(
      {
        id: "my-concept-1",
        activity_id: "my-practice",
        title: "概念练习",
        kind: "single_choice",
        prompt: "填写经过核验的题干。",
        options: ["选项一", "选项二"],
        answer: "选项一",
        samples: [],
        hidden_cases: [],
        source: { kind: "teacher_authored" },
      },
      null,
      2,
    ),
  ),
  preview = ref<{ kind: string; url: string; text: string } | null>(null);
let uploadFile: File | undefined,
  timer: ReturnType<typeof setTimeout> | undefined;
async function safe(job: () => Promise<void>) {
  try {
    error.value = "";
    await job();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "操作失败";
    notify(error.value);
  }
}
async function loadCourses() {
  courses.value = await api.get("/teacher/courses");
}
async function curriculumUpdated() {
  await loadCourses();
  emit("updated");
}
async function loadQuests() {
  quests.value = await api.get("/teacher/quests");
}
async function loadQuestions() {
  questions.value = await api.get("/teacher/questions");
}
async function loadMaterials() {
  await safe(async () => {
    materials.value = await api.get("/teacher/materials");
    clearTimeout(timer);
    if (
      materials.value.some((m) => ["queued", "processing"].includes(m.status))
    )
      timer = setTimeout(() => {
        void loadMaterials();
      }, 2000);
  });
}
function selectFile(e: Event) {
  uploadFile = (e.target as HTMLInputElement).files?.[0];
}
async function upload() {
  if (!uploadFile) return;
  uploadBusy.value = true;
  await safe(async () => {
    const form = new FormData();
    form.set("file", uploadFile!);
    form.set("title", uploadForm.title);
    form.set("chapter_id", String(uploadForm.chapter_id));
    if (uploadForm.material_id) form.set("material_id", uploadForm.material_id);
    await api.request("/teacher/materials/upload", {
      method: "POST",
      body: form,
    });
    await loadMaterials();
    notify("已上传，处理状态将自动更新。");
  });
  uploadBusy.value = false;
}
function editActivity(a: TeacherActivity) {
  Object.assign(activity, {
    ...a,
    material_id: a.material_id ?? "",
    edit: true,
  });
}
async function saveActivity() {
  await safe(async () => {
    const fields = {
      title: activity.title,
      npc_key: activity.npc_key,
      assistant_allowed: activity.assistant_allowed,
      material_id: activity.material_id || null,
      knowledge_point_ids: activity.knowledge_point_ids,
    };
    if (activity.edit)
      await api.put(`/teacher/activities/${activity.id}`, fields);
    else
      await api.post("/teacher/activities", {
        ...fields,
        id: activity.id,
        kind: activity.kind,
        chapter_id: activity.chapter_id,
      });
    await loadCourses();
    notify("活动已保存。");
  });
}
function selectQuest(e: Event) {
  const index = (e.target as HTMLSelectElement).value;
  if (index === "") return;
  const q = quests.value[Number(index)];
  questJson.value = JSON.stringify(q?.definition ?? q, null, 2);
}
async function validateQuest() {
  await safe(async () => {
    validation.value = JSON.stringify(
      await api.post("/teacher/quests/validate", {
        definition: JSON.parse(questJson.value),
      }),
      null,
      2,
    );
  });
}
async function publishQuest() {
  await safe(async () => {
    await api.post("/teacher/quests", {
      definition: JSON.parse(questJson.value),
    });
    await loadQuests();
    notify("新任务版本已创建，请更新班级发布规则。");
  });
}
async function createNpc() {
  await safe(async () => {
    await api.post("/teacher/npcs", JSON.parse(npcJson.value));
    await loadCourses();
    notify("NPC已创建。");
  });
}
async function createQuestion() {
  await safe(async () => {
    const result = await api.post<{
      id: string;
      version: number;
      activity_version: number;
    }>("/teacher/questions", JSON.parse(questionJson.value));
    await Promise.all([loadQuestions(), loadCourses()]);
    notify(
      `题目已创建，关联活动已更新为版本 ${result.activity_version}。请在新任务定义中引用该版本。`,
    );
  });
}
function closePreview() {
  if (preview.value?.url) URL.revokeObjectURL(preview.value.url);
  preview.value = null;
}
async function previewMaterial(m: Material) {
  await safe(async () => {
    closePreview();
    if (m.kind === "text") {
      const content = await api.get<Material>(
        `/materials/${m.id}/versions/${m.version}?classroom_id=${props.classroomId}`,
      );
      preview.value = {
        kind: "text",
        url: "",
        text: content.pages
          .map((p) => `第${p.page_no}页\n${p.text}`)
          .join("\n\n"),
      };
    } else {
      const blob = await api.blob(
        `/materials/${m.id}/versions/${m.version}/file?classroom_id=${props.classroomId}`,
      );
      preview.value = {
        kind: m.kind,
        url: URL.createObjectURL(blob),
        text: "",
      };
    }
  });
}
onMounted(() =>
  safe(async () => {
    await Promise.all([
      loadCourses(),
      loadMaterials(),
      loadQuests(),
      loadQuestions(),
    ]);
  }),
);
onBeforeUnmount(() => {
  clearTimeout(timer);
  closePreview();
});
</script>
