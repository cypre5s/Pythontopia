<template>
  <!-- Only parser-generated HTML: raw HTML, unsafe links and images are disabled. -->
  <!-- eslint-disable-next-line vue/no-v-html -->
  <div class="assistant-markdown" v-html="renderAssistantMarkdown(text)"></div>
</template>
<script setup lang="ts">
import { renderAssistantMarkdown } from "../shared/assistantMarkdown";
defineProps<{ text: string }>();
</script>
