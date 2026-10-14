<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import PyButton from '@/components/ui/PyButton.vue';
import KcHeatmap from '@/components/teacher/KcHeatmap.vue';
import ResourceUsageChart from '@/components/teacher/ResourceUsageChart.vue';
import ClassroomOverviewTab from '@/components/teacher/ClassroomOverviewTab.vue';
import ClassroomQuestTab from '@/components/teacher/ClassroomQuestTab.vue';
import ClassroomMembersTab from '@/components/teacher/ClassroomMembersTab.vue';
import ClassroomHotspotsTab from '@/components/teacher/ClassroomHotspotsTab.vue';
import MainlineStarsTab from '@/components/teacher/MainlineStarsTab.vue';
import ChapterTestsTab from '@/components/teacher/ChapterTestsTab.vue';
import EndlessActivityTab from '@/components/teacher/EndlessActivityTab.vue';
import MainlineProgressTab from '@/components/teacher/MainlineProgressTab.vue';
import { useTeacherStore } from '@stores/teacher';
import { useToastStore } from '@stores/toast';
import { useClassroomLive } from '@/composables/useClassroomLive';
import { triggerRemediation, type RemediationResultDto } from '@/api/teacher';
import type { ClassroomQuestCreateRequest } from '@/api/dto/ClassroomQuest';

const route = useRoute();
const router = useRouter();
const teacher = useTeacherStore();
const toast = useToastStore();
const live = useClassroomLive();
const liveSubscribed = ref(false);

const tabs = [
  { id: 'overview', label: '概览' },
  { id: 'live', label: '直播' },
  { id: 'quests', label: '任务' },
  { id: 'heatmap', label: 'KC 热力图' },
  { id: 'hotspots', label: '高频误区' },
  { id: 'resources', label: '资源使用' },
  { id: 'members', label: '成员' },
  { id: 'ml-stars', label: '主线星级' },
  { id: 'ml-tests', label: '章测成绩' },
  { id: 'ml-endless', label: '无尽活跃' },
  { id: 'ml-progress', label: '主线进度' },
] as const;

type TabId = (typeof tabs)[number]['id'];
const activeTab = ref<TabId>('overview');

const classroomId = computed<number>(() => Number(route.params.id));

function connectLive(): void {
  liveSubscribed.value = true;
  live.connect(classroomId.value);
}

function disconnectLive(): void {
  liveSubscribed.value = false;
  live.disconnect();
}

const classroom = computed(() => teacher.current.classroom);
const dashboard = computed(() => teacher.current.dashboard);
const members = computed(() => teacher.current.members);
const quests = computed(() => teacher.current.quests);

// 班级级错误（成员 / 任务拉取失败，含 403）。与看板级错误分开展示：
// 只要其中任意一层挂了，页面都必须给出可见反馈 + 重试，而不是永久「正在加载...」。
const classroomError = computed(() => teacher.current.lastError ?? teacher.lastError);
const classroomLoading = computed(() => teacher.current.loading || teacher.loading);
const isForbidden = computed(() => {
  const message = classroomError.value;
  return message != null && /403|无权|权限|FORBIDDEN/i.test(message);
});
const classroomName = computed(() => {
  if (classroom.value) return classroom.value.name;
  if (classroomError.value) return '无法加载班级';
  return classroomLoading.value ? '正在加载...' : '未找到班级';
});

async function retry(): Promise<void> {
  await teacher.reloadCurrent(classroomId.value);
}

// ── 一键补救包（P6.1）：基于全班薄弱 KC 生成针对性补救资源 ──
const remediationLoading = ref(false);
const remediationError = ref<string | null>(null);
const remediationResult = ref<RemediationResultDto | null>(null);

async function generateRemediation(): Promise<void> {
  remediationLoading.value = true;
  remediationError.value = null;
  try {
    remediationResult.value = await triggerRemediation(classroomId.value);
  } catch (error) {
    remediationError.value = error instanceof Error ? error.message : '补救包生成失败';
  } finally {
    remediationLoading.value = false;
  }
}

// 班级级与看板级并列执行：任何一条失败都不得阻断另一条（旧写法是顺序 await，
// openClassroom 一抛错 loadDashboard 就永远不会跑，页面停在「正在加载...」）。
onMounted(async () => {
  await teacher.reloadCurrent(classroomId.value);
});

watch(
  () => Number(route.params.id),
  async (newId, oldId) => {
    if (newId === oldId) return;
    if (liveSubscribed.value) live.connect(newId);
    await teacher.reloadCurrent(newId);
  },
);

// 防重复提交：发布是「新建 classroom_quest 行」的非幂等写，双击 / 回车会造出两条同名任务
// （id 不同，uniq_quest_classroom_user 挡不住），每个学生因此拿到两条一模一样的任务。
const publishing = ref(false);

async function publishQuest(payload: ClassroomQuestCreateRequest) {
  if (publishing.value) return;
  publishing.value = true;
  try {
    const response = await teacher.publishQuest(classroomId.value, payload);
    toast.show(
      `发布成功：${response.createdQuests}/${response.totalStudents} 学生`,
      'success',
    );
    await teacher.loadDashboard(classroomId.value);
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '发布失败', 'error');
  } finally {
    publishing.value = false;
  }
}

async function archiveQuest(id: number) {
  try {
    await teacher.archiveQuest(classroomId.value, id);
    toast.show('已归档', 'success');
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '归档失败', 'error');
  }
}

async function removeMember(studentId: number) {
  try {
    await teacher.removeMember(classroomId.value, studentId);
    toast.show('已移除成员', 'success');
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '移除失败', 'error');
  }
}

const regeneratingJoinCode = ref(false);

async function regenerate() {
  if (regeneratingJoinCode.value) return;
  regeneratingJoinCode.value = true;
  try {
    const updated = await teacher.regenerateJoinCode(classroomId.value);
    toast.show(`已生成新加入码：${updated.joinCode ?? '——'}`, 'success');
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '重置失败', 'error');
  } finally {
    regeneratingJoinCode.value = false;
  }
}

function back() {
  router.push({ name: 'teacher' });
}
</script>

<template>
  <AppLayout>
    <section
      v-if="isForbidden"
      class="cd-banner cd-banner--error cd-banner--action cd-forbidden"
      role="alert"
      data-testid="teacher-forbidden"
    >
      <span>你没有查看这个班级的权限（仅班级归属教师可进入）。</span>
      <PyButton
        variant="secondary"
        size="sm"
        test-id="teacher-detail-back"
        @click="back"
      >
        返回班级列表
      </PyButton>
    </section>
    <!-- dashboard-01 pattern: sidebar + inset -->
    <div
      v-else
      class="cd-shell"
      data-testid="teacher-detail"
    >
      <!-- ─── Left sidebar nav ─── -->
      <aside class="cd-nav">
        <div class="cd-nav__head">
          <button
            class="cd-back"
            @click="back"
          >
            ← 班级列表
          </button>
          <h2 class="cd-nav__name">
            {{ classroomName }}
          </h2>
          <div class="cd-nav__meta">
            <span class="cd-badge">{{ classroom?.status }}</span>
            <code class="cd-code">{{ classroom?.joinCode ?? '——' }}</code>
          </div>
        </div>

        <nav class="cd-nav__list">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            class="cd-nav__item"
            :class="{ 'cd-nav__item--active': tab.id === activeTab }"
            @click="activeTab = tab.id"
          >
            {{ tab.label }}
          </button>
        </nav>

        <div class="cd-nav__foot">
          <PyButton
            variant="secondary"
            size="sm"
            test-id="regenerate-join-code"
            :disabled="regeneratingJoinCode"
            @click="regenerate"
          >
            {{ regeneratingJoinCode ? '重置中…' : '重置加入码' }}
          </PyButton>
          <PyButton
            variant="primary"
            size="sm"
            :disabled="remediationLoading"
            data-testid="remediation-trigger"
            @click="generateRemediation"
          >
            {{ remediationLoading ? '生成中…' : '一键生成补救包' }}
          </PyButton>
        </div>
      </aside>

      <!-- ─── Main content (SidebarInset pattern) ─── -->
      <main class="cd-main">
        <!-- 班级级错误（含 403）：独立于看板错误，必须显式可见并可重试 -->
        <div
          v-if="classroomError"
          class="cd-banner cd-banner--error cd-banner--action"
          data-testid="classroom-error"
        >
          <span>
            {{ isForbidden ? '你没有查看这个班级的权限（仅班级归属教师可进入）。' : `班级数据加载失败：${classroomError}` }}
          </span>
          <PyButton
            v-if="!isForbidden"
            variant="secondary"
            size="sm"
            test-id="classroom-retry"
            @click="retry"
          >
            重试
          </PyButton>
        </div>
        <p
          v-else-if="classroomLoading"
          class="cd-banner"
          data-testid="classroom-loading"
        >
          班级数据加载中...
        </p>

        <div
          v-if="dashboard.lastError"
          class="cd-banner cd-banner--error cd-banner--action"
          data-testid="dashboard-error"
        >
          <span>看板加载失败：{{ dashboard.lastError }}</span>
          <PyButton
            variant="secondary"
            size="sm"
            test-id="dashboard-retry"
            @click="teacher.loadDashboard(classroomId)"
          >
            重试
          </PyButton>
        </div>
        <p
          v-else-if="dashboard.loading"
          class="cd-banner"
        >
          看板加载中...
        </p>

        <section
          v-if="remediationError || remediationResult"
          class="cd-remediation"
          data-testid="remediation-panel"
        >
          <p
            v-if="remediationError"
            class="cd-remediation__error"
            data-testid="remediation-error"
          >
            补救包生成失败：{{ remediationError }}
          </p>
          <template v-else-if="remediationResult">
            <p class="cd-remediation__summary">
              共发现 {{ remediationResult.weakKcCount }} 个薄弱知识点
            </p>
            <p
              v-if="remediationResult.partial"
              class="cd-remediation__partial"
              data-testid="remediation-partial"
            >
              本次已生成 {{ remediationResult.packs.length }}/{{ remediationResult.weakKcCount }} 个补救包（受每小时生成配额限制），稍后可再次点击补齐剩余部分。
            </p>
            <p
              v-if="remediationResult.packs.length === 0"
              class="cd-empty"
              data-testid="remediation-empty"
            >
              全班暂无明显薄弱知识点，无需补救。
            </p>
            <ul
              v-else
              class="cd-remediation__packs"
              data-testid="remediation-packs"
            >
              <li
                v-for="pack in remediationResult.packs"
                :key="pack.kcCode"
                class="cd-remediation__pack"
                :data-testid="`remediation-pack-${pack.kcCode}`"
              >
                <span class="cd-remediation__kc">{{ pack.kcName }}</span>
                <span class="cd-remediation__count">{{ pack.resourceIds.length }} 个补救资源</span>
                <span class="cd-remediation__pages">证据页：{{ pack.evidencePageIds.length > 0 ? pack.evidencePageIds.join('、') : '—' }}</span>
              </li>
            </ul>
          </template>
        </section>

        <div class="cd-content">
          <ClassroomOverviewTab
            v-show="activeTab === 'overview'"
            :classroom="classroom"
            :members-count="members.length"
            :quest-summary="dashboard.questSummary"
            :kc-heatmap="dashboard.kcHeatmap"
            :recent-activity="dashboard.recentActivity"
          />

          <section
            v-show="activeTab === 'live'"
            class="live-tab"
            data-testid="classroom-live-tab"
          >
            <div class="live-header">
              <span :class="['live-dot', { 'live-dot--on': live.connected.value }]" />
              <span>{{ live.connected.value ? '已连接直播' : '未连接' }}</span>
              <PyButton
                v-if="!live.connected.value"
                variant="secondary"
                size="sm"
                @click="connectLive"
              >
                开始直播
              </PyButton>
              <PyButton
                v-else
                variant="secondary"
                size="sm"
                @click="disconnectLive"
              >
                断开
              </PyButton>
            </div>
            <p
              v-if="live.error.value"
              class="cd-banner cd-banner--error"
              data-testid="live-error"
            >
              {{ live.error.value }}
            </p>
            <div
              v-if="live.events.value.length === 0"
              class="cd-empty"
            >
              等待学生提交事件…
            </div>
            <ul
              v-else
              class="live-events"
            >
              <li
                v-for="(evt, i) in live.events.value"
                :key="i"
                class="live-event"
              >
                <span class="live-event__type">{{ evt.type }}</span>
                <span class="live-event__time">{{ new Date(evt.timestamp).toLocaleTimeString() }}</span>
                <pre class="live-event__data">{{ JSON.stringify(evt.data, null, 2) }}</pre>
              </li>
            </ul>
          </section>

          <ClassroomQuestTab
            v-show="activeTab === 'quests'"
            data-testid="quest-tab"
            :quests="quests"
            :quest-summary="dashboard.questSummary"
            :publishing="publishing"
            @publish="publishQuest"
            @archive="archiveQuest"
          />

          <KcHeatmap
            v-show="activeTab === 'heatmap'"
            :entries="dashboard.kcHeatmap"
          />

          <ClassroomHotspotsTab
            v-show="activeTab === 'hotspots'"
            :hotspots="dashboard.errorHotspots"
          />

          <ResourceUsageChart
            v-show="activeTab === 'resources'"
            :usage="dashboard.resourceUsage"
          />

          <ClassroomMembersTab
            v-show="activeTab === 'members'"
            :members="members"
            @remove="removeMember"
          />

          <!-- R-15 主线看板（自取数 Tab，仅依赖 classroomId；v-if 首次激活才拉取） -->
          <MainlineStarsTab
            v-if="activeTab === 'ml-stars'"
            :classroom-id="classroomId"
          />
          <ChapterTestsTab
            v-if="activeTab === 'ml-tests'"
            :classroom-id="classroomId"
          />
          <EndlessActivityTab
            v-if="activeTab === 'ml-endless'"
            :classroom-id="classroomId"
          />
          <MainlineProgressTab
            v-if="activeTab === 'ml-progress'"
            :classroom-id="classroomId"
          />
        </div>
      </main>
    </div>
  </AppLayout>
</template>

<style scoped>
/* ─── dashboard-01 shell: sidebar + inset ─── */
.cd-shell {
  display: grid;
  grid-template-columns: 220px 1fr;
  min-height: calc(100vh - 48px);
  background: var(--pyt-color-bg-primary);
}

/* ─── Left sidebar ─── */
.cd-nav {
  display: flex;
  flex-direction: column;
  border-right: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
}
.cd-nav__head {
  padding: 16px 14px 12px;
  border-bottom: 1px solid var(--pyt-color-border-soft);
  display: grid;
  gap: 6px;
}
.cd-back {
  background: none;
  border: none;
  color: var(--pyt-color-info);
  cursor: pointer;
  font-size: var(--pyt-font-size-sm);
  padding: 0;
  text-align: left;
}
.cd-back:hover { color: var(--pyt-color-text-primary); }
.cd-nav__name {
  margin: 0;
  font-size: var(--pyt-font-size-lg);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  line-height: 1.3;
  word-break: break-all;
}
.cd-nav__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.cd-badge {
  background: var(--pyt-color-info-bg);
  color: var(--pyt-color-info);
  border: 1px solid var(--pyt-color-info);
  border-radius: var(--pyt-radius-full);
  padding: 1px 8px;
  font-size: var(--pyt-font-size-xs);
  font-weight: 600;
}
.cd-code {
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-hint);
  letter-spacing: 0.5px;
}
.cd-nav__list {
  flex: 1 1 auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cd-nav__item {
  width: 100%;
  text-align: left;
  background: none;
  border: none;
  border-left: 3px solid transparent;
  border-radius: var(--pyt-radius-md);
  padding: 7px 10px;
  font-size: var(--pyt-font-size-base);
  font-family: var(--pyt-font-read);
  color: var(--pyt-color-text-muted);
  cursor: pointer;
  transition: background var(--pyt-motion-base), color var(--pyt-motion-base);
}
.cd-nav__item:hover {
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
}
.cd-nav__item--active {
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
  border-left-color: var(--pyt-color-gold);
}
.cd-nav__foot {
  padding: 12px 14px;
  border-top: 1px solid var(--pyt-color-border-soft);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* ─── Main content ─── */
.cd-main {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.cd-banner {
  padding: 10px 20px;
  background: var(--pyt-color-bg-soft);
  border-bottom: 1px solid var(--pyt-color-border-soft);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
  margin: 0;
}
.cd-banner--error {
  background: var(--pyt-color-danger-bg);
  border-color: var(--pyt-color-danger);
  color: var(--pyt-color-danger);
}
.cd-banner--action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.cd-forbidden {
  margin: 16px;
}
.cd-content {
  flex: 1 1 auto;
  padding: 20px 24px;
  overflow-y: auto;
}
.cd-empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}

/* ─── Remediation panel ─── */
.cd-remediation {
  margin: 12px 20px 0;
  padding: 12px 14px;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  display: grid;
  gap: 8px;
}
.cd-remediation__error { margin: 0; color: var(--pyt-color-danger); font-size: var(--pyt-font-size-sm); }
.cd-remediation__summary { margin: 0; font-size: 13px; font-weight: 600; color: var(--pyt-color-text-primary); }
.cd-remediation__partial { margin: 0; font-size: 12px; color: var(--pyt-color-warning); }
.cd-remediation__packs { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
.cd-remediation__pack {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
  font-size: 13px;
}
.cd-remediation__kc { font-weight: 600; color: var(--pyt-color-text-primary); }
.cd-remediation__count { font-size: 12px; color: var(--pyt-color-info); white-space: nowrap; }
.cd-remediation__pages { font-size: 12px; color: var(--pyt-color-text-hint); white-space: nowrap; }

/* ─── Live tab ─── */
.live-tab { display: grid; gap: 12px; }
.live-header { display: flex; align-items: center; gap: 10px; padding: 8px; background: var(--pyt-color-bg-soft); border-radius: 6px; font-size: 13px; }
.live-dot { width: 10px; height: 10px; border-radius: 50%; background: #ccc; }
.live-dot--on { background: #16a34a; animation: pulse-live 1.5s infinite; }
@keyframes pulse-live { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
.live-events { list-style: none; padding: 0; margin: 0; display: grid; gap: 6px; max-height: 400px; overflow: auto; }
.live-event { padding: 8px; background: var(--pyt-color-bg-elevated); border: 1px solid var(--pyt-color-border); border-radius: 4px; display: grid; grid-template-columns: auto 1fr; gap: 6px; font-size: 12px; }
.live-event__type { font-weight: 600; color: var(--pyt-color-info); text-transform: uppercase; font-size: 10px; padding: 2px 6px; background: var(--pyt-color-info-bg); border-radius: 3px; }
.live-event__time { color: var(--pyt-color-text-hint); font-size: 11px; text-align: right; }
.live-event__data { grid-column: 1 / -1; margin: 0; padding: 6px; background: var(--pyt-color-bg-soft); border: 1px solid var(--pyt-color-border); font-size: 11px; white-space: pre-wrap; max-height: 100px; overflow: auto; }

/* ─── Responsive ─── */
@media (max-width: 900px) {
  .cd-shell { grid-template-columns: 1fr; }
  .cd-nav { border-right: none; border-bottom: 2px solid var(--pyt-color-border); }
  .cd-nav__list { flex-direction: row; flex-wrap: wrap; }
  .cd-nav__item { width: auto; }
}
</style>
