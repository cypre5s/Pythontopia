<template>
  <div class="toast-layer" role="status" aria-live="polite">
    <p v-for="item in notices" :key="item.id">{{ item.text }}</p>
  </div>
</template>
<script setup lang="ts">
import { notices } from "./api";
</script>
