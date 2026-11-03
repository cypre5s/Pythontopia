<template>
  <main class="teacher-page">
    <aside class="teacher-nav">
      <RouterLink class="brand" to="/world"
        >✦ PYTHONTOPIA<small>教师工作台</small></RouterLink
      ><button
        v-for="item in sections"
        :key="item.id"
        :class="{ selected: section === item.id }"
        @click="section = item.id"
      >
        {{ item.label }}</button
      ><RouterLink to="/world">进入小镇 ↗</RouterLink
      ><button @click="logout">退出账号</button>
    </aside>
    <section class="teacher-main">
      <header class="teacher-heading">
        <div>
          <p class="eyebrow">TEACHER WORKSPACE</p>
          <h1>{{ sections.find((s) => s.id === section)?.label }}</h1>
        </div>
        <label
          >当前班级<select v-model="selectedClass" @change="loadProgress">
            <option v-for="c in session.classes" :key="c.id" :value="c.id">
              {{ c.name }}
            </option>
          </select></label
        >
      </header>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading">正在读取工作台…</p>
      <template v-else-if="authorized"
        ><template v-if="section === 'classes'"
          ><form class="teacher-form" @submit.prevent="createClass">
            <label
              >班级名称<input
                v-model="className"
                required
                placeholder="Python 入门一班" /></label
            ><button class="primary">创建班级</button>
          </form>
          <article v-for="c in session.classes" :key="c.id" class="class-card">
            <h3>{{ c.name }}</h3>
            <p>
              加入码 <code>{{ c.join_code }}</code>
            </p>
            <button
              @click="
                selectedClass = c.id;
                loadProgress();
                section = 'progress';
              "
            >
              查看成员与学习记录 →
            </button>
          </article></template
        ><template v-else-if="section === 'release'"
          ><p class="notice">
            按
            {{
              TIME_ZONE
            }}
            时区设置开放时间，留空立即生效。暂停阻止新访问并保留历史记录。
          </p>
          <form class="teacher-form" @submit.prevent="release">
            <label
              >章节<select v-model.number="releaseForm.chapter_id">
                <option v-for="c in chapters" :key="c.id" :value="c.id">
                  {{ c.title }}
                </option>
              </select></label
            ><label
              >状态<select v-model="releaseForm.status">
                <option value="published">开放</option>
                <option value="paused">暂停开放</option>
              </select></label
            ><label
              >开放时间（{{ TIME_ZONE }}）<input
                v-model="releaseForm.opens_at"
                type="datetime-local" /></label
            ><label
              >前置章节<select v-model="releaseForm.prerequisite_chapter_id">
                <option value="">无前置</option>
                <option v-for="c in chapters" :key="c.id" :value="c.id">
                  {{ c.title }}
                </option>
              </select></label
            ><label
              >发布任务版本<input
                v-model.number="releaseForm.version"
                type="number"
                min="1" /></label
            ><button class="primary" :disabled="!selectedClass">
              保存发布规则
            </button>
          </form>
          <p class="notice">{{ releaseResult }}</p></template
        ><TeacherContent
          v-else-if="
            [
              'curriculum',
              'materials',
              'activities',
              'quests',
              'questions',
            ].includes(section)
          "
          :section="section"
          :classroom-id="selectedClass"
          @updated="loadChapters"
        /><template v-else
          ><div class="stats-strip">
            <div>
              <strong>{{ progress.members.length }}</strong
              >班级成员
            </div>
            <div>
              <strong>{{ progress.events.length }}</strong
              >任务事件
            </div>
            <div>
              <strong>{{ progress.submissions.length }}</strong
              >正式提交
            </div>
          </div>
          <button :disabled="!selectedClass" @click="loadProgress">
            刷新真实记录
          </button>
          <h2>班级成员</h2>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>学生</th>
                  <th>节点</th>
                  <th>提交</th>
                  <th>参与</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="s in progress.members" :key="s.id">
                  <td>
                    {{ s.name }}<small>{{ s.email }}</small>
                  </td>
                  <td>{{ s.completed_nodes }}</td>
                  <td>{{ s.submissions }}</td>
                  <td>{{ s.records }}</td>
                  <td>
                    <button @click="studentId = s.id">查看记录</button
                    ><button @click="removeMember(s.id)">移出班级</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="!progress.members.length" class="notice">
            还没有学生加入此班级。
          </p>
          <h2>提交状态分布</h2>
          <p
            v-for="(count, status) in progress.error_distribution"
            :key="status"
          >
            {{ status }}：{{ count }}
          </p>
          <template v-if="studentId"
            ><h2>学生 #{{ studentId }} 的真实记录</h2>
            <ol>
              <li
                v-for="event in progress.events.filter(
                  (e) => e.user_id === studentId,
                )"
                :key="event.id"
              >
                {{ time(event.created_at) }} · {{ event.node_id }} · 版本
                {{ event.content_version }}
              </li>
            </ol>
            <table>
              <thead>
                <tr>
                  <th>题目</th>
                  <th>结果</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="s in progress.submissions.filter(
                    (s) => s.user_id === studentId,
                  )"
                  :key="s.id"
                >
                  <td>{{ s.question_id }}</td>
                  <td>{{ s.status }}</td>
                  <td>{{ time(s.created_at) }}</td>
                </tr>
              </tbody>
            </table></template
          ></template
        ></template
      >
    </section>
  </main>
</template>
<script setup lang="ts">
import { onMounted, ref, reactive } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { api, notify } from "../shared/api";
import { TIME_ZONE, localDateTimeToIso, formatTimestamp } from "../shared/time";
import { useSession } from "../shared/session";
import type { Chapter } from "../shared/types";
import TeacherContent from "./TeacherContent.vue";
interface Progress {
  members: {
    id: number;
    name: string;
    email: string;
    completed_nodes: number;
    submissions: number;
    records: number;
  }[];
  events: {
    id: string;
    user_id: number;
    node_id: string;
    content_version: number;
    created_at: string;
  }[];
  submissions: {
    id: string;
    user_id: number;
    question_id: string;
    status: string;
    created_at: string;
  }[];
  error_distribution: Record<string, number>;
}
const session = useSession(),
  router = useRouter(),
  section = ref("classes"),
  sections = [
    { id: "classes", label: "班级" },
    { id: "curriculum", label: "课程与知识点" },
    { id: "release", label: "章节发布" },
    { id: "materials", label: "材料与版本" },
    { id: "activities", label: "活动与 NPC" },
    { id: "quests", label: "任务配置" },
    { id: "questions", label: "题库" },
    { id: "progress", label: "学习记录" },
  ],
  selectedClass = ref<number | null>(null),
  className = ref(""),
  chapters = ref<Chapter[]>([]),
  error = ref(""),
  loading = ref(true),
  authorized = ref(false),
  studentId = ref<number | null>(null),
  progress = ref<Progress>({
    members: [],
    events: [],
    submissions: [],
    error_distribution: {},
  }),
  releaseForm = reactive({
    chapter_id: 179,
    status: "published",
    opens_at: "",
    prerequisite_chapter_id: "" as string | number,
    version: 1,
  }),
  releaseResult = ref("服务端按班级、版本、前置和当前时间判断开放状态。");
async function safe(job: () => Promise<void>) {
  try {
    error.value = "";
    await job();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "操作失败";
    notify(error.value);
  }
}
async function loadChapters() {
  chapters.value = (
    await api.get<{ chapters: Chapter[] }>("/teacher/courses")
  ).chapters;
}
async function loadProgress() {
  if (selectedClass.value)
    await safe(async () => {
      progress.value = await api.get(
        `/teacher/classrooms/${selectedClass.value}/progress`,
      );
    });
}
async function createClass() {
  await safe(async () => {
    await api.post("/classrooms", { name: className.value });
    await session.load();
    selectedClass.value =
      session.classes[session.classes.length - 1]?.id ?? null;
    className.value = "";
    notify("班级已创建，请分享加入码并发布章节。");
  });
}
async function release() {
  await safe(async () => {
    await api.put(
      `/teacher/classrooms/${selectedClass.value}/releases/${releaseForm.chapter_id}`,
      {
        status: releaseForm.status,
        opens_at: releaseForm.opens_at
          ? localDateTimeToIso(releaseForm.opens_at)
          : null,
        prerequisite_chapter_id: releaseForm.prerequisite_chapter_id || null,
        version: releaseForm.version,
      },
    );
    releaseResult.value = "班级开放规则已保存，学生刷新进度即可获得新任务。";
    notify("发布规则已保存");
  });
}
async function removeMember(id: number) {
  await safe(async () => {
    await api.request(
      `/teacher/classrooms/${selectedClass.value}/members/${id}`,
      { method: "DELETE" },
    );
    await loadProgress();
  });
}
function time(value: string) {
  try {
    return formatTimestamp(value);
  } catch {
    return "时间格式异常";
  }
}
async function logout() {
  try {
    await api.post("/auth/logout", {});
  } finally {
    session.reset();
    await router.push("/");
  }
}
onMounted(async () => {
  await safe(async () => {
    await session.load();
    if (session.user?.role === "student") {
      error.value = "此账号没有教师权限";
      return;
    }
    authorized.value = true;
    selectedClass.value = session.classes[0]?.id ?? null;
    chapters.value = (
      await api.get<{ chapters: Chapter[] }>("/teacher/courses")
    ).chapters;
    await loadProgress();
  });
  loading.value = false;
});
</script>
