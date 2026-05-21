<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import WorkshopPanel from '@/components/workshop/WorkshopPanel.vue';
import ResourceListPanel from '@/components/workshop/ResourceListPanel.vue';
import ResourceDetailPanel from '@/components/workshop/ResourceDetailPanel.vue';
import { useLearningStore } from '@stores/learning';

const learning = useLearningStore();
const route = useRoute();

const selectedId = computed<number | null>(() => {
  const id = route.query.id;
  if (typeof id === 'string' && /^\d+$/.test(id)) {
    return Number(id);
  }
  return null;
});

onMounted(async () => {
  if (learning.field.crops.length === 0) {
    await learning.loadField();
  }
  if (!learning.profile) {
    await learning.loadProfile();
  }
});

watch(
  selectedId,
  async (id) => {
    if (id !== null) {
      await learning.openResource(id);
    }
  },
  { immediate: false },
);
</script>

<template>
  <AppLayout>
    <section class="workshop-page">
      <div class="left">
        <WorkshopPanel />
        <ResourceListPanel />
      </div>
      <div class="right">
        <ResourceDetailPanel :resource-id="selectedId" />
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.workshop-page {
  display: grid;
  grid-template-columns: minmax(0, 360px) 1fr;
  gap: 18px;
  padding: 16px;
  background: linear-gradient(180deg, #fef7e3 0%, #f3e4be 100%);
  min-height: calc(100vh - 64px);
}
.left,
.right {
  display: grid;
  gap: 14px;
}
@media (max-width: 1024px) {
  .workshop-page {
    grid-template-columns: 1fr;
  }
}
</style>
