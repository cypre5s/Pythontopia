<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useLearningStore } from '@stores/learning';

const learning = useLearningStore();

const quests = computed(() => learning.quests.classroom);

onMounted(async () => {
  if (learning.quests.classroom.length === 0) {
    await learning.loadClassroomQuests();
  }
});
</script>

<template>
  <section
    class="classroom-tab"
    data-testid="classroom-tab"
  >
    <p
      v-if="quests.length === 0"
      class="empty"
    >
      暂时没有班级任务。教师在阶段 12 上线后会在这里发布。
    </p>
    <ul
      v-else
      class="quest-list"
    >
      <li
        v-for="quest in quests"
        :key="quest.id"
        class="quest-card"
      >
        <header>
          <h3>{{ quest.title }}</h3>
          <span class="badge">截止 {{ quest.dueDate }}</span>
        </header>
        <p
          v-if="quest.description"
          class="description"
        >
          {{ quest.description }}
        </p>
        <small>KC：{{ quest.kcCodes.join(', ') || '未指定' }} · 类型：{{ quest.nodeType }}</small>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.empty {
  padding: 14px;
  background: #fdf3d0;
  border-radius: 8px;
  text-align: center;
  color: #6e4f1d;
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
  border: 2px solid #c9a86b;
  border-radius: 8px;
  padding: 12px;
  display: grid;
  gap: 6px;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
header h3 {
  margin: 0;
  font-size: 16px;
}
.badge {
  padding: 2px 8px;
  background: #2f5b8a;
  color: #fff7e6;
  border-radius: 999px;
  font-size: 12px;
}
.description {
  margin: 0;
  color: #4d3613;
  line-height: 1.55;
}
small {
  color: #6e4f1d;
  font-size: 12px;
}
</style>
