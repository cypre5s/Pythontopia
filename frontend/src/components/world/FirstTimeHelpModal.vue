<script setup lang="ts">
// W4-LOGIN-UX：首次进入 /world 时显示操作引导，关闭后写入 localStorage 标记，
// 避免后续重复打扰。仅在客户端 mounted 后做 storage 检查，SSR 不会触发。
// W4-BGM：dismiss 是首次进入 /world 后保证发生的 user gesture，必须调用
// unlockAudio() 让浏览器 autoplay 策略放行，补放 WorldScene 已排队的 BGM。
import { onMounted, ref } from 'vue';
import { unlockAudio } from '@/composables/useAudio';

const HELP_FLAG = 'pyt_helped_v1';

const visible = ref(false);

function dismiss(): void {
  visible.value = false;
  unlockAudio();
  try {
    window.localStorage.setItem(HELP_FLAG, '1');
  } catch {
    // 隐私模式下 localStorage 可能不可用，忽略写入失败。
  }
}

onMounted(() => {
  try {
    const seen = window.localStorage.getItem(HELP_FLAG);
    if (!seen) {
      visible.value = true;
    }
  } catch {
    visible.value = true;
  }
});

defineExpose({
  reopen(): void {
    visible.value = true;
  },
});
</script>

<template>
  <div
    v-if="visible"
    class="pyt-help-modal"
    role="dialog"
    aria-modal="true"
    aria-labelledby="pyt-help-title"
  >
    <div
      class="pyt-help-backdrop"
      @click="dismiss"
    />
    <div class="pyt-help-panel">
      <h2
        id="pyt-help-title"
        class="pyt-help-title"
      >
        欢迎来到 Pythontopia
      </h2>
      <p class="pyt-help-lede">
        先花 10 秒看一下操作指南，让小镇之旅更顺畅。
      </p>
      <ul class="pyt-help-list">
        <li>
          <span class="pyt-help-key">WASD</span>
          <span class="pyt-help-key">↑ ↓ ← →</span>
          <span class="pyt-help-text">移动角色，走到地图边缘自动切换场景</span>
        </li>
        <li>
          <span class="pyt-help-key">F</span>
          <span class="pyt-help-text">与 NPC 对话、调查物品、领取任务</span>
        </li>
        <li>
          <span class="pyt-help-key">ESC</span>
          <span class="pyt-help-text">关闭当前对话或弹窗</span>
        </li>
        <li>
          <span class="pyt-help-key">点击</span>
          <span class="pyt-help-text">点击建筑或导航卡片，进入功能页面</span>
        </li>
      </ul>
      <button
        type="button"
        class="pyt-help-btn"
        autofocus
        @click="dismiss"
      >
        我知道了，开始探索
      </button>
    </div>
  </div>
</template>

<style scoped>
.pyt-help-modal {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pyt-help-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(28, 43, 24, 0.7);
  cursor: pointer;
}

.pyt-help-panel {
  position: relative;
  width: min(520px, calc(100vw - 32px));
  border: 3px solid #4d3613;
  border-radius: 12px;
  background: #fff7e6;
  box-shadow: 6px 6px 0 #4d3613, 0 16px 32px rgba(0, 0, 0, 0.3);
  padding: 28px 28px 24px;
}

.pyt-help-title {
  margin: 0 0 6px;
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', monospace);
  color: #d4af37;
  font-size: 28px;
  text-shadow: 2px 2px 0 #4d3613;
  letter-spacing: 1px;
}

.pyt-help-lede {
  margin: 0 0 18px;
  color: #6e4f1d;
  font-size: 14px;
}

.pyt-help-list {
  list-style: none;
  margin: 0 0 22px;
  padding: 0;
  display: grid;
  gap: 10px;
}

.pyt-help-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #fdf3d0;
  border: 2px solid #c9a86b;
  border-radius: 6px;
}

.pyt-help-key {
  display: inline-block;
  min-width: 44px;
  padding: 4px 8px;
  border: 2px solid #4d3613;
  border-radius: 4px;
  background: #ffd166;
  color: #4d3613;
  font-family: var(--pyt-font-pixel-mono, 'Ark Pixel 12 Mono', monospace);
  font-weight: 700;
  font-size: 13px;
  text-align: center;
  box-shadow: 2px 2px 0 #4d3613;
}

.pyt-help-text {
  flex: 1;
  color: #4d3613;
  font-size: 14px;
  line-height: 1.5;
}

.pyt-help-btn {
  width: 100%;
  min-height: 44px;
  background: #ffd166;
  border: 2px solid #4d3613;
  border-radius: 6px;
  color: #4d3613;
  font-weight: 800;
  font-size: 15px;
  cursor: pointer;
  box-shadow: 3px 3px 0 #4d3613;
}

.pyt-help-btn:active {
  transform: translate(2px, 2px);
  box-shadow: 1px 1px 0 #4d3613;
}
</style>
