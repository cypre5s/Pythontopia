<script setup lang="ts">
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import PyButton from '@/components/ui/PyButton.vue';
import PyTabs from '@/components/ui/PyTabs.vue';
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

const TAB_KEYS = new Set<string>(TABS.map((t) => t.key));

/** 支持 /board?tab=classroom 深链：班长对话「领班级任务」直接落到班级任务页签。 */
function initialTab(): TabKey {
  try {
    const q = useRoute().query['tab'];
    const key = typeof q === 'string' ? q : null;
    if (key && TAB_KEYS.has(key)) return key as TabKey;
  } catch {
    /* 无路由上下文（独立挂载测试）时走默认 */
  }
  return 'today';
}

const activeTab = ref<TabKey>(initialTab());
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

function selectTab(key: string): void {
  activeTab.value = key as TabKey;
}
</script>

<template>
  <section
    class="board"
    data-testid="board-panel"
  >
    <header class="header">
      <h2>公告板</h2>
      <PyButton
        variant="primary"
        test-id="board-plan"
        :disabled="planning"
        @click="planNow"
      >
        {{ planning ? '规划中…' : '规划本周路径' }}
      </PyButton>
    </header>

    <PyTabs
      :tabs="TABS"
      :model-value="activeTab"
      test-id-prefix="tab"
      @update:model-value="selectTab"
    />

    <TodayQuestsTab v-if="activeTab === 'today'" />
    <PathTreeTab v-else-if="activeTab === 'path'" />
    <ClassroomQuestsTab v-else-if="activeTab === 'classroom'" />
    <ArchivedPathsTab v-else-if="activeTab === 'archived'" />
  </section>
</template>

<style scoped>
.board {
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-soft) 100%);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
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
</style>
