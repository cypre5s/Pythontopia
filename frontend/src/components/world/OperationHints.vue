<script setup lang="ts">
// 屏幕底部常驻操作提示条，让玩家进入 town_hub 时一眼看到核心操作（移动 / 交互 / 关闭 / 任务）。
// 设计要点：
//   1. v-if 只在 /world 路由显示，避免遮挡其他页面；
//   2. 纯展示组件，无 store 依赖，挂载到 WorldPage.vue 即可。
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const visible = computed<boolean>(() => route.path === '/world');
</script>

<template>
  <div
    v-if="visible"
    class="operation-hints"
    data-testid="operation-hints"
    role="status"
    aria-label="操作提示"
  >
    <span class="hint-item">
      <span class="hint-key">↑ ↓ ← →</span>
      <span class="hint-key">WASD</span>
      <span class="hint-text">移动</span>
    </span>
    <span class="hint-dot">·</span>
    <span class="hint-item">
      <span class="hint-key">F</span>
      <span class="hint-text">交互</span>
    </span>
    <span class="hint-dot">·</span>
    <span class="hint-item">
      <span class="hint-key">B</span>
      <span class="hint-text">背包</span>
    </span>
    <span class="hint-dot">·</span>
    <span class="hint-item">
      <span class="hint-key">M</span>
      <span class="hint-text">地图</span>
    </span>
    <span class="hint-dot">·</span>
    <span class="hint-item">
      <span class="hint-key">L</span>
      <span class="hint-text">课程</span>
    </span>
    <span class="hint-dot">·</span>
    <span class="hint-item">
      <span class="hint-key">,</span>
      <span class="hint-text">设置</span>
    </span>
    <span class="hint-dot">·</span>
    <span class="hint-item">
      <span class="hint-key">Tab</span>
      <span class="hint-text">任务</span>
    </span>
    <span class="hint-dot">·</span>
    <span class="hint-item">
      <span class="hint-key">ESC</span>
      <span class="hint-text">关闭</span>
    </span>
  </div>
</template>

<style scoped>
.operation-hints {
  position: fixed;
  left: 50%;
  bottom: 20px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  background: rgba(28, 24, 16, 0.78);
  border: 2px solid #d4af37;
  border-radius: 999px;
  color: var(--pyt-color-gold-bright);
  font-family: var(--pyt-font-pixel), 'PingFang SC', 'Noto Sans CJK SC', monospace;
  font-size: 14px;
  letter-spacing: 0.5px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
  pointer-events: none;
  z-index: 60;
  user-select: none;
  max-width: calc(100vw - 32px);
  flex-wrap: wrap;
  justify-content: center;
}

.hint-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.hint-key {
  display: inline-block;
  min-width: 22px;
  padding: 2px 7px;
  background: var(--pyt-color-gold-bright);
  color: var(--pyt-color-text-primary);
  border: 1px solid var(--pyt-color-text-primary);
  border-radius: 4px;
  font-weight: 700;
  font-size: 12px;
  line-height: 1.2;
  text-align: center;
  box-shadow: 1px 1px 0 var(--pyt-color-text-primary);
}

.hint-text {
  color: var(--pyt-color-gold-bright);
  font-size: 13px;
}

.hint-dot {
  color: rgba(255, 209, 102, 0.5);
  font-size: 14px;
}

/* 窄屏压缩间距，节省垂直空间不挤画布 */
@media (max-width: 760px) {
  .operation-hints {
    bottom: 8px;
    padding: 4px 10px;
    gap: 4px;
    font-size: 11px;
    max-width: calc(100vw - 16px);
  }
  .hint-key {
    min-width: 18px;
    padding: 1px 4px;
    font-size: 10px;
    box-shadow: 1px 1px 0 var(--pyt-color-text-primary);
  }
  .hint-text {
    font-size: 10px;
  }
}

@media (max-width: 480px) {
  .operation-hints {
    /* 移动端改为横向滚动，避免换行占高 */
    flex-wrap: nowrap;
    overflow-x: auto;
    scrollbar-width: none;
  }
  .operation-hints::-webkit-scrollbar {
    display: none;
  }
}
</style>
