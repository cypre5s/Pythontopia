<script setup lang="ts">
import { useToastStore } from '@stores/toast';

const toast = useToastStore();
</script>

<template>
  <div
    v-if="toast.visible"
    class="toast"
    :class="toast.kind"
    :role="toast.kind === 'error' ? 'alert' : 'status'"
    :aria-live="toast.kind === 'error' ? 'assertive' : 'polite'"
  >
    {{ toast.message }}
  </div>
</template>
