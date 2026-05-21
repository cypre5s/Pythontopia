<script setup lang="ts">
import type { StoryboardContent } from '@/api/dto/Workshop';

const props = defineProps<{
  content: StoryboardContent;
}>();
</script>

<template>
  <ol class="storyboard">
    <li
      v-for="(scene, index) in props.content.scenes"
      :key="index"
    >
      <header>
        <span class="badge">{{ scene.duration_seconds }}s</span>
        <h3>{{ scene.title }}</h3>
      </header>
      <p class="narration">
        旁白：{{ scene.narration }}
      </p>
      <p class="visual">
        画面：{{ scene.visual_hint }}
      </p>
      <small>关联 KC: {{ scene.kc_codes.join(', ') || '未标注' }}</small>
    </li>
  </ol>
</template>

<style scoped>
.storyboard {
  list-style: none;
  padding: 0;
  display: grid;
  gap: 12px;
}
.storyboard li {
  background: #fffbe9;
  border: 2px solid #c9a86b;
  border-radius: 8px;
  padding: 12px;
}
.storyboard header {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.storyboard h3 {
  margin: 0;
  font-size: 16px;
}
.badge {
  background: #4f8a3e;
  color: #fff7e6;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
}
.narration {
  margin: 6px 0;
  line-height: 1.55;
}
.visual {
  margin: 0 0 6px;
  color: #5e4923;
}
small {
  color: #6e4f1d;
  font-size: 12px;
}
</style>
