<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import TrainingPanel from '@/components/training/TrainingPanel.vue';
import { useLearningStore } from '@stores/learning';

const learning = useLearningStore();
const route = useRoute();

// problemId 是共享课程题目（非私有），保留在 URL，以支持收藏 / 分享 / 刷新可达。
const problemId = computed<number | null>(() => {
  const raw = route.query.problemId;
  if (typeof raw === 'string' && /^\d+$/.test(raw)) {
    return Number(raw);
  }
  return null;
});

const DEFAULT_PROBLEM_ID = 476;

// S1：变式会话私有 id（bugMineSessionId）不再经 URL 传递，改由 BugMinePanel 在导航前写入
// store（setPendingVariantSession）承载；本页只负责加载共享课程题 problemId（非私有）。
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
  height: calc(100vh - 64px);
  min-height: calc(100vh - 64px);
  overflow: hidden;
  background: var(--pyt-color-bg-primary);
}
</style>
