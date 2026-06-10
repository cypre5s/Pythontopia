<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import ReviewHallPanel from '@/components/review/ReviewHallPanel.vue';
import { playBgm, stopBgm } from '@/composables/useAudio';
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
      <nav class="review-hall-page__actions">
        <RouterLink
          class="review-hall-page__feynman"
          :to="{ name: 'feynman' }"
        >
          测 · 费曼反演：教会 AI 学弟
        </RouterLink>
      </nav>
      <ReviewHallPanel />
    </section>
  </AppLayout>
</template>

<style scoped>
.review-hall-page {
  padding: 16px;
  min-height: calc(100vh - 64px);
  background: linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%);
}

.review-hall-page__actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--pyt-space-3);
}

.review-hall-page__feynman {
  border: 2px solid var(--pyt-color-border-strong);
  background: var(--pyt-color-primary);
  color: var(--pyt-color-text-on-dark);
  padding: var(--pyt-space-2) var(--pyt-space-4);
  font-family: var(--pyt-font-pixel);
  text-decoration: none;
}
</style>
