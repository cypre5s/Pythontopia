<script setup lang="ts">
import { onMounted } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import DialogModal from '@/components/game/DialogModal.vue';
import GameCanvas from '@/components/game/GameCanvas.vue';
import { useWorldStore } from '@stores/world';

const world = useWorldStore();

onMounted(async () => {
  if (!world.session) {
    await world.loadSession();
  }
});
</script>

<template>
  <AppLayout>
    <section class="town-screen">
      <GameCanvas />
      <DialogModal />
    </section>
  </AppLayout>
</template>
