<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import type { QuestDto } from '@/api/dto/Quest';
import { kcDisplayName } from '@/data/kcNames';

const learning = useLearningStore();
const toast = useToastStore();

const quests = computed<QuestDto[]>(() => learning.quests.today);

onMounted(async () => {
  if (learning.quests.today.length === 0 && !learning.quests.loading) {
    await learning.loadTodayQuests();
  }
});

async function start(id: number): Promise<void> {
  try {
    await learning.startQuestById(id);
    toast.show('已开始任务。', 'success');
  } catch (error) {
    const message = error instanceof Error ? error.message : '开始任务失败';
    toast.show(message, 'error');
  }
}

async function complete(id: number): Promise<void> {
  try {
    await learning.completeQuestById(id, { note: '在公告板完成' });
    toast.show('任务已完成。', 'success');
  } catch (error) {
    const message = error instanceof Error ? error.message : '任务完成失败';
    toast.show(message, 'error');
  }
}

async function skip(id: number): Promise<void> {
  try {
    await learning.skipQuestById(id, { reason: '用户跳过' });
    toast.show('已跳过任务。', 'success');
  } catch (error) {
    const message = error instanceof Error ? error.message : '跳过任务失败';
    toast.show(message, 'error');
  }
}
</script>

<template>
  <section
    class="today-tab"
    data-testid="today-tab"
  >
    <p
      v-if="learning.quests.loading"
      class="feedback"
      role="status"
    >
      正在加载今日任务…
    </p>
    <div
      v-else-if="learning.quests.lastError"
      class="feedback error"
      role="alert"
    >
      <p>加载今日任务失败：{{ learning.quests.lastError }}</p>
      <button
        type="button"
        data-testid="today-quests-retry"
        @click="learning.loadTodayQuests()"
      >
        重试
      </button>
    </div>
    <p
      v-else-if="quests.length === 0"
      class="empty"
    >
      今日暂无待办任务。可以去公告板的“规划本周路径”重新生成。
    </p>
    <ul
      v-else-if="quests.length > 0"
      class="quest-list"
      data-testid="quest-list"
    >
      <li
        v-for="quest in quests"
        :key="quest.id"
        class="quest-card"
      >
        <header>
          <span
            class="badge"
            :class="quest.source.toLowerCase()"
          >
            {{ quest.source }}
          </span>
          <h3>{{ quest.title }}</h3>
        </header>
        <p
          v-if="quest.description"
          class="description"
        >
          {{ quest.description }}
        </p>
        <p class="meta">
          知识点: {{ quest.kcCode ? kcDisplayName(quest.kcCode) : '未关联' }} · 类型: {{ quest.nodeType }} · 截止 {{ quest.dueDate }}
        </p>
        <div class="actions">
          <button
            v-if="quest.status === 'OPEN'"
            type="button"
            class="primary"
            @click="start(quest.id)"
          >
            开始
          </button>
          <button
            v-if="quest.status === 'OPEN' || quest.status === 'IN_PROGRESS'"
            type="button"
            class="primary"
            @click="complete(quest.id)"
          >
            完成
          </button>
          <button
            v-if="quest.status === 'OPEN' || quest.status === 'IN_PROGRESS'"
            type="button"
            class="ghost"
            @click="skip(quest.id)"
          >
            跳过
          </button>
          <span
            v-if="quest.status === 'IN_PROGRESS'"
            class="status"
          >进行中</span>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.today-tab {
  display: grid;
  gap: 12px;
}
.quest-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
}
.quest-card {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 12px;
  display: grid;
  gap: 6px;
}
header {
  display: flex;
  align-items: center;
  gap: 8px;
}
header h3 {
  margin: 0;
  font-size: 16px;
}
.badge {
  padding: 2px 6px;
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
  border-radius: 999px;
  font-size: 12px;
}
.badge.path_planner { background: #4f8a3e; }
.badge.classroom { background: #2f5b8a; }
.badge.system_default { background: #c47a35; }
.description {
  margin: 0;
  color: var(--pyt-color-text-primary);
  line-height: 1.55;
}
.meta {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.actions button {
  padding: 6px 12px;
  border-radius: 6px;
  border: 2px solid var(--pyt-color-text-hint);
  cursor: pointer;
}
.actions .primary {
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
}
.actions .ghost {
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
}
.status {
  background: #fff2c9;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.empty {
  padding: 14px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
.feedback {
  margin: 0;
  padding: 14px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
}
.error {
  color: var(--pyt-color-text-primary);
}
.error p {
  margin: 0 0 8px;
}
.error button {
  padding: 6px 12px;
  border-radius: 6px;
  border: 2px solid var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-hint);
  cursor: pointer;
}
</style>
