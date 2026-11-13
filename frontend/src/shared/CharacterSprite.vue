<template>
  <span v-if="image" class="character-sprite" aria-hidden="true" :style="spriteStyle"></span>
</template>
<script setup lang="ts">
import { computed } from "vue";
import type { Npc } from "./types";
import { useCharacterImage } from "./characterImage";
const props = defineProps<{ sprite?: Npc["sprite"] }>();
const image = useCharacterImage(computed(() => props.sprite?.url));
const spriteStyle = computed(() => {
  const width = props.sprite?.frame_width ?? 16, height = props.sprite?.frame_height ?? 20;
  const scale = Math.min(2, 28 / height, 28 / width);
  return { width: `${width * scale}px`, height: `${height * scale}px`, backgroundImage: `url("${image.value}")` };
});
</script>
<style scoped>
.character-sprite { display: inline-block; flex-shrink: 0; background-size: 300% 400%; background-position: 50% 0; background-repeat: no-repeat; image-rendering: pixelated; }
</style>
