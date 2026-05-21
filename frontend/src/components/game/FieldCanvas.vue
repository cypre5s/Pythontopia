<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { createPhaserGame, type PhaserGameHandle, type PhaserSceneConfig } from '@game/adapters/phaserGame';
import { BootScene } from '@game/scenes/BootScene';
import { FieldScene } from '@game/scenes/FieldScene';

const root = ref<HTMLElement | null>(null);
const progress = ref(0);
let game: PhaserGameHandle | null = null;

function onProgress(event: Event): void {
  const custom = event as CustomEvent<{ progress: number }>;
  progress.value = custom.detail.progress;
}

onMounted(() => {
  window.addEventListener('pythontopia:load-progress', onProgress);
  if (root.value) {
    const scenes: PhaserSceneConfig[] = [BootScene, FieldScene];
    game = createPhaserGame(root.value, scenes);
  }
});

onUnmounted(() => {
  window.removeEventListener('pythontopia:load-progress', onProgress);
  game?.destroy(true);
  game = null;
});
</script>

<template>
  <div class="game-frame field-frame">
    <div
      ref="root"
      class="game-root"
    />
    <div
      v-if="progress < 1"
      class="load-overlay"
    >
      <span>知识田加载 {{ Math.round(progress * 100) }}%</span>
    </div>
  </div>
</template>

<style scoped>
.field-frame {
  border: 2px solid #6e4f1d;
  border-radius: 8px;
  overflow: hidden;
  background: #f3e4be;
}
.load-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(110, 79, 29, 0.4);
  color: #fff7e6;
  font-family: monospace;
}
</style>
