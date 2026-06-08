<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import BugMinePanel from '@/components/bugmine/BugMinePanel.vue';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { useWorldStore } from '@stores/world';

const learning = useLearningStore();
const toast = useToastStore();
const world = useWorldStore();
const route = useRoute();
const router = useRouter();

const submissionId = computed<number | null>(() => {
  const raw = route.query.submissionId;
  if (typeof raw === 'string' && /^\d+$/.test(raw)) {
    return Number(raw);
  }
  return null;
});

const sessionId = computed<number | null>(() => {
  const raw = route.query.sessionId;
  if (typeof raw === 'string' && /^\d+$/.test(raw)) {
    return Number(raw);
  }
  return null;
});

async function bootstrap(): Promise<void> {
  await learning.loadBugMineErrorBook();
  if (submissionId.value != null) {
    const session = await learning.openBugMine(submissionId.value);
    if (!session) {
      toast.show(learning.bugMine.lastError ?? '无法打开矿洞会话', 'error');
      return;
    }
    await router.replace({
      path: '/bug-mine',
      query: { sessionId: String(session.id) },
    });
    return;
  }
  if (sessionId.value != null) {
    await learning.loadBugMine(sessionId.value);
    if (learning.bugMine.lastError) {
      toast.show(learning.bugMine.lastError, 'error');
    }
  }
}

onMounted(async () => {
  world.setPendingTravel('mine_south', { x: 15, y: 2 });
  window.dispatchEvent(new CustomEvent('pythontopia:world-travel'));
  await bootstrap();
});

watch([submissionId, sessionId], () => {
  void bootstrap();
});
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
  height: calc(100vh - 64px);
  min-height: calc(100vh - 64px);
  overflow: auto;
  padding: 16px;
  background: linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%);
}
</style>
