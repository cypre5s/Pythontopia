<script setup lang="ts">
/**
 * NPC 首句个性化寒暄横幅（R-15 §8）。
 *
 * 读 dialog store 的 greeting 状态，渲染在脚本对话首节点之前（挂载于对话面板顶部）：
 *   - loading：低调「正在回忆…」提示（与 meta/dialog 并行拉取期间）；
 *   - ready：记忆感知个性化寒暄（如「上次 for 循环错了 3 次，今天去矿洞复盘？」）；
 *   - fallback：记忆/agent 服务不可用 →「（记忆服务暂不可用）」，照常走脚本（不伪造内容）；
 *   - none/idle 或加载出错：不渲染（直接走脚本）。
 *
 * 自包含、零 props：与 DialogTree / DialogModal 同样消费 dialog store，便于由对话面板直接内嵌。
 * 颜色全部走 tokens.css（--pyt-color-*）。
 */
import { computed } from 'vue';
import { useDialogStore } from '@/stores/dialog';

const dialog = useDialogStore();

/** 仅在对话正常展开（无加载错误）且寒暄处于可展示态时渲染。 */
const show = computed<boolean>(() => {
  if (!dialog.visible || dialog.loadError !== null) {
    return false;
  }
  return (
    dialog.greetingStatus === 'loading' ||
    dialog.greetingStatus === 'ready' ||
    dialog.greetingStatus === 'fallback'
  );
});
</script>

<template>
  <div
    v-if="show"
    class="npc-greeting pyt-pixel-text"
    data-testid="npc-greeting-banner"
  >
    <p
      v-if="dialog.greetingStatus === 'loading'"
      class="npc-greeting__hint"
      data-testid="npc-greeting-loading"
    >
      正在回忆你的近期学情……
    </p>
    <p
      v-else-if="dialog.greetingStatus === 'ready'"
      class="npc-greeting__text"
      data-testid="npc-greeting"
    >
      <span
        class="npc-greeting__icon"
        aria-hidden="true"
      >✦</span>{{ dialog.greetingText }}
    </p>
    <p
      v-else
      class="npc-greeting__hint npc-greeting__hint--unavailable"
      data-testid="npc-greeting-fallback"
    >
      （记忆服务暂不可用）
    </p>
  </div>
</template>

<style scoped>
.npc-greeting {
  margin: 0 0 var(--pyt-space-2);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-left: 4px solid var(--pyt-color-gold);
  border-radius: var(--pyt-radius-md);
  color: var(--pyt-color-text-primary);
}

.npc-greeting__text {
  margin: 0;
  font-size: var(--pyt-font-size-base);
  line-height: 1.7;
  color: var(--pyt-color-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.npc-greeting__icon {
  margin-right: var(--pyt-space-1);
  color: var(--pyt-color-gold);
}

.npc-greeting__hint {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
  font-style: italic;
}

.npc-greeting__hint--unavailable {
  color: var(--pyt-color-text-hint);
}
</style>
