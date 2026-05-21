<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import TrainingPanel from '@/components/training/TrainingPanel.vue';
import { useLearningStore } from '@stores/learning';

const learning = useLearningStore();
const route = useRoute();

const problemId = computed<number | null>(() => {
  const raw = route.query.problemId;
  if (typeof raw === 'string' && /^\d+$/.test(raw)) {
    return Number(raw);
  }
  return null;
});

const DEFAULT_PROBLEM_ID = 476;

onMounted(async () => {
  const target = problemId.value ?? DEFAULT_PROBLEM_ID;
  await learning.loadTrainingProblem(target);
});

watch(problemId, async (id) => {
  if (id) {
    await learning.loadTrainingProblem(id);
  }
});
</script>

<template>
  <AppLayout>
    <section class="training-page">
      <TrainingPanel />
    </section>
  </AppLayout>
</template>

<style scoped>
.training-page {
  padding: 16px;
  min-height: calc(100vh - 64px);
  background: linear-gradient(180deg, #fef7e3 0%, #f3e4be 100%);
}
</style>
