<script setup lang="ts">
import { useDialogStore } from '@stores/dialog';

const dialog = useDialogStore();
</script>

<template>
  <section
    v-if="dialog.isOpen"
    class="dialog-modal"
    aria-live="polite"
  >
    <div class="dialog-panel">
      <p
        v-for="line in dialog.lines"
        :key="line"
      >
        {{ line }}
      </p>
      <button
        type="button"
        @click="dialog.close"
      >
        知道了
      </button>
    </div>
  </section>
</template>
