<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { createPhaserGame, type PhaserGameHandle, type PhaserSceneConfig } from '@game/adapters/phaserGame';
import { BootScene } from '@game/scenes/BootScene';
import { TownScene } from '@game/scenes/TownScene';

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
    const scenes: PhaserSceneConfig[] = [BootScene, TownScene];
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
  <div class="game-frame">
    <div
      ref="root"
      class="game-root"
    />
    <div
      v-if="progress < 1"
      class="load-overlay"
    >
      <span>加载 {{ Math.round(progress * 100) }}%</span>
    </div>
  </div>
</template>
