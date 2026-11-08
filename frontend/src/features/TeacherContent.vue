<template>
  <div class="admin-content admin-stack">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <KeepAlive
      ><TeacherCurriculum
        v-if="section === 'curriculum'"
        @updated="curriculumUpdated"
    /></KeepAlive>
    <KeepAlive
      ><TeacherMaterials
        v-if="section === 'materials'"
        :chapters="courses.chapters" /><TeacherQuestionBank
        v-else-if="section === 'questions'"
        :courses="courses"
        @updated="curriculumUpdated"
    /></KeepAlive>
    <template v-if="section === 'activities'">
      <section class="admin-panel">
        <header class="panel-heading">
          <div>
            <h2>
              学习活动
              <span class="count-badge">{{ courses.activities.length }}</span>
            </h2>
            <p class="panel-description">
              查看章节活动，维护材料、知识点与 NPC 的关联。
            </p>
          </div>
          <Button @click="startActivity">新建活动</Button>
        </header>
        <div class="table-toolbar">
          <label class="search-field"
            ><span class="sr-only">搜索活动标题或 ID</span
            ><input
              v-model="activitySearch"
              type="search"
              placeholder="搜索活动标题或 ID"
          /></label>
          <label
            >章节<select v-model="activityChapter">
              <option value="">全部章节</option>
              <option
                v-for="c in courses.chapters"
                :key="c.id"
                :value="String(c.id)"
              >
                {{ c.title }}
              </option>
            </select></label
          >
          <span class="muted">{{ filteredActivities.length }} 个活动</span>
        </div>
        <div class="admin-table-scroll">
          <table>
            <thead>
              <tr>
                <th>活动</th>
                <th>类型 / 章节</th>
                <th>NPC</th>
                <th>知识点</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in filteredActivities" :key="a.id">
                <td class="record-title">
                  {{ a.title }}<small>{{ a.id }}</small>
                </td>
                <td>
                  {{ activityKinds[a.kind] ?? a.kind
                  }}<small>{{ chapterTitle(a.chapter_id) }}</small>
                </td>
                <td>
                  {{
                    courses.npcs.find((n) => n.key === a.npc_key)?.name ??
                    a.npc_key
                  }}
                </td>
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
                <td>
                  <Button
                    v-if="
                      session.user?.role === 'admin' ||
                      a.owner_id === session.user?.id
                    "
                    variant="outline"
                    size="sm"
                    @click="editActivity(a)"
                    >编辑绑定</Button
                  ><span v-else class="status-badge">共享只读</span>
                </td>
              </tr>
              <tr v-if="!filteredActivities.length">
                <td colspan="5" class="admin-empty">
                  {{
                    courses.activities.length
                      ? "没有匹配的活动，请调整搜索或章节筛选。"
                      : "还没有学习活动，可以先创建活动并绑定课程材料。"
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <details
        :open="activityEditorOpen"
        class="admin-panel admin-editor"
        @toggle="
          activityEditorOpen = ($event.target as HTMLDetailsElement).open
        "
      >
        <summary>{{ activity.edit ? "编辑自建活动" : "新建活动" }}</summary>
        <p class="panel-description">
          共享活动只读。保存自建活动后，可在任务配置中引用活动 ID。
        </p>
        <form class="teacher-form" @submit.prevent="saveActivity">
          <h3 class="form-section">基本信息</h3>
          <label
            >活动 ID<input
              v-model="activity.id"
              required
              :disabled="activity.edit"
              placeholder="例如：my-python-lesson"
          /></label>
          <label
            >标题<input
              v-model="activity.title"
              required
              placeholder="学生看到的活动名称"
          /></label>
          <label
            >章节<select
              v-model.number="activity.chapter_id"
              :disabled="activity.edit"
            >
              <option v-for="c in courses.chapters" :key="c.id" :value="c.id">
                {{ c.title }}
              </option>
            </select></label
          >
          <label
            >类型<select v-model="activity.kind" :disabled="activity.edit">
              <option value="lesson">教学</option>
              <option value="reading">阅读</option>
              <option value="practice">练习</option>
              <option value="assessment">测验</option>
            </select></label
          >
          <h3 class="form-section">内容与角色</h3>
          <label
            >NPC<select v-model="activity.npc_key">
              <option v-for="n in courses.npcs" :key="n.key" :value="n.key">
                {{ n.name }}
              </option>
            </select></label
          >
          <label
            >材料<select v-model="activity.material_id">
              <option value="">无材料</option>
              <option v-for="m in materials" :key="m.id" :value="m.id">
                {{ m.title }}
              </option>
            </select></label
          >
          <KnowledgePointSelect
            v-model="activity.knowledge_point_ids"
            :chapter-id="activity.chapter_id"
            :points="courses.knowledge_points ?? []"
          />
          <label class="checkbox-label full-width"
            ><input
              v-model="activity.assistant_allowed"
              type="checkbox"
            />允许学生在活动内主动使用助教</label
          >
          <div class="form-actions">
            <Button type="submit" :disabled="saving">{{
              saving ? "正在保存…" : activity.edit ? "保存活动" : "创建活动"
            }}</Button
            ><Button
              v-if="activity.edit"
              variant="outline"
              :disabled="saving"
              @click="startActivity"
              >另建活动</Button
            ><Button variant="ghost" @click="activityEditorOpen = false"
              >收起</Button
            >
          </div>
        </form>
      </details>
    </template>
    <template v-else-if="section === 'quests'">
      <section class="admin-panel">
        <header class="panel-heading">
          <div>
            <h2>任务定义</h2>
            <p class="panel-description">
              选择已有定义作为起点，修改并校验后保存当前配置。
            </p>
          </div>
          <span class="count-badge">{{ quests.length }} 个定义</span>
        </header>
        <ol class="admin-steps">
          <li>选择任务定义</li>
          <li>编辑并校验</li>
          <li>保存后到「章节管理」设置开放规则</li>
        </ol>
        <label
          >已有任务<select @change="selectQuest">
            <option value="">选择定义作为起点</option>
            <option v-for="(q, index) in quests" :key="index" :value="index">
              {{ String(q.title ?? q.id) }}
            </option>
          </select></label
        >
        <p v-if="!quests.length" class="field-hint">
          暂无任务定义，可在下方编辑完整任务 JSON。
        </p>
        <details open class="admin-editor">
          <summary>任务 JSON 编辑器</summary>
          <label
            >任务 JSON<textarea
              v-model="questJson"
              class="json-editor"
              rows="20"
              spellcheck="false"
              placeholder="选择已有任务，或填写新的任务定义"
            ></textarea>
          </label>
        </details>
        <p class="field-hint">
          重复 ID、缺失引用、循环和非法效果会被拒绝；学生进度和成绩继续保留。
        </p>
        <div class="form-actions">
          <Button
            variant="outline"
            :disabled="saving || !questJson.trim()"
            @click="validateQuest"
            >校验任务</Button
          ><Button
            :disabled="saving || !questJson.trim()"
            @click="publishQuest"
            >{{ saving ? "正在处理…" : "保存任务配置" }}</Button
          >
        </div>
        <pre v-if="validation" class="notice" role="status">{{
          validation
        }}</pre>
      </section>
      <details
        v-if="session.user?.role === 'admin'"
        class="admin-panel admin-editor"
      >
        <summary>新增 NPC</summary>
        <p class="panel-description">
          配置小镇角色的地图位置和脚本对白。已有角色可直接在活动表单中绑定。
        </p>
        <label
          >NPC JSON<textarea
            v-model="npcJson"
            class="json-editor"
            rows="10"
            spellcheck="false"
          ></textarea>
        </label>
        <div class="form-actions">
          <Button :disabled="saving || !npcJson.trim()" @click="createNpc">{{
            saving ? "正在创建…" : "创建 NPC"
          }}</Button>
        </div>
      </details>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, reactive, watch } from "vue";
import { Button } from "../components/ui/button";
import { api, notify } from "../shared/api";
import TeacherCurriculum from "./TeacherCurriculum.vue";
import TeacherMaterials from "./TeacherMaterials.vue";
import TeacherQuestionBank from "./TeacherQuestionBank.vue";
import KnowledgePointSelect from "./KnowledgePointSelect.vue";
import type { KnowledgePoint } from "../shared/curriculum";
import { useSession } from "../shared/session";
import type { Activity, Chapter, Material, Npc } from "../shared/types";
interface TeacherActivity extends Omit<Activity, "material" | "questions"> {
  material_id: number | null;
  knowledge_point_ids: number[];
  owner_id: number | null;
  question_ids?: string[];
}
const session = useSession(),
  emit = defineEmits<{ updated: [] }>();
defineProps<{ section: string; classroomId: number | null }>();
const courses = ref<{
  chapters: Chapter[];
  activities: TeacherActivity[];
  npcs: Npc[];
  knowledge_points: KnowledgePoint[];
}>({ chapters: [], activities: [], npcs: [], knowledge_points: [] });
const materials = ref<Material[]>([]),
  quests = ref<Record<string, unknown>[]>([]),
  error = ref(""),
  saving = ref(false);
const activity = reactive({
  edit: false,
  id: "",
  title: "",
  chapter_id: 179,
  kind: "lesson",
  npc_key: "mayor_py",
  material_id: "" as string | number,
  assistant_allowed: true,
  knowledge_point_ids: [] as number[],
});
const activityEditorOpen = ref(false),
  activitySearch = ref(""),
  activityChapter = ref("");
const questJson = ref(""),
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
  );
watch(questJson, () => {
  validation.value = "";
});
const activityKinds: Record<string, string> = {
  lesson: "教学",
  reading: "阅读",
  practice: "练习",
  assessment: "测验",
};
const filteredActivities = computed(() =>
  courses.value.activities.filter(
    (a) =>
      (!activityChapter.value ||
        String(a.chapter_id) === activityChapter.value) &&
      `${a.title} ${a.id}`
        .toLowerCase()
        .includes(activitySearch.value.trim().toLowerCase()),
  ),
);
function chapterTitle(id: number) {
  return (
    courses.value.chapters.find((c) => c.id === id)?.title ?? `章节 #${id}`
  );
}
function startActivity() {
  Object.assign(activity, {
    edit: false,
    id: "",
    title: "",
    chapter_id: courses.value.chapters[0]?.id ?? 179,
    kind: "lesson",
    npc_key: courses.value.npcs[0]?.key ?? "mayor_py",
    material_id: "",
    assistant_allowed: true,
    knowledge_point_ids: [],
  });
  activityEditorOpen.value = true;
}
async function safe(job: () => Promise<void>, mutation = false) {
  if (mutation && saving.value) return;
  if (mutation) saving.value = true;
  try {
    error.value = "";
    await job();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "操作失败";
    notify(error.value);
  } finally {
    if (mutation) saving.value = false;
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
async function loadMaterials() {
  materials.value = await api.get<Material[]>("/teacher/materials");
}
function editActivity(a: TeacherActivity) {
  Object.assign(activity, {
    ...a,
    material_id: a.material_id ?? "",
    edit: true,
  });
  activityEditorOpen.value = true;
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
  }, true);
}
function selectQuest(e: Event) {
  const index = (e.target as HTMLSelectElement).value;
  if (index === "") return;
  const q = quests.value[Number(index)];
  questJson.value = JSON.stringify(q?.definition ?? q, null, 2);
}
async function validateQuest() {
  await safe(async () => {
    const definition = questJson.value;
    const result = await api.post("/teacher/quests/validate", {
      definition: JSON.parse(definition),
    });
    if (definition === questJson.value)
      validation.value = JSON.stringify(result, null, 2);
  }, true);
}
async function publishQuest() {
  await safe(async () => {
    await api.post("/teacher/quests", {
      definition: JSON.parse(questJson.value),
    });
    await loadQuests();
    notify("任务配置已保存。");
  }, true);
}
async function createNpc() {
  await safe(async () => {
    await api.post("/teacher/npcs", JSON.parse(npcJson.value));
    await loadCourses();
    notify("NPC已创建。");
  }, true);
}
onMounted(() =>
  safe(async () => {
    await Promise.all([loadCourses(), loadMaterials(), loadQuests()]);
  }),
);
</script>
