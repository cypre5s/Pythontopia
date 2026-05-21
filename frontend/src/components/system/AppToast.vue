<script setup lang="ts">
import { useToastStore } from '@stores/toast';

const toast = useToastStore();
</script>

<template>
  <div
    v-if="toast.visible"
    class="toast"
    :class="toast.kind"
    role="status"
  >
    {{ toast.message }}
  </div>
</template>
