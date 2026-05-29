<script setup lang="ts">
import { onMounted } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import TutorPanel from '@/components/tutor/TutorPanel.vue';
import { useTutorStore } from '@stores/tutor';

const tutor = useTutorStore();

onMounted(() => {
  void tutor.loadSessions();
});
</script>

<template>
  <AppLayout>
    <section
      class="tutor-page"
      data-testid="tutor-page"
    >
      <TutorPanel />
    </section>
  </AppLayout>
</template>

<style scoped>
.tutor-page {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - 56px);
  background:
    linear-gradient(180deg, rgba(255, 247, 230, 0.5), rgba(243, 228, 190, 0.7)),
    repeating-linear-gradient(135deg, rgba(110, 79, 29, 0.05) 0 6px, transparent 6px 14px),
    #fef6dd;
  overflow: hidden;
}
</style>
