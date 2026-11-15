<template>
  <p v-if="error" class="developer-feedback is-error" role="alert">{{ error }}</p>
  <p v-else-if="message" class="developer-feedback" role="status">{{ message }}</p>
</template>

<script setup lang="ts">
defineProps<{ error?: string; message?: string }>();
</script>
