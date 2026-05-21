<script setup lang="ts">
import { onMounted } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import ReviewHallPanel from '@/components/review/ReviewHallPanel.vue';
import { useLearningStore } from '@stores/learning';

const learning = useLearningStore();

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
