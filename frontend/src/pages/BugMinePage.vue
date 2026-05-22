<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import BugMinePanel from '@/components/bugmine/BugMinePanel.vue';
import { playBgm, stopBgm } from '@/utils/bgm';
import { useLearningStore } from '@stores/learning';

const learning = useLearningStore();
const route = useRoute();

const sessionId = computed<number | null>(() => {
  const raw = route.query.sessionId;
  if (typeof raw === 'string' && /^\d+$/.test(raw)) {
    return Number(raw);
  }
  return null;
});

const submissionId = computed<number | null>(() => {
  const raw = route.query.submissionId;
  if (typeof raw === 'string' && /^\d+$/.test(raw)) {
    return Number(raw);
  }
  return null;
});

async function refresh(): Promise<void> {
  if (sessionId.value) {
    await learning.loadBugMine(sessionId.value);
    return;
  }
  if (submissionId.value) {
    await learning.openBugMine(submissionId.value);
  }
}

onMounted(() => {
  playBgm('bgm_bug_mine');
});
onMounted(refresh);
onUnmounted(() => {
  stopBgm();
});
watch([sessionId, submissionId], refresh);
</script>

<template>
  <AppLayout>
    <section class="bug-mine-page">
      <BugMinePanel />
    </section>
  </AppLayout>
</template>

<style scoped>
.bug-mine-page {
  padding: 16px;
  min-height: calc(100vh - 64px);
  background: linear-gradient(180deg, #1a0d05 0%, #2a1a0d 100%);
}
</style>
