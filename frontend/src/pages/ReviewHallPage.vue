<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import ReviewHallPanel from '@/components/review/ReviewHallPanel.vue';
import { playBgm, stopBgm } from '@/utils/bgm';
import { useLearningStore } from '@stores/learning';

const learning = useLearningStore();

onMounted(() => {
  playBgm('bgm_quest');
});
onMounted(async () => {
  await Promise.all([
    learning.loadEvaluations(4),
    learning.loadActivePath(),
  ]);
  const [first] = learning.evaluation.list;
  if (first) {
    await learning.openEvaluation(first.id);
  }
});
onUnmounted(() => {
  stopBgm();
});
</script>

<template>
  <AppLayout>
    <section class="review-hall-page">
      <ReviewHallPanel />
    </section>
  </AppLayout>
</template>

<style scoped>
.review-hall-page {
  padding: 16px;
  min-height: calc(100vh - 64px);
  background: linear-gradient(180deg, #fef7e3 0%, #f3e4be 100%);
}
</style>
