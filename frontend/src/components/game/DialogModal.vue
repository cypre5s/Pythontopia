<script setup lang="ts">
// @deprecated 由 DialogTree.vue 替代，保留仅用于过渡期 placeholder 入口与单元测试。
// 新代码请通过 store.openWithTree(npcKey) + DialogTree.vue（已挂载在 App.vue）走对话树渲染。
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
