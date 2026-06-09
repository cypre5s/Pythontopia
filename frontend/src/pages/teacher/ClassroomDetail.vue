<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import KcHeatmap from '@/components/teacher/KcHeatmap.vue';
import ResourceUsageChart from '@/components/teacher/ResourceUsageChart.vue';
import ClassroomOverviewTab from '@/components/teacher/ClassroomOverviewTab.vue';
import ClassroomQuestTab from '@/components/teacher/ClassroomQuestTab.vue';
import ClassroomMembersTab from '@/components/teacher/ClassroomMembersTab.vue';
import ClassroomHotspotsTab from '@/components/teacher/ClassroomHotspotsTab.vue';
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

const tabs = [
  { id: 'overview', label: '概览' },
  { id: 'live', label: '直播' },
  { id: 'quests', label: '任务' },
  { id: 'heatmap', label: 'KC 热力图' },
  { id: 'hotspots', label: '高频误区' },
  { id: 'resources', label: '资源使用' },
  { id: 'members', label: '成员' },
] as const;

type TabId = (typeof tabs)[number]['id'];
const activeTab = ref<TabId>('overview');

const classroomId = computed<number>(() => Number(route.params.id));

const classroom = computed(() => teacher.current.classroom);
const dashboard = computed(() => teacher.current.dashboard);
const members = computed(() => teacher.current.members);
const quests = computed(() => teacher.current.quests);

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

onMounted(async () => {
  await teacher.openClassroom(classroomId.value);
  await teacher.loadDashboard(classroomId.value);
});

watch(
  () => Number(route.params.id),
  async (newId, oldId) => {
    if (newId === oldId) return;
    await teacher.openClassroom(newId);
    await teacher.loadDashboard(newId);
  },
);

async function publishQuest(payload: ClassroomQuestCreateRequest) {
  try {
    const response = await teacher.publishQuest(classroomId.value, payload);
    toast.show(
      `发布成功：${response.createdQuests}/${response.totalStudents} 学生`,
      'success',
    );
    await teacher.loadDashboard(classroomId.value);
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '发布失败', 'error');
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

async function regenerate() {
  try {
    const updated = await teacher.regenerateJoinCode(classroomId.value);
    toast.show(`已生成新加入码：${updated.joinCode ?? '——'}`, 'success');
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '重置失败', 'error');
  }
}

function back() {
  router.push({ name: 'teacher' });
}
</script>

<template>
  <AppLayout>
    <section
      class="teacher-detail"
      data-testid="teacher-detail"
    >
      <header>
        <button
          class="back"
          @click="back"
        >
          ← 返回班级列表
        </button>
        <h1>{{ classroom?.name ?? '正在加载...' }}</h1>
        <div class="toolbar">
          <span class="badge">{{ classroom?.status }}</span>
          <span class="code">加入码：<code>{{ classroom?.joinCode ?? '——' }}</code></span>
          <button
            class="secondary"
            @click="regenerate"
          >
            重置加入码
          </button>
        </div>
      </header>

      <!-- ── 一键补救包（P6.1）：全班薄弱 KC → 针对性补救资源 ── -->
      <section
        class="remediation"
        data-testid="remediation-panel"
      >
        <div class="remediation__head">
          <button
            type="button"
            class="remediation__btn"
            data-testid="remediation-trigger"
            :disabled="remediationLoading"
            @click="generateRemediation"
          >
            {{ remediationLoading ? '生成中…' : '一键生成补救包' }}
          </button>
          <span class="remediation__hint">
            基于全班薄弱 KC 自动生成针对性补救资源（含课件证据页）
          </span>
        </div>

        <p
          v-if="remediationError"
          class="error"
          data-testid="remediation-error"
        >
          补救包生成失败：{{ remediationError }}
        </p>
        <template v-else-if="remediationResult">
          <p class="remediation__summary">
            共发现 {{ remediationResult.weakKcCount }} 个薄弱知识点
          </p>
          <p
            v-if="remediationResult.packs.length === 0"
            class="empty"
            data-testid="remediation-empty"
          >
            全班暂无明显薄弱知识点，无需补救。
          </p>
          <ul
            v-else
            class="remediation__packs"
            data-testid="remediation-packs"
          >
            <li
              v-for="pack in remediationResult.packs"
              :key="pack.kcCode"
              class="remediation__pack"
              :data-testid="`remediation-pack-${pack.kcCode}`"
            >
              <span class="remediation__kc">{{ pack.kcName }}</span>
              <span class="remediation__count">{{ pack.resourceIds.length }} 个补救资源</span>
              <span class="remediation__pages">
                证据页：{{ pack.evidencePageIds.length > 0 ? pack.evidencePageIds.join('、') : '—' }}
              </span>
            </li>
          </ul>
        </template>
      </section>

      <nav class="tabs">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="{ active: tab.id === activeTab }"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
        </button>
      </nav>

      <p
        v-if="dashboard.lastError"
        class="error"
      >
        看板加载失败：{{ dashboard.lastError }}
      </p>
      <p
        v-else-if="dashboard.loading"
        class="empty"
      >
        看板加载中...
      </p>

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
          <button
            v-if="!live.connected.value"
            class="secondary"
            @click="live.connect(classroomId)"
          >
            开始直播
          </button>
          <button
            v-else
            class="secondary"
            @click="live.disconnect()"
          >
            断开
          </button>
        </div>
        <div v-if="live.events.value.length === 0" class="empty">
          等待学生提交事件…
        </div>
        <ul v-else class="live-events">
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
        :quests="quests"
        :quest-summary="dashboard.questSummary"
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
    </section>
  </AppLayout>
</template>

<style scoped>
.teacher-detail {
  padding: 20px 24px;
  display: grid;
  gap: 16px;
}
.back {
  background: transparent;
  border: none;
  color: #2f5b8a;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
  margin-bottom: 6px;
}
header h1 {
  margin: 0 0 6px;
  font-size: 22px;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.badge {
  background: #2f5b8a;
  color: var(--pyt-color-bg-elevated);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
}
.code {
  font-size: 13px;
  color: var(--pyt-color-text-primary);
}
.secondary {
  background: transparent;
  color: #2f5b8a;
  border: 2px solid #2f5b8a;
  border-radius: 6px;
  padding: 4px 10px;
  cursor: pointer;
}
.tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  border-bottom: 2px solid var(--pyt-color-border);
  padding-bottom: 4px;
}
.tabs button {
  border: none;
  background: transparent;
  padding: 6px 14px;
  cursor: pointer;
  color: var(--pyt-color-text-primary);
  font-weight: 500;
  border-bottom: 3px solid transparent;
}
.tabs button.active {
  color: #2f5b8a;
  border-bottom-color: #2f5b8a;
}
.error {
  background: #fbe9d7;
  border: 2px solid #c97f3a;
  border-radius: var(--pyt-radius-lg);
  padding: 10px;
  color: var(--pyt-color-text-hint);
}
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
}

/* ── 一键补救包 ── */
.remediation {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
}
.remediation__head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.remediation__btn {
  background: #2f5b8a;
  color: var(--pyt-color-bg-elevated);
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  cursor: pointer;
  font-weight: 600;
  font-size: 14px;
}
.remediation__btn:disabled {
  opacity: 0.6;
  cursor: progress;
}
.remediation__hint {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.remediation__summary {
  margin: 0;
  font-size: 13px;
  color: var(--pyt-color-text-primary);
  font-weight: 600;
}
.remediation__packs {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}
.remediation__pack {
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
.remediation__kc {
  font-weight: 600;
  color: var(--pyt-color-text-primary);
}
.remediation__count {
  font-size: 12px;
  color: #2f5b8a;
  white-space: nowrap;
}
.remediation__pages {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  white-space: nowrap;
}

.live-tab { display: grid; gap: 12px; }
.live-header { display: flex; align-items: center; gap: 10px; padding: 8px; background: var(--pyt-color-bg-soft); border-radius: 6px; font-size: 13px; }
.live-dot { width: 10px; height: 10px; border-radius: 50%; background: #ccc; }
.live-dot--on { background: #16a34a; animation: pulse-live 1.5s infinite; }
@keyframes pulse-live { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
.live-events { list-style: none; padding: 0; margin: 0; display: grid; gap: 6px; max-height: 400px; overflow: auto; }
.live-event { padding: 8px; background: var(--pyt-color-bg-elevated); border: 1px solid var(--pyt-color-border); border-radius: 4px; display: grid; grid-template-columns: auto 1fr; gap: 6px; font-size: 12px; }
.live-event__type { font-weight: 600; color: #2f5b8a; text-transform: uppercase; font-size: 10px; padding: 2px 6px; background: #e0eaf5; border-radius: 3px; }
.live-event__time { color: var(--pyt-color-text-hint); font-size: 11px; text-align: right; }
.live-event__data { grid-column: 1 / -1; margin: 0; padding: 6px; background: var(--pyt-color-bg-soft); border: 1px solid var(--pyt-color-border); font-size: 11px; white-space: pre-wrap; max-height: 100px; overflow: auto; }
</style>
