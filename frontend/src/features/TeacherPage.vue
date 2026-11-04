<template>
  <main class="teacher-page">
    <aside class="teacher-nav" aria-label="教师管理导航">
      <RouterLink class="brand" to="/world"
        ><GameIcon name="grid"/><span>PYTHONTOPIA<small>教学管理后台</small></span></RouterLink
      ><template v-for="item in sections" :key="item.id"><p v-if="['classes', 'curriculum', 'activities'].includes(item.id)" class="nav-group-title">{{ item.id === "classes" ? "班级教学" : item.id === "curriculum" ? "课程资源" : "学习体验" }}</p><button
        :class="{ selected: section === item.id }" :aria-current="section === item.id ? 'page' : undefined"
        @click="section = item.id"
      >
        <GameIcon :name="item.icon"/>{{ item.label }}</button></template
      ><RouterLink to="/world"><GameIcon name="compass"/>进入小镇</RouterLink
      ><button @click="logout"><GameIcon name="logout"/>退出账号</button>
    </aside>
    <section class="teacher-main">
      <header class="teacher-heading">
        <div>
          <p class="eyebrow">教学管理 / {{ sections.find((s) => s.id === section)?.label }}</p>
          <h1>{{ sections.find((s) => s.id === section)?.label }}</h1><p class="page-description">{{ descriptions[section] }}</p>
        </div>
        <label
          >当前班级<select v-model="selectedClass" @change="loadProgress">
            <option v-for="c in session.classes" :key="c.id" :value="c.id">
              {{ c.name }}
            </option>
          </select></label
        >
      </header>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="loading">正在读取工作台…</p>
      <template v-else-if="authorized"
        ><template v-if="section === 'classes'"
          ><form class="teacher-form" @submit.prevent="createClass"><h2>创建班级</h2>
            <label
              >班级名称<input
                v-model="className"
                required
                placeholder="Python 入门一班" /></label
            ><button class="primary" :disabled="saving"><GameIcon name="plus"/>{{ saving ? "正在创建…" : "创建班级" }}</button>
          </form>
          <h2>我的班级 <span class="count-badge">{{ session.classes.length }}</span></h2><p v-if="!session.classes.length" class="notice">创建班级后，分享加入码邀请学生，再设置章节发布规则。</p><article v-for="c in session.classes" :key="c.id" class="class-card">
            <h3>{{ c.name }}</h3>
            <p>
              加入码 <code>{{ c.join_code }}</code> <button @click="copyCode(c.join_code)"><GameIcon name="copy"/>复制加入码</button>
            </p>
            <button
              @click="
                selectedClass = c.id;
                loadProgress();
                section = 'progress';
              "
            >
              查看成员与学习记录 <GameIcon name="arrowRight"/>
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
          <button :disabled="!selectedClass || progressLoading" @click="loadProgress">
            <GameIcon name="refresh"/>刷新真实记录
          </button>
          <div class="section-toolbar"><h2>班级成员</h2><label class="search-field"><span class="sr-only">搜索学生姓名或邮箱</span><input v-model="memberSearch" type="search" placeholder="搜索学生姓名或邮箱"/></label></div><p v-if="progressLoading" role="status">正在更新班级记录…</p><p v-if="memberSearch && !filteredMembers.length" class="notice">没有匹配的学生，请调整搜索关键词。</p>
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
                <tr v-for="s in filteredMembers" :key="s.id">
                  <td>
                    {{ s.name }}<small>{{ s.email }}</small>
                  </td>
                  <td>{{ s.completed_nodes }}</td>
                  <td>{{ s.submissions }}</td>
                  <td>{{ s.records }}</td>
                  <td>
                    <button @click="studentId = s.id">查看记录</button
                    ><button class="danger" :disabled="saving" @click="removeMember(s.id)">移出班级</button>
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
            {{ statusLabels[status] ?? status }}：{{ count }}
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
                  <td>{{ statusLabels[s.status] ?? s.status }}</td>
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
import { computed, onMounted, ref, reactive } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { api, notify } from "../shared/api";
import { TIME_ZONE, localDateTimeToIso, formatTimestamp } from "../shared/time";
import { useSession } from "../shared/session";
import type { Chapter } from "../shared/types";
import TeacherContent from "./TeacherContent.vue";
import GameIcon from "../shared/GameIcon.vue";
import "./teacherUi.css";
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
    {id:"classes",label:"班级",icon:"people" as const},
    {id:"progress",label:"学习记录",icon:"chart" as const},
    {id:"release",label:"章节发布",icon:"publish" as const},
    {id:"curriculum",label:"课程与知识点",icon:"book" as const},
    {id:"materials",label:"材料与版本",icon:"folder" as const},
    {id:"questions",label:"题库",icon:"grid" as const},
    {id:"activities",label:"活动与 NPC",icon:"activity" as const},
    {id:"quests",label:"任务配置",icon:"journal" as const},
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
const statusLabels: Record<string,string> = { accepted:"通过", wrong_answer:"答案错误", queued:"排队中", running:"运行中", runtime_error:"运行错误", time_limit_exceeded:"超时", infrastructure_error:"判题服务异常" };
const descriptions: Record<string,string> = { classes: "管理教学班级与加入方式，查看学生学习情况。", curriculum: "组织课程章节与知识点，维护教学结构。", release: "设置当前班级的开放时间、前置章节与任务版本。", materials: "管理课程资料、文件版本和处理状态。", activities: "关联学习活动、课程材料与小镇角色。", quests: "配置任务节点、分支与完成条件。", questions: "编写和审核题目，确认来源后再发布。", progress: "查看当前班级的参与情况、任务进度与正式提交。" };
const memberSearch = ref(""), saving = ref(false), progressLoading = ref(false);
const filteredMembers = computed(() => progress.value.members.filter(s => (s.name + " " + s.email).toLowerCase().includes(memberSearch.value.trim().toLowerCase())));
async function copyCode(code: string | undefined) { try { await navigator.clipboard.writeText(code ?? ""); notify("加入码已复制"); } catch { notify("复制失败，请选中加入码手动复制"); } }
let progressRequest = 0;
async function safe(job: () => Promise<void>) {
  try {
    error.value = ""; saving.value = true;
    await job();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "操作失败";
    notify(error.value);
  } finally { saving.value = false; }
}
async function loadChapters() {
  chapters.value = (
    await api.get<{ chapters: Chapter[] }>("/teacher/courses")
  ).chapters;
}
async function loadProgress() {
  const classroom = selectedClass.value, request = ++progressRequest;
  studentId.value = null;
  progress.value = { members: [], events: [], submissions: [], error_distribution: {} };
  if (!classroom) return;
  progressLoading.value = true;
  try { const result = await api.get<Progress>(`/teacher/classrooms/${classroom}/progress`); if (request === progressRequest) progress.value = result; }
  catch (e) { if (request === progressRequest) error.value = e instanceof Error ? e.message : "记录加载失败"; }
  finally { if (request === progressRequest) progressLoading.value = false; }
}
async function createClass() {
  await safe(async () => {
    await api.post("/classrooms", { name: className.value });
    await session.load();
    selectedClass.value =
      session.classes[session.classes.length - 1]?.id ?? null;
    await loadProgress();
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
 if (!window.confirm("确认将「" + progress.value.members.find(s => s.id === id)?.name + "」移出当前班级？该学生将失去此班级的访问权限。")) return;
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
