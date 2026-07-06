<script setup lang="ts">
import { onMounted } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import BoardPanel from '@/components/board/BoardPanel.vue';
import { useLearningStore } from '@stores/learning';

const learning = useLearningStore();

onMounted(async () => {
  await Promise.all([
    learning.loadActivePath(),
    learning.loadTodayQuests(),
    learning.loadClassroomQuests(),
  ]);
});
</script>

<template>
  <AppLayout>
    <section class="board-page">
      <BoardPanel />
    </section>
  </AppLayout>
</template>

<style scoped>
.board-page {
  padding: 16px;
  min-height: calc(100vh - 64px);
  background: linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%);
}
</style>
