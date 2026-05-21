<script setup lang="ts">
import { ref } from 'vue';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import TodayQuestsTab from './TodayQuestsTab.vue';
import PathTreeTab from './PathTreeTab.vue';
import ClassroomQuestsTab from './ClassroomQuestsTab.vue';
import ArchivedPathsTab from './ArchivedPathsTab.vue';

type TabKey = 'today' | 'path' | 'classroom' | 'archived';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'today', label: '今日任务' },
  { key: 'path', label: '本周路径' },
  { key: 'classroom', label: '班级任务' },
  { key: 'archived', label: '历史路径' },
];

const activeTab = ref<TabKey>('today');
const planning = ref<boolean>(false);

const learning = useLearningStore();
const toast = useToastStore();

async function planNow(): Promise<void> {
  planning.value = true;
  try {
    await learning.planPath();
    toast.show('路径规划完成。', 'success');
    activeTab.value = 'path';
  } catch (error) {
    const message = error instanceof Error ? error.message : '路径规划失败';
    toast.show(message, 'error');
  } finally {
    planning.value = false;
  }
}
</script>

<template>
  <section
    class="board"
    data-testid="board-panel"
  >
    <header class="header">
      <div>
        <h2>公告板</h2>
        <p class="hint">
          公告板是教育闭环的"播种 → 浇水"调度站：今日任务、本周路径与班级任务全部真实落库；
          点击"规划本周路径"由 PathPlannerAgent 基于你的画像 + KC mastery 即时生成。
        </p>
      </div>
      <button
        type="button"
        class="primary"
        :disabled="planning"
        @click="planNow"
      >
        {{ planning ? '规划中…' : '规划本周路径' }}
      </button>
    </header>

    <nav class="tabs">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        type="button"
        :class="['tab', { active: activeTab === tab.key }]"
        :data-testid="`tab-${tab.key}`"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <TodayQuestsTab v-if="activeTab === 'today'" />
    <PathTreeTab v-else-if="activeTab === 'path'" />
    <ClassroomQuestsTab v-else-if="activeTab === 'classroom'" />
    <ArchivedPathsTab v-else-if="activeTab === 'archived'" />
  </section>
</template>

<style scoped>
.board {
  background: linear-gradient(180deg, #fff7e6 0%, #fdf3d0 100%);
  border: 2px solid #c9a86b;
  border-radius: 10px;
  padding: 16px;
  display: grid;
  gap: 14px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.header h2 {
  margin: 0;
}
.hint {
  margin: 4px 0 0;
  color: #6e4f1d;
  font-size: 12px;
}
.primary {
  background: #6e4f1d;
  color: #fff7e6;
  border: 2px solid #4d3613;
  border-radius: 4px;
  padding: 6px 16px;
  cursor: pointer;
  font-weight: 600;
}
.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.tabs {
  display: flex;
  gap: 6px;
  border-bottom: 2px solid #c9a86b;
}
.tab {
  background: #fff7e6;
  border: 1px solid #c9a86b;
  border-bottom: none;
  padding: 6px 12px;
  cursor: pointer;
  border-radius: 6px 6px 0 0;
  font-weight: 600;
  color: #4d3613;
}
.tab.active {
  background: #6e4f1d;
  color: #fff7e6;
}
</style>
