<template>
  <main class="teacher-page" :class="{ 'nav-open': navOpen }">
    <aside
      id="teacher-navigation"
      class="teacher-nav"
      aria-label="教师管理导航"
    >
      <RouterLink class="brand" to="/teacher"
        ><span class="brand-mark"><GraduationCap :size="21" /></span
        ><span>PYTHONTOPIA<small>教学管理工作台</small></span></RouterLink
      >
      <nav class="nav-sections">
        <div v-for="group in navigation" :key="group.label" class="nav-group">
          <p class="nav-group-title">{{ group.label }}</p>
          <Button
            v-for="item in group.items"
            :key="item.id"
            variant="ghost"
            :class="{ selected: section === item.id }"
            :aria-current="section === item.id ? 'page' : undefined"
            @click="selectSection(item.id)"
          >
            <component :is="item.icon" :size="17" /><span>{{
              item.label
            }}</span>
          </Button>
        </div>
      </nav>
      <div class="nav-footer">
        <RouterLink to="/world"
          ><Compass :size="17" />进入小镇<ArrowUpRight :size="14"
        /></RouterLink>
        <div class="teacher-account">
          <span class="account-avatar">{{
            session.user?.name?.slice(0, 1) || "师"
          }}</span
          ><span
            >{{ session.user?.name || "教师"
            }}<small>{{
              session.user?.role === "admin" ? "管理员" : "教师账号"
            }}</small></span
          ><Button
            variant="ghost"
            size="icon"
            aria-label="退出账号"
            @click="logout"
            ><LogOut :size="17"
          /></Button>
        </div>
      </div>
    </aside>
    <div class="teacher-shell">
      <header class="teacher-topbar">
        <div class="topbar-location">
          <Button
            class="mobile-nav-toggle"
            variant="ghost"
            size="icon"
            aria-label="切换管理导航"
            aria-controls="teacher-navigation"
            :aria-expanded="navOpen"
            @click="navOpen = !navOpen"
            ><Menu :size="20" /></Button
          ><span>教学管理</span><ChevronRight :size="14" /><strong>{{
            currentSection?.label
          }}</strong>
        </div>
        <label class="class-switcher"
          ><span>当前班级</span
          ><select
            v-model="selectedClass"
            :disabled="!session.classes.length"
            @change="loadProgress"
          >
            <option v-if="!session.classes.length" :value="null">
              暂无班级
            </option>
            <option v-for="c in session.classes" :key="c.id" :value="c.id">
              {{ c.name }}
            </option>
          </select></label
        >
      </header>
      <section class="teacher-main">
        <header class="teacher-heading">
          <h1>{{ currentSection?.label }}</h1>
          <Button
            v-if="section === 'classes' && authorized"
            @click="createOpen = true"
            ><Plus :size="16" />创建班级</Button
          ><Button
            v-else-if="section === 'progress' && authorized"
            variant="outline"
            :disabled="!selectedClass || progressLoading"
            @click="loadProgress"
            ><RefreshCw :size="16" />{{
              progressLoading ? "正在更新…" : "刷新记录"
            }}</Button
          >
        </header>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <div v-if="loading" class="admin-empty" role="status">
          正在读取教学工作台…
        </div>
        <template v-else-if="authorized">
          <PasswordForm v-if="section === 'account'" class="admin-panel" />
          <TeacherStudents v-if="section === 'students'" :key="selectedClass ?? 0" :classroom-id="selectedClass" @changed="loadProgress" />
          <template v-if="section === 'classes'">
            <div class="table-toolbar list-toolbar">
              <span class="list-count"
                >共 {{ session.classes.length }} 个班级</span
              >
              <label class="search-field"
                ><Search :size="16" /><span class="sr-only">搜索班级</span
                ><input
                  v-model="classSearch"
                  type="search"
                  placeholder="搜索班级名称"
              /></label>
            </div>
            <div v-if="filteredClasses.length" class="class-grid">
              <Card v-for="c in filteredClasses" :key="c.id" class="class-card">
                <div class="class-card-heading">
                  <span class="class-icon"><Users :size="22" /></span
                  ><span
                    class="status-badge"
                    :data-status="selectedClass === c.id ? 'ready' : ''"
                    >{{
                      selectedClass === c.id ? "当前班级" : "教学班级"
                    }}</span
                  >
                </div>
                <h3>{{ c.name }}</h3>
                <p class="class-card-caption">班级 #{{ c.id }}</p>
                <div class="join-code">
                  <span>加入码</span
                  ><code>{{ c.join_code || "暂无加入码" }}</code
                  ><Button
                    variant="ghost"
                    size="icon"
                    aria-label="复制加入码"
                    :disabled="!c.join_code"
                    @click="copyCode(c.join_code)"
                    ><Copy :size="15"
                  /></Button>
                </div>
                <div class="class-card-actions">
                  <Button variant="outline" @click="openClass(c.id, 'progress')"
                    >查看成员与学习记录<ArrowRight :size="15" /></Button
                  ><Button variant="ghost" @click="openClass(c.id, 'release')"
                    >章节管理</Button
                  >
                </div>
              </Card>
            </div>
            <div v-else class="admin-empty">
              <Users :size="32" />
              <h3>
                {{ classSearch ? "没有匹配的班级" : "从第一个教学班级开始" }}
              </h3>
              <p>
                {{
                  classSearch
                    ? "试试其他名称，或清空搜索条件。"
                    : "创建班级、邀请学生，再为班级开放课程章节。"
                }}
              </p>
              <Button v-if="!classSearch" @click="createOpen = true"
                ><Plus :size="16" />创建班级</Button
              >
            </div>
          </template>
          <KeepAlive
            ><TeacherChapters
              v-if="section === 'release'"
              :classroom-id="selectedClass"
              :classroom-name="selectedClassName ?? ''"
              :chapters="chapters"
          /></KeepAlive>
          <template v-if="section === 'progress'">
            <p v-if="!selectedClass" class="notice">请先创建或选择一个班级。</p>
            <div class="table-toolbar list-toolbar member-toolbar">
              <span class="member-total"
                >共 {{ progress.members.length }} 人</span
              >
              <label class="search-field"
                ><Search :size="16" /><span class="sr-only"
                  >搜索学生姓名或学号</span
                ><input
                  v-model="memberSearch"
                  type="search"
                  placeholder="搜索学生姓名或学号"
              /></label>
            </div>
            <section class="admin-panel member-panel">
              <p v-if="progressLoading" class="loading-notice" role="status">
                正在更新班级记录…
              </p>
              <div class="admin-table-scroll">
                <Table
                  ><TableHeader
                    ><TableRow
                      ><TableHead>学生</TableHead
                      ><TableHead>已完成节点</TableHead
                      ><TableHead>已完成章节</TableHead
                      ><TableHead>参与记录</TableHead
                      ><TableHead class="table-actions-heading"
                        >操作</TableHead
                      ></TableRow
                    ></TableHeader
                  ><TableBody
                    ><TableRow v-for="s in pagedMembers" :key="s.id"
                      ><TableCell
                        ><span class="record-title">{{ s.name }}</span
                        ><small>{{ s.student_id ?? '未分配学号' }}</small></TableCell
                      ><TableCell>{{ s.completed_nodes }}</TableCell
                      ><TableCell>{{ s.completed_chapters }}</TableCell
                      ><TableCell>{{ s.records }}</TableCell
                      ><TableCell
                        ><div class="row-actions">
                          <Button
                            variant="outline"
                            size="sm"
                            @click="studentId = s.id"
                            >查看记录</Button
                          ><Button
                            variant="ghost"
                            size="sm"
                            class="danger"
                            :disabled="saving"
                            @click="pendingMember = s"
                            >移出班级</Button
                          >
                        </div></TableCell
                      ></TableRow
                    ></TableBody
                  ></Table
                >
              </div>
              <div
                v-if="!filteredMembers.length && !progressLoading"
                class="admin-empty"
              >
                <Search :size="26" />
                <h3>
                  {{ memberSearch ? "没有匹配的学生" : "还没有学生加入此班级" }}
                </h3>
                <p>
                  {{
                    memberSearch
                      ? "请调整搜索关键词。"
                      : "到班级页面复制加入码，邀请学生加入。"
                  }}
                </p>
              </div>
              <div v-if="filteredMembers.length" class="table-pagination">
                <span
                  >共 {{ filteredMembers.length }} 位学生 · 第
                  {{ memberPage }} / {{ memberPageCount }} 页</span
                >
                <div>
                  <Button
                    variant="outline"
                    size="sm"
                    :disabled="memberPage <= 1"
                    aria-label="上一页学生"
                    @click="memberPage--"
                    ><ChevronLeft :size="16" /></Button
                  ><Button
                    variant="outline"
                    size="sm"
                    :disabled="memberPage >= memberPageCount"
                    aria-label="下一页学生"
                    @click="memberPage++"
                    ><ChevronRight :size="16"
                  /></Button>
                </div>
              </div>
            </section>
          </template>
          <KeepAlive
            ><TeacherContent
              v-if="isContentSection"
              :section="section"
              :classroom-id="selectedClass"
              @updated="loadChapters"
          /></KeepAlive>
        </template>
      </section>
    </div>
    <AdminDialog
      v-model:open="createOpen"
      title="创建班级"
      description="创建后分享加入码，邀请学生加入你的教学班级。"
    >
      <form class="teacher-form" @submit.prevent="createClass">
        <label
          >班级名称<input
            v-model="className"
            required
            maxlength="120"
            placeholder="Python 入门一班"
            autofocus
        /></label>
        <div class="form-actions">
          <Button variant="outline" @click="createOpen = false">取消</Button
          ><Button type="submit" :disabled="saving">{{
            saving ? "正在创建…" : "创建班级"
          }}</Button>
        </div>
      </form>
    </AdminDialog>
    <AdminDialog
      :open="!!pendingMember"
      title="移出班级"
      :description="`确认将「${pendingMember?.name ?? ''}」移出当前班级？该学生将失去此班级的访问权限。`"
      @update:open="
        (open) => {
          if (!open) pendingMember = null;
        }
      "
      ><div class="form-actions">
        <Button
          variant="outline"
          :disabled="saving"
          @click="pendingMember = null"
          >取消</Button
        ><Button
          variant="destructive"
          :disabled="saving"
          @click="removeMember"
          >{{ saving ? "正在移出…" : "确认移出" }}</Button
        >
      </div></AdminDialog
    >
    <AdminDialog
      :open="studentId !== null"
      :title="`${selectedStudent?.name ?? '学生'}的学习记录`"
      :description="
        selectedStudent?.student_id ?? '当前班级的任务事件和正式提交记录。'
      "
      wide
      @update:open="
        (open) => {
          if (!open) studentId = null;
        }
      "
    >
      <h3>
        任务事件 <span class="count-badge">{{ studentEvents.length }}</span>
      </h3>
      <ol v-if="studentEvents.length" class="event-timeline">
        <li v-for="event in studentEvents" :key="event.id">
          <strong>{{ event.node_id }}</strong
          ><span
            >{{ time(event.created_at) }}</span
          >
        </li>
      </ol>
      <p v-else class="admin-empty compact">暂无任务事件。</p>
      <h3>
        正式提交
        <span class="count-badge">{{ studentSubmissions.length }}</span>
      </h3>
      <div class="admin-table-scroll">
        <table>
          <thead>
            <tr>
              <th>题目</th>
              <th>结果</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in studentSubmissions" :key="s.id">
              <td>{{ s.question_id }}</td>
              <td>
                <span class="status-badge" :data-status="s.status">{{
                  statusLabels[s.status] ?? s.status
                }}</span>
              </td>
              <td>{{ time(s.created_at) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!studentSubmissions.length" class="admin-empty compact">
        暂无正式提交。
      </p>
    </AdminDialog>
  </main>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { RouterLink, useRouter } from "vue-router";
import {
  GraduationCap,
  Users,
  ChartNoAxesCombined,
  Send,
  BookOpen,
  FolderOpen,
  ListChecks,
  MessagesSquare,
  Route,
  Compass,
  ArrowUpRight,
  LogOut,
  Menu,
  ChevronRight,
  ChevronLeft,
  Plus,
  RefreshCw,
  Copy,
  ArrowRight,
  Search,
} from "@lucide/vue";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "../components/ui/table";
import AdminDialog from "../components/admin/AdminDialog.vue";
import { api, notify } from "../shared/api";
import { formatTimestamp } from "../shared/time";
import { useSession } from "../shared/session";
import type { Chapter } from "../shared/types";
import TeacherContent from "./TeacherContent.vue";
import TeacherStudents from "./TeacherStudents.vue";
import PasswordForm from "./PasswordForm.vue";
import TeacherChapters from "./TeacherChapters.vue";
import "./teacherTheme.css";
import "./teacherUi.css";
interface Member {
  id: number;
  name: string;
  student_id: string | null;
  completed_nodes: number;
  completed_chapters: number;
  submissions: number;
  records: number;
}
interface Progress {
  members: Member[];
  events: {
    id: string;
    user_id: number;
    node_id: string;

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
  navOpen = ref(false),
  createOpen = ref(false);
const navigation = [
  {
    label: "班级教学",
    items: [
      { id: "classes", label: "班级", icon: Users },
      { id: "students", label: "学生账号", icon: Users },
      { id: "account", label: "我的密码", icon: GraduationCap },
      { id: "progress", label: "学习记录", icon: ChartNoAxesCombined },
      { id: "release", label: "章节管理", icon: Send },
    ],
  },
  {
    label: "课程资源",
    items: [
      { id: "curriculum", label: "课程与知识点", icon: BookOpen },
      { id: "materials", label: "课程材料", icon: FolderOpen },
      { id: "questions", label: "题库", icon: ListChecks },
    ],
  },
  {
    label: "学习体验",
    items: [
      { id: "activities", label: "活动与 NPC", icon: MessagesSquare },
      { id: "quests", label: "任务配置", icon: Route },
    ],
  },
];
const currentSection = computed(() =>
  navigation.flatMap((g) => g.items).find((s) => s.id === section.value),
);
const isContentSection = computed(() =>
  ["curriculum", "materials", "activities", "quests", "questions"].includes(
    section.value,
  ),
);
const selectedClass = ref<number | null>(null),
  selectedClassName = computed(
    () => session.classes.find((c) => c.id === selectedClass.value)?.name,
  ),
  className = ref(""),
  classSearch = ref(""),
  chapters = ref<Chapter[]>([]),
  error = ref(""),
  loading = ref(true),
  authorized = ref(false),
  studentId = ref<number | null>(null),
  pendingMember = ref<Member | null>(null);
const emptyProgress = (): Progress => ({
  members: [],
  events: [],
  submissions: [],
  error_distribution: {},
});
const progress = ref<Progress>(emptyProgress());
const statusLabels: Record<string, string> = {
  prepared: "准备中",
  dispatching: "送交中",
  accepted: "通过",
  wrong_answer: "答案错误",
  queued: "排队中",
  running: "运行中",
  compile_error: "编译错误",
  runtime_error: "运行错误",
  time_limit: "超时",
  time_limit_exceeded: "超时",
  memory_limit: "内存超限",
  infrastructure_error: "判题服务异常",
  submission_unknown: "待对账",
};
const memberSearch = ref(""),
  memberPage = ref(1),
  saving = ref(false),
  progressLoading = ref(false);
const filteredClasses = computed(() =>
  session.classes.filter((c) =>
    c.name.toLowerCase().includes(classSearch.value.trim().toLowerCase()),
  ),
);
const filteredMembers = computed(() =>
  progress.value.members.filter((s) =>
    (s.name + " " + (s.student_id ?? ""))
      .toLowerCase()
      .includes(memberSearch.value.trim().toLowerCase()),
  ),
);
const memberPageCount = computed(() =>
  Math.max(1, Math.ceil(filteredMembers.value.length / 10)),
);
const pagedMembers = computed(() =>
  filteredMembers.value.slice(
    (memberPage.value - 1) * 10,
    memberPage.value * 10,
  ),
);
const selectedStudent = computed(() =>
  progress.value.members.find((s) => s.id === studentId.value),
);
const studentEvents = computed(() =>
  progress.value.events.filter((e) => e.user_id === studentId.value),
);
const studentSubmissions = computed(() =>
  progress.value.submissions.filter((s) => s.user_id === studentId.value),
);
watch(memberSearch, () => {
  memberPage.value = 1;
});
function selectSection(id: string) {
  section.value = id;
  navOpen.value = false;
  error.value = "";
  void nextTick(() => window.scrollTo({ top: 0, behavior: "auto" }));
}
function openClass(id: number, target: string) {
  selectedClass.value = id;
  selectSection(target);
  void loadProgress();
}
async function copyCode(code: string | undefined) {
  try {
    await navigator.clipboard.writeText(code ?? "");
    notify("加入码已复制");
  } catch {
    notify("复制失败，请选中加入码手动复制");
  }
}
async function safe(job: () => Promise<void>) {
  if (saving.value) return;
  try {
    error.value = "";
    saving.value = true;
    await job();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "操作失败";
    notify(error.value);
  } finally {
    saving.value = false;
  }
}
async function loadChapters() {
  chapters.value = (
    await api.get<{ chapters: Chapter[] }>("/teacher/courses")
  ).chapters;
}
let progressRequest = 0;
async function loadProgress() {
  const classroom = selectedClass.value,
    request = ++progressRequest;
  studentId.value = null;
  pendingMember.value = null;
  memberPage.value = 1;
  progress.value = emptyProgress();
  progressLoading.value = false;
  if (!classroom) return;
  progressLoading.value = true;
  try {
    const result = await api.get<Progress>(
      `/teacher/classrooms/${classroom}/progress`,
    );
    if (request === progressRequest) progress.value = result;
  } catch (e) {
    if (request === progressRequest)
      error.value = e instanceof Error ? e.message : "记录加载失败";
  } finally {
    if (request === progressRequest) progressLoading.value = false;
  }
}
async function createClass() {
  await safe(async () => {
    await api.post("/classrooms", { name: className.value });
    await session.load();
    selectedClass.value =
      session.classes[session.classes.length - 1]?.id ?? null;
    await loadProgress();
    className.value = "";
    createOpen.value = false;
    notify("班级已创建，请分享加入码并安排章节解锁时间。");
  });
}
async function removeMember() {
  const member = pendingMember.value,
    classroom = selectedClass.value;
  if (!member || !classroom) return;
  await safe(async () => {
    await api.request(`/teacher/classrooms/${classroom}/members/${member.id}`, {
      method: "DELETE",
    });
    pendingMember.value = null;
    await loadProgress();
    notify("已移出班级");
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
    if (!["teacher", "admin"].includes(session.user?.role ?? "")) {
      error.value = "此账号没有教师权限";
      return;
    }
    authorized.value = true;
    selectedClass.value = session.classes[0]?.id ?? null;
    await loadChapters();
    await loadProgress();
  });
  loading.value = false;
});
</script>
