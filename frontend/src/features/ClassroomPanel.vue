<template>
  <section class="town-workspace classroom-workspace">
    <div class="town-book" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
      <aside :hidden="sidebarCollapsed" :inert="sidebarCollapsed" class="town-sidebar"><SidebarToggle />
        <nav class="town-directory" aria-label="班级目录">
          <button v-for="item in sections" :key="item.key" class="town-directory-item" :class="{ 'is-current': section === item.key }" :aria-pressed="section === item.key" :disabled="busy" @click="selectSection(item.key)"><GameIcon :name="item.icon" /><span>{{ item.label }}</span></button>
        </nav>
        <div class="classroom-sidebar-summary"><GameIcon name="people" /><span>已加入 <strong>{{ session.classes.length }}</strong> 个班级</span></div>
      </aside>
      <main class="town-content classroom-content">
        <p v-if="error" class="classroom-message is-error" role="alert">{{ error }}</p>
        <p v-if="message" class="classroom-message" role="status">{{ message }}</p>
        <template v-if="section === 'current'">
          <section v-if="currentClass" class="town-section current-classroom" aria-label="当前班级">
            <div class="current-classroom-header"><div class="classroom-emblem" aria-hidden="true"><GameIcon name="people" /></div><div><span class="classroom-status">当前班级</span><h3>{{ currentClass.name }}</h3><p v-if="currentClass.teacher_name">授课教师：{{ currentClass.teacher_name }}</p></div></div>
            <dl class="classroom-details"><div><dt>课程</dt><dd>Python 语言基础</dd></div><div><dt>学习者</dt><dd>{{ session.user?.name }}</dd></div><div><dt>章节</dt><dd>{{ availableChapters }} 章已开放 · {{ completedChapters }} 章已完成</dd></div></dl>
          </section>
          <div v-else class="town-empty classroom-no-current"><GameIcon name="people" /><h3>尚未选择班级</h3><p>选择已加入的班级，或使用教师提供的加入码加入新班级。</p><div class="classroom-actions"><button v-if="session.classes.length" class="town-primary" @click="selectSection('switch')">选择班级</button><button class="town-secondary" @click="selectSection('join')">加入班级</button></div></div>
          <section v-if="currentClass" class="town-section classroom-learning" aria-label="当前学习进度">
            <div class="classroom-section-heading"><h3><GameIcon name="chart" />学习进度</h3><span>{{ completedMainQuests }} / {{ mainQuests.length }} 项主线已完成</span></div>
            <template v-if="currentQuest">
              <div class="classroom-quest-heading"><h4>{{ currentQuest.title }}</h4><span class="classroom-status" :class="{ 'is-complete': currentQuest.status === 'complete' }">{{ currentQuest.status === 'complete' ? '已完成' : '进行中' }}</span></div>
              <div class="classroom-progress-caption"><span>任务步骤</span><strong>{{ currentQuest.status === 'complete' ? '已完成' : `${completedSteps} / ${requiredSteps.length} 步` }}</strong></div>
              <progress class="classroom-progress" :value="currentQuest.status === 'complete' ? Math.max(1, requiredSteps.length) : completedSteps" :max="Math.max(1, requiredSteps.length)" aria-label="当前主线任务进度" />
              <div v-if="nextGoal && currentQuest.status !== 'complete'" class="classroom-next-goal"><GameIcon name="compass" /><div><span>当前目标</span><strong>{{ nextGoal.title }}</strong></div></div>
              <p v-else-if="currentQuest.status === 'complete'" class="classroom-progress-note">当前主线已完成，学习记录已保存在这个班级。</p>
              <p v-else class="classroom-progress-note">任务开放后，可在小镇继续学习。</p>
            </template>
            <p v-else class="town-empty">当前班级还没有已开放的主线任务。</p>
          </section>
        </template>
        <section v-else-if="section === 'switch'" class="town-section classroom-switch" aria-label="切换班级">
          <div class="classroom-section-heading"><h3>切换班级</h3><span>{{ session.classes.length }} 个已加入班级</span></div><p class="classroom-section-note">选择一个班级，继续该班级的学习。</p>
          <div v-if="session.classes.length" class="classroom-options">
            <button v-for="classroom in session.classes" :key="classroom.id" class="classroom-option" :class="{ 'is-current': session.classroomId === classroom.id }" :disabled="busy || session.classroomId === classroom.id" :aria-label="`${session.classroomId === classroom.id ? '当前班级' : '切换到'}：${classroom.name}`" @click="switchClass(classroom.id)"><span class="classroom-option-icon"><GameIcon name="people" /></span><span class="classroom-option-details"><strong>{{ classroom.name }}</strong><small v-if="classroom.teacher_name">{{ classroom.teacher_name }}</small></span><span class="classroom-option-action">{{ switchingId === classroom.id ? '正在切换…' : session.classroomId === classroom.id ? '当前班级' : '切换' }}</span><GameIcon v-if="session.classroomId !== classroom.id" name="arrowRight" /></button>
          </div>
          <div v-else class="town-empty">还没有加入班级。<button class="town-secondary" @click="selectSection('join')">加入班级</button></div>
        </section>
        <section v-else class="town-section classroom-join" aria-label="加入新班级">
          <h3>加入新班级</h3><p class="classroom-section-note">输入教师提供的加入码。</p>
          <form class="classroom-join-form" @submit.prevent="join"><label class="town-field" for="classroom-join-code">班级加入码</label><div class="classroom-join-controls"><input id="classroom-join-code" v-model="joinCode" placeholder="输入加入码" autocomplete="off" required :disabled="busy" /><button class="town-primary" :disabled="busy || !joinCode.trim()">{{ joining ? '正在加入…' : '加入班级' }}</button></div></form>
          <p class="classroom-progress-note">加入后可以选择切换，已有班级的学习记录会保留。</p>
        </section>
      </main>
    </div>
  </section>
</template>
<script setup lang="ts">
import SidebarToggle from "../shared/SidebarToggle.vue";
import { useWorkspaceSidebar } from "../shared/workspaceSidebar";
const { collapsed: sidebarCollapsed } = useWorkspaceSidebar();
import { computed, onBeforeUnmount, ref } from "vue";
import GameIcon from "../shared/GameIcon.vue";
import { useSession } from "../shared/session";
import { api } from "../shared/api";
import { requiredQuestNodes } from "../shared/questProgress";
type Section = "current" | "switch" | "join";
const sections = [{ key: "current", label: "当前班级", icon: "people" }, { key: "switch", label: "切换班级", icon: "refresh" }, { key: "join", label: "加入班级", icon: "plus" }] as const;
const session = useSession(), section = ref<Section>("current"), joinCode = ref("");
const switchingId = ref<number | null>(null), joining = ref(false), error = ref(""), message = ref("");
const busy = computed(() => switchingId.value !== null || joining.value);
const currentClass = computed(() => session.classes.find(item => item.id === session.classroomId));
const mainQuests = computed(() => session.world?.quests.filter(quest => quest.kind === "main") ?? []);
const completedMainQuests = computed(() => mainQuests.value.filter(quest => quest.status === "complete").length);
const availableChapters = computed(() => session.world?.chapters.filter(chapter => chapter.available).length ?? 0);
const completedChapters = computed(() => {
  const chapters = new Set(mainQuests.value.map(quest => quest.chapter_id));
  return [...chapters].filter(id => mainQuests.value.filter(quest => quest.chapter_id === id).every(quest => quest.status === "complete")).length;
});
const currentQuest = computed(() => mainQuests.value.find(quest => quest.id === session.trackedQuest && quest.status !== "complete") ?? mainQuests.value.find(quest => quest.status !== "complete") ?? mainQuests.value[0]);
const requiredSteps = computed(() => requiredQuestNodes(currentQuest.value));
const completedSteps = computed(() => requiredSteps.value.filter(node => node.status === "complete").length);
const nextGoal = computed(() => requiredSteps.value.find(node => node.status === "ready"));
let disposed = false, operation = 0;
const currentOperation = (owner: number | undefined, generation: number) => !disposed && session.user?.id === owner && generation === operation;
onBeforeUnmount(() => { disposed = true; operation++; });
function selectSection(next: Section) { section.value = next; error.value = ""; message.value = ""; }
async function switchClass(id: number) {
  if (busy.value || id === session.classroomId) return;
  const owner = session.user?.id, generation = ++operation;
  switchingId.value = id; error.value = ""; message.value = "";
  try {
    const switched = await session.switchClassroom(id);
    if (!switched || !currentOperation(owner, generation)) return;
    section.value = "current";
    message.value = `已切换到${currentClass.value?.name ?? "所选班级"}。`;
  } catch (cause) {
    if (currentOperation(owner, generation)) error.value = cause instanceof Error ? cause.message : "切换班级失败，请重试。";
  } finally { if (currentOperation(owner, generation)) switchingId.value = null; }
}
async function join() {
  const code = joinCode.value.trim();
  if (busy.value || !code) return;
  const owner = session.user?.id, generation = ++operation;
  joining.value = true; error.value = ""; message.value = "";
  try {
    await api.post("/classrooms/join", { join_code: code });
    if (!currentOperation(owner, generation)) return;
    await session.load();
    if (!currentOperation(owner, generation)) return;
    joinCode.value = ""; section.value = "switch";
    message.value = "已加入班级，可以在列表中选择切换。";
  } catch (cause) {
    if (currentOperation(owner, generation)) error.value = cause instanceof Error ? cause.message : "加入班级失败，请重试。";
  } finally { if (currentOperation(owner, generation)) joining.value = false; }
}
</script>
<style scoped>
.classroom-sidebar-summary { display:flex;align-items:center;gap:10px;margin:auto 20px 22px;padding-top:20px;border-top:1px solid #b8a38255;color:#d0c4a7;font-size:.9rem; }
.classroom-sidebar-summary > svg { width:21px;height:21px;flex-shrink:0; }
.classroom-content { display:flex;flex-direction:column;gap:24px; }
.current-classroom-header { display:flex;align-items:center;gap:22px; }
.current-classroom-header > div:last-child { min-width:0; }.current-classroom-header h3 { overflow-wrap:anywhere; }
.classroom-emblem { display:grid;place-items:center;width:80px;height:80px;flex-shrink:0;color:#f9edce;background:linear-gradient(135deg,#3f675b,#254a40);border:1px solid #224337;border-radius:18px;box-shadow:inset 0 0 0 5px #fff3c014,0 4px 0 #ad906626; }
.classroom-emblem svg { width:42px;height:42px; }
.current-classroom h3 { margin:8px 0 0;font-size:clamp(1.4rem,2.2vw,2rem);line-height:1.5;color:#3e4738; }
.current-classroom-header p { color:#7a705e;margin:8px 0 0; }
.classroom-status { display:inline-flex;align-items:center;width:fit-content;border-radius:20px;padding:4px 11px;background:#e7dfcd;color:#715d3f;font-size:.85rem;white-space:nowrap; }
.classroom-status.is-complete { background:#e2ebd5;color:#4c6a38; }
.classroom-details { display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:22px;padding:24px 0 0;margin:26px 0 0;border-top:1px solid #d9cbb2; }
.classroom-details > div { min-width:0; }.classroom-details dt { font-size:.9rem;color:#8a775c;margin-bottom:8px; }.classroom-details dd { margin:0;color:#544c3c;line-height:1.65;overflow-wrap:anywhere; }
.classroom-section-heading { display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;margin-bottom:20px; }.classroom-section-heading h3 { display:flex;align-items:center;gap:10px;margin:0; }.classroom-section-heading h3 svg { width:23px;height:23px;color:#967244; }.classroom-section-heading > span { color:#85745d;font-size:.9rem; }
.classroom-quest-heading { display:flex;align-items:center;justify-content:space-between;gap:20px;padding-top:20px;border-top:1px solid #ddd0b7; }.classroom-quest-heading h4 { margin:0;color:#4b4938;font-size:1.1rem;font-weight:600;line-height:1.7; }
.classroom-progress-caption { display:flex;justify-content:space-between;gap:16px;margin:24px 0 12px;font-size:.95rem;color:#79684e; }.classroom-progress-caption strong { font-weight:500;color:#526b44; }
.classroom-progress { width:100%;height:14px;appearance:none;border:1px solid #c9b994;border-radius:14px;overflow:hidden;background:#e7ddc7; }.classroom-progress::-webkit-progress-bar { background:#e7ddc7;box-shadow:inset 0 2px 3px #715d2920; }.classroom-progress::-webkit-progress-value { background:linear-gradient(90deg,#7a9e62,#a3bd7b);border-radius:14px; }.classroom-progress::-moz-progress-bar { background:#87a463; }
.classroom-next-goal { display:flex;align-items:center;gap:14px;margin-top:24px;padding:18px 20px;background:#ece7d4;border-radius:12px;border:1px solid #d8ccb3; }.classroom-next-goal > svg { width:28px;height:28px;color:#8c7748;flex-shrink:0; }.classroom-next-goal span { display:block;color:#8d7c63;font-size:.85rem;margin-bottom:5px; }.classroom-next-goal strong { display:block;font-weight:500;line-height:1.6;color:#4b503b; }
.classroom-next-goal > div { min-width:0;overflow-wrap:anywhere; }
.classroom-progress-note,.classroom-section-note { color:#84745d;line-height:1.8;margin:18px 0 0;font-size:.95rem; }.classroom-section-note { margin:0 0 24px; }
.classroom-actions { display:flex;justify-content:center;gap:14px;flex-wrap:wrap;margin-top:24px; }.classroom-no-current > svg { width:48px;height:48px;color:#9a845c; }
.classroom-options { display:grid;gap:14px; }.classroom-option { width:100%;display:flex;align-items:center;gap:18px;padding:20px;text-align:left;border:1px solid #d9c8a7;border-radius:12px;background:#fffbf2;color:#534b3b; }.classroom-option:hover:not(:disabled) { background:#fff5dc;border-color:#bb9557; }.classroom-option.is-current { background:#e9ecd9;border-color:#b7c49c;opacity:1; }.classroom-option-icon { display:grid;place-items:center;width:48px;height:48px;flex-shrink:0;border-radius:12px;background:#efe6d2;color:#8c7750; }.classroom-option-icon svg { width:25px;height:25px; }.classroom-option-details { flex:1;min-width:0; }.classroom-option-details strong { display:block;font-size:1.05rem;line-height:1.65;overflow-wrap:anywhere; }.classroom-option-details small { display:block;color:#8c7a62;margin-top:4px;font-size:.85rem; }.classroom-option-action { color:#6a7653;font-size:.9rem;white-space:nowrap; }.classroom-option > svg { width:20px;height:20px;flex-shrink:0;color:#9b835a; }
.classroom-join-form { margin:30px 0 0;max-width:640px; }.classroom-join-form label { display:block;color:#6c5a40;margin-bottom:12px; }.classroom-join-controls { display:flex;gap:14px; }.classroom-join-controls input { flex:1;min-width:0;border:1px solid #cbbb9b;border-radius:10px;padding:14px 16px;color:#494634;background:#fffcf5;min-height:48px; }.classroom-join-controls button { min-width:120px; }
.classroom-message { margin:0;padding:14px 18px;border:1px solid #bdcba4;border-radius:10px;background:#eaf0df;color:#49633c;line-height:1.7; }.classroom-message.is-error { background:#f8e7da;border-color:#dab6a2;color:#8a4835; }
@media(max-width:850px) { .classroom-details { gap:16px;grid-template-columns:1fr; }.classroom-details > div { display:flex;justify-content:space-between;gap:20px; }.classroom-details dt { margin:0; }.classroom-details dd { text-align:right; }.current-classroom-header { gap:16px; }.classroom-emblem { width:64px;height:64px; }.classroom-emblem svg { width:34px;height:34px; } }
@media(max-width:650px) { .classroom-sidebar-summary { display:none; }.classroom-option { padding:16px;gap:12px; }.classroom-option-icon { display:none; }.classroom-option-action { font-size:.85rem; }.classroom-join-controls { flex-direction:column; }.classroom-quest-heading { align-items:flex-start;flex-wrap:wrap; }.classroom-section-heading > span { font-size:.85rem; }.classroom-next-goal { padding:16px; } }
</style>
